package net.pokereport.luna.lunita;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Memoria de Lunita por jugador.
 *
 * <p>La concesión se decide y registra en una única transacción de MariaDB.
 * No hay contador de RAM: dos clics concurrentes compiten por el mismo
 * {@code UPDATE} condicional y solo uno puede mover {@code last_reward}.
 */
public final class LunitaMemoryService {
    public static final long RECOMPENSA_PLATA = 1_000L;
    public static final long COOLDOWN_MILLIS = 12L * 60L * 60L * 1_000L;

    private final Database db;

    public LunitaMemoryService(Database db) {
        this.db = db;
    }

    public record Interaccion(boolean regalo, long restantesMillis,
                              int saludos, int afinidad) {}

    /** Registra un saludo y entrega Plata una sola vez por ventana de 12 h. */
    public Interaccion saludar(long playerId) throws Exception {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                asegurarFila(c, playerId);
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE lunita_player_memory
                           SET last_interaction = CURRENT_TIMESTAMP(3),
                               greetings = greetings + 1,
                               affinity = LEAST(100, affinity + 1),
                               last_reward = CURRENT_TIMESTAMP(3)
                         WHERE player_id = ?
                           AND (last_reward IS NULL
                                OR last_reward <= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 12 HOUR))
                        """)) {
                    ps.setLong(1, playerId);
                    boolean regalo = ps.executeUpdate() == 1;
                    if (regalo) {
                        // UUID determinista: incluso tras una caída entre el UPDATE
                        // y el commit, el libro contable nunca acepta un duplicado.
                        String slot = Long.toString(System.currentTimeMillis() / COOLDOWN_MILLIS);
                        String clave = UUID.nameUUIDFromBytes(
                                ("lunita:" + playerId + ':' + slot)
                                        .getBytes(StandardCharsets.UTF_8)).toString();
                        LunaEternal.economy().applyInTransaction(c, playerId,
                                Currency.POKEDOLLAR, RECOMPENSA_PLATA,
                                "lunita_regalo", "lunita", null, clave);
                    } else {
                        actualizarSaludoSinRegalo(c, playerId);
                    }
                    Interaccion resultado = leer(c, playerId, regalo);
                    c.commit();
                    return resultado;
                }
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static void asegurarFila(Connection c, long playerId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO lunita_player_memory (player_id)
                VALUES (?) ON DUPLICATE KEY UPDATE player_id = VALUES(player_id)
                """)) {
            ps.setLong(1, playerId);
            ps.executeUpdate();
        }
    }

    private static void actualizarSaludoSinRegalo(Connection c, long playerId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE lunita_player_memory
                   SET last_interaction = CURRENT_TIMESTAMP(3),
                       greetings = greetings + 1,
                       affinity = LEAST(100, affinity + 1)
                 WHERE player_id = ?
                """)) {
            ps.setLong(1, playerId);
            ps.executeUpdate();
        }
    }

    private static Interaccion leer(Connection c, long playerId, boolean regalo) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT greetings, affinity,
                       GREATEST(0, TIMESTAMPDIFF(MICROSECOND, CURRENT_TIMESTAMP(3),
                           DATE_ADD(last_reward, INTERVAL 12 HOUR)) / 1000)
                  FROM lunita_player_memory WHERE player_id = ?
                """)) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Memoria de Lunita ausente");
                return new Interaccion(regalo, regalo ? 0L : rs.getLong(3),
                        rs.getInt(1), rs.getInt(2));
            }
        }
    }
}
