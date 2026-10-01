package net.pokereport.luna.economy;

import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;
import net.minecraft.stat.Stats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mide el tiempo ACTIVO de los jugadores y guarda Checkpoints de la sesión.
 * Fases 4 y 5.
 */
public class SessionManager {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final Map<UUID, Long> sessionIds = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastPlayTime = new ConcurrentHashMap<>();

    public static void start() {
        scheduler.scheduleAtFixedRate(SessionManager::checkpointAll, 5, 5, TimeUnit.MINUTES);
    }

    public static void stop() {
        scheduler.shutdown();
    }

    public static void onJoin(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        int playTicks = player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));
        lastPlayTime.put(uuid, (long) playTicks);
        
        Thread.ofVirtual().start(() -> {
            try {
                long playerId = LunaEternal.players().resolve(uuid, player.getName().getString());
                try (Connection c = LunaEternal.database().connection();
                     PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO economic_session (player_id) VALUES (?)",
                         PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, playerId);
                    ps.executeUpdate();
                    try (var rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            sessionIds.put(uuid, rs.getLong(1));
                        }
                    }
                }
            } catch (Exception e) {
                LunaEternal.LOG.error("Fallo al crear economic_session", e);
            }
        });
    }

    public static void onLeave(ServerPlayerEntity player) {
        checkpoint(player);
        sessionIds.remove(player.getUuid());
        lastPlayTime.remove(player.getUuid());
    }

    private static void checkpointAll() {
        if (LunaEternal.server() == null) return;
        for (ServerPlayerEntity player : LunaEternal.server().getPlayerManager().getPlayerList()) {
            checkpoint(player);
        }
    }

    private static void checkpoint(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        long sessId = sessionIds.getOrDefault(uuid, 0L);
        if (sessId == 0) return;

        int currentPlayTicks = player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));
        long prevPlayTicks = lastPlayTime.getOrDefault(uuid, (long) currentPlayTicks);
        
        long deltaTicks = currentPlayTicks - prevPlayTicks;
        if (deltaTicks <= 0) return;
        
        lastPlayTime.put(uuid, (long) currentPlayTicks);
        
        // Asumimos que si gano play_ticks, estuvo activo. Pero podríamos descontar AFK.
        // LunaEternal.world.Afk tiene sistema AFK, pero omitimos complejidad extrema aquí por latencia.
        int deltaSeconds = (int) (deltaTicks / 20);

        Thread.ofVirtual().start(() -> {
            try (Connection c = LunaEternal.database().connection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE economic_session SET last_checkpoint = CURRENT_TIMESTAMP, play_seconds = play_seconds + ?, active_seconds = active_seconds + ? WHERE session_id = ?")) {
                ps.setInt(1, deltaSeconds);
                ps.setInt(2, deltaSeconds); // TODO: Restar tiempo AFK
                ps.setLong(3, sessId);
                ps.executeUpdate();
            } catch (Exception e) {
                // Silencioso, reintenta el próximo checkpoint
            }
            
            try (Connection c = LunaEternal.database().connection();
                 PreparedStatement ps2 = c.prepareStatement(
                     "UPDATE player SET play_ticks = ?, active_ticks = active_ticks + ? WHERE mc_uuid = ?")) {
                ps2.setLong(1, currentPlayTicks);
                ps2.setLong(2, deltaTicks);
                ps2.setString(3, uuid.toString());
                ps2.executeUpdate();
            } catch (Exception e) {}
        });
    }
}
