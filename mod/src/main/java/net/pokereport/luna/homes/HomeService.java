package net.pokereport.luna.homes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.ui.Tablist.Rank;

/**
 * Servicio de persistencia y gestión de Hogares (/home) y Pwarps (/pwarp).
 *
 * <p>Persiste en MariaDB (tabla player_homes) y mantiene caché en memoria
 * para consultas instantáneas y autocompletado en comandos.
 */
public final class HomeService {

    public record Home(long id, long playerId, String name, String dimension,
                       double x, double y, double z, float yaw, float pitch, boolean isPublic) {}

    public record PwarpEntry(String creadorNombre, UUID creadorUuid, Home home) {}

    private final Database db;

    // Cache: playerId -> (nombreHomeLowerCase -> Home)
    private final Map<Long, Map<String, Home>> cacheHomes = new ConcurrentHashMap<>();

    // Cache de Pwarps: "creador:home" -> PwarpEntry
    private final Map<String, PwarpEntry> cachePwarps = new ConcurrentHashMap<>();

    public HomeService(Database db) {
        this.db = db;
    }

    public static int limiteHomes(Rank rank, boolean isStaff) {
        if (isStaff) return 100;
        if (rank == null || rank.equipo) return 100;
        return switch (rank) {
            case LEYENDA -> 7;
            case MAESTRO -> 5;
            case CAMPEON -> 4;
            case ELITE -> 3;
            default -> 0;
        };
    }

    public static int limitePwarps(Rank rank, boolean isStaff) {
        if (isStaff) return 100;
        if (rank == null || rank.equipo) return 100;
        return switch (rank) {
            case LEYENDA -> 3;
            case MAESTRO -> 2;
            case CAMPEON -> 1;
            case ELITE -> 1;
            default -> 0;
        };
    }

    /**
     * Carga inicial de todos los pwarps públicos al arrancar el servidor.
     */
    public void cargarPwarps() {
        LunaEternal.submit(() -> {
            try (Connection c = db.connection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT h.id, h.player_id, h.name, h.dimension, h.x, h.y, h.z, h.yaw, h.pitch, h.is_public, "
                   + "p.name AS player_name, p.uuid AS player_uuid "
                   + "FROM player_homes h "
                   + "JOIN player p ON h.player_id = p.player_id "
                   + "WHERE h.is_public = TRUE")) {
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, PwarpEntry> nuevos = new HashMap<>();
                    while (rs.next()) {
                        Home h = new Home(
                            rs.getLong("id"),
                            rs.getLong("player_id"),
                            rs.getString("name"),
                            rs.getString("dimension"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            true
                        );
                        String pName = rs.getString("player_name");
                        String pUuidStr = rs.getString("player_uuid");
                        UUID pUuid = null;
                        try {
                            if (pUuidStr != null) pUuid = UUID.fromString(pUuidStr);
                        } catch (Exception ignored) {}

                        String clave = (pName != null ? pName.toLowerCase(Locale.ROOT) : "anon") + ":" + h.name().toLowerCase(Locale.ROOT);
                        nuevos.put(clave, new PwarpEntry(pName != null ? pName : "Desconocido", pUuid, h));
                    }
                    cachePwarps.clear();
                    cachePwarps.putAll(nuevos);
                    LunaEternal.LOG.info("Hogares públicos (Pwarps) cargados: {}", cachePwarps.size());
                }
            } catch (Exception e) {
                LunaEternal.LOG.error("Error al cargar pwarps de MariaDB", e);
            }
        });
    }

    /**
     * Carga los homes de un jugador en la caché.
     */
    public void cargarJugador(long playerId, Runnable onComplete) {
        LunaEternal.submit(() -> {
            try (Connection c = db.connection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT id, player_id, name, dimension, x, y, z, yaw, pitch, is_public "
                   + "FROM player_homes WHERE player_id = ?")) {
                ps.setLong(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, Home> map = new ConcurrentHashMap<>();
                    while (rs.next()) {
                        Home h = new Home(
                            rs.getLong("id"),
                            rs.getLong("player_id"),
                            rs.getString("name"),
                            rs.getString("dimension"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            rs.getBoolean("is_public")
                        );
                        map.put(h.name().toLowerCase(Locale.ROOT), h);
                    }
                    cacheHomes.put(playerId, map);
                }
            } catch (Exception e) {
                LunaEternal.LOG.error("Error al cargar homes del jugador {}", playerId, e);
            } finally {
                if (onComplete != null) onComplete.run();
            }
        });
    }

    public Home getHome(long playerId, String name) {
        Map<String, Home> map = cacheHomes.get(playerId);
        if (map == null) return null;
        return map.get(name.toLowerCase(Locale.ROOT));
    }

    public List<Home> getHomes(long playerId) {
        Map<String, Home> map = cacheHomes.get(playerId);
        if (map == null) return List.of();
        return new ArrayList<>(map.values());
    }

    public int contarHomes(long playerId) {
        Map<String, Home> map = cacheHomes.get(playerId);
        return map != null ? map.size() : 0;
    }

    public int contarPwarps(long playerId) {
        Map<String, Home> map = cacheHomes.get(playerId);
        if (map == null) return 0;
        int count = 0;
        for (Home h : map.values()) {
            if (h.isPublic()) count++;
        }
        return count;
    }

    public List<PwarpEntry> getTodosPwarps() {
        return new ArrayList<>(cachePwarps.values());
    }

    public PwarpEntry getPwarp(String creadorNombre, String homeName) {
        String clave = creadorNombre.toLowerCase(Locale.ROOT) + ":" + homeName.toLowerCase(Locale.ROOT);
        return cachePwarps.get(clave);
    }

    /**
     * Guarda o actualiza un home en DB y en caché.
     */
    public void guardarHome(long playerId, String playerName, UUID playerUuid, String name,
                            String dimension, double x, double y, double z, float yaw, float pitch,
                            Runnable onDone, Consumer<Throwable> onError) {
        LunaEternal.submit(() -> {
            try (Connection c = db.connection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO player_homes (player_id, name, dimension, x, y, z, yaw, pitch, is_public) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE) "
                   + "ON DUPLICATE KEY UPDATE dimension = VALUES(dimension), x = VALUES(x), y = VALUES(y), "
                   + "z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)")) {
                ps.setLong(1, playerId);
                ps.setString(2, name);
                ps.setString(3, dimension);
                ps.setDouble(4, x);
                ps.setDouble(5, y);
                ps.setDouble(6, z);
                ps.setFloat(7, yaw);
                ps.setFloat(8, pitch);
                ps.executeUpdate();

                // Recargar en caché
                cargarJugador(playerId, onDone);
            } catch (Exception e) {
                LunaEternal.LOG.error("Error al guardar home '{}' para {}", name, playerId, e);
                if (onError != null) onError.accept(e);
            }
        });
    }

    /**
     * Elimina un home en DB y en caché.
     */
    public void borrarHome(long playerId, String playerName, String name,
                           Runnable onDone, Consumer<Throwable> onError) {
        LunaEternal.submit(() -> {
            try (Connection c = db.connection();
                 PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM player_homes WHERE player_id = ? AND name = ?")) {
                ps.setLong(1, playerId);
                ps.setString(2, name);
                ps.executeUpdate();

                // Quitar de pwarps si estaba público
                if (playerName != null) {
                    String clave = playerName.toLowerCase(Locale.ROOT) + ":" + name.toLowerCase(Locale.ROOT);
                    cachePwarps.remove(clave);
                }

                cargarJugador(playerId, onDone);
            } catch (Exception e) {
                LunaEternal.LOG.error("Error al borrar home '{}' para {}", name, playerId, e);
                if (onError != null) onError.accept(e);
            }
        });
    }

    /**
     * Modifica el estado público (pwarp) de un home.
     */
    public void setPublico(long playerId, String playerName, UUID playerUuid, String name, boolean isPublic,
                           Runnable onDone, Consumer<Throwable> onError) {
        LunaEternal.submit(() -> {
            try (Connection c = db.connection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE player_homes SET is_public = ? WHERE player_id = ? AND name = ?")) {
                ps.setBoolean(1, isPublic);
                ps.setLong(2, playerId);
                ps.setString(3, name);
                ps.executeUpdate();

                String clave = (playerName != null ? playerName.toLowerCase(Locale.ROOT) : "anon") + ":" + name.toLowerCase(Locale.ROOT);
                if (!isPublic) {
                    cachePwarps.remove(clave);
                }

                cargarJugador(playerId, () -> {
                    if (isPublic) {
                        Home h = getHome(playerId, name);
                        if (h != null) {
                            cachePwarps.put(clave, new PwarpEntry(playerName, playerUuid, h));
                        }
                    }
                    if (onDone != null) onDone.run();
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("Error al actualizar visibilidad de home '{}' para {}", name, playerId, e);
                if (onError != null) onError.accept(e);
            }
        });
    }

    public void olvidar(long playerId) {
        cacheHomes.remove(playerId);
    }
}
