package net.pokereport.luna.gym;

import java.util.ArrayList;
import java.util.List;
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
 * <p>Delimitado exactamente entre las coordenadas solicitadas por el usuario:
 * X: 4127.0 a 4157.0 (~30 bloques de ancho)
 * Y: 67.0 a 78.0 (suelo en Y=68, altura de combate)
 * Z: 19.0 a 74.0 (~55 bloques de largo)
 *
 * <p>Reglas de diseño del usuario:
 * <ul>
 *   <li><b>Sin daño</b>: NO baja vida ni corazones al jugador.</li>
 *   <li><b>Visibilidad limpia y lejana</b>: partículas translúcidas (WITCH y PORTAL)
 *       distribuidas por TODO el recuadro de la arena usando {@code force = true},
 *       visibles incluso desde el extremo opuesto a lo lejos sin opacar la vista.</li>
 * </ul>
 */
public final class ZonaVenenoKoga {

    public static final double MIN_X = 4127.0;
    public static final double MAX_X = 4157.0;
    public static final double MIN_Y = 67.0;
    public static final double MAX_Y = 78.0;
    public static final double MIN_Z = 19.0;
    public static final double MAX_Z = 74.0;

    public static final Box CAJA_ARENA = new Box(MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Y, MAX_Z);
    // Observadores dentro de la arena o a una distancia de visión de hasta 64 bloques
    private static final Box CAJA_OBSERVADORES = CAJA_ARENA.expand(64.0, 20.0, 64.0);

    private static final Random RNG = new Random();

    // Se procesa cada 2 ticks (10 veces por segundo) para fluidez y presencia constante
    private static final int CADA_TICKS = 2;

    // Cuadrícula 3x5 para garantizar cobertura homogénea en toda la arena (sin huecos vacíos)
    private static final int CELDAS_X = 3;
    private static final int CELDAS_Z = 5;
    private static final double ANCHO_CELDA_X = (MAX_X - MIN_X) / CELDAS_X; // ~10.0
    private static final double ANCHO_CELDA_Z = (MAX_Z - MIN_Z) / CELDAS_Z; // ~11.0

    private ZonaVenenoKoga() {}

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % CADA_TICKS != 0) {
            return;
        }

        ServerWorld mundo = server.getWorld(LunaDimensions.GIMNASIOS);
        if (mundo == null || mundo.getPlayers().isEmpty()) {
            return;
        }

        for (int ranura = 0; ranura < Gimnasio.RANURAS; ranura++) {
            double zOffset = ranura * Gimnasio.PASO_RANURA;
            Box cajaArena = CAJA_ARENA.offset(0, 0, zOffset);
            Box cajaObservadores = CAJA_OBSERVADORES.offset(0, 0, zOffset);

            List<ServerPlayerEntity> observadores = new ArrayList<>();
            for (ServerPlayerEntity p : mundo.getPlayers()) {
                if (cajaObservadores.contains(p.getX(), p.getY(), p.getZ())) {
                    observadores.add(p);
                }
            }

            if (observadores.isEmpty()) {
                continue;
            }

            // 1. Efecto ambiental distribuido por TODO el recuadro de la arena (visible a lo lejos)
            for (int cx = 0; cx < CELDAS_X; cx++) {
                for (int cz = 0; cz < CELDAS_Z; cz++) {
                    if (RNG.nextBoolean()) {
                        double x = MIN_X + (cx + RNG.nextDouble()) * ANCHO_CELDA_X;
                        double z = (MIN_Z + zOffset) + (cz + RNG.nextDouble()) * ANCHO_CELDA_Z;
                        double y = 68.2 + RNG.nextDouble() * 1.8;

                        for (ServerPlayerEntity obs : observadores) {
                            // WITCH: Chispitas mágicas violetas flotantes (translúcidas, elegantes)
                            mundo.spawnParticles(obs, ParticleTypes.WITCH, true,
                                    x, y, z, 2, 0.4, 0.2, 0.4, 0.01);

                            // Emanación suave desde el suelo con partículas PORTAL
                            if (RNG.nextFloat() < 0.35f) {
                                mundo.spawnParticles(obs, ParticleTypes.PORTAL, true,
                                        x, 68.1, z, 1, 0.2, 0.1, 0.2, 0.02);
                            }
                        }
                    }
                }
            }

            // 2. Efecto personal adicional para quienes caminan DENTRO de la arena
            for (ServerPlayerEntity jugador : observadores) {
                double px = jugador.getX();
                double py = jugador.getY();
                double pz = jugador.getZ();

                if (cajaArena.contains(px, py, pz)) {
                    mundo.spawnParticles(jugador, ParticleTypes.WITCH, true,
                            px + (RNG.nextDouble() - 0.5) * 3.5,
                            py + RNG.nextDouble() * 1.6,
                            pz + (RNG.nextDouble() - 0.5) * 3.5,
                            2, 0.2, 0.2, 0.2, 0.01);

                    if (RNG.nextBoolean()) {
                        mundo.spawnParticles(jugador, ParticleTypes.PORTAL, true,
                                px + (RNG.nextDouble() - 0.5) * 2.5,
                                py + 0.1,
                                pz + (RNG.nextDouble() - 0.5) * 2.5,
                                1, 0.1, 0.1, 0.1, 0.02);
                    }
                }
            }
        }
    }
}
