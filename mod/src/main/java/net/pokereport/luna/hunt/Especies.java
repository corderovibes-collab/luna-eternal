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

    public record Especie(String nombre, int dex, int rareza) {}

    /**
     * DE QUÉ DEPENDE QUE UN POKÉMON SEA RARO.
     *
     * <h2>⚠⚠⚠ ANTES LA RAREZA ERA LA POSICIÓN EN LA LISTA</h2>
     *
     * Se sorteaban seis al azar y al primero se le ponía ★, al segundo ★★ y al
     * tercero ★★★. <b>Las estrellas no significaban nada</b>: un Caterpie podía
     * salir de ★★★ y pagar 2.500, y un Dragonite de ★ pagando 500. Lo dijo el
     * usuario: <i>«que tenga sentido»</i>.
     *
     * <h2>Los cuatro datos, y por qué hacen falta los cuatro</h2>
     *
     * <table>
     *   <tr><td><b>BST</b></td>
     *       <td>la suma de estadísticas base. Dice <b>lo bueno que es</b>:
     *           Dragonite 600, Pidgeot 479, Caterpie 195</td></tr>
     *   <tr><td><b>Ratio de captura</b></td>
     *       <td>lo dice el propio juego: 255 se atrapa solo, 25 no. Es el único
     *           dato que habla de <b>lo difícil de conseguir</b> y no de lo
     *           fuerte</td></tr>
     *   <tr><td><b>Forma final</b></td>
     *       <td>tener preevolución y no evolucionar más significa que alguien
     *           ha hecho el camino</td></tr>
     *   <tr><td><b>Etiquetas</b></td>
     *       <td>inicial y fósil son especiales por diseño del juego, no por
     *           números</td></tr>
     * </table>
     *
     * <p>⚠ <b>Ninguno solo vale.</b> Con el BST, Gyarados y Dragonite empatan.
     * Con el ratio de captura, <i>casi todas</i> las evoluciones finales valen
     * 45 y Pidgeot saldría igual que Tyranitar. Juntos se separan.
     *
     * <p>⚠ Y por eso los tramos no son simétricos: 45 es <b>el ratio normal de
     * una evolución final</b>, así que premiarlo mucho convertiría a todas las
     * finales en ★★★. El corte que de verdad marca rareza está en 30.
     */
    private static int puntos(com.cobblemon.mod.common.pokemon.Species s) {
        int p = 0;
        int bst = 0;
        try {
            for (var v : s.getBaseStats().values()) {
                bst += v == null ? 0 : v;
            }
        } catch (Throwable ignored) {
            // Sin estadísticas, que cuente como común: es preferible una caza
            // fácil de más que una imposible.
        }
        if (bst >= 570) {
            p += 3;          // pseudolegendarios: Dragonite, Tyranitar
        } else if (bst >= 500) {
            p += 2;          // Charizard, Snorlax, Lapras, Gyarados
        } else if (bst >= 420) {
            p += 1;          // Pidgeot, Dragonair
        }

        try {
            int captura = s.getCatchRate();
            if (captura <= 30) {
                p += 2;      // Snorlax, Chansey
            } else if (captura <= 60) {
                p += 1;      // casi toda evolución final
            }
        } catch (Throwable ignored) {
            // idem
        }

        try {
            boolean tienePre = s.getPreEvolution() != null;
            boolean sigueEvolucionando = !s.getEvolutions().isEmpty();
            if (tienePre && !sigueEvolucionando) {
                p += 1;
            }
        } catch (Throwable ignored) {
            // idem
        }

        try {
            for (String etiqueta : s.getLabels()) {
                String e = etiqueta.toLowerCase();
                if (e.equals("starter") || e.equals("fossil")) {
                    p += 1;
                    break;
                }
            }
        } catch (Throwable ignored) {
            // idem
        }
        return p;
    }

    /** 1 común · 2 raro · 3 muy raro. */
    private static int rarezaDe(com.cobblemon.mod.common.pokemon.Species s) {
        int p = puntos(s);
        if (p >= 4) {
            return 3;
        }
        return p >= 2 ? 2 : 1;
    }

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

                // ⚠⚠⚠ EL IDENTIFICADOR, NO EL NOMBRE VISIBLE. Con
                //    `getName().toLowerCase()` esto sorteaba cazas de
                //    «mr. mime» y «farfetch’d», que NO son identificadores
                //    validos: la primera vez que salio una, tumbo el autotest
                //    entero. Ver net.pokereport.luna.pokedex.ClaveEspecie.
                out.add(new Especie(net.pokereport.luna.pokedex.ClaveEspecie.de(s), dex,
                        rarezaDe(s)));
            }
        } catch (Throwable t) {
            LunaEternal.LOG.error("No se pudo leer el registro de especies", t);
        }

        if (out.isEmpty()) {
            // Sin registro no hay cazas, pero el servidor tiene que seguir.
            // Un puñado de iniciales evita que la pantalla salga vacia.
            LunaEternal.LOG.warn("Registro de especies vacío: uso una lista mínima");
            out.add(new Especie("caterpie", 10, 1));
            out.add(new Especie("pidgey", 16, 1));
            out.add(new Especie("pidgeotto", 17, 2));
            out.add(new Especie("dragonair", 148, 2));
            out.add(new Especie("charizard", 6, 3));
            out.add(new Especie("dragonite", 149, 3));
        }
        long c1 = out.stream().filter(e -> e.rareza() == 1).count();
        long c2 = out.stream().filter(e -> e.rareza() == 2).count();
        long c3 = out.stream().filter(e -> e.rareza() == 3).count();
        LunaEternal.LOG.info("Cazas: {} especies candidatas (Kanto+Johto, "
                             + "sin legendarios) · ★ {} · ★★ {} · ★★★ {}",
                             out.size(), c1, c2, c3);
        cache = out;
        return out;
    }

    /**
     * Una especie de cada nivel de rareza, al azar.
     *
     * <h2>⚠⚠ SE SORTEA POR TRAMO, no se sortea y se etiqueta después</h2>
     *
     * Es lo que hace que la estrella signifique algo. Sorteando al azar y
     * repartiendo estrellas por posición, un ciclo podía tener tres Pokémon
     * comunes con estrellas distintas — y entonces la ★★★ solo decía «este
     * paga más», que es justo lo que no queremos.
     *
     * <p>⚠ Si un tramo se quedara vacío se coge del más cercano <b>en vez de
     * devolver menos objetivos</b>: una pantalla con dos filas donde siempre
     * hay tres parece rota, y quedarse sin cazas es peor que una caza fácil.
     */
    public static List<Especie> unaDeCada() {
        var todas = disponibles();
        var salida = new ArrayList<Especie>(3);
        for (int r = 1; r <= 3; r++) {
            var tramo = new ArrayList<Especie>();
            for (var e : todas) {
                if (e.rareza() == r) {
                    tramo.add(e);
                }
            }
            if (tramo.isEmpty()) {
                LunaEternal.LOG.warn("Cazas: no hay especies de rareza {}", r);
                tramo = new ArrayList<>(todas);
            }
            salida.add(tramo.get(ThreadLocalRandom.current().nextInt(tramo.size())));
        }
        return salida;
    }

    /** {@code n} especies distintas, al azar. Sin mirar la rareza. */
    public static List<Especie> sortear(int n) {
        List<Especie> copia = new ArrayList<>(disponibles());
        Collections.shuffle(copia, ThreadLocalRandom.current());
        return copia.subList(0, Math.min(n, copia.size()));
    }

    /** La rareza de una especie por su nombre. 0 si no es candidata. */
    public static int rareza(String nombre) {
        if (nombre == null) {
            return 0;
        }
        String n = nombre.toLowerCase();
        for (var e : disponibles()) {
            if (e.nombre().equals(n)) {
                return e.rareza();
            }
        }
        return 0;
    }
}
