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
                if (dex >= 1 && dex <= MAX_DEX) {
                    out.add(new Especie(s.getName().toLowerCase(), dex));
                }
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
