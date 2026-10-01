package net.pokereport.luna.progression;

import net.minecraft.util.math.BlockPos;
import net.pokereport.luna.LunaEternal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caché autoritativo y asíncrono para el Anti-Exploit de Silk Touch.
 * Evita la condición de carrera (Race Condition) donde un jugador
 * coloca y rompe un mineral más rápido de lo que la base de datos
 * puede registrar el INSERT, impidiendo que el sistema lo tome como natural.
 */
public final class PlacedOreManager {
    public static void startCleanup() {
        Thread.ofVirtual().start(() -> {
            try { Thread.sleep(60000); } catch (Exception e){}
            try (java.sql.Connection c = LunaEternal.database().connection();
                 java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM placed_ore WHERE placed_at < DATE_SUB(NOW(), INTERVAL 7 DAY)")) {
                ps.executeUpdate();
            } catch (Exception e) {}
        });
    }


    // Registro rápido en memoria. La clave es un hash simple de la posición.
    private static final Set<String> CACHE = ConcurrentHashMap.newKeySet();

    private PlacedOreManager() {}

    private static String key(BlockPos pos, String dim) {
        return dim + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    /**
     * Llamado sincronamente desde MixinBlockItem al colocar.
     * BLOQUEA INMEDIATAMENTE en memoria antes de derivar a DB.
     */
    public static void markPlaced(BlockPos pos, String dim) {
        String k = key(pos, dim);
        CACHE.add(k);
        
        Thread.ofVirtual().start(() -> {
            try (Connection c = LunaEternal.database().connection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO placed_ore (x, y, z, dim) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, pos.getX());
                ps.setInt(2, pos.getY());
                ps.setInt(3, pos.getZ());
                ps.setString(4, dim);
                ps.executeUpdate();
            } catch (Exception e) {
                LunaEternal.LOG.error("Fallo guardando placed_ore en DB", e);
            }
        });
    }

    /**
     * Llamado asíncronamente desde OficiosListener al romper.
     * Primero revisa memoria, luego DB.
     * Devuelve TRUE si fue colocado por el jugador (y NO debe dar XP).
     */
    public static boolean consume(BlockPos pos, String dim) {
        String k = key(pos, dim);
        boolean inMemory = CACHE.remove(k);
        
        // Revisamos en BD y lo borramos
        boolean inDb = false;
        try (Connection c = LunaEternal.database().connection();
             PreparedStatement ps = c.prepareStatement(
                 "DELETE FROM placed_ore WHERE x=? AND y=? AND z=? AND dim=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            ps.setString(4, dim);
            inDb = ps.executeUpdate() > 0;
        } catch (Exception e) {
            LunaEternal.LOG.error("Fallo consumiendo placed_ore en DB", e);
        }
        
        return inMemory || inDb;
    }
}
