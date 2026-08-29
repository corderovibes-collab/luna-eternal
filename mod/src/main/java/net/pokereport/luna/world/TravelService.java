package net.pokereport.luna.world;

import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.pokereport.luna.LunaEternal;

import java.util.Set;

/**
 * Viaje entre dimensiones.
 *
 * <p>Resuelve dos problemas que en un servidor real se notan enseguida:
 * <ol>
 *   <li><b>El vacío.</b> El Lobby y la Ciudadela se generan sin un solo
 *       bloque. Teletransportar ahí sin más deja al jugador cayendo para
 *       siempre — así que se coloca una plataforma si no existe.</li>
 *   <li><b>Aterrizar dentro de la roca.</b> En el Salvaje la altura del
 *       terreno es desconocida, así que se busca el punto seguro en vez de
 *       usar una Y fija.</li>
 * </ol>
 */
public final class TravelService {

    /**
     * Dónde aterriza cada dimensión vacía.
     *
     * <p><b>La ciudadela ya no es 0,64,0.</b> Se construyó la plaza y el punto
     * de llegada quedó en {@code 4,27 · 70 · 0,36} — medido por el usuario
     * dentro del juego, no calculado. Dejarlo en el origen soltaba al jugador
     * por debajo del suelo nuevo.
     *
     * <h2>⚠⚠ ES UN {@code Vec3d} Y NO UN {@code BlockPos}, Y NO ES UN CAPRICHO</h2>
     *
     * Con un bloque, lo que se hacía era «la casilla, más medio bloque»: el
     * jugador caía en (4,5 · 69 · 0,5), que <b>no es donde el usuario se puso a
     * medir</b>. Los decimales que midió <i>son</i> la posición; redondearlos la
     * mueve media casilla a un lado y un bloque hacia abajo.
     *
     * <p>Es una constante y no un ajuste porque cambiarla es una línea y un
     * despliegue, y no va a cambiar a menudo. Cuando la ciudadela esté acabada,
     * esto pasará a leerse de la base de datos junto con los puntos de viaje.
     */
    private static final Vec3d SPAWN_CIUDADELA = new Vec3d(4.27, 70, 0.36);
    private static final Vec3d SPAWN_LOBBY = new Vec3d(0.5, 64, 0.5);
    /** Radio de la plataforma de emergencia. */
    private static final int PLATFORM_RADIUS = 4;

    private TravelService() {}

    /**
     * Lleva al jugador a una dimensión. Devuelve {@code false} si la dimensión
     * no está cargada — lo que ocurre si el datapack no se aplicó.
     */
    public static boolean travel(ServerPlayerEntity player, RegistryKey<World> target,
                                 String displayName) {
        var server = player.getServer();
        if (server == null) return false;

        ServerWorld world = server.getWorld(target);
        if (world == null) {
            player.sendMessage(Text.literal(
                "§cEse mundo todavía no está disponible."), false);
            LunaEternal.LOG.warn("Dimension no cargada: {}", target.getValue());
            return false;
        }

        if (player.getWorld() == world) {
            player.sendMessage(Text.literal("§7Ya estás en " + displayName + "."), true);
            return true;
        }

        Vec3d destination = safeSpawn(world);

        player.closeHandledScreen();
        // ⚠ Sin sumar medio bloque: `safeSpawn` ya devuelve la posición EXACTA.
        //   Sumarlo aquí --como se hacía-- desplazaba media casilla el punto que
        //   el usuario había medido de pie en el juego.
        player.teleport(world, destination.x, destination.y, destination.z,
            Set.of(), player.getYaw(), player.getPitch());

        player.playSoundToPlayer(SoundEvents.BLOCK_PORTAL_TRAVEL,
            SoundCategory.MASTER, 0.2f, 1.4f);
        player.sendMessage(Text.literal("§8» §fHas llegado a §6" + displayName), false);
        return true;
    }

    /**
     * Punto seguro de una dimensión. En las vacías construye la plataforma si
     * hace falta; en las generadas busca la superficie.
     */
    private static Vec3d safeSpawn(ServerWorld world) {
        boolean isVoid = world.getRegistryKey().equals(LunaDimensions.LOBBY)
                      || world.getRegistryKey().equals(LunaDimensions.CIUDADELA);

        if (isVoid) {
            Vec3d punto = world.getRegistryKey().equals(LunaDimensions.CIUDADELA)
                ? SPAWN_CIUDADELA : SPAWN_LOBBY;
            // ⚠ El suelo es la casilla de DEBAJO de los pies. Con el punto en
            //   y=70, eso es y=69. Pasarle el punto en vez del suelo fue lo que
            //   un día plantó nueve por nueve bloques de piedra en mitad de la
            //   plaza: el aire estaba ENCIMA del suelo, así que la comprobación
            //   «¿es aire?» decía que sí.
            ensurePlatform(world, BlockPos.ofFloored(punto).down());
            return punto;
        }

        BlockPos spawn = world.getSpawnPos();
        // Mismo motivo: sin el chunk cargado, getTopY devuelve basura y el
        // jugador aterriza dentro de la roca o en el aire.
        world.getChunk(spawn);

        int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                             spawn.getX(), spawn.getZ());
        return new Vec3d(spawn.getX() + 0.5,
                Math.max(y, world.getBottomY() + 1), spawn.getZ() + 0.5);
    }

    /**
     * Coloca una plataforma <b>de suelo</b> si en ese punto no hay nada.
     *
     * <p>Es una red de seguridad, no decoración: evita el fallo más tonto y más
     * frustrante posible — caer al vacío al entrar por primera vez.
     *
     * <p><b>Ojo con el nivel al que se le llama.</b> Antes se le pasaba el
     * punto de aparición (y=64) en vez del suelo (y=63), así que en cuanto la
     * ciudadela tuvo suelo real la comprobación «¿es aire?» daba que sí —
     * porque el aire estaba <i>encima</i> del suelo— y plantaba nueve por nueve
     * bloques de piedra en mitad de la plaza. La plataforma va donde va el
     * suelo, no donde van los pies.
     */
    private static void ensurePlatform(ServerWorld world, BlockPos center) {
        // Sin esto no funciona nada: en una dimensión sin jugadores los chunks
        // NO están cargados, y escribir bloques ahí se ignora en silencio.
        // Verificado en el servidor: "That position is not loaded".
        world.getChunk(center);

        if (!world.getBlockState(center).isAir()) return;   // ya hay algo

        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                BlockPos pos = center.add(x, 0, z);
                if (world.getBlockState(pos).isAir()) {
                    world.setBlockState(pos, Blocks.SMOOTH_STONE.getDefaultState());
                }
            }
        }
        LunaEternal.LOG.info("Plataforma de emergencia creada en {} ({})",
                             center, world.getRegistryKey().getValue());
    }

    /** Nombre legible de una dimensión, para mensajes. */
    public static String nameOf(RegistryKey<World> key) {
        if (key.equals(LunaDimensions.LOBBY)) return "el Lobby";
        if (key.equals(LunaDimensions.CIUDADELA)) return "la Ciudadela";
        if (key.equals(LunaDimensions.SALVAJE)) return "el Mundo Salvaje";
        if (key.equals(LunaDimensions.HOGAR)) return "el Mundo Hogar";
        return key.getValue().toString();
    }
}
