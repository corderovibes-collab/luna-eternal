package net.pokereport.luna.kit;

import net.pokereport.luna.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;

/**
 * Reclamación de kits, con el cooldown en la base de datos.
 *
 * <p>El cooldown <b>no vive en memoria</b>. Uno en memoria se reinicia al
 * reiniciar el servidor, y ese es el exploit más barato que existe: reclamar,
 * esperar un reinicio, reclamar otra vez. Tampoco vive en el cliente (P6).
 */
public final class KitService {

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

    public KitService(Database db) {
        this.db = db;
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
     *
     * <p>⚠⚠ Y no daba ningún error, ni se veía en el log, ni se podía notar
     * hasta que alguien reclamó un kit por primera vez — el catálogo llevaba
     * desde PHASE 3 sin puerta, así que este fallo llevaba ahí desde entonces.
     *
     * <p>Preguntando a la base <b>cuántos segundos han pasado</b>, los dos
     * extremos de la resta salen del mismo reloj y la zona horaria deja de
     * importar.
     */
    public Status status(long playerId, KitCatalog.Kit kit) throws SQLException {
        try (Connection c = db.connection();
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
                    // ⚠ `nextAvailable` se construye sumando los segundos que
                    //   faltan a AHORA, no a la fecha guardada: asi quien lo lea
                    //   con el reloj de la JVM saca el mismo numero.
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
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                LocalDateTime last = null;
                int times = 0;
                boolean existe = false;

                // ⚠⚠ Los segundos transcurridos los cuenta LA BASE. Ver el
                //    javadoc de `status`: aqui se comparaba con el reloj de la
                //    JVM, que va en otra zona horaria, y la espera salia cuatro
                //    horas mas larga de lo que es.
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
     *
     * <p>⚠ Un encantamiento que no exista se salta con un aviso en vez de tumbar
     * la entrega: el jugador prefiere su armadura sin encantar a no recibir
     * nada, y el aviso deja rastro para arreglarlo.
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
     * Entrega un kit a un jugador conectado.
     *
     * <h2>⚠⚠⚠ SE COMPRUEBA EL SITIO ANTES DE RECLAMAR, NO DESPUÉS</h2>
     *
     * {@link #claim} marca la fecha y arranca el reloj de 24 h. Si se marcara
     * primero y luego no cupiera nada, el jugador <b>habría gastado el kit</b> y
     * las piezas estarían por el suelo o perdidas. Por eso el hueco se cuenta
     * antes: con la mochila llena no se reclama y no se gasta nada.
     *
     * <h2>⚠⚠ Y SI ALGO FALLA DESPUÉS DE MARCAR, SE DESHACE</h2>
     *
     * Es la única parte que no puede vivir en la transacción —un inventario no
     * es una tabla— así que se deshace a mano, igual que hace el escaparate
     * cuando no se puede pagar la tasa.
     *
     * @return {@code null} si fue bien; si no, la razón para enseñársela
     */
    public String entregar(ServerPlayerEntity jugador, long playerId,
                           KitCatalog.Kit kit) throws SQLException {
        // ⚠ El rango se mira aqui y no en la pantalla: el cliente manda un
        //   identificador y nada mas (P6).
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
        if (libres < kit.items().size()) {
            return "necesitas " + kit.items().size() + " huecos libres en la mochila";
        }

        if (!claim(playerId, kit)) {
            return "todavia no toca";
        }

        var servidor = jugador.getServer();
        try {
            for (var it : kit.items()) {
                ItemStack pila = pila(it, servidor);
                if (!inv.insertStack(pila)) {
                    // ⚠ No deberia pasar --el hueco se conto antes-- pero si
                    //   pasa, al suelo antes que al vacio.
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
     * Reclamación única y genérica, para cosas que no son kits del catálogo
     * pero comparten la misma garantía: <b>una vez y solo una</b>.
     *
     * <p>Reutiliza la tabla y el bloqueo de fila en vez de inventar otra
     * mecánica. La elección del inicial es el caso que lo motivó: dos clics
     * rápidos no pueden entregar dos Pokémon.
     *
     * @return {@code true} si es la primera vez
     */
    public boolean claimOnce(long playerId, String key) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT IGNORE INTO kit_claim (player_id, kit_id, last_claimed) "
               + "VALUES (?,?,CURRENT_TIMESTAMP(3))")) {
            ps.setLong(1, playerId);
            ps.setString(2, key);
            // INSERT IGNORE: si la fila ya existe no inserta y devuelve 0.
            // La unicidad la garantiza la clave primaria, no una comprobación
            // previa que podría adelantarse otro hilo.
            return ps.executeUpdate() > 0;
        }
    }

    /** ¿Ya reclamó esta cosa única? */
    public boolean hasClaimed(long playerId, String key) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT 1 FROM kit_claim WHERE player_id = ? AND kit_id = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** Deshace una reclamación única. */
    public void undoOnce(long playerId, String key) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "DELETE FROM kit_claim WHERE player_id = ? AND kit_id = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, key);
            ps.executeUpdate();
        }
    }

    /**
     * Deshace una reclamación.
     *
     * <p>Solo para cuando la entrega falla después de haber marcado. Es
     * preferible marcar primero y deshacer si algo va mal que entregar primero
     * y arriesgarse a marcar dos veces: entregar de más es un regalo,
     * reclamar de más es un exploit.
     */
    public void undo(long playerId, KitCatalog.Kit kit) throws SQLException {
        try (Connection c = db.connection();
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
}
