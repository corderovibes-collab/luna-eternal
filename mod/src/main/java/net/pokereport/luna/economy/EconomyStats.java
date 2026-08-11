package net.pokereport.luna.economy;

import net.pokereport.luna.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Telemetría económica (ECO-003).
 *
 * <p>Todos los números del diseño —margen de expedición, tramos del impuesto,
 * tope de kits, XP de las vías— son <b>estimaciones razonadas, no medidas</b>.
 * Esto es lo que las convierte en decisiones informadas.
 *
 * <p>No hace falta instrumentar nada: el libro de asientos ya lleva motivo y
 * fecha en cada movimiento desde el primer día, así que todo esto es un
 * {@code GROUP BY}. Esa fue la razón de diseñarlo así (R3).
 */
public final class EconomyStats {

    /** Cuánto entra y cuánto sale por una vía concreta. */
    public record Flow(String reason, long created, long destroyed, long operations) {
        public long net() { return created - destroyed; }
    }

    /** Reparto de riqueza. Las medias mienten; los percentiles no. */
    public record Distribution(long players, long total, long p50, long p90, long p99,
                               long max) {
        public long average() { return players == 0 ? 0 : total / players; }
    }

    private final Database db;

    public EconomyStats(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------ masa monetaria

    /** Cuánto dinero existe, por moneda. */
    public Map<Currency, Long> moneySupply() throws SQLException {
        Map<Currency, Long> out = new EnumMap<>(Currency.class);
        for (Currency c : Currency.values()) out.put(c, 0L);

        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT currency, COALESCE(SUM(balance),0) FROM player_economy "
               + "GROUP BY currency")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(Currency.valueOf(rs.getString(1)), rs.getLong(2));
                }
            }
        }
        return out;
    }

    /**
     * Comprueba que la masa monetaria cuadra con el libro.
     *
     * <p>Es la auditoría global: si el total de saldos no coincide con la suma
     * de todos los asientos, <b>hay dinero creado o perdido fuera del
     * sistema</b>. Devuelve la diferencia, que debe ser cero.
     */
    public long supplyDiscrepancy(Currency currency) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                SELECT
                  (SELECT COALESCE(SUM(balance),0) FROM player_economy WHERE currency = ?)
                  -
                  (SELECT COALESCE(SUM(delta),0) FROM ledger_entry WHERE currency = ?)
                """)) {
            ps.setString(1, currency.name());
            ps.setString(2, currency.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    // ------------------------------------------------------------ flujos

    /**
     * Entradas y salidas por motivo en las últimas {@code horas}.
     *
     * <p>Es la respuesta a la pregunta que gobierna toda la economía:
     * <b>¿de dónde sale el dinero y por dónde se va?</b> Si el grifo principal
     * deja de ser jugar, se ve aquí antes que en las quejas.
     */
    public List<Flow> flows(Currency currency, int horas) throws SQLException {
        List<Flow> out = new ArrayList<>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                SELECT reason,
                       COALESCE(SUM(CASE WHEN delta > 0 THEN delta ELSE 0 END),0),
                       COALESCE(SUM(CASE WHEN delta < 0 THEN -delta ELSE 0 END),0),
                       COUNT(*)
                FROM ledger_entry
                WHERE currency = ?
                  AND created_at >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL ? HOUR)
                GROUP BY reason
                ORDER BY (SUM(ABS(delta))) DESC
                """)) {
            ps.setString(1, currency.name());
            ps.setInt(2, horas);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Flow(rs.getString(1), rs.getLong(2),
                                     rs.getLong(3), rs.getLong(4)));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------ riqueza

    /**
     * Reparto de riqueza por percentiles.
     *
     * <p>La media es inútil aquí: un jugador con diez millones la dispara y
     * oculta que el resto no tiene nada. El P50 dice cómo vive la mitad de la
     * gente, y la distancia entre P50 y P99 dice si la economía se está
     * concentrando.
     */
    public Distribution wealth(Currency currency) throws SQLException {
        List<Long> saldos = new ArrayList<>();
        long total = 0;

        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT balance FROM player_economy WHERE currency = ? "
               + "ORDER BY balance ASC")) {
            ps.setString(1, currency.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long b = rs.getLong(1);
                    saldos.add(b);
                    total += b;
                }
            }
        }

        if (saldos.isEmpty()) return new Distribution(0, 0, 0, 0, 0, 0);
        return new Distribution(
            saldos.size(), total,
            percentil(saldos, 50), percentil(saldos, 90), percentil(saldos, 99),
            saldos.get(saldos.size() - 1));
    }

    private static long percentil(List<Long> ordenados, int p) {
        if (ordenados.isEmpty()) return 0;
        int idx = (int) Math.ceil(p / 100.0 * ordenados.size()) - 1;
        return ordenados.get(Math.max(0, Math.min(idx, ordenados.size() - 1)));
    }

    // ------------------------------------------------------------ actividad

    /** Jugadores registrados y cuántos han hecho algo en las últimas horas. */
    public long activePlayers(int horas) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(DISTINCT player_id) FROM ledger_entry "
               + "WHERE created_at >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL ? HOUR)")) {
            ps.setInt(1, horas);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** Total de jugadores conocidos. */
    public long totalPlayers() throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM player")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /**
     * Cuánto gana un jugador activo al día, en mediana.
     *
     * <p>Es <b>el número que falta</b> para calibrar todo lo demás: el margen
     * de expedición, el tope de los kits y los tramos del impuesto se definen
     * como fracciones de esto.
     */
    public long medianDailyIncome(Currency currency) throws SQLException {
        List<Long> ingresos = new ArrayList<>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                SELECT player_id, SUM(delta) AS ingreso
                FROM ledger_entry
                WHERE currency = ? AND delta > 0
                  AND created_at >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 24 HOUR)
                GROUP BY player_id
                ORDER BY ingreso ASC
                """)) {
            ps.setString(1, currency.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ingresos.add(rs.getLong(2));
            }
        }
        return percentil(ingresos, 50);
    }
}
