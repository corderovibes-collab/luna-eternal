package net.pokereport.luna.pokedex;

import java.util.Locale;
import java.util.Set;

/**
 * Qué especies tienen voz grabada en la Pokédex.
 *
 * <p>Es el catálogo, y nada más: no toca red, ni base de datos, ni sonido. Se
 * separa del oyente justamente para poder comprobarlo en {@code /luna autotest}
 * sin necesidad de un jugador escaneando (MOD-006).
 *
 * <p><b>La lista se escribe a mano y es correcto.</b> Podría deducirse mirando
 * los ficheros del cliente, pero el servidor no los tiene —el arte viaja en el
 * jar de cliente— y adivinar acabaría mandando al jugador a reproducir un
 * sonido que no existe. Aquí un nombre de más es un error visible en el
 * arranque; un nombre de menos, solo una especie muda.
 */
public final class VozService {

    /**
     * Las especies con voz, en minúsculas y sin espacios.
     *
     * <p>Cada una necesita <b>dos</b> cosas en el cliente, y las dos las genera
     * {@code tools/gen_voces.py}: el {@code .ogg} en
     * {@code assets/lunaeternal/sounds/pokedex/} y su entrada en
     * {@code sounds.json}. Añadir aquí sin generar allí deja al jugador con un
     * escaneo silencioso.
     */
    private static final Set<String> CON_VOZ = Set.of(
            "bulbasaur"
    );

    private VozService() {}

    /**
     * Normaliza el nombre que da Cobblemon al que usan los ficheros.
     *
     * <p>Sus identificadores vienen como {@code cobblemon:bulbasaur} o con
     * mayúsculas según de dónde se lean, y las formas regionales traen guiones
     * y espacios. Se reduce todo a minúsculas sin espacios.
     */
    public static String normalizar(String especie) {
        if (especie == null) {
            return "";
        }
        String s = especie.toLowerCase(Locale.ROOT).trim();
        int dosPuntos = s.indexOf(':');
        if (dosPuntos >= 0) {
            s = s.substring(dosPuntos + 1);
        }
        return s.replace(' ', '_');
    }

    /** ¿Hay voz grabada para esta especie? */
    public static boolean tieneVoz(String especie) {
        return CON_VOZ.contains(normalizar(especie));
    }

    /** Cuántas voces hay hoy. Se anuncia al arrancar. */
    public static int cuantas() {
        return CON_VOZ.size();
    }
}
