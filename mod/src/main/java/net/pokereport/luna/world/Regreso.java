package net.pokereport.luna.world;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;

/**
 * DÓNDE SE QUEDÓ CADA UNO EN CADA MUNDO.
 *
 * <p>Petición del usuario: <i>«en Mundo Hogar la primera ida es aleatoria, la
 * segunda ya vas donde te quedaste la última vez»</i>.
 *
 * <h2>⚠⚠ POR QUÉ NO VALE LO QUE YA GUARDA MINECRAFT</h2>
 *
 * Minecraft recuerda <b>una</b> posición por jugador: dónde estaba al
 * desconectar. No recuerda «dónde estaba en el Hogar» <i>mientras está en el
 * Salvaje</i>, que es exactamente lo que hace falta: viajas al salvaje, juegas
 * dos horas, vuelves a casa y tienes que aparecer <b>en tu casa</b>, no en el
 * último sitio donde estuviste.
 *
 * <h2>⚠ El Salvaje NO se guarda, y es por diseño</h2>
 *
 * La tabla podría, pero ahí la entrada es <b>siempre aleatoria</b>: de eso va
 * un mundo salvaje que además se reinicia cada semana. Guardar una posición de
 * un mundo que va a desaparecer sería devolver a la gente a un sitio que ya no
 * existe.
 */
public final class Regreso {

    private final Database db;

    public Regreso(Database db) {
        this.db = db;
    }

    /** Dónde estaba, o {@code null} si es su primera vez en ese mundo. */
    public Vec3d leer(long playerId, RegistryKey<World> mundo) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT x, y, z FROM world_return "
               + "WHERE player_id = ? AND world_key = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, mundo.getValue().toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? new Vec3d(rs.getDouble(1), rs.getDouble(2), rs.getDouble(3))
                        : null;
            }
        }
    }

    /**
     * Apunta dónde está ahora.
     *
     * <p>⚠ {@code ON DUPLICATE KEY UPDATE} y no borrar-e-insertar: entre las dos
     * operaciones habría un instante sin fila, y una lectura ahí devolvería
     * «primera vez» — que manda al jugador a un sitio al azar en vez de a su
     * casa.
     */
    public void guardar(long playerId, RegistryKey<World> mundo, Vec3d donde,
                        float yaw, float pitch) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO world_return (player_id, world_key, x, y, z, yaw, pitch) "
               + "VALUES (?,?,?,?,?,?,?) "
               + "ON DUPLICATE KEY UPDATE x=VALUES(x), y=VALUES(y), z=VALUES(z), "
               + "yaw=VALUES(yaw), pitch=VALUES(pitch)")) {
            ps.setLong(1, playerId);
            ps.setString(2, mundo.getValue().toString());
            ps.setDouble(3, donde.x);
            ps.setDouble(4, donde.y);
            ps.setDouble(5, donde.z);
            ps.setFloat(6, yaw);
            ps.setFloat(7, pitch);
            ps.executeUpdate();
        }
    }

    /**
     * Apunta dónde está, si el mundo en el que está merece recordarse.
     *
     * <p>Se llama <b>antes</b> de sacarlo de un mundo y al desconectar.
     *
     * <p>⚠ Va por el executor de E/S, y la posición se lee <b>aquí</b>, en el
     * hilo del servidor: pasarle el jugador al executor y leerla allí sería
     * leer el mundo desde fuera — y con un jugador que ya se ha ido, ni
     * siquiera está.
     */
    public static void apuntar(ServerPlayerEntity jugador) {
        var svc = LunaEternal.regresos();
        if (svc == null) {
            return;
        }
        var clave = jugador.getServerWorld().getRegistryKey();
        if (!seRecuerda(clave)) {
            return;
        }
        final var pos = jugador.getPos();
        final float yaw = jugador.getYaw(), pitch = jugador.getPitch();
        final var uuid = jugador.getUuid();
        final String nombre = jugador.getName().getString();
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(uuid, nombre);
                svc.guardar(id, clave, pos, yaw, pitch);
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo apuntar dónde estaba {}: {}",
                        nombre, e.toString());
            }
        });
    }

    /**
     * ¿De este mundo se recuerda la posición?
     *
     * <p>⚠ Solo el Hogar. El Salvaje se reinicia cada semana, así que devolver
     * a alguien a «donde estaba» sería devolverlo a un sitio que ya no existe.
     * Y la ciudadela y el lobby tienen un punto de llegada fijo a propósito.
     */
    public static boolean seRecuerda(RegistryKey<World> mundo) {
        return LunaDimensions.HOGAR.equals(mundo);
    }
}
