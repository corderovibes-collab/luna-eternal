package net.pokereport.luna.clan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import net.pokereport.luna.db.Database;

/** Persistencia independiente del hogar del clan. */
public final class ClanHomeService {
    public record ClanHome(long clanId, String dimension, double x, double y,
                           double z, float yaw, float pitch) {}

    private final Database db;

    public ClanHomeService(Database db) {
        this.db = db;
    }

    public ClanHome get(long clanId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT clan_id, dimension, x, y, z, yaw, pitch "
                   + "FROM clan_home WHERE clan_id = ?")) {
            ps.setLong(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new ClanHome(rs.getLong("clan_id"),
                        rs.getString("dimension"), rs.getDouble("x"),
                        rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
            }
        }
    }

    public void set(long clanId, long playerId, String dimension,
                    double x, double y, double z, float yaw, float pitch)
            throws SQLException {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new SQLException("Coordenadas no finitas rechazadas");
        }
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO clan_home (clan_id,dimension,x,y,z,yaw,pitch,updated_by) "
                   + "VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE "
                   + "dimension=VALUES(dimension),x=VALUES(x),y=VALUES(y),z=VALUES(z),"
                   + "yaw=VALUES(yaw),pitch=VALUES(pitch),updated_by=VALUES(updated_by)")) {
            ps.setLong(1, clanId);
            ps.setString(2, dimension);
            ps.setDouble(3, x); ps.setDouble(4, y); ps.setDouble(5, z);
            ps.setFloat(6, yaw); ps.setFloat(7, pitch); ps.setLong(8, playerId);
            ps.executeUpdate();
        }
    }

    public boolean delete(long clanId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM clan_home WHERE clan_id = ?")) {
            ps.setLong(1, clanId);
            return ps.executeUpdate() > 0;
        }
    }
}
