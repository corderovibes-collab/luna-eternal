package net.pokereport.luna.pokedex;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «¿Ha pasado ya suficiente desde la última vez para esta clave?»
 *
 * <p>Una clave es lo que quiera quien lo use: un jugador, un jugador y una
 * especie, o nada. Sirve para dos cosas que parecen distintas y son la misma:
 * <b>tragarse repeticiones que no pidió nadie</b> y <b>frenar a quien insiste</b>.
 *
 * <p>Usa {@link System#nanoTime()} y no el reloj de pared: nanoTime no da saltos
 * si alguien cambia la hora del sistema ni con el horario de verano, y aquí solo
 * importan las diferencias.
 *
 * <p>Se limpia sola: cuando pasa de {@code TOPE} claves tira las caducadas. Sin
 * eso, un servidor con mucha gente acumularía una entrada por jugador para
 * siempre — poco, pero para siempre.
 */
public final class Cooldown {

    /** A partir de aquí se barren las caducadas. Muy por encima de lo normal. */
    private static final int TOPE = 512;

    private final Map<String, Long> ultima = new ConcurrentHashMap<>();
    private final long esperaNanos;

    public Cooldown(long milis) {
        this.esperaNanos = milis * 1_000_000L;
    }

    /**
     * ¿Toca? Si sí, <b>anota que toca ahora</b> y devuelve {@code true}.
     *
     * <p>Comprueba y marca en la misma llamada a propósito: separarlo invita a
     * comprobar y olvidarse de marcar, que es como se cuelan las repeticiones
     * que esto viene a evitar.
     */
    public boolean toca(String clave) {
        long ahora = System.nanoTime();
        Long antes = ultima.get(clave);
        if (antes != null && ahora - antes < esperaNanos) {
            return false;
        }
        ultima.put(clave, ahora);
        if (ultima.size() > TOPE) {
            ultima.entrySet().removeIf(e -> ahora - e.getValue() >= esperaNanos);
        }
        return true;
    }

    /** Olvida una clave. Al salir un jugador, para no guardarlo eternamente. */
    public void olvidar(String clave) {
        ultima.remove(clave);
    }
}
