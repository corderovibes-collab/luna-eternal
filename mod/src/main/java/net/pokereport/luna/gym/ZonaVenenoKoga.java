package net.pokereport.luna.gym;

import java.util.Random;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.pokereport.luna.world.LunaDimensions;

/**
 * Efecto ambiental visual de veneno morado en el Gimnasio de Koga.
 *
 * <p>Delimitado entre las coordenadas solicitadas:
 * X: 4127.50 a 4156.49 (~29 bloques de ancho)
 * Y: 64.0 a 85.0 (suelo, plataformas y altura de salto)
 * Z: 73.51 a 195.50 (~122 bloques de longitud)
 *
 * <p>Reglas de diseño acordadas con el usuario:
 * <ul>
 *   <li><b>Sin daño</b>: NO baja vida ni corazones al jugador.</li>
 *   <li><b>Visibilidad limpia</b>: partículas translúcidas (WITCH y PORTAL) que se ven
 *       claramente en el ambiente sin opacar ni tapar la pantalla en primera persona.</li>
 * </ul>
 */
public final class ZonaVenenoKoga {

    private static final double MIN_X = 4127.0;
    private static final double MAX_X = 4157.0;
    private static final double MIN_Y = 64.0;
    private static final double MAX_Y = 85.0;
    private static final double MIN_Z = 73.0;
    private static final double MAX_Z = 196.0;

    private static final Box CAJA_VENENO = new Box(MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Y, MAX_Z);
    private static final Box CAJA_PROXIMIDAD = CAJA_VENENO.expand(12.0, 4.0, 12.0);

    private static final Random RNG = new Random();

    // Se procesa cada 4 ticks (~5 veces por segundo): fluido a la vista y consumo 0 de CPU
    private static final int CADA_TICKS = 4;

    private ZonaVenenoKoga() {}

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % CADA_TICKS != 0) {
            return;
        }

        ServerWorld mundo = server.getWorld(LunaDimensions.GIMNASIOS);
        if (mundo == null || mundo.getPlayers().isEmpty()) {
            return;
        }

        for (ServerPlayerEntity jugador : mundo.getPlayers()) {
            double px = jugador.getX();
            double py = jugador.getY();
            double pz = jugador.getZ();

            // 1. Jugador dentro de la zona de veneno
            if (CAJA_VENENO.contains(px, py, pz)) {
                // Chispas violetas de veneno flotando suavemente alrededor del jugador
                mundo.spawnParticles(ParticleTypes.WITCH,
                        px + (RNG.nextDouble() - 0.5) * 8.0,
                        py + RNG.nextDouble() * 2.2,
                        pz + (RNG.nextDouble() - 0.5) * 8.0,
                        4,
                        0.2, 0.2, 0.2,
                        0.02);

                // Partículas moradas de portal que flotan hacia arriba desde el suelo
                mundo.spawnParticles(ParticleTypes.PORTAL,
                        px + (RNG.nextDouble() - 0.5) * 6.0,
                        py + RNG.nextDouble() * 1.5,
                        pz + (RNG.nextDouble() - 0.5) * 6.0,
                        2,
                        0.1, 0.1, 0.1,
                        0.05);

                // Efluvio suave cerca del suelo simulando suelo/aire tóxico
                if (RNG.nextBoolean()) {
                    mundo.spawnParticles(ParticleTypes.WITCH,
                            px + (RNG.nextDouble() - 0.5) * 3.0,
                            py + 0.15,
                            pz + (RNG.nextDouble() - 0.5) * 3.0,
                            2,
                            0.1, 0.05, 0.1,
                            0.01);
                }
            } else if (CAJA_PROXIMIDAD.contains(px, py, pz)) {
                // 2. Jugador aproximándose: efecto sutil en el borde visible de la zona
                double bx = Math.max(MIN_X, Math.min(MAX_X, px));
                double bz = Math.max(MIN_Z, Math.min(MAX_Z, pz));
                double by = Math.max(MIN_Y, Math.min(MAX_Y, py));

                mundo.spawnParticles(ParticleTypes.WITCH,
                        bx + (RNG.nextDouble() - 0.5) * 3.0,
                        by + RNG.nextDouble() * 1.8,
                        bz + (RNG.nextDouble() - 0.5) * 3.0,
                        2,
                        0.15, 0.15, 0.15,
                        0.01);
            }
        }
    }
}
