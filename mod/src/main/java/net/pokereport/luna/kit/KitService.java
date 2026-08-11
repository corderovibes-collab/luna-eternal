package net.pokereport.luna.kit;

import net.pokereport.luna.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;

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

    public Status status(long playerId, KitCatalog.Kit kit) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT last_claimed, times_claimed FROM kit_claim "
               + "WHERE player_id = ? AND kit_id = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, kit.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Status.ready();

                LocalDateTime last = rs.getTimestamp(1).toLocalDateTime();
                int times = rs.getInt(2);

                if (kit.once()) {
                    return new Status(false, null, times, "Ya lo reclamaste");
                }
                LocalDateTime next = last.plusHours(kit.cooldownHours());
                if (LocalDateTime.now().isBefore(next)) {
                    return new Status(false, next, times, null);
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

                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT last_claimed, times_claimed FROM kit_claim "
                      + "WHERE player_id = ? AND kit_id = ? FOR UPDATE")) {
                    ps.setLong(1, playerId);
                    ps.setString(2, kit.id());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            existe = true;
                            last = rs.getTimestamp(1).toLocalDateTime();
                            times = rs.getInt(2);
                        }
                    }
                }

                if (existe) {
                    if (kit.once()) { c.rollback(); return false; }
                    if (LocalDateTime.now().isBefore(last.plusHours(kit.cooldownHours()))) {
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
