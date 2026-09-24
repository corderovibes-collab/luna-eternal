package net.pokereport.luna.kit;

import net.pokereport.luna.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;
import net.pokereport.luna.economy.EconomyService;

/**
 * Reclamación y adquisición de kits, con el cooldown y auditoría en la base de datos.
 *
 * <p>El cooldown <b>no vive en memoria</b>. Uno en memoria se reinicia al
 * reiniciar el servidor, y ese es el exploit más barato que existe: reclamar,
 * esperar un reinicio, reclamar otra vez. Tampoco vive en el cliente (P6).
 */
public class KitService {

    /** Registro auditable e inmutable de compra de un kit exclusivo. */
    public record ExclusivePurchaseRecord(
            String purchaseId,
            String playerUuid,
            long playerId,
            String kitId,
            long price,
            String currency,
            long balanceBefore,
            long balanceAfter,
            String purchaseStatus,
            String claimStatus,
            LocalDateTime purchasedAt,
            LocalDateTime claimedAt) {}

    /** Consumidor de entrega de items (permite desacoplar lógica de entrega para tests y headless). */
    @FunctionalInterface
    public interface ItemDeliveryConsumer {
        void deliver(KitCatalog.Kit kit) throws Exception;
    }

    /** Estado de un kit para un jugador. */
    public record Status(boolean claimable, LocalDateTime nextAvailable,
                         int timesClaimed, String reason) {

        public static Status ready() { return new Status(true, null, 0, null); }

        /** Lo que queda, en texto. */
        public String remaining() {
            if (nextAvailable == null) return "";
            Duration d = Duration.between(LocalDateTime.now(), nextAvailable);
            if (d.isNegative()) return "ya";
            long h = d.toHours();
            return h >= 1 ? h + " h " + (d.toMinutes() % 60) + " min"
                          : d.toMinutes() + " min";
        }
    }

    private final Database db;
    private final ConcurrentHashMap<Long, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

    public KitService(Database db) {
        this.db = db;
    }

    protected Connection getConnection() throws SQLException {
        if (db == null) throw new SQLException("Database no configurada");
        return db.connection();
    }

    /**
     * ⚠⚠⚠ EL TIEMPO SE MIDE EN LA BASE, NO EN JAVA, Y ESO NO ES UN DETALLE.
     *
     * <p>La fecha se guarda con {@code CURRENT_TIMESTAMP(3)} —el reloj de
     * MariaDB— y aquí se comparaba con {@code LocalDateTime.now()}, que es el
     * reloj de la JVM. <b>Son dos relojes distintos y no están en la misma zona
     * horaria</b>: medido en producción, MariaDB va en UTC y el servidor de
     * juego cuatro horas por detrás. Resultado: una espera de 24 h se anunciaba
     * como <b>27 h 59 min</b>.
     */
    public Status status(long playerId, KitCatalog.Kit kit) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT times_claimed, "
               + "TIMESTAMPDIFF(SECOND, last_claimed, CURRENT_TIMESTAMP(3)) "
               + "FROM kit_claim WHERE player_id = ? AND kit_id = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, kit.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Status.ready();

                int times = rs.getInt(1);
                long transcurrido = rs.getLong(2);

                if (kit.once()) {
                    return new Status(false, null, times, "Ya lo reclamaste");
                }
                long faltan = kit.cooldownHours() * 3600L - transcurrido;
                if (faltan > 0) {
                    return new Status(false, LocalDateTime.now().plusSeconds(faltan),
                                      times, null);
                }
                return new Status(true, null, times, null);
            }
        }
    }

    /**
     * Reclama el kit. Devuelve {@code true} si procede entregarlo.
     *
     * <p>La comprobación y la marca van en <b>la misma transacción con la fila
     * bloqueada</b>. Sin eso, dos clics rápidos —o dos sesiones a la vez—
     * pasarían ambos la comprobación y el kit se entregaría dos veces.
     */
    public boolean claim(long playerId, KitCatalog.Kit kit) throws SQLException {
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                int times = 0;
                boolean existe = false;
                long transcurrido = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT times_claimed, "
                      + "TIMESTAMPDIFF(SECOND, last_claimed, CURRENT_TIMESTAMP(3)) "
                      + "FROM kit_claim WHERE player_id = ? AND kit_id = ? FOR UPDATE")) {
                    ps.setLong(1, playerId);
                    ps.setString(2, kit.id());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            existe = true;
                            times = rs.getInt(1);
                            transcurrido = rs.getLong(2);
                        }
                    }
                }

                if (existe) {
                    if (kit.once()) { c.rollback(); return false; }
                    if (transcurrido < kit.cooldownHours() * 3600L) {
                        c.rollback();
                        return false;
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE kit_claim SET last_claimed = CURRENT_TIMESTAMP(3), "
                          + "times_claimed = ? WHERE player_id = ? AND kit_id = ?")) {
                        ps.setInt(1, times + 1);
                        ps.setLong(2, playerId);
                        ps.setString(3, kit.id());
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO kit_claim (player_id, kit_id, last_claimed, "
                          + "times_claimed) VALUES (?,?,CURRENT_TIMESTAMP(3),1)")) {
                        ps.setLong(1, playerId);
                        ps.setString(2, kit.id());
                        ps.executeUpdate();
                    }
                }

                c.commit();
                return true;

            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Construye la pila de un objeto del kit, con sus encantamientos.
     */
    public static ItemStack pila(KitCatalog.KitItem it, MinecraftServer servidor) {
        ItemStack pila = new ItemStack(it.item(), it.count());
        if (it.encantamientos().isEmpty()) {
            return pila;
        }
        var registro = servidor.getRegistryManager()
                .getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
        for (var e : it.encantamientos().entrySet()) {
            var clave = RegistryKey.of(RegistryKeys.ENCHANTMENT, e.getKey());
            var entrada = registro.getOptional(clave);
            if (entrada.isEmpty()) {
                LunaEternal.LOG.warn("El encantamiento {} no existe: se entrega sin el",
                        e.getKey());
                continue;
            }
            pila.addEnchantment(entrada.get(), e.getValue());
        }
        return pila;
    }

    /**
     * Entrega un kit de rango a un jugador conectado.
     */
    public String entregar(ServerPlayerEntity jugador, long playerId,
                           KitCatalog.Kit kit) throws SQLException {
        if (kit.requiredRank() != null) {
            var pide = net.pokereport.luna.ui.Tablist.Rank.de(kit.requiredRank());
            if (net.pokereport.luna.ui.Tablist.escalonDe(jugador) < pide.escalon) {
                return "te falta el rango " + kit.requiredRank();
            }
        }

        int libres = 0;
        var inv = jugador.getInventory();
        for (int i = 0; i < inv.main.size(); i++) {
            if (inv.main.get(i).isEmpty()) {
                libres++;
            }
        }
        int requeridos = Math.min(kit.items().size(), 24);
        if (libres < requeridos) {
            return "necesitas al menos " + requeridos + " huecos libres en la mochila";
        }

        if (!claim(playerId, kit)) {
            return "todavia no toca";
        }

        var servidor = jugador.getServer();
        try {
            for (var it : kit.items()) {
                ItemStack pila = pila(it, servidor);
                if (!inv.insertStack(pila)) {
                    jugador.dropItem(pila, false);
                }
            }
        } catch (Exception e) {
            LunaEternal.LOG.error("Fallo al entregar el kit {} a {}", kit.id(),
                    jugador.getGameProfile().getName(), e);
            undo(playerId, kit);
            return "no se pudo entregar; vuelve a intentarlo";
        }
        return null;
    }

    /**
     * Compra la propiedad de un kit exclusivo y lo transfiere a 'Mis Kits'.
     */
    public String comprar(ServerPlayerEntity jugador, long playerId,
                          KitCatalog.Kit kit, EconomyService economy)
            throws SQLException {
        UUID uuid = jugador != null ? jugador.getUuid() : null;
        return comprar(uuid, playerId, kit, economy, UUID.randomUUID().toString());
    }

    /**
     * Compra atómica y blindada de un kit exclusivo con clave de idempotencia explícita y bloqueo por jugador.
     */
    public String comprar(UUID playerUuid, long playerId,
                          KitCatalog.Kit kit, EconomyService economy,
                          String purchaseId) throws SQLException {
        if (!"exclusive".equals(kit.category())) {
            return "kit exclusivo inválido";
        }
        if (kit.lunaPrice() != KitCatalog.PRECIO_KIT_EXCLUSIVO) {
            return "precio de kit exclusivo inválido";
        }
        if (!kit.enabled()) {
            return "este kit se encuentra deshabilitado";
        }
        Instant now = Instant.now();
        if (kit.availableFrom() != null && now.isBefore(kit.availableFrom())) {
            return "este kit aún no está disponible";
        }
        if (kit.availableUntil() != null && now.isAfter(kit.availableUntil())) {
            return "este kit ha expirado";
        }

        ReentrantLock lock = playerLocks.computeIfAbsent(playerId, p -> new ReentrantLock());
        lock.lock();
        try {
            String cleanPurchaseId = (purchaseId == null || purchaseId.isBlank())
                    ? UUID.randomUUID().toString()
                    : purchaseId;
            String uuidStr = playerUuid != null ? playerUuid.toString() : ("00000000-0000-0000-0000-" + String.format("%012d", playerId));

            try (Connection c = getConnection()) {
                c.setAutoCommit(false);
                try {
                    // 1. Validar límite de compra
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT COUNT(*) FROM exclusive_kit_purchase WHERE player_id = ? AND kit_id = ? AND purchase_status = 'COMPLETED' FOR UPDATE")) {
                        ps.setLong(1, playerId);
                        ps.setString(2, kit.id());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next() && rs.getInt(1) >= kit.purchaseLimit()) {
                                c.rollback();
                                return "ya posees este kit";
                            }
                        }
                    }

                    // 2. Bloquear y verificar saldo en player_economy
                    long balanceBefore = 0;
                    boolean foundEconomy = false;
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT balance FROM player_economy WHERE player_id = ? AND currency = ? FOR UPDATE")) {
                        ps.setLong(1, playerId);
                        ps.setString(2, Currency.REPORTCOIN.name());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                foundEconomy = true;
                                balanceBefore = rs.getLong(1);
                            }
                        }
                    }

                    if (!foundEconomy || balanceBefore < kit.lunaPrice()) {
                        c.rollback();
                        return "no tienes suficientes LunaCoins";
                    }

                    // 3. Débito contable mediante EconomyService con clave de idempotencia
                    String idempotencyKey = "KITPURCHASE:" + uuidStr + ":" + kit.id() + ":" + cleanPurchaseId;
                    economy.applyInTransaction(c, playerId, Currency.REPORTCOIN, -kit.lunaPrice(),
                            "exclusive_kit", "kit", null, idempotencyKey);

                    long balanceAfter = balanceBefore - kit.lunaPrice();

                    // 4. Registrar en exclusive_kit_purchase
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO exclusive_kit_purchase ("
                          + "purchase_id, player_uuid, player_id, kit_id, price, currency, "
                          + "balance_before, balance_after, purchase_status, claim_status, purchased_at"
                          + ") VALUES (?, ?, ?, ?, ?, 'REPORTCOIN', ?, ?, 'COMPLETED', 'PENDING', CURRENT_TIMESTAMP(3))")) {
                        ps.setString(1, cleanPurchaseId);
                        ps.setString(2, uuidStr);
                        ps.setLong(3, playerId);
                        ps.setString(4, kit.id());
                        ps.setLong(5, kit.lunaPrice());
                        ps.setLong(6, balanceBefore);
                        ps.setLong(7, balanceAfter);
                        ps.executeUpdate();
                    }

                    // 5. Compatibilidad: registrar en kit_claim
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT IGNORE INTO kit_claim (player_id, kit_id, last_claimed, times_claimed) "
                          + "VALUES (?, ?, CURRENT_TIMESTAMP(3), 1)")) {
                        ps.setLong(1, playerId);
                        ps.setString(2, "exclusive:" + kit.id());
                        ps.executeUpdate();
                    }

                    c.commit();
                    return null;

                } catch (EconomyException e) {
                    c.rollback();
                    return "no tienes suficientes LunaCoins";
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean posee(long playerId, KitCatalog.Kit kit) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT 1 FROM exclusive_kit_purchase WHERE player_id = ? AND kit_id = ? AND purchase_status = 'COMPLETED' "
               + "UNION SELECT 1 FROM kit_claim WHERE player_id = ? AND kit_id = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, kit.id());
            ps.setLong(3, playerId);
            ps.setString(4, "exclusive:" + kit.id());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean haReclamado(long playerId, KitCatalog.Kit kit) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT 1 FROM exclusive_kit_purchase WHERE player_id = ? AND kit_id = ? AND purchase_status = 'COMPLETED' AND claim_status = 'CLAIMED' "
               + "UNION SELECT 1 FROM kit_claim WHERE player_id = ? AND kit_id = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, kit.id());
            ps.setLong(3, playerId);
            ps.setString(4, "exclusive_claim:" + kit.id());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Reclama el kit exclusivo de un jugador conectado.
     */
    public String reclamarExclusivo(ServerPlayerEntity jugador, long playerId,
                                   KitCatalog.Kit kit) throws SQLException {
        int libres = 0;
        var inv = jugador.getInventory();
        for (int i = 0; i < inv.main.size(); i++) {
            if (inv.main.get(i).isEmpty()) {
                libres++;
            }
        }
        var servidor = jugador.getServer();
        return reclamarExclusivo(playerId, kit, libres, k -> {
            for (var it : k.items()) {
                ItemStack pila = pila(it, servidor);
                if (!inv.insertStack(pila)) {
                    jugador.dropItem(pila, false);
                }
            }
        });
    }

    /**
     * Reclama el contenido del kit exclusivo.
     * Si no hay huecos suficientes, la compra NUNCA se pierde ni se dropean items:
     * permanece como claim PENDING en 'Mis Kits'.
     */
    public String reclamarExclusivo(long playerId, KitCatalog.Kit kit, int libres,
                                   ItemDeliveryConsumer deliveryConsumer) throws SQLException {
        if (!posee(playerId, kit)) {
            return "no posees este kit; adquiérelo primero en 'Kits Exclusivos'";
        }
        if (haReclamado(playerId, kit)) {
            return "ya has reclamado el contenido de este kit";
        }
        int requeridos = Math.min(kit.items().size(), 24);
        if (libres < requeridos) {
            return "necesitas al menos " + requeridos + " espacios vacíos en tu inventario";
        }

        ReentrantLock lock = playerLocks.computeIfAbsent(playerId, p -> new ReentrantLock());
        lock.lock();
        try {
            String purchaseId = null;
            try (Connection c = getConnection()) {
                c.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT purchase_id FROM exclusive_kit_purchase "
                          + "WHERE player_id = ? AND kit_id = ? AND purchase_status = 'COMPLETED' AND claim_status = 'PENDING' "
                          + "LIMIT 1 FOR UPDATE")) {
                        ps.setLong(1, playerId);
                        ps.setString(2, kit.id());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                purchaseId = rs.getString(1);
                            }
                        }
                    }

                    if (purchaseId == null) {
                        c.rollback();
                        return "ya has reclamado el contenido de este kit";
                    }

                    if (purchaseId != null) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE exclusive_kit_purchase SET claim_status = 'CLAIMED', claimed_at = CURRENT_TIMESTAMP(3) "
                              + "WHERE purchase_id = ?")) {
                            ps.setString(1, purchaseId);
                            ps.executeUpdate();
                        }
                        try (PreparedStatement ps = c.prepareStatement(
                                "INSERT IGNORE INTO kit_claim (player_id, kit_id, last_claimed, times_claimed) "
                              + "VALUES (?, ?, CURRENT_TIMESTAMP(3), 1)")) {
                            ps.setLong(1, playerId);
                            ps.setString(2, "exclusive_claim:" + kit.id());
                            ps.executeUpdate();
                        }
                    }
                    c.commit();
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            }

            if (deliveryConsumer != null) {
                try {
                    deliveryConsumer.deliver(kit);
                } catch (Exception e) {
                    LunaEternal.LOG.error("Fallo al entregar el kit exclusivo {} al jugador {}", kit.id(), playerId, e);
                    deshacerReclamacionExclusiva(playerId, kit.id(), purchaseId);
                    return "error al entregar los objetos; vuelve a intentarlo";
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    public void deshacerReclamacionExclusiva(long playerId, String kitId, String purchaseId) throws SQLException {
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                if (purchaseId != null) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE exclusive_kit_purchase SET claim_status = 'PENDING', claimed_at = NULL "
                          + "WHERE purchase_id = ?")) {
                        ps.setString(1, purchaseId);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM kit_claim WHERE player_id = ? AND kit_id = ?")) {
                    ps.setLong(1, playerId);
                    ps.setString(2, "exclusive_claim:" + kitId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // ---- Métodos analíticos y telemetría de kits exclusivos ------------------

    public long totalKitsVendidos() throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM exclusive_kit_purchase WHERE purchase_status = 'COMPLETED'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    public long totalLunaCoinsGastadas() throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COALESCE(SUM(price), 0) FROM exclusive_kit_purchase WHERE purchase_status = 'COMPLETED'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    public Map<String, Long> ventasPorKit() throws SQLException {
        Map<String, Long> map = new LinkedHashMap<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT kit_id, COUNT(*) FROM exclusive_kit_purchase WHERE purchase_status = 'COMPLETED' GROUP BY kit_id")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString(1), rs.getLong(2));
                }
            }
        }
        return map;
    }

    public List<ExclusivePurchaseRecord> comprasDeJugador(long playerId) throws SQLException {
        List<ExclusivePurchaseRecord> list = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT purchase_id, player_uuid, player_id, kit_id, price, currency, "
               + "balance_before, balance_after, purchase_status, claim_status, purchased_at, claimed_at "
               + "FROM exclusive_kit_purchase WHERE player_id = ? ORDER BY purchased_at DESC")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRecord(rs));
                }
            }
        }
        return list;
    }

    public ExclusivePurchaseRecord obtenerCompra(String purchaseId) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT purchase_id, player_uuid, player_id, kit_id, price, currency, "
               + "balance_before, balance_after, purchase_status, claim_status, purchased_at, claimed_at "
               + "FROM exclusive_kit_purchase WHERE purchase_id = ?")) {
            ps.setString(1, purchaseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRecord(rs);
                }
            }
        }
        return null;
    }

    private static ExclusivePurchaseRecord mapRecord(ResultSet rs) throws SQLException {
        var pAt = rs.getTimestamp(11);
        var cAt = rs.getTimestamp(12);
        return new ExclusivePurchaseRecord(
            rs.getString(1),
            rs.getString(2),
            rs.getLong(3),
            rs.getString(4),
            rs.getLong(5),
            rs.getString(6),
            rs.getLong(7),
            rs.getLong(8),
            rs.getString(9),
            rs.getString(10),
            pAt != null ? pAt.toLocalDateTime() : null,
            cAt != null ? cAt.toLocalDateTime() : null
        );
    }

    // ---- Métodos auxiliares y cooldown de kits genéricos ---------------------

    public boolean claimOnce(long playerId, String key) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT IGNORE INTO kit_claim (player_id, kit_id, last_claimed) "
               + "VALUES (?,?,CURRENT_TIMESTAMP(3))")) {
            ps.setLong(1, playerId);
            ps.setString(2, key);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean hasClaimed(long playerId, String key) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT 1 FROM kit_claim WHERE player_id = ? AND kit_id = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public void undoOnce(long playerId, String key) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "DELETE FROM kit_claim WHERE player_id = ? AND kit_id = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, key);
            ps.executeUpdate();
        }
    }

    public void undo(long playerId, KitCatalog.Kit kit) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE kit_claim SET times_claimed = GREATEST(times_claimed - 1, 0), "
               + "last_claimed = DATE_SUB(last_claimed, INTERVAL ? HOUR) "
               + "WHERE player_id = ? AND kit_id = ?")) {
            ps.setInt(1, Math.max(kit.cooldownHours(), 1));
            ps.setLong(2, playerId);
            ps.setString(3, kit.id());
            ps.executeUpdate();
        }
    }

    public long rotacionEpochMs() {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT UNIX_TIMESTAMP(rota_en) * 1000 FROM kit_rotacion WHERE id = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long l = rs.getLong(1);
                    if (l > 0) return l;
                }
            }
        } catch (Exception e) {
            LunaEternal.LOG.warn("No se pudo leer la rotacion de kits: {}", e.toString());
        }
        return System.currentTimeMillis() + 5184000000L; // 60 dias por defecto
    }

    public void resetRotacion(int dias) throws SQLException {
        int d = Math.max(1, dias);
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO kit_rotacion (id, rota_en) VALUES (1, DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? DAY)) ON DUPLICATE KEY UPDATE rota_en = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? DAY)")) {
            ps.setInt(1, d);
            ps.setInt(2, d);
            ps.executeUpdate();
        }
    }
}
