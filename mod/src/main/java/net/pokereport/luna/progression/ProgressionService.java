package net.pokereport.luna.progression;

import net.pokereport.luna.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Progreso del jugador en las cinco Vías.
 *
 * <p>Igual que la economía, esto <b>nunca</b> se toca desde el hilo del
 * servidor: se llama desde {@code LunaEternal.submit()}.
 */
public final class ProgressionService {

    /** Estado de una vía. */
    public record PathState(Path path, int level, long xp) {

        public long xpForNext() {
            return Path.xpForNextLevel(level);
        }

        public boolean maxed() {
            return level >= Path.MAX_LEVEL;
        }

        /** Progreso hacia el siguiente nivel, de 0.0 a 1.0. */
        public double fraction() {
            if (maxed()) return 1.0;
            long need = xpForNext();
            return need <= 0 ? 0 : Math.min(1.0, (double) xp / need);
        }
    }

    private final Database db;

    public ProgressionService(Database db) {
        this.db = db;
    }

    /** Todas las vías del jugador. Las que no existen salen a nivel 0. */
    public Map<Path, PathState> all(long playerId) throws SQLException {
        Map<Path, PathState> out = new EnumMap<>(Path.class);
        for (Path p : Path.values()) out.put(p, new PathState(p, 0, 0));

        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT path, level, xp FROM player_path WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Path p = Path.valueOf(rs.getString("path"));
                    out.put(p, new PathState(p, rs.getInt("level"), rs.getLong("xp")));
                }
            }
        }
        return out;
    }

    /**
     * Suma experiencia y sube de nivel si toca.
     *
     * <p>El bucle de subida contempla que una sola concesión cruce varios
     * niveles: si no, una recompensa grande dejaría al jugador con XP de sobra
     * y el nivel sin subir, que es un fallo silencioso y confuso.
     *
     * @return el estado resultante
     */
    /**
     * Una concesión de XP, con el nivel que había ANTES.
     *
     * <p>⚠ El nivel anterior sale de la MISMA transacción que la subida, y eso no
     * es un lujo: para pagar por subir hay que saber si se ha subido, y leerlo
     * fuera abriría una ventana en la que dos concesiones a la vez veen el mismo
     * «antes» y <b>pagan el mismo nivel dos veces</b>. Aquí sale del
     * {@code SELECT ... FOR UPDATE} que ya bloqueaba la fila.
     */
    public record Subida(PathState estado, int nivelAnterior) {
        public boolean subio() {
            return estado.level() > nivelAnterior;
        }
    }

    public PathState grant(long playerId, Path path, long amount) throws SQLException {
        return grantDetallado(playerId, path, amount).estado();
    }

    public Subida grantDetallado(long playerId, Path path, long amount) throws SQLException {
        if (amount <= 0) throw new IllegalArgumentException("La XP debe ser positiva");

        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                ensureRow(c, playerId, path);

                int level;
                long xp;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT level, xp FROM player_path "
                      + "WHERE player_id = ? AND path = ? FOR UPDATE")) {
                    ps.setLong(1, playerId);
                    ps.setString(2, path.name());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Fila de vía no encontrada");
                        level = rs.getInt(1);
                        xp = rs.getLong(2);
                    }
                }

                int nivelAntes = level;
                xp += amount;
                while (level < Path.MAX_LEVEL && xp >= Path.xpForNextLevel(level)) {
                    xp -= Path.xpForNextLevel(level);
                    level++;
                }
                if (level >= Path.MAX_LEVEL) xp = 0;   // al tope no se acumula

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE player_path SET level = ?, xp = ? "
                      + "WHERE player_id = ? AND path = ?")) {
                    ps.setInt(1, level);
                    ps.setLong(2, xp);
                    ps.setLong(3, playerId);
                    ps.setString(4, path.name());
                    ps.executeUpdate();
                }

                c.commit();
                return new Subida(new PathState(path, level, xp), nivelAntes);

            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Vía con el nivel más alto. Es la que se muestra en la barra lateral. */
    public PathState dominant(long playerId) throws SQLException {
        PathState best = null;
        for (PathState s : all(playerId).values()) {
            if (best == null
                || s.level() > best.level()
                || (s.level() == best.level() && s.xp() > best.xp())) {
                best = s;
            }
        }
        return best;
    }

    private static void ensureRow(Connection c, long playerId, Path path)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO player_path (player_id, path) VALUES (?,?) "
              + "ON DUPLICATE KEY UPDATE player_id = player_id")) {
            ps.setLong(1, playerId);
            ps.setString(2, path.name());
            ps.executeUpdate();
        }
    }
}
