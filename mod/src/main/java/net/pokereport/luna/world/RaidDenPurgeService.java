package net.pokereport.luna.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import net.pokereport.luna.LunaEternal;

import java.util.ArrayList;
import java.util.List;

/**
 * ERRADICACIÓN SEGURA DE RAID DENS EN MUNDO HOGAR Y MUNDOS SALVAJES.
 *
 * <p>No accede jamás a chunks no cargados ni ejecuta cambios sincrónicos
 * dentro del evento de carga de chunks (evita getChunkBlocking / Watchdog deadlock).
 */
public final class RaidDenPurgeService {

    private static final String MOD_NAMESPACE = "cobblemonraiddens";
    private static int tickCounter = 0;

    private RaidDenPurgeService() {}

    /**
     * Purga exclusivamente la posición exacta del bloque y su BlockEntity.
     * Solo si el chunk ya está cargado y usando NOTIFY_LISTENERS (sin propagación vecina).
     */
    public static void purgar(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) return;
        if (!world.isChunkLoaded(pos)) return;
        var state = world.getBlockState(pos);
        var id = Registries.BLOCK.getId(state.getBlock());
        if (id != null && MOD_NAMESPACE.equals(id.getNamespace())) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.removeBlockEntity(pos);
        }
    }

    public static void registrar() {
        // Capa 1: Al cargar un chunk, recolectar posiciones y ejecutar fuera del ciclo de carga
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (!FaunaControl.esHogarOSalvaje(world.getRegistryKey())) return;
            if (chunk instanceof WorldChunk wc) {
                List<BlockPos> aPurgar = null;
                for (var entry : wc.getBlockEntities().entrySet()) {
                    BlockEntity be = entry.getValue();
                    if (be != null) {
                        var id = Registries.BLOCK_ENTITY_TYPE.getId(be.getType());
                        if (id != null && MOD_NAMESPACE.equals(id.getNamespace())) {
                            if (aPurgar == null) aPurgar = new ArrayList<>();
                            aPurgar.add(entry.getKey().toImmutable());
                        }
                    }
                }
                if (aPurgar != null) {
                    final List<BlockPos> lista = aPurgar;
                    world.getServer().execute(() -> {
                        for (BlockPos p : lista) {
                            purgar(world, p);
                        }
                    });
                }
            }
        });

        // Capa 2: Si el jugador intenta hacer clic en un bloque de raid, purgarlo
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (FaunaControl.esHogarOSalvaje(world.getRegistryKey())) {
                BlockPos pos = hitResult.getBlockPos();
                if (world.isChunkLoaded(pos)) {
                    var id = Registries.BLOCK.getId(world.getBlockState(pos).getBlock());
                    if (id != null && MOD_NAMESPACE.equals(id.getNamespace())) {
                        if (world instanceof ServerWorld sw) {
                            purgar(sw, pos);
                        }
                        return ActionResult.SUCCESS;
                    }
                }
            }
            return ActionResult.PASS;
        });

        // Capa 3: Barrido periódico de chunks cargados en memoria alrededor de jugadores activos
        ServerTickEvents.START_WORLD_TICK.register(world -> {
            if (!FaunaControl.esHogarOSalvaje(world.getRegistryKey())) return;
            tickCounter++;
            if (tickCounter % 40 != 0) return;

            for (var player : world.getPlayers()) {
                ChunkPos cp = player.getChunkPos();
                for (int cx = cp.x - 3; cx <= cp.x + 3; cx++) {
                    for (int cz = cp.z - 3; cz <= cp.z + 3; cz++) {
                        WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
                        if (chunk != null) {
                            List<BlockPos> encontrados = null;
                            for (var entry : chunk.getBlockEntities().entrySet()) {
                                BlockEntity be = entry.getValue();
                                if (be != null) {
                                    var id = Registries.BLOCK_ENTITY_TYPE.getId(be.getType());
                                    if (id != null && MOD_NAMESPACE.equals(id.getNamespace())) {
                                        if (encontrados == null) encontrados = new ArrayList<>();
                                        encontrados.add(entry.getKey().toImmutable());
                                    }
                                }
                            }
                            if (encontrados != null) {
                                for (BlockPos bp : encontrados) {
                                    purgar(world, bp);
                                }
                            }
                        }
                    }
                }
            }
        });

        LunaEternal.LOG.info("RaidDens: servicio de erradicación activo y seguro (sin deadlocks de chunk)");
    }
}
