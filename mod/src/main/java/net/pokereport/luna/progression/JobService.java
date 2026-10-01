package net.pokereport.luna.progression;

import java.sql.SQLException;
import net.pokereport.luna.db.Database;

/** Selección persistente de un único trabajo activo entre las ocho especialidades. */
public final class JobService {
    public record Estado(Path trabajo, long actividades, long plataGanada) {}
    private final Database db;

    public JobService(Database db) { this.db = db; }

    public Estado estado(long playerId) throws SQLException {
        try (var c = db.connection(); var ps = c.prepareStatement(
                "SELECT job_id,activities,silver_earned FROM player_job WHERE player_id=? AND left_at IS NULL")) {
            ps.setLong(1, playerId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return new Estado(null, 0, 0);
                try {
                    Path p = Path.valueOf(rs.getString(1));
                    return p.esTrabajoSeleccionable() ? new Estado(p, rs.getLong(2), rs.getLong(3))
                            : new Estado(null, 0, 0);
                } catch (IllegalArgumentException e) {
                    return new Estado(null, 0, 0);
                }
            }
        }
    }

    public String seleccionar(long playerId, String nombre) throws SQLException {
        final Path trabajo;
        try { trabajo = Path.valueOf(nombre == null ? "" : nombre.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { return "Ese trabajo no existe."; }
        if (!trabajo.esTrabajoSeleccionable()) return "Ese trabajo no se puede seleccionar.";
        try (var c = db.connection()) {
            c.setAutoCommit(false);
            try {
                // Bloquea la fila estable del jugador: también serializa la
                // primera selección, cuando player_job todavía no existe.
                try (var lock = c.prepareStatement(
                        "SELECT player_id FROM player WHERE player_id=? FOR UPDATE")) {
                    lock.setLong(1, playerId); lock.executeQuery();
                }
                try (var actual = c.prepareStatement(
                        "SELECT 1 FROM player_job WHERE player_id=? AND left_at IS NULL")) {
                    actual.setLong(1, playerId);
                    try (var rs = actual.executeQuery()) {
                        if (rs.next()) {
                            c.rollback();
                            return "Primero debes abandonar tu trabajo actual.";
                        }
                    }
                }
                try (var ps = c.prepareStatement("""
                        INSERT INTO player_job(player_id,job_id,selected_at,left_at,activities,silver_earned)
                        VALUES(?,?,CURRENT_TIMESTAMP(3),NULL,0,0)
                        ON DUPLICATE KEY UPDATE job_id=VALUES(job_id),selected_at=VALUES(selected_at),
                          left_at=NULL,activities=0,silver_earned=0
                        """)) {
                    ps.setLong(1, playerId); ps.setString(2, trabajo.name()); ps.executeUpdate();
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return null;
    }

    public String abandonar(long playerId) throws SQLException {
        try (var c = db.connection(); var ps = c.prepareStatement(
                "UPDATE player_job SET left_at=CURRENT_TIMESTAMP(3) WHERE player_id=? AND left_at IS NULL")) {
            ps.setLong(1, playerId);
            return ps.executeUpdate() == 1 ? null : "No tienes un trabajo activo.";
        }
    }

    public boolean esActivo(long playerId, Path trabajo) throws SQLException {
        Estado e = estado(playerId);
        return e.trabajo() == trabajo;
    }

    public void anotarActividad(long playerId) throws SQLException {
        try (var c = db.connection(); var ps = c.prepareStatement(
                "UPDATE player_job SET activities=activities+1 WHERE player_id=? AND left_at IS NULL")) {
            ps.setLong(1, playerId); ps.executeUpdate();
        }
    }

    public void anotarGanancia(long playerId, long plata) throws SQLException {
        if (plata <= 0) return;
        try (var c = db.connection(); var ps = c.prepareStatement(
                "UPDATE player_job SET silver_earned=silver_earned+? WHERE player_id=? AND left_at IS NULL")) {
            ps.setLong(1, plata); ps.setLong(2, playerId); ps.executeUpdate();
        }
    }
}
