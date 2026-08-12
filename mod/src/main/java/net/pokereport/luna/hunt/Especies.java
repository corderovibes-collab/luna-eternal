package net.pokereport.luna.hunt;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import net.pokereport.luna.LunaEternal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * De dónde salen los Pokémon de una caza.
 *
 * <p>La lista <b>no se escribe a mano</b>: se pregunta al registro de
 * Cobblemon en tiempo de ejecución. Una lista propia de 251 nombres se
 * desincronizaría en cuanto cambiara algo, y ya nos ha pasado con los nombres
 * raros (farfetch'd, porygon-z).
 *
 * <p>Se filtra por número de Pokédex ≤ 251 para respetar D-017: solo Kanto y
 * Johto están activos, y sortear un Pokémon que no puede aparecer sería una
 * caza imposible.
 */
public final class Especies {

    /** Kanto + Johto (D-017). */
    private static final int MAX_DEX = 251;

    /**
     * Etiquetas que descartan una especie como objetivo de caza.
     *
     * <p>Salio Mewtwo en el primer ciclo y no puede ser: una caza tiene que
     * ser <b>alcanzable en 12 horas</b>. Un legendario aparece una vez cada
     * mucho o directamente no aparece, asi que la caza seria imposible y el
     * jugador solo aprenderia a ignorarlas.
     *
     * <p>Los legendarios tienen su sitio —los cofres (D-020)—, no aqui.
     */
    private static final java.util.Set<String> PROHIBIDAS = java.util.Set.of(
        "legendary", "mythical", "ultra_beast", "paradox", "restricted");

    public record Especie(String nombre, int dex) {}

    private static volatile List<Especie> cache;

    private Especies() {}

    /** Todas las candidatas. Se calcula una vez: el registro no cambia. */
    public static List<Especie> disponibles() {
        List<Especie> local = cache;
        if (local != null) return local;

        List<Especie> out = new ArrayList<>();
        try {
            for (var s : PokemonSpecies.getImplemented()) {
                int dex = s.getNationalPokedexNumber();
                if (dex < 1 || dex > MAX_DEX) continue;

                boolean rara = false;
                try {
                    for (String etiqueta : s.getLabels()) {
                        if (PROHIBIDAS.contains(etiqueta.toLowerCase())) {
                            rara = true;
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                    // Si algun dia desaparecen las etiquetas, es preferible
                    // dejar pasar la especie que quedarse sin cazas.
                }
                if (rara) continue;

                out.add(new Especie(s.getName().toLowerCase(), dex));
            }
        } catch (Throwable t) {
            LunaEternal.LOG.error("No se pudo leer el registro de especies", t);
        }

        if (out.isEmpty()) {
            // Sin registro no hay cazas, pero el servidor tiene que seguir.
            // Un puñado de iniciales evita que la pantalla salga vacia.
            LunaEternal.LOG.warn("Registro de especies vacío: uso una lista mínima");
            out.add(new Especie("bulbasaur", 1));
            out.add(new Especie("charmander", 4));
            out.add(new Especie("squirtle", 7));
            out.add(new Especie("pikachu", 25));
            out.add(new Especie("chikorita", 152));
        }
        LunaEternal.LOG.info("Cazas: {} especies candidatas (Kanto+Johto, "
                             + "sin legendarios)", out.size());
        cache = out;
        return out;
    }

    /** {@code n} especies distintas, al azar. */
    public static List<Especie> sortear(int n) {
        List<Especie> copia = new ArrayList<>(disponibles());
        Collections.shuffle(copia, ThreadLocalRandom.current());
        return copia.subList(0, Math.min(n, copia.size()));
    }
}
