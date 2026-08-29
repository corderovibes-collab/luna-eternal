package net.pokereport.luna.gym;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.pokereport.luna.LunaEternal;

/**
 * «HAZ ESTO DENTRO DE N TICKS», EN EL HILO DEL SERVIDOR.
 *
 * <h2>Por qué no vale ni un hilo ni el executor de E/S</h2>
 *
 * Lo que hay que retrasar es <b>tocar el mundo</b>: mover a un jugador de
 * dimensión cuando el combate ha terminado de recoger. Eso solo se puede hacer
 * en el hilo del servidor, y {@code LunaEternal.submit} va justo al contrario —
 * saca el trabajo de ahí.
 *
 * <p>Un {@code Thread.sleep} sería peor todavía: bloquearía el hilo del que
 * saliera y, si fuera el del servidor, el servidor entero.
 *
 * <h2>⚠⚠ POR QUÉ HAY QUE ESPERAR</h2>
 *
 * Al terminar un combate, Cobblemon sigue trabajando unos ticks: animaciones,
 * guardar los Pokémon, mensajes. Sacar al jugador del mundo en ese instante deja
 * <b>Pokémon sueltos en una sala vacía</b> — y esa sala es una copia que se
 * reutiliza, así que se quedarían ahí para el siguiente.
 *
 * <h2>⚠ Y CADA TAREA SE PROTEGE POR SEPARADO</h2>
 *
 * Si una lanzara una excepción, se llevaría por delante a las que quedan en la
 * lista — que no tienen nada que ver con ella. El aviso va al log y la siguiente
 * sigue su camino.
 */
public final class Programador {

    private Programador() {}

    private record Tarea(long cuando, Runnable que) {}

    /**
     * ⚠ Sincronizada aunque el tick sea de un solo hilo: {@link #en} se llama
     * desde el evento de fin de combate de Cobblemon, y no está garantizado que
     * eso corra en el hilo del servidor.
     */
    private static final List<Tarea> PENDIENTES =
            java.util.Collections.synchronizedList(new ArrayList<>());

    private static long ticks;

    /** Encola algo para dentro de {@code espera} ticks. */
    public static void en(int espera, Runnable que) {
        PENDIENTES.add(new Tarea(ticks + Math.max(1, espera), que));
    }

    /** Se llama una vez por tick desde {@code LunaEternal}. */
    public static void tick(MinecraftServer servidor) {
        ticks++;
        if (PENDIENTES.isEmpty()) {
            return;
        }
        List<Runnable> toca = null;
        synchronized (PENDIENTES) {
            var it = PENDIENTES.iterator();
            while (it.hasNext()) {
                var t = it.next();
                if (t.cuando() <= ticks) {
                    if (toca == null) {
                        toca = new ArrayList<>(2);
                    }
                    toca.add(t.que());
                    it.remove();
                }
            }
        }
        if (toca == null) {
            return;
        }
        // ⚠ Fuera del `synchronized`: una tarea puede encolar otra, y hacerlo
        //   con el candado cogido se bloquearía a sí misma.
        for (Runnable r : toca) {
            try {
                r.run();
            } catch (Throwable t) {
                LunaEternal.LOG.error("Fallo en una tarea programada", t);
            }
        }
    }

    /** Al parar el servidor: lo pendiente ya no tiene mundo al que volver. */
    public static void olvidarTodo() {
        PENDIENTES.clear();
    }

    /** Cuántas quedan. Para el autotest. */
    public static int pendientes() {
        return PENDIENTES.size();
    }
}
