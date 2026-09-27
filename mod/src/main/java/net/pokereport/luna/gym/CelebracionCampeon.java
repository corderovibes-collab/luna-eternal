package net.pokereport.luna.gym;

import java.util.UUID;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Celebración breve para victorias de campeonato.
 *
 * <p>Las explosiones se emiten como partículas del servidor, no como cohetes
 * físicos: decoran la arena sin incendiar el mapa, dañar entidades ni dejar
 * objetos persistentes en una ranura que otro jugador reutilizará.
 */
public final class CelebracionCampeon {

    private CelebracionCampeon() {}

    /** Tres ráfagas antes del regreso de cinco segundos a la Ciudadela. */
    public static void lanzar(ServerPlayerEntity jugador) {
        MinecraftServer servidor = jugador.getServer();
        if (servidor == null) {
            return;
        }
        UUID uuid = jugador.getUuid();
        ServerWorld arena = jugador.getServerWorld();
        for (int rafaga = 0; rafaga < 3; rafaga++) {
            final int indice = rafaga;
            Programador.en(6 + rafaga * 16, () -> {
                ServerPlayerEntity vivo = servidor.getPlayerManager().getPlayer(uuid);
                if (vivo == null || vivo.isRemoved() || vivo.getServerWorld() != arena) {
                    return;
                }
                estallar(arena, vivo.getPos(), indice);
            });
        }
    }

    private static void estallar(ServerWorld mundo, Vec3d centro, int indice) {
        double lateral = indice == 1 ? 0.0 : (indice == 0 ? -3.5 : 3.5);
        double alto = 3.0 + indice * 0.55;
        mundo.spawnParticles(ParticleTypes.FIREWORK,
                centro.x + lateral, centro.y + alto, centro.z + 1.5,
                28, 1.25, 0.8, 1.25, 0.08);
        mundo.playSound(null, centro.x + lateral, centro.y + alto, centro.z + 1.5,
                SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS,
                1.0f, 1.0f + indice * 0.08f);
    }
}
