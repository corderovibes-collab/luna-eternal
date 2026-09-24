package net.pokereport.luna.tebex;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.player.PlayerService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Resolver centralizado y canónico de identidad de jugador para transacciones de Tebex.
 *
 * <p>Maneja entornos mixtos Java / Bedrock (Geyser/Floodgate) y tiendas en modo Offline.
 * Casos soportados:
 * <ul>
 *   <li><b>CASO 1:</b> La identidad es un UUID canónico válido -> Se utiliza directamente el UUID.</li>
 *   <li><b>CASO 2:</b> La identidad es un username y el jugador ya existe en la base de datos ->
 *       Búsqueda case-insensitive, recupera el UUID real almacenado y entrega de inmediato (offline u online).</li>
 *   <li><b>CASO 3:</b> La identidad es un username y el jugador está actualmente conectado ->
 *       Se obtiene el UUID real observado por el servidor en runtime (Minecraft/Floodgate),
 *       se sincroniza en DB y se entrega de inmediato.</li>
 *   <li><b>CASO 4:</b> La identidad es un username y NUNCA ha entrado al servidor ->
 *       NO genera un UUID ficticio (protección estricta para no romper UUIDs de Floodgate/Geyser).
 *       Retorna estado {@code PENDING_PLAYER_RESOLUTION} para encolar la entrega hasta su primer login.</li>
 * </ul>
 */
public final class TebexPlayerResolver {

    private TebexPlayerResolver() {}

    public enum Status {
        RESOLVED,
        PENDING_PLAYER_RESOLUTION,
        REJECTED
    }

    public record ResolutionResult(
            Status status,
            UUID uuid,
            Long playerId,
            String username,
            String failureReason
    ) {
        public static ResolutionResult resolved(UUID uuid, long playerId, String username) {
            return new ResolutionResult(Status.RESOLVED, uuid, playerId, username, null);
        }

        public static ResolutionResult pending(String username) {
            return new ResolutionResult(Status.PENDING_PLAYER_RESOLUTION, null, null, username, null);
        }

        public static ResolutionResult rejected(String reason) {
            return new ResolutionResult(Status.REJECTED, null, null, null, reason);
        }

        public boolean isResolved() {
            return status == Status.RESOLVED;
        }

        public boolean isPending() {
            return status == Status.PENDING_PLAYER_RESOLUTION;
        }
    }

    @FunctionalInterface
    public interface OnlinePlayerLookup {
        OnlinePlayerInfo findOnline(String usernameOrUuid);
        record OnlinePlayerInfo(UUID uuid, String name) {}
    }

    /**
     * Resuelve la identidad provista por Tebex a un jugador concreto o determina si debe quedar pendiente.
     */
    public static ResolutionResult resolve(
            Connection c,
            MinecraftServer server,
            PlayerService playerService,
            String rawIdentity,
            String usernameHint
    ) throws SQLException {
        OnlinePlayerLookup lookup = (server != null) ? (query) -> {
            try {
                UUID u = TebexParser.parseCanonicalUuid(query);
                if (u != null) {
                    ServerPlayerEntity sp = server.getPlayerManager().getPlayer(u);
                    if (sp != null) return new OnlinePlayerLookup.OnlinePlayerInfo(sp.getUuid(), sp.getGameProfile().getName());
                } else {
                    ServerPlayerEntity sp = server.getPlayerManager().getPlayer(query);
                    if (sp == null) {
                        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                            if (online.getGameProfile().getName().equalsIgnoreCase(query)) {
                                sp = online;
                                break;
                            }
                        }
                    }
                    if (sp != null) return new OnlinePlayerLookup.OnlinePlayerInfo(sp.getUuid(), sp.getGameProfile().getName());
                }
            } catch (Throwable ignored) {}
            return null;
        } : null;
        return resolveWithLookup(c, lookup, playerService, rawIdentity, usernameHint);
    }

    public static ResolutionResult resolveWithLookup(
            Connection c,
            OnlinePlayerLookup onlineLookup,
            PlayerService playerService,
            String rawIdentity,
            String usernameHint
    ) throws SQLException {
        if (rawIdentity == null || rawIdentity.isBlank()) {
            return ResolutionResult.rejected("IDENTIDAD_VACIA");
        }

        String identity = rawIdentity.trim();

        // CASO 1: identity es un UUID canónico válido
        UUID parsedUuid = TebexParser.parseCanonicalUuid(identity);
        if (parsedUuid != null) {
            return resolveByUuid(c, onlineLookup, playerService, parsedUuid, usernameHint);
        }

        // Si no es un UUID, identity es un username
        return resolveByUsername(c, onlineLookup, playerService, identity);
    }

    private static ResolutionResult resolveByUuid(
            Connection c,
            OnlinePlayerLookup onlineLookup,
            PlayerService playerService,
            UUID uuid,
            String usernameHint
    ) throws SQLException {
        // 1. Buscar en tabla player
        String selectSql = "SELECT player_id, username FROM player WHERE mc_uuid = ? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(selectSql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long pid = rs.getLong("player_id");
                    String uname = rs.getString("username");
                    return ResolutionResult.resolved(uuid, pid, uname != null ? uname : usernameHint);
                }
            }
        }

        // 2. Si no está en DB pero el jugador está conectado
        if (onlineLookup != null) {
            try {
                OnlinePlayerLookup.OnlinePlayerInfo info = onlineLookup.findOnline(uuid.toString());
                if (info != null) {
                    long pid = resolveOrCreatePlayerInDb(c, playerService, info.uuid(), info.name());
                    return ResolutionResult.resolved(info.uuid(), pid, info.name());
                }
            } catch (Throwable ignored) {}
        }

        // 3. Jugador offline con UUID conocido: registrar en DB con usernameHint
        String effectiveName = (usernameHint != null && !usernameHint.isBlank()) ? usernameHint.trim() : "TebexBuyer";
        long pid = resolveOrCreatePlayerInDb(c, playerService, uuid, effectiveName);
        return ResolutionResult.resolved(uuid, pid, effectiveName);
    }

    private static ResolutionResult resolveByUsername(
            Connection c,
            OnlinePlayerLookup onlineLookup,
            PlayerService playerService,
            String username
    ) throws SQLException {
        // CASO 2: identity es username y existe player en nuestra DB (case-insensitive)
        String selectSql = "SELECT player_id, mc_uuid, username FROM player WHERE LOWER(username) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(selectSql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long pid = rs.getLong("player_id");
                    String uuidStr = rs.getString("mc_uuid");
                    String storedName = rs.getString("username");
                    if (uuidStr != null && !uuidStr.isBlank()) {
                        try {
                            UUID storedUuid = UUID.fromString(uuidStr);
                            return ResolutionResult.resolved(storedUuid, pid, storedName != null ? storedName : username);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        }

        // CASO 3: identity es username y el jugador está actualmente online
        if (onlineLookup != null) {
            try {
                OnlinePlayerLookup.OnlinePlayerInfo info = onlineLookup.findOnline(username);
                if (info != null) {
                    long pid = resolveOrCreatePlayerInDb(c, playerService, info.uuid(), info.name());
                    return ResolutionResult.resolved(info.uuid(), pid, info.name());
                }
            } catch (Throwable ignored) {}
        }

        // CASO 4: identity es username y NUNCA ha entrado al servidor
        // NO inventar UUID con nameUUIDFromBytes. Retornar pendiente.
        return ResolutionResult.pending(username);
    }

    public static long resolveOrCreatePlayerInDb(
            Connection c,
            PlayerService playerService,
            UUID uuid,
            String username
    ) throws SQLException {
        if (playerService != null) {
            try {
                return playerService.resolve(uuid, username);
            } catch (Exception ignored) {}
        }

        String sql = """
            INSERT INTO player (mc_uuid, username, last_seen)
            VALUES (?, ?, CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE username = VALUES(username), last_seen = CURRENT_TIMESTAMP(3)
            """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next() && keys.getLong(1) > 0) {
                    return keys.getLong(1);
                }
            }
        }

        try (PreparedStatement ps = c.prepareStatement("SELECT player_id FROM player WHERE mc_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        throw new SQLException("No se pudo resolver player_id para " + uuid + " (" + username + ")");
    }
}
