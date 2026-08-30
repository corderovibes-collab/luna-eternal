package net.pokereport.luna.world;

import java.util.Set;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * MOVER A UN JUGADOR, SIEMPRE IGUAL.
 *
 * <p>Había ocho teletransportes repartidos por el mod y cada uno hacía las
 * cosas a su manera: unos apuntaban dónde estabas y otros no, unos cargaban el
 * destino y otros no. Esto es el sitio único.
 *
 * <h2>⚠⚠⚠ EL CHUNK DE DESTINO SE CARGA ANTES DE MOVER</h2>
 *
 * En una dimensión donde no hay nadie <b>no hay ni un chunk cargado</b>, y
 * llegar a uno que todavía no existe es la mitad de los problemas raros de
 * teletransporte: caes antes de que haya suelo, o te quedas en la pantalla de
 * carga.
 *
 * <p>Es exactamente el mismo caso que ya estaba escrito para los <i>bloques</i>
 * en {@code TravelService.ensurePlatform} —«escribir en un chunk descargado se
 * ignora en silencio»— aplicado a las <i>entidades</i>. La ciudadela es el peor
 * caso: es un vacío con una isla, así que aterrizar sin chunk es caerse.
 *
 * <h2>⚠⚠ Y SE APUNTA DÓNDE ESTABAS ANTES DE MOVERTE</h2>
 *
 * Después ya estás en el otro mundo y se guardaría la posición de destino:
 * volver te devolvería a donde acabas de llegar. Es una trampa en la que este
 * proyecto ya cayó dos veces —el Mundo Hogar y la arena del gimnasio— así que
 * aquí va antes, y no depende de que quien llame se acuerde.
 */
public final class Traslado {

    private Traslado() {}

    /**
     * Lleva a un jugador, cargando el destino y apuntando de dónde viene.
     *
     * @return {@code false} si el jugador ya no está
     */
    public static boolean ir(ServerPlayerEntity jugador, ServerWorld destino,
                             Vec3d donde, float giro, float inclinacion) {
        if (jugador.isRemoved() || jugador.isDisconnected()) {
            return false;
        }
        // ⚠ Primero el chunk. Después el apunte. Y por último mover.
        destino.getChunk(BlockPos.ofFloored(donde));
        Regreso.apuntar(jugador);
        jugador.closeHandledScreen();
        jugador.teleport(destino, donde.x, donde.y, donde.z, Set.of(),
                giro, inclinacion);
        return true;
    }

    /** Como {@link #ir}, conservando hacia dónde miraba. */
    public static boolean ir(ServerPlayerEntity jugador, ServerWorld destino,
                             Vec3d donde) {
        return ir(jugador, destino, donde, jugador.getYaw(), jugador.getPitch());
    }
}
