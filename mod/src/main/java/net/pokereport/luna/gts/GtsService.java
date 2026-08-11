package net.pokereport.luna.gts;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mercado entre jugadores.
 *
 * <p>Sostiene la economía entera: su <b>impuesto progresivo es el único sink
 * que escala con la riqueza</b>. Los consumibles cuestan lo mismo al novato
 * que al millonario; el impuesto no
 * ({@code docs/economy/economy-overview.md} §4).
 *
 * <p>Dos invariantes que no se negocian:
 * <ol>
 *   <li><b>Custodia.</b> Lo listado sale del inventario y vive en la tabla.
 *       Nunca puede estar en dos sitios.</li>
 *   <li><b>Una sola venta.</b> {@code SELECT … FOR UPDATE} bloquea la fila
 *       hasta el commit, así que dos compradores simultáneos no pueden
 *       llevarse el mismo objeto.</li>
 * </ol>
 */
public final class GtsService {

    /** Comisión al publicar, en tanto por mil. 10 ‰ = 1 %. */
    public static final long LISTING_FEE_PER_MILLE = 10;

    /** Duración de un listado. */
    public static final int LISTING_HOURS = 48;

    /** Tramos del impuesto de venta: {hasta, porcentaje}. */
    private static final long[][] TAX_BRACKETS = {
        {     10_000,  5},
        {    100_000,  8},
        {  1_000_000, 12},
        {Long.MAX_VALUE, 18}
    };

    private final Database db;

    public GtsService(Database db) {
        this.db = db;
    }

    /** Un listado, tal y como se enseña. */
    public record Listing(long id, long sellerId, String sellerName, String state,
                          String displayName, String itemId, int quantity,
                          long price, LocalDateTime expiresAt, byte[] payload) {}

    /** Resultado de una operación, ya redactado para el jugador. */
    public record Result(boolean ok, String message, byte[] payload) {
        public static Result fail(String msg) { return new Result(false, msg, null); }
        public static Result ok(String msg)   { return new Result(true, msg, null); }
    }

    // ------------------------------------------------------------ impuesto

    /**
     * Impuesto de una venta. Progresivo por tramos, como el IRPF: cada tramo
     * grava solo la parte que le corresponde.
     *
     * <p>Aplicar el porcentaje del tramo alto a todo el importe crearía saltos
     * absurdos —vender por 10 001 dejaría menos neto que vender por 10 000— y
     * los jugadores lo detectan enseguida.
     */
    public static long taxFor(long price) {
        long tax = 0;
        long remaining = price;
        long previous = 0;
        for (long[] bracket : TAX_BRACKETS) {
            if (remaining <= 0) break;
            long span = Math.min(remaining, bracket[0] - previous);
            tax += span * bracket[1] / 100;
            remaining -= span;
            previous = bracket[0];
        }
        return tax;
    }

    public static long listingFee(long price) {
        return Math.max(1, price * LISTING_FEE_PER_MILLE / 1000);
    }

    // ------------------------------------------------------------ publicar

    /**
     * Publica un listado. Cobra la tasa por adelantado y <b>no la devuelve</b>
     * aunque no se venda: es lo que hace caro inundar el mercado de precios
     * falsos para manipular la percepción de valor.
     */
    public Result publish(long sellerId, byte[] payload, String displayName,
                          String itemId, int quantity, long price)
            throws SQLException {

        if (price <= 0) return Result.fail("§cEl precio debe ser positivo.");
        if (payload == null || payload.length == 0) {
            return Result.fail("§cNo hay nada que publicar.");
        }

        long fee = listingFee(price);

        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                // La tasa se cobra dentro de la MISMA transacción que crea el
                // listado: o hay listado y se cobró, o no hay ninguna de las dos.
                LunaEternal.economy().applyInTransaction(
                    c, sellerId, Currency.POKEDOLLAR, -fee,
                    "gts_listing", "gts", null, UUID.randomUUID().toString());

                long id;
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO gts_listing
                          (seller_id, kind, payload, payload_hash, price,
                           display_name, item_id, quantity, expires_at)
                        VALUES (?, 'ITEM', ?, ?, ?, ?, ?, ?,
                                DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? HOUR))
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, sellerId);
                    ps.setBytes(2, payload);
                    ps.setString(3, sha256(payload));
                    ps.setLong(4, price);
                    ps.setString(5, displayName);
                    ps.setString(6, itemId);
                    ps.setInt(7, quantity);
                    ps.setInt(8, LISTING_HOURS);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        id = keys.next() ? keys.getLong(1) : -1;
                    }
                }

                c.commit();
                return Result.ok("§aPublicado por §f" + fmt(price)
                    + " §7(tasa: §f" + fmt(fee) + "§7, no reembolsable) §8#" + id);

            } catch (EconomyException e) {
                c.rollback();
                return Result.fail(e.kind == EconomyException.Kind.INSUFFICIENT_FUNDS
                    ? "§cNo tienes para pagar la tasa de " + fmt(fee) + "."
                    : "§c" + e.getMessage());
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // ------------------------------------------------------------ comprar

    /**
     * Compra un listado. Toda la operación en una transacción, con la fila
     * bloqueada: dos compradores simultáneos no pueden llevarse lo mismo.
     *
     * @return el payload en custodia si la compra salió bien
     */
    public Result buy(long buyerId, long listingId) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                long sellerId, price;
                byte[] payload;
                String name;

                // FOR UPDATE: si otro comprador ya está dentro de esta
                // transacción, aquí se espera; y cuando pase, el estado ya no
                // será ACTIVE y la consulta no devolverá nada.
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT seller_id, price, payload, display_name
                        FROM gts_listing
                        WHERE listing_id = ? AND state = 'ACTIVE'
                              AND expires_at > CURRENT_TIMESTAMP(3)
                        FOR UPDATE
                        """)) {
                    ps.setLong(1, listingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return Result.fail("§cEse listado ya no está disponible.");
                        }
                        sellerId = rs.getLong(1);
                        price    = rs.getLong(2);
                        payload  = rs.getBytes(3);
                        name     = rs.getString(4);
                    }
                }

                if (sellerId == buyerId) {
                    c.rollback();
                    return Result.fail("§cNo puedes comprarte a ti mismo.");
                }

                long tax = taxFor(price);
                long net = price - tax;
                String key = UUID.randomUUID().toString();

                var economy = LunaEternal.economy();
                economy.applyInTransaction(c, buyerId, Currency.POKEDOLLAR, -price,
                    "gts_buy", "gts", listingId, key + ":buy");
                economy.applyInTransaction(c, sellerId, Currency.POKEDOLLAR, net,
                    "gts_sale", "gts", listingId, key + ":sale");
                // El impuesto NO va a un fondo: se destruye. Un sink que
                // reaparece en otro sitio no es un sink.

                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE gts_listing
                        SET state = 'SOLD', buyer_id = ?, sold_at = CURRENT_TIMESTAMP(3)
                        WHERE listing_id = ? AND state = 'ACTIVE'
                        """)) {
                    ps.setLong(1, buyerId);
                    ps.setLong(2, listingId);
                    if (ps.executeUpdate() != 1) {
                        c.rollback();
                        return Result.fail("§cEse listado ya no está disponible.");
                    }
                }

                c.commit();
                return new Result(true,
                    "§aComprado §f" + name + " §7por §f" + fmt(price), payload);

            } catch (EconomyException e) {
                c.rollback();
                return Result.fail(e.kind == EconomyException.Kind.INSUFFICIENT_FUNDS
                    ? "§cNo tienes suficiente dinero." : "§c" + e.getMessage());
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // ------------------------------------------------------------ cancelar

    /** Retira un listado propio. La tasa no se devuelve. */
    public Result cancel(long sellerId, long listingId) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                byte[] payload;
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT payload FROM gts_listing
                        WHERE listing_id = ? AND seller_id = ? AND state = 'ACTIVE'
                        FOR UPDATE
                        """)) {
                    ps.setLong(1, listingId);
                    ps.setLong(2, sellerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return Result.fail("§cNo se puede cancelar ese listado.");
                        }
                        payload = rs.getBytes(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE gts_listing SET state='CANCELLED' WHERE listing_id=?")) {
                    ps.setLong(1, listingId);
                    ps.executeUpdate();
                }
                c.commit();
                return new Result(true, "§eListado retirado.", payload);
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // ------------------------------------------------------------ consultas

    public List<Listing> browse(int page, int perPage) throws SQLException {
        return query("""
            SELECT g.listing_id, g.seller_id, p.username, g.state, g.display_name,
                   g.item_id, g.quantity, g.price, g.expires_at, NULL
            FROM gts_listing g JOIN player p ON p.player_id = g.seller_id
            WHERE g.state = 'ACTIVE' AND g.expires_at > CURRENT_TIMESTAMP(3)
            ORDER BY g.listed_at DESC
            LIMIT ? OFFSET ?
            """, ps -> {
                ps.setInt(1, perPage);
                ps.setInt(2, page * perPage);
            });
    }

    public List<Listing> mine(long sellerId) throws SQLException {
        return query("""
            SELECT g.listing_id, g.seller_id, p.username, g.state, g.display_name,
                   g.item_id, g.quantity, g.price, g.expires_at, NULL
            FROM gts_listing g JOIN player p ON p.player_id = g.seller_id
            WHERE g.seller_id = ? AND g.state = 'ACTIVE'
            ORDER BY g.listed_at DESC LIMIT 45
            """, ps -> ps.setLong(1, sellerId));
    }

    public int activeCount(long sellerId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM gts_listing WHERE seller_id=? AND state='ACTIVE'")) {
            ps.setLong(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ------------------------------------------------------- entrega diferida

    /** Algo que el jugador tiene pendiente de recibir. */
    public record Claim(long listingId, String displayName, byte[] payload, String reason) {}

    /**
     * Lo que el jugador tiene pendiente de recibir.
     *
     * <p>Existe porque el dinero vive en la base y los objetos en el
     * inventario: <b>no hay transacción atómica entre los dos</b>. Si el
     * servidor cae justo después del commit de una compra, el comprador ha
     * pagado y no tiene nada. Con esto, la entrega deja de ser un efecto
     * secundario y pasa a ser un estado consultable.
     *
     * <p>Cubre tres casos, y el tercero no estaba resuelto de ninguna forma:
     * comprado sin entregar, cancelado sin devolver, y <b>caducado sin
     * devolver</b> — antes, un listado que vencía dejaba el objeto perdido.
     */
    public List<Claim> pendingClaims(long playerId) throws SQLException {
        List<Claim> out = new ArrayList<>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                SELECT listing_id, display_name, payload, state, seller_id
                FROM gts_listing
                WHERE delivered_at IS NULL
                  AND (   (state = 'SOLD'      AND buyer_id  = ?)
                       OR (state IN ('CANCELLED','EXPIRED') AND seller_id = ?))
                LIMIT 64
                """)) {
            ps.setLong(1, playerId);
            ps.setLong(2, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String state = rs.getString("state");
                    out.add(new Claim(
                        rs.getLong("listing_id"),
                        rs.getString("display_name"),
                        rs.getBytes("payload"),
                        switch (state) {
                            case "SOLD"      -> "compra";
                            case "CANCELLED" -> "listado retirado";
                            default          -> "listado caducado";
                        }));
                }
            }
        }
        return out;
    }

    /**
     * Marca una reclamación como entregada.
     *
     * <p><b>Se llama DESPUÉS de que el objeto esté en el inventario</b>, nunca
     * antes. Si se marcara primero y la entrega fallara, el objeto
     * desaparecería para siempre; marcándolo después, lo peor que puede pasar
     * es entregarlo dos veces en un cierre inesperado — y entregar de más es
     * recuperable, perder no.
     */
    public void markDelivered(long listingId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                UPDATE gts_listing SET delivered_at = CURRENT_TIMESTAMP(3)
                WHERE listing_id = ? AND delivered_at IS NULL
                """)) {
            ps.setLong(1, listingId);
            ps.executeUpdate();
        }
    }

    /** Marca como caducados los listados vencidos. */
    public int expireOld() throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                UPDATE gts_listing SET state='EXPIRED'
                WHERE state='ACTIVE' AND expires_at <= CURRENT_TIMESTAMP(3)
                """)) {
            return ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------ interno

    @FunctionalInterface
    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private List<Listing> query(String sql, Binder binder) throws SQLException {
        List<Listing> out = new ArrayList<>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Listing(
                        rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getInt(7), rs.getLong(8),
                        rs.getTimestamp(9).toLocalDateTime(), null));
                }
            }
        }
        return out;
    }

    private static String sha256(byte[] data) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static String fmt(long v) {
        return String.format("%,d", v);
    }
}
