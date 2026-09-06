package net.pokereport.luna.torrebatalla;

import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.world.LunaDimensions;
import java.util.concurrent.ConcurrentHashMap;

public class TorreBatallaService {
    
    // Rastrea en qué arena está cada jugador
    private static final ConcurrentHashMap<ServerPlayerEntity, Integer> jugadoresEnArena = new ConcurrentHashMap<>();
    
    // Rastrea qué arenas (IDs) están en uso
    private static final ConcurrentHashMap<Integer, Boolean> arenasOcupadas = new ConcurrentHashMap<>();

    public static void iniciarCola(ServerPlayerEntity jugador, int modo) {
        // Encontrar una arena libre
        int arenaId = 0;
        while (arenasOcupadas.containsKey(arenaId) && arenasOcupadas.get(arenaId)) {
            arenaId++;
        }
        
        arenasOcupadas.put(arenaId, true);
        jugadoresEnArena.put(jugador, arenaId);
        
        // Coordenadas
        int cx = arenaId * 1000;
        int cy = 100;
        int cz = 0;
        
        ServerWorld mundoTorre = jugador.getServer().getWorld(LunaDimensions.TORRE);
        if (mundoTorre == null) {
            jugador.sendMessage(net.minecraft.text.Text.literal("Error: La dimensión de la torre no está cargada."));
            return;
        }

        // Construir la base 10x10 (radio 5) si no existe
        construirBase(mundoTorre, cx, cy, cz);
        
        // Teletransportar al jugador al centro
        jugador.teleport(mundoTorre, cx + 0.5, cy + 1, cz + 0.5, 0, 0);
        
        // TODO: Fase 3/4 - Spawnear al primer rival o iniciar combate
        jugador.sendMessage(net.minecraft.text.Text.literal("¡Bienvenido a la Arena " + arenaId + "! Modo: " + modo));
    }

    private static void construirBase(ServerWorld mundo, int cx, int cy, int cz) {
        // Una plataforma simple de 11x11 centrada
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos pos = new BlockPos(cx + dx, cy, cz + dz);
                // Solo colocar si es aire para no causar lag repitiéndolo
                if (mundo.isAir(pos)) {
                    mundo.setBlockState(pos, Blocks.SMOOTH_QUARTZ.getDefaultState());
                }
            }
        }
    }
    
    public static void salir(ServerPlayerEntity jugador) {
        Integer arenaId = jugadoresEnArena.remove(jugador);
        if (arenaId != null) {
            arenasOcupadas.put(arenaId, false);
            // TODO: Teletransportar de vuelta a la entrada o al spawn
        }
    }
}