package net.pokereport.luna.player;

import net.pokereport.luna.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resuelve el {@code player_id} interno a partir del UUID de Minecraft.
 *
 * <p>Implementa R1 / D-010: el resto del esquema <b>solo</b> conoce
 * {@code player_id}. Si algún día se cambia {@code online-mode}, los UUID
 * cambian pero basta actualizar una columna de esta tabla.
 *
 * <p>La caché evita ir a la base en cada acción; se llena al conectar el
 * jugador y se limpia al desconectar.
 */
public final class PlayerService {

    private final Database db;
    private final Map<UUID, Long> cache = new ConcurrentHashMap<>();

    public PlayerService(Database db) {
        this.db = db;
    }

    /** Devuelve el player_id, creándolo si es la primera vez que entra. */
    /**
     * El {@code player_id} de alguien por su NOMBRE, o {@code null}.
     *
     * <p>⚠ <b>No crea la fila</b>, al contrario que {@link #resolve}. Es para
     * buscar a otra persona —invitar a un clan, mirar su ficha— y crear un
     * jugador que no existe por escribir mal un nombre convertiría cada errata en
     * una cuenta fantasma.
     *
     * <p>⚠ Y busca por el nombre <b>del último inicio de sesión</b>, que es lo
     * único que hay. Con {@code online-mode=false} un nombre no es una identidad
     * estable: quien se lo cambie deja de ser encontrable por el viejo. Es
     * aceptable para invitar —se invita a quien está jugando— y no lo sería para
     * nada económico, que va siempre por {@code player_id} (D-010).
     */
    public Long resolveByName(String username) throws SQLException {
        if (username == null || username.isBlank()) {
            return null;
        }
        try (var c = db.connection();
             var ps = c.prepareStatement(
                     "SELECT player_id FROM player WHERE username = ? LIMIT 1")) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    public long resolve(UUID mcUuid, String username) throws SQLException {
        Long cached = cache.get(mcUuid);
        if (cached != null) return cached;

        try (Connection c = db.connection()) {
            long id = findOrCreate(c, mcUuid, username);
            cache.put(mcUuid, id);
            return id;
        }
    }

    private long findOrCreate(Connection c, UUID mcUuid, String username)
            throws SQLException {

        // INSERT ... ON DUPLICATE KEY: una sola ida y vuelta, y sin condición
        // de carrera si dos hilos resuelven el mismo jugador a la vez.
        String sql = """
            INSERT INTO player (mc_uuid, username, last_seen)
            VALUES (?, ?, CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE
                username  = VALUES(username),
                last_seen = CURRENT_TIMESTAMP(3)
            """;
        try (PreparedStatement ps =
                 c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, mcUuid.toString());
            ps.setString(2, username);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    if (id > 0) return id;
                }
            }
        }

        // ON DUPLICATE KEY no siempre devuelve la clave generada: releemos.
        try (PreparedStatement ps = c.prepareStatement(
                 "SELECT player_id FROM player WHERE mc_uuid = ?")) {
            ps.setString(1, mcUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("No se pudo resolver player_id para " + mcUuid);
    }

    public void forget(UUID mcUuid) {
        cache.remove(mcUuid);
    }

    public int cachedCount() {
        return cache.size();
    }
}
