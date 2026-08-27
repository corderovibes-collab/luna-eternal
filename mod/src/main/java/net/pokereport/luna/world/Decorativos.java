package net.pokereport.luna.world;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;

/**
 * POKÉMON DE DECORACIÓN: quietos, sin etiqueta y sin nivel.
 *
 * <p>Petición del usuario para el laboratorio de Oak: un Mewtwo flotando dentro
 * de un recipiente y los tres iniciales de Kanto delante, <i>«que no vayan a
 * hacer ninguna acción, ahí de decoración»</i>.
 *
 * <h2>⚠⚠ ES UN POKÉMON DE VERDAD, NO UNA ESTATUA</h2>
 *
 * No hay forma de dibujar un modelo de Cobblemon sin su entidad: los modelos y
 * sus animaciones viven dentro del mod. Así que se usa su entidad y se le
 * apagan <b>todas</b> las cosas que la hacen un Pokémon vivo.
 *
 * <h2>⚠⚠⚠ Y SON SIETE COSAS, NO UNA</h2>
 *
 * Olvidar cualquiera deja algo que se nota:
 *
 * <table>
 *   <tr><td>{@code hideNameRendering}</td><td>sin la etiqueta ni el nivel encima</td></tr>
 *   <tr><td>{@code setAiDisabled}</td><td>no anda, no mira, no huye</td></tr>
 *   <tr><td>{@code uncatchable}</td><td><b>no se puede capturar</b>. Sin esto,
 *       la decoración del laboratorio dura hasta el primer jugador con una
 *       Poké Ball</td></tr>
 *   <tr><td>{@code setInvulnerable}</td><td>no se le puede pegar</td></tr>
 *   <tr><td>{@code setSilent}</td><td>callado. Cuatro Pokémon haciendo ruido en
 *       un cuarto pequeño es insoportable</td></tr>
 *   <tr><td>{@code setPersistent}</td><td>no desaparece al alejarse. Sin esto,
 *       la decoración se esfuma sola y nadie sabe por qué</td></tr>
 *   <tr><td>{@code enablePoseTypeRecalculation}</td><td>⚠ <b>la que no es
 *       obvia</b>: Cobblemon recalcula la postura en cada tick. Sin apagarlo,
 *       poner «dormido» dura exactamente un tick</td></tr>
 * </table>
 *
 * <p>⚠ Y llevan una etiqueta de marcador ({@link #MARCA}) para poder borrarlos
 * después. Sin una marca, quitar la decoración obliga a acertarle con el ratón
 * a cada uno — y a distinguirlos de un Pokémon de verdad.
 */
public final class Decorativos {

    private Decorativos() {}

    /** La etiqueta que los distingue de un Pokémon cualquiera. */
    public static final String MARCA = "luna_decorativo";

    /**
     * Las posturas que se pueden pedir.
     *
     * <p>⚠ Tres y no las quince de Cobblemon: las demás son estados de combate
     * o de movimiento, y una postura de andar en algo que no anda se ve peor
     * que estar de pie.
     */
    public enum Postura {
        QUIETO(PoseType.STAND, false),
        DORMIDO(PoseType.SLEEP, false),
        // ⚠ Flotando va SIN GRAVEDAD, si no cae al suelo y se queda ahí con
        //   cara de estar volando. Es lo que hace falta para el recipiente del
        //   laboratorio.
        FLOTANDO(PoseType.HOVER, true);

        public final PoseType pose;
        public final boolean sinGravedad;

        Postura(PoseType pose, boolean sinGravedad) {
            this.pose = pose;
            this.sinGravedad = sinGravedad;
        }
    }

    /**
     * Coloca uno. Devuelve {@code null} si la especie no existe.
     *
     * <p>⚠ La postura se fija <b>después</b> de soltarlo en el mundo: antes de
     * existir, el rastreador de datos no se sincroniza con nadie y el valor se
     * pierde en el camino.
     */
    public static PokemonEntity colocar(ServerWorld mundo, String especie,
                                        Postura postura, Vec3d donde, float giro) {
        PokemonEntity e;
        try {
            // ⚠ `uncatchable` es una propiedad de Cobblemon, no un invento
            //   nuestro: se pone con su misma sintaxis y la respeta su propio
            //   código de captura. Comprobado en el jar (UncatchableProperty).
            var props = PokemonProperties.Companion.parse(
                    especie.toLowerCase(java.util.Locale.ROOT) + " uncatchable");
            e = props.createEntity(mundo);
        } catch (Exception ex) {
            LunaEternal.LOG.warn("No se pudo crear el decorativo {}: {}",
                    especie, ex.toString());
            return null;
        }

        e.refreshPositionAndAngles(donde.x, donde.y, donde.z, giro, 0f);
        e.setHeadYaw(giro);
        e.setBodyYaw(giro);

        e.hideNameRendering();
        e.setAiDisabled(true);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setPersistent();
        e.setNoGravity(postura.sinGravedad);
        e.addCommandTag(MARCA);
        // ⚠⚠ ESTA ES LA QUE NO ES OBVIA. Cobblemon recalcula la postura en cada
        //    tick a partir de si anda, vuela o nada. Sin apagarlo, «dormido»
        //    dura un tick y vuelve a ponerse de pie.
        e.setEnablePoseTypeRecalculation(false);

        if (!mundo.spawnEntity(e)) {
            return null;
        }
        e.getDataTracker().set(PokemonEntity.getPOSE_TYPE(), postura.pose);
        return e;
    }

    /** Cuántos decorativos hay cerca, y los borra. */
    public static int quitar(ServerWorld mundo, Vec3d centro, double radio) {
        var caja = net.minecraft.util.math.Box.of(centro, radio * 2, radio * 2, radio * 2);
        int n = 0;
        for (var e : mundo.getEntitiesByClass(PokemonEntity.class, caja,
                x -> x.getCommandTags().contains(MARCA))) {
            e.discard();
            n++;
        }
        return n;
    }
}
