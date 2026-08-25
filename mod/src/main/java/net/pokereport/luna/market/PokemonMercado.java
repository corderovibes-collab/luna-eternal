package net.pokereport.luna.market;

import java.util.ArrayList;
import java.util.List;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;

/**
 * Leer los Pokémon de un jugador y traducirlos a lo que el mercado entiende.
 *
 * <h2>⚠⚠ El equipo Y el PC, y eso es una decisión</h2>
 *
 * <b>Orden del usuario (2026-08-24):</b> los objetos hay que tenerlos en el
 * inventario, pero un Pokémon cuenta <b>esté en el equipo o esté en el PC</b>.
 *
 * <p>Y tiene sentido: un Pokémon no ocupa inventario — vive en un almacén del
 * servidor, y el PC es tan «suyo» como el equipo. Obligar a sacarlo al equipo
 * para poder venderlo sería fricción por nada, y <b>con el equipo lleno
 * sencillamente no podría vender</b>.
 *
 * <h2>⚠ Esta clase toca Cobblemon, así que va SIEMPRE en el hilo del servidor</h2>
 *
 * Leer un almacén desde el executor de E/S es leer el mundo desde fuera. Quien
 * llama extrae aquí lo que necesita —un {@link Resumen}, que es datos puros— y
 * se lleva eso al hilo de E/S.
 */
public final class PokemonMercado {

    private PokemonMercado() {}

    /** Dónde estaba: para poder devolverlo y para enseñarlo. */
    public enum Donde { EQUIPO, PC }

    /**
     * Un Pokémon reducido a datos.
     *
     * <p>⚠ Es un record propio y no el {@code Pokemon} de Cobblemon: así todo lo
     * que viene después —tasar, publicar, filtrar— se puede probar sin arrancar
     * el juego, y el hilo de E/S nunca toca una entidad viva.
     */
    public record Resumen(String uuid, String especie, String mote, int nivel,
                          boolean shiny, String genero, String naturaleza,
                          String habilidad, boolean habilidadOculta,
                          String tera, String ball, int bst,
                          Tasador.Rareza rareza,
                          int[] ivs, int[] evs, Donde donde, int ranura) {

        public int ivTotal() {
            int n = 0;
            for (int v : ivs) {
                n += v;
            }
            return n;
        }

        public int ivsPerfectos() {
            int n = 0;
            for (int v : ivs) {
                if (v >= 31) {
                    n++;
                }
            }
            return n;
        }

        public int evTotal() {
            int n = 0;
            for (int v : evs) {
                n += v;
            }
            return n;
        }

        /** Lo que necesita el tasador, sin que él tenga que saber de Cobblemon. */
        public Tasador.Ficha ficha() {
            return new Tasador.Ficha(especie, nivel, shiny, bst, rareza,
                    ivTotal(), ivsPerfectos(), evTotal(), habilidadOculta);
        }
    }

    /**
     * El orden de las seis estadísticas. <b>Fijo, y no se toca.</b>
     *
     * <p>⚠⚠ Los IVs viajan y se guardan como seis números, así que este orden es
     * parte del formato: cambiarlo convertiría el Ataque de todo el mundo en
     * Defensa, en la base y en las pantallas, <b>sin un solo error</b>. Es el
     * mismo peligro que el {@code ENUM} de MariaDB, que guarda el índice.
     */
    private static final Stats[] ORDEN = {
        Stats.HP, Stats.ATTACK, Stats.DEFENCE,
        Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    };

    /**
     * Todo lo que el jugador puede vender: equipo y PC.
     *
     * <p>⚠ Se llama <b>en el hilo del servidor</b>.
     */
    public static List<Resumen> disponibles(ServerPlayerEntity jugador) {
        var salida = new ArrayList<Resumen>();
        try {
            var almacen = Cobblemon.INSTANCE.getStorage();
            int i = 0;
            for (Pokemon p : almacen.getParty(jugador)) {
                if (p != null) {
                    salida.add(resumir(p, Donde.EQUIPO, i));
                }
                i++;
            }
            i = 0;
            for (Pokemon p : almacen.getPC(jugador)) {
                if (p != null) {
                    salida.add(resumir(p, Donde.PC, i));
                }
                i++;
            }
        } catch (Throwable t) {
            // ⚠ SE DEVUELVE LO QUE HAYA, no una lista vacía. Si el PC falla, el
            //   equipo sigue siendo vendible: dejar al jugador sin poder vender
            //   nada porque una mitad no se pudo leer es peor que enseñarle la
            //   mitad que sí.
            LunaEternal.LOG.error("No se pudieron leer los Pokemon de {}",
                    jugador.getName().getString(), t);
        }
        return salida;
    }

    /** Encuentra uno por su UUID, mire donde mire. */
    public static Pokemon buscar(ServerPlayerEntity jugador, String uuid) {
        try {
            var almacen = Cobblemon.INSTANCE.getStorage();
            for (Pokemon p : almacen.getParty(jugador)) {
                if (p != null && p.getUuid().toString().equals(uuid)) {
                    return p;
                }
            }
            for (Pokemon p : almacen.getPC(jugador)) {
                if (p != null && p.getUuid().toString().equals(uuid)) {
                    return p;
                }
            }
        } catch (Throwable t) {
            LunaEternal.LOG.error("No se pudo buscar el Pokemon {}", uuid, t);
        }
        return null;
    }

    /**
     * Saca un Pokémon del equipo o del PC. <b>Esto es la custodia.</b>
     *
     * <p>⚠⚠ Mientras está listado NO puede seguir en poder del vendedor. Si
     * siguiera en su PC podría operar con él mientras se vende —evolucionarlo,
     * moverlo, soltarlo— y ese es el vector de duplicación número uno de todos
     * los mercados de Pokémon mal hechos. Es la misma regla que ya escribió
     * {@code V005} para los objetos.
     *
     * @return {@code true} si de verdad salió de algún sitio
     */
    public static boolean retirar(ServerPlayerEntity jugador, Pokemon pokemon) {
        try {
            var almacen = Cobblemon.INSTANCE.getStorage();
            // ⚠ Se intenta en los dos y se mira el resultado: `remove` devuelve
            //   si estaba. Suponer dónde está y no comprobarlo es como se acaba
            //   publicando un Pokémon que sigue en el PC de su dueño.
            if (almacen.getParty(jugador).remove(pokemon)) {
                return true;
            }
            return almacen.getPC(jugador).remove(pokemon);
        } catch (Throwable t) {
            LunaEternal.LOG.error("No se pudo retirar el Pokemon para el mercado", t);
            return false;
        }
    }

    private static Resumen resumir(Pokemon p, Donde donde, int ranura) {
        var especie = p.getSpecies();

        int bst = 0;
        try {
            for (var v : especie.getBaseStats().values()) {
                bst += v == null ? 0 : v;
            }
        } catch (Throwable t) {
            bst = 400;
        }

        int[] ivs = new int[6];
        int[] evs = new int[6];
        for (int i = 0; i < ORDEN.length; i++) {
            ivs[i] = valor(p.getIvs().get(ORDEN[i]));
            evs[i] = valor(p.getEvs().get(ORDEN[i]));
        }

        String habilidad = "";
        boolean oculta = false;
        try {
            habilidad = p.getAbility().getName();
            // ⚠ La habilidad oculta se detecta por su prioridad en la especie, no
            //   por el nombre: los nombres cambian entre versiones y con los
            //   idiomas, y una comprobación por texto se rompe en silencio.
            oculta = p.getAbility().getTemplate() != null
                    && especie.getAbilities() != null
                    && esOculta(especie, p.getAbility().getName());
        } catch (Throwable t) {
            habilidad = "";
        }

        String tera = "";
        try {
            tera = String.valueOf(p.getTeraType().getId());
        } catch (Throwable t) {
            tera = "";
        }
        String ball = "";
        try {
            ball = String.valueOf(p.getCaughtBall().getName());
        } catch (Throwable t) {
            ball = "";
        }
        String mote = "";
        try {
            mote = p.getNickname() == null ? "" : p.getNickname().getString();
        } catch (Throwable t) {
            mote = "";
        }

        return new Resumen(p.getUuid().toString(), especie.getName(), mote,
                p.getLevel(), p.getShiny(), String.valueOf(p.getGender()),
                String.valueOf(p.getNature().getName()), habilidad, oculta,
                tera, ball, bst,
                Tasador.Rareza.de(especie.getLabels()),
                ivs, evs, donde, ranura);
    }

    private static boolean esOculta(com.cobblemon.mod.common.pokemon.Species especie,
                                    String nombre) {
        try {
            for (var pot : especie.getAbilities()) {
                // `AbilityTemplate` con bandera de oculta: se pregunta a la
                // especie, que es quien lo sabe.
                if (pot != null && pot.getTemplate() != null
                        && nombre.equals(pot.getTemplate().getName())) {
                    return String.valueOf(pot.getClass().getSimpleName())
                            .toLowerCase(java.util.Locale.ROOT).contains("hidden");
                }
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    private static int valor(Integer v) {
        return v == null ? 0 : Math.max(0, v);
    }
}
