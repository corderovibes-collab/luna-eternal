package net.pokereport.luna.hunt;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cazas y Crianza (HUNT-001).
 *
 * <p><b>Las mismas para todo el servidor</b>, rotando cada 12 horas. Es
 * decisión del usuario y es la correcta: que todos persigan lo mismo a la vez
 * crea conversación, y convierte la rotación en una fila de base de datos en
 * vez de N.
 *
 * <p><b>Solo cuenta capturar.</b> También se planteó contar combates, y se
 * descartó por una razón concreta: un combate contra otro jugador se amaña en
 * dos minutos, así que la caza dejaría de significar nada.
 */
public final class HuntService {

    /** Cada cuánto rota. 12 h: cabe una sesión de tarde y una de noche. */
    private static final long HORAS = 12;

    /** Cuántos objetivos de cada tipo por ciclo. */
    private static final int CAZAS = 3;
    private static final int CRIANZAS = 2;

    public enum Tipo { CAPTURA, CRIANZA }

    public record Objetivo(long id, Tipo tipo, String especie, int necesarios,
                           long premioDolar, long premioMarca,
                           int hechos, boolean cobrado) {
        public boolean completo() { return hechos >= necesarios; }
    }

    public record Ciclo(long id, long terminaEn, List<Objetivo> objetivos) {}

    private final Database db;

    public HuntService(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------ rotación

    /**
     * Devuelve el ciclo vigente, creándolo si hace falta.
     *
     * <p>Se genera <b>bajo demanda</b>, no con un temporizador. Un temporizador
     * exige que el servidor esté encendido en el instante exacto del cambio; si
     * estuvo caído, la rotación se salta y nadie se entera. Así, el primero que
     * mira después de las 12 h la provoca.
     */
    public Ciclo cicloActual(long playerId) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                Long id = vigente(c);
                if (id == null) id = crearCiclo(c);
                Ciclo ciclo = leer(c, id, playerId);
                c.commit();
                return ciclo;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private Long vigente(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM hunt_cycle WHERE ends_at > CURRENT_TIMESTAMP(3) "
              + "ORDER BY id DESC LIMIT 1 FOR UPDATE")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private long crearCiclo(Connection c) throws SQLException {
        long id;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO hunt_cycle (ends_at) VALUES "
              + "(DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? HOUR))",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, HORAS);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                id = rs.getLong(1);
            }
        }

        var elegidas = Especies.sortear(CAZAS + CRIANZAS);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO hunt_target (cycle_id, kind, species, needed, "
              + "reward_dollar, reward_mark) VALUES (?,?,?,?,?,?)")) {
            for (int i = 0; i < elegidas.size(); i++) {
                boolean caza = i < CAZAS;
                var esp = elegidas.get(i);
                // Criar cuesta mucho más que capturar, así que pide una sola
                // unidad y paga más. Si pidiera tres, nadie lo intentaría.
                int necesarios = caza ? 1 + (i % 3) : 1;
                ps.setLong(1, id);
                ps.setString(2, caza ? "CAPTURA" : "CRIANZA");
                ps.setString(3, esp.nombre());
                ps.setInt(4, necesarios);
                ps.setLong(5, (caza ? 600L : 1500L) * necesarios);
                ps.setLong(6, (caza ? 8L : 20L) * necesarios);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        LunaEternal.LOG.info("Cazas: ciclo {} creado, rota en {} h", id, HORAS);
        return id;
    }

    private Ciclo leer(Connection c, long cicloId, long playerId)
            throws SQLException {
        long termina = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT UNIX_TIMESTAMP(ends_at) FROM hunt_cycle WHERE id=?")) {
            ps.setLong(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) termina = rs.getLong(1);
            }
        }

        List<Objetivo> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT t.id, t.kind, t.species, t.needed, t.reward_dollar,
                       t.reward_mark,
                       COALESCE(p.done, 0), p.claimed_at IS NOT NULL
                FROM hunt_target t
                LEFT JOIN hunt_progress p
                       ON p.target_id = t.id AND p.player_id = ?
                WHERE t.cycle_id = ?
                ORDER BY t.kind DESC, t.id
                """)) {
            ps.setLong(1, playerId);
            ps.setLong(2, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Objetivo(rs.getLong(1),
                        Tipo.valueOf(rs.getString(2)), rs.getString(3),
                        rs.getInt(4), rs.getLong(5), rs.getLong(6),
                        rs.getInt(7), rs.getBoolean(8)));
                }
            }
        }
        return new Ciclo(cicloId, termina, out);
    }

    // ------------------------------------------------------------ progreso

    /**
     * Suma uno a los objetivos vivos de esa especie.
     *
     * <p>No falla si no hay ninguno: capturar algo que no está de caza es lo
     * normal, no un error.
     */
    public void avanzar(long playerId, String especie, Tipo tipo) {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                INSERT INTO hunt_progress (player_id, target_id, done)
                SELECT ?, t.id, 1
                  FROM hunt_target t
                  JOIN hunt_cycle y ON y.id = t.cycle_id
                 WHERE t.species = ? AND t.kind = ?
                   AND y.ends_at > CURRENT_TIMESTAMP(3)
                ON DUPLICATE KEY UPDATE done = done + 1
                """)) {
            ps.setLong(1, playerId);
            ps.setString(2, especie);
            ps.setString(3, tipo.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            LunaEternal.LOG.error("No se pudo avanzar la caza de {}", especie, e);
        }
    }

    // ------------------------------------------------------------ cobro

    public enum Resultado { PAGADO, NO_COMPLETO, YA_COBRADO, CADUCADO }

    /**
     * Entrega el premio de un objetivo, una sola vez.
     *
     * <p>Todo ocurre en una transacción: se marca cobrado y se paga en el mismo
     * bloque. Si el pago fallase, la marca se deshace. Y el {@code claimed_at
     * IS NULL} del UPDATE es lo que hace imposible cobrar dos veces aunque
     * lleguen dos clics a la vez: el segundo actualiza cero filas.
     */
    public Resultado cobrar(long playerId, long objetivoId, UUID clave) {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                long dolar, marca;
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT t.reward_dollar, t.reward_mark, t.needed,
                               COALESCE(p.done,0), p.claimed_at,
                               y.ends_at > CURRENT_TIMESTAMP(3)
                          FROM hunt_target t
                          JOIN hunt_cycle y ON y.id = t.cycle_id
                          LEFT JOIN hunt_progress p
                                 ON p.target_id = t.id AND p.player_id = ?
                         WHERE t.id = ?
                         FOR UPDATE
                        """)) {
                    ps.setLong(1, playerId);
                    ps.setLong(2, objetivoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) return Resultado.CADUCADO;
                        dolar = rs.getLong(1);
                        marca = rs.getLong(2);
                        int necesarios = rs.getInt(3), hechos = rs.getInt(4);
                        boolean cobrado = rs.getTimestamp(5) != null;
                        boolean vivo = rs.getBoolean(6);
                        if (!vivo) return Resultado.CADUCADO;
                        if (cobrado) return Resultado.YA_COBRADO;
                        if (hechos < necesarios) return Resultado.NO_COMPLETO;
                    }
                }

                int filas;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE hunt_progress SET claimed_at = CURRENT_TIMESTAMP(3) "
                      + "WHERE player_id=? AND target_id=? AND claimed_at IS NULL")) {
                    ps.setLong(1, playerId);
                    ps.setLong(2, objetivoId);
                    filas = ps.executeUpdate();
                }
                if (filas == 0) {
                    c.rollback();
                    return Resultado.YA_COBRADO;
                }

                var eco = LunaEternal.economy();
                eco.applyInTransaction(c, playerId, Currency.POKEDOLLAR, dolar,
                    "hunt_reward", "hunt", objetivoId, clave + ":d");
                eco.applyInTransaction(c, playerId, Currency.MARK, marca,
                    "hunt_reward", "hunt", objetivoId, clave + ":m");

                c.commit();
                return Resultado.PAGADO;
            } catch (Exception e) {
                c.rollback();
                LunaEternal.LOG.error("Error cobrando la caza {}", objetivoId, e);
                return Resultado.NO_COMPLETO;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("Sin conexión al cobrar la caza", e);
            return Resultado.NO_COMPLETO;
        }
    }
}
