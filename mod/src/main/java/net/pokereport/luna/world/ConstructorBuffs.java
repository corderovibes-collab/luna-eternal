package net.pokereport.luna.world;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * Visión nocturna, velocidad y salto para quien está construyendo la ciudadela.
 *
 * <p><b>Por qué hace falta.</b> La ciudadela es de noche permanente
 * ({@code fixed_time 18000}) con luz ambiental de 0,45: es la estética que se
 * quiere para el jugador, pero para <i>construirla</i> es un estorbo — no se
 * distingue un bloque gris oscuro de otro. Y una ciudad de 56×56 se recorre
 * muchas veces al día.
 *
 * <p><b>Solo a los constructores, y solo dentro.</b> Dos condiciones, y las dos
 * importan: fuera de la ciudadela nadie los tiene, y dentro solo quien puede
 * construir de verdad. Un jugador normal que un día entre a mirar la ve tal y
 * como está pensada, de noche.
 *
 * <h2>Por qué OP nivel 2 y no una lista aparte</h2>
 *
 * <p>Porque <b>ya es</b> la definición de constructor en este servidor (D-028):
 * es lo que Axiom y WorldEdit comprueban para dejar construir. Mantener una
 * segunda lista de nombres solo conseguiría que se desincronizara con la
 * primera, y entonces habría gente que puede construir a oscuras y gente que
 * ve pero no puede tocar nada.
 */
public final class ConstructorBuffs {

    /**
     * Cada cuánto se repasa. Cinco segundos.
     *
     * <p>Como los efectos son infinitos, este número no decide cuánto duran:
     * decide cuánto se tarda en <b>ponerlos al entrar y quitarlos al salir</b>.
     * Cinco segundos cambiando de dimensión no se notan, y repasar la lista de
     * conectados una vez cada cien ticks no le cuesta nada al servidor.
     */
    private static final int CADA = 100;

    /**
     * ⚠️ INFINITOS, así que ALGUIEN tiene que quitarlos — y es este mismo
     * repaso.
     *
     * <p>Un efecto con duración se va solo; uno infinito se queda para siempre
     * si nadie lo retira, incluso fuera de la ciudadela o después de perder el
     * OP. Por eso {@link #tick} no solo da: también <b>quita</b> a quien ya no
     * cumple. Al hacerse en el mismo sitio y sobre todos los conectados, cubre
     * de una vez todas las salidas —cambiar de dimensión, perder el rango,
     * reconectar en otro mundo— sin tener que acordarse de engancharse a cada
     * una de ellas.
     */
    private static final int INFINITO = StatusEffectInstance.INFINITE;

    private static final List<RegistryEntry<StatusEffect>> EFECTOS = List.of(
            StatusEffects.NIGHT_VISION,
            StatusEffects.SPEED,
            StatusEffects.JUMP_BOOST);

    /**
     * Velocidad II: la de CORRER, no la de picar.
     *
     * <p>Aquí hubo {@code HASTE} y estaba mal. En Minecraft «Prisa» es
     * velocidad de <b>minado</b> —lo rápido que se rompe un bloque— y lo que
     * hace falta para recorrer una plaza de 56×56 es {@code SPEED}, que es la
     * de moverse. Se llaman parecido en español y hacen cosas distintas.
     */
    private static final int NIVEL_VELOCIDAD = 1;

    /**
     * ⚠️ EFECTOS QUE DIMOS ALGUNA VEZ Y YA NO DAMOS. Hay que retirarlos.
     *
     * <p>Y esto no es teoría: pasó. {@code HASTE} estuvo en la lista de arriba
     * una hora, y al cambiarlo por {@code SPEED} se quedó puesto —infinito— en
     * todos los que lo habían recibido. Nadie se lo quitaba: {@link #quitar}
     * solo entra cuando el jugador deja de cumplir, y ellos seguían siendo
     * constructores dentro de la ciudadela. Hubo que limpiarlo a mano con
     * {@code /effect clear}.
     *
     * <p>Es el precio de que sean infinitos, y se paga aquí: <b>al quitar una
     * cosa de {@code EFECTOS}, se añade a esta lista</b>. Así el propio repaso
     * lo limpia de todo el mundo y nadie se queda con un efecto fantasma.
     */
    private static final List<RegistryEntry<StatusEffect>> RETIRADOS = List.of(
            StatusEffects.HASTE);

    private ConstructorBuffs() {}

    /** Se llama una vez por tick; él decide cuándo toca. */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % CADA != 0) {
            return;
        }
        for (ServerPlayerEntity jugador : server.getPlayerManager().getPlayerList()) {
            // Lo retirado se limpia SIEMPRE, cumpla o no: quien lo tenga
            // puesto de una version anterior sigue siendo constructor y no
            // pasaria nunca por `quitar`.
            limpiar(jugador, RETIRADOS);
            if (aplica(jugador)) {
                dar(jugador);
            } else {
                limpiar(jugador, EFECTOS);
            }
        }
    }

    private static void dar(ServerPlayerEntity jugador) {
        for (RegistryEntry<StatusEffect> efecto : EFECTOS) {
            int nivel = efecto == StatusEffects.SPEED ? NIVEL_VELOCIDAD : 0;
            StatusEffectInstance actual = jugador.getStatusEffect(efecto);
            // Solo se manda si falta o no es el nuestro. Reasignarlo cada cinco
            // segundos gastaria un paquete por jugador y efecto para nada, y
            // ademas reinicia la animacion del icono, que parpadearia.
            if (actual != null && actual.isInfinite() && actual.getAmplifier() == nivel) {
                continue;
            }
            jugador.addStatusEffect(new StatusEffectInstance(
                    efecto, INFINITO, nivel,
                    true,      // ambiental: el borde de pantalla no parpadea
                    false,     // sin particulas: estorban al construir
                    true));    // pero CON icono, para saber por que ve
        }
    }

    /**
     * Retira los nuestros, y <b>solo</b> los nuestros.
     *
     * <p>La marca es que sean infinitos: una poción de visión nocturna dura
     * ocho minutos, no para siempre. Así a quien se bebió una no se le corta
     * por salir de la ciudadela, que sería un efecto secundario absurdo de una
     * ayuda para constructores.
     */
    private static void limpiar(ServerPlayerEntity jugador,
                                List<RegistryEntry<StatusEffect>> cuales) {
        for (RegistryEntry<StatusEffect> efecto : cuales) {
            StatusEffectInstance actual = jugador.getStatusEffect(efecto);
            if (actual != null && actual.isInfinite()) {
                jugador.removeStatusEffect(efecto);
            }
        }
    }

    /** ¿Está en la ciudadela y puede construir en ella? */
    private static boolean aplica(ServerPlayerEntity jugador) {
        return jugador.getWorld().getRegistryKey() == LunaDimensions.CIUDADELA
                && jugador.hasPermissionLevel(2);
    }
}
