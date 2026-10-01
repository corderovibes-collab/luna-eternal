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

    public record PwarpEntry(String creadorNombre, UUID creadorUuid, Home home,
                             String descripcion, String categoria, long visitas) {}

    /** Proyección segura para la interfaz: nunca contiene coordenadas. */
    public record PwarpView(long id, String creador, String nombre, String descripcion,
                            String categoria, String dimension, long visitas,
                            boolean favorito, boolean propio, long reciente) {}

    private static final Set<String> CATEGORIAS = Set.of(
            "TIENDA", "GRANJA", "CONSTRUCCION", "EVENTO", "SERVICIO", "OTROS");

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
                   + "h.description, h.category, h.visits, "
                   + "p.username AS player_name, p.mc_uuid AS player_uuid "
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
                        nuevos.put(clave, new PwarpEntry(pName != null ? pName : "Desconocido", pUuid, h,
                                rs.getString("description"), rs.getString("category"), rs.getLong("visits")));
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
                            cachePwarps.put(clave, new PwarpEntry(playerName, playerUuid, h, "", "OTROS", 0));
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

    public PwarpEntry getPwarp(long homeId) {
        for (PwarpEntry entry : cachePwarps.values()) {
            if (entry.home().id() == homeId) return entry;
        }
        return null;
    }

    public void listarPwarps(long viewerId, Consumer<List<PwarpView>> ok, Consumer<Throwable> error) {
        LunaEternal.submit(() -> {
            String sql = "SELECT h.id,p.username,h.name,h.description,h.category,h.dimension,h.visits," +
                    "(f.home_id IS NOT NULL) favorite,(h.player_id=?) own," +
                    "COALESCE(UNIX_TIMESTAMP(r.visited_at),0) recent " +
                    "FROM player_homes h JOIN player p ON p.player_id=h.player_id " +
                    "LEFT JOIN pwarp_favorite f ON f.home_id=h.id AND f.player_id=? " +
                    "LEFT JOIN pwarp_recent r ON r.home_id=h.id AND r.player_id=? " +
                    "WHERE h.is_public=TRUE OR h.player_id=? ORDER BY h.visits DESC,h.id DESC LIMIT 200";
            try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 1; i <= 4; i++) ps.setLong(i, viewerId);
                List<PwarpView> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new PwarpView(rs.getLong(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getLong(7),
                            rs.getBoolean(8), rs.getBoolean(9), rs.getLong(10)));
                }
                ok.accept(List.copyOf(out));
            } catch (Throwable t) {
                LunaEternal.LOG.error("No se pudo listar el catálogo de pWarps", t);
                if (error != null) error.accept(t);
            }
        });
    }

    public void toggleFavorito(long playerId, long homeId, Runnable done, Consumer<Throwable> error) {
        LunaEternal.submit(() -> {
            try (Connection c = db.connection()) {
                c.setAutoCommit(false);
                try (PreparedStatement visible = c.prepareStatement("SELECT id FROM player_homes WHERE id=? AND is_public=TRUE FOR UPDATE");
                     PreparedStatement exists = c.prepareStatement("SELECT 1 FROM pwarp_favorite WHERE player_id=? AND home_id=?");
                     PreparedStatement del = c.prepareStatement("DELETE FROM pwarp_favorite WHERE player_id=? AND home_id=?");
                     PreparedStatement add = c.prepareStatement("INSERT INTO pwarp_favorite(player_id,home_id) VALUES(?,?)")) {
                    visible.setLong(1, homeId);
                    try (ResultSet rs = visible.executeQuery()) { if (!rs.next()) throw new IllegalArgumentException("pWarp no disponible"); }
                    exists.setLong(1, playerId); exists.setLong(2, homeId);
                    boolean found; try (ResultSet rs = exists.executeQuery()) { found = rs.next(); }
                    PreparedStatement change = found ? del : add;
                    change.setLong(1, playerId); change.setLong(2, homeId); change.executeUpdate();
                    c.commit();
                } catch (Throwable t) { c.rollback(); throw t; }
                if (done != null) done.run();
            } catch (Throwable t) { if (error != null) error.accept(t); }
        });
    }

    public void actualizarMetadata(long playerId, long homeId, String descripcion, String categoria,
                                   Runnable done, Consumer<Throwable> error) {
        String desc = descripcion == null ? "" : descripcion.strip();
        String cat = categoria == null ? "OTROS" : categoria.strip().toUpperCase(Locale.ROOT);
        if (desc.length() > 120 || desc.chars().anyMatch(ch -> Character.isISOControl(ch)) || !CATEGORIAS.contains(cat)) {
            if (error != null) error.accept(new IllegalArgumentException("Datos de pWarp no válidos"));
            return;
        }
        LunaEternal.submit(() -> {
            try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(
                    "UPDATE player_homes SET description=?,category=? WHERE id=? AND player_id=?")) {
                ps.setString(1, desc); ps.setString(2, cat); ps.setLong(3, homeId); ps.setLong(4, playerId);
                if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Ese pWarp no te pertenece");
                cargarPwarps();
                if (done != null) done.run();
            } catch (Throwable t) { if (error != null) error.accept(t); }
        });
    }

    public void registrarVisita(long playerId, long homeId) {
        LunaEternal.submit(() -> {
            try (Connection c = db.connection()) {
                c.setAutoCommit(false);
                try (PreparedStatement inc = c.prepareStatement(
                        "UPDATE player_homes SET visits=visits+1,last_visit_at=CURRENT_TIMESTAMP(3) WHERE id=? AND is_public=TRUE");
                     PreparedStatement recent = c.prepareStatement(
                        "INSERT INTO pwarp_recent(player_id,home_id,visited_at,visit_count) VALUES(?,?,CURRENT_TIMESTAMP(3),1) " +
                        "ON DUPLICATE KEY UPDATE visited_at=VALUES(visited_at),visit_count=visit_count+1")) {
                    inc.setLong(1, homeId);
                    if (inc.executeUpdate() == 1) {
                        recent.setLong(1, playerId); recent.setLong(2, homeId); recent.executeUpdate();
                    }
                    c.commit();
                } catch (Throwable t) { c.rollback(); throw t; }
                cargarPwarps();
            } catch (Throwable t) { LunaEternal.LOG.error("No se pudo registrar visita a pWarp {}", homeId, t); }
        });
    }
}
