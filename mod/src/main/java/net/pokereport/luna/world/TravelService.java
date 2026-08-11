package net.pokereport.luna.world;

import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
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

    /** Punto de aparición de las dimensiones vacías. */
    private static final BlockPos VOID_SPAWN = new BlockPos(0, 64, 0);
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

        BlockPos destination = safeSpawn(world);

        player.closeHandledScreen();
        player.teleport(world,
            destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
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
    private static BlockPos safeSpawn(ServerWorld world) {
        boolean isVoid = world.getRegistryKey().equals(LunaDimensions.LOBBY)
                      || world.getRegistryKey().equals(LunaDimensions.CIUDADELA);

        if (isVoid) {
            ensurePlatform(world, VOID_SPAWN);
            return VOID_SPAWN.up();
        }

        BlockPos spawn = world.getSpawnPos();
        // Mismo motivo: sin el chunk cargado, getTopY devuelve basura y el
        // jugador aterriza dentro de la roca o en el aire.
        world.getChunk(spawn);

        int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                             spawn.getX(), spawn.getZ());
        return new BlockPos(spawn.getX(), Math.max(y, world.getBottomY() + 1), spawn.getZ());
    }

    /**
     * Coloca una plataforma bajo el punto de aparición si no hay nada.
     *
     * <p>Es una red de seguridad, no decoración: en cuanto la ciudadela esté
     * construida no se activará nunca. Pero mientras tanto evita el fallo más
     * tonto y más frustrante posible — caer al vacío al entrar por primera vez.
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
