package net.pokereport.luna.crate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LOS COFRES Y LO QUE SALE DE ELLOS.
 *
 * <p>Diseño y decisión en {@code docs/economy/treasures.md} y D-020.
 *
 * <h2>⚠⚠⚠ ESTA TABLA ES LA MISMA QUE LEEN EL SERVIDOR Y LA PANTALLA</h2>
 *
 * D-020 hizo obligatorias las <b>probabilidades públicas</b>, y eso solo
 * significa algo si el porcentaje que se enseña sale <b>del mismo sitio del que
 * sortea el servidor</b>. Con dos tablas —una para sortear y otra para
 * enseñar— el sistema seguiría funcionando y el número de la pantalla sería
 * una decoración: nadie lo notaría jamás.
 *
 * <p>Por eso esta clase vive en {@code main} y no en {@code client}: es la
 * misma decisión que {@code Gimnasio}, que también dibujan las dos partes.
 *
 * <h2>⚠⚠ EL PESO NO ES EL PORCENTAJE</h2>
 *
 * Cada premio lleva un <b>peso</b> y el porcentaje se calcula dividiendo por la
 * suma. Escribir porcentajes a mano obliga a que sumen 100 <b>y nada lo
 * comprueba</b>: al añadir un premio, o alguien reajusta los demás a mano, o la
 * tabla miente. Con pesos, añadir uno es una línea.
 *
 * <h2>⚠⚠ LO QUE NO SE HACE, Y ES DELIBERADO</h2>
 *
 * <b>Nada de «casi lo consigues».</b> La ruleta se para donde salió el premio y
 * ya: no se frena al lado del legendario para luego moverse. Eso es
 * manipulación, está escrito en {@code treasures.md} §4.5, y no hace falta.
 */
public final class Cofre {

    private Cofre() {}

    /** De qué es un premio. */
    public enum Tipo { OBJETO, POKEMON, PLATA, LUNACOINS }

    /** Cómo se consigue la llave de un cofre. */
    public enum Llave {
        /** Jugando. Hoy: una hora de juego activo. */
        JUEGO,
        /** Con LunaCoins. */
        PREMIUM
    }

    /**
     * Un premio de la tabla.
     *
     * @param tipo     objeto, Pokémon o moneda
     * @param id       el identificador de Cobblemon o de Minecraft
     * @param cantidad cuántos. En un Pokémon es siempre 1
     * @param peso     su parte del sorteo. El porcentaje se CALCULA
     * @param mayor    ¿es el premio gordo? La piedad garantiza uno de estos
     * @param shiny    solo para Pokémon
     */
    public record Premio(Tipo tipo, String id, int cantidad, int peso,
                         boolean mayor, boolean shiny) {}

    private static Premio obj(String id, int cantidad, int peso) {
        return new Premio(Tipo.OBJETO, "cobblemon:" + id, cantidad, peso, false, false);
    }

    private static Premio objMayor(String id, int cantidad, int peso) {
        return new Premio(Tipo.OBJETO, "cobblemon:" + id, cantidad, peso, true, false);
    }

    private static Premio plata(int cantidad, int peso) {
        return new Premio(Tipo.PLATA, "plata", cantidad, peso, false, false);
    }

    private static Premio poke(String especie, int peso, boolean mayor, boolean shiny) {
        return new Premio(Tipo.POKEMON, especie, 1, peso, mayor, shiny);
    }

    /**
     * Un cofre.
     *
     * @param id       identificador estable. Da la clave de traducción
     * @param llave    cómo se consigue su llave
     * @param precio   qué cuesta la llave en LunaCoins. 0 si no se compra
     * @param piedad   tras cuántas aperturas sin premio mayor se garantiza uno
     * @param premios  la tabla. El porcentaje sale de los pesos
     */
    public record Cofre_(String id, Llave llave, int precio, int piedad,
                         List<Premio> premios) {

        /** La suma de los pesos. El denominador de todos los porcentajes. */
        public int pesoTotal() {
            int t = 0;
            for (Premio p : premios) {
                t += p.peso();
            }
            return t;
        }

        /**
         * El porcentaje de un premio, en tanto por uno.
         *
         * <p>⚠ Se calcula, no se guarda: ver el javadoc de la clase.
         */
        public double probabilidad(Premio p) {
            int t = pesoTotal();
            return t <= 0 ? 0 : (double) p.peso() / t;
        }

        /** ¿Tiene premio mayor? Si no, la piedad no aplica. */
        public boolean tieneMayor() {
            for (Premio p : premios) {
                if (p.mayor()) {
                    return true;
                }
            }
            return false;
        }
    }

    // ---- los once legendarios de Kanto y Johto ---------------------------
    //
    // ⚠⚠ ONCE Y NO MAS, y no es una elección estética: este servidor solo tiene
    //    Kanto y Johto (D-017). Meter un legendario de Hoenn en un cofre haría
    //    aparecer una especie que la Pokédex no reconoce — se leería como un
    //    fallo, no como un premio.
    //
    // ⚠ Los identificadores se comprobaron contra data/cobblemon/species/ del
    //   jar antes de escribirlos. `hooh` va SIN guion bajo; `ho_oh` no existe y
    //   no habría dado ningún error: habría dado un cofre que no entrega nada.
    private static final String[] LEGENDARIOS_MENORES = {
        "articuno", "zapdos", "moltres", "raikou", "entei", "suicune"
    };

    /**
     * Los cinco de arriba: los que la piedad garantiza.
     *
     * <p>⚠⚠ Mewtwo, Mew, Lugia, Ho-Oh y Celebi salen <b>mucho menos</b> que los
     * otros seis, y son los que cuentan como premio mayor. Si todos pesaran lo
     * mismo, el cofre no tendría nada que perseguir — y la piedad, que es lo
     * que acota el gasto, no tendría a qué apuntar.
     */
    private static final String[] LEGENDARIOS_MAYORES = {
        "mewtwo", "mew", "lugia", "hooh", "celebi"
    };

    private static List<Premio> tablaLegendarios(boolean shiny) {
        var salida = new ArrayList<Premio>();
        for (String e : LEGENDARIOS_MENORES) {
            salida.add(poke(e, 150, false, shiny));
        }
        for (String e : LEGENDARIOS_MAYORES) {
            salida.add(poke(e, 20, true, shiny));
        }
        return salida;
    }

    /**
     * LOS CUATRO COFRES.
     *
     * <h2>⚠⚠⚠ LOS PRECIOS SON PROVISIONALES, IGUAL QUE LOS DE LA TIENDA</h2>
     *
     * Y por el mismo motivo que dijo el usuario entonces: <i>«más adelante
     * definimos precios porque necesitamos un análisis general de la
     * economía»</i>. Están aquí para que el sistema funcione, no porque estén
     * calibrados.
     *
     * <p>⚠⚠ Y hay un motivo extra para no fijarlos hoy, que es peor de lo que
     * parece: <b>las LunaCoins llevan semanas ganándose y no se gastan en
     * nada</b> ({@code ECO-005}). Poner precio a una llave sin saber cuánto
     * tiene acumulada la gente es elegir a ciegas entre «los veteranos lo
     * compran todo el primer día» y «los nuevos no llegan nunca».
     */
    public static final List<Cofre_> TODOS = Arrays.asList(

        // ---- GACHA DIARIO: gratis, una hora de juego activo ---------------
        //
        // ⚠⚠ Es el único con llave de JUEGO, y es lo que hace defendible al
        //    resto: la regla dura de treasures.md §4.1 dice que toda llave se
        //    pueda conseguir jugando. Con los legendarios eso no se cumple —
        //    D-020 lo aceptó a sabiendas— pero al menos hay una puerta gratis
        //    que da algo todos los días.
        //
        // ⚠⚠ SON COSAS DE EMPEZAR, NO DE COMPETIR (orden del usuario). Balls
        //    corrientes, curación, curaestados y bayas: lo que a un jugador
        //    nuevo le falta el primer día. Lo competitivo está en el Gachapón,
        //    que se paga.
        //
        // ⚠ 21 premios y no 10: con pocos, abrirlo tres días seguidos ya lo
        //   enseña entero y deja de tener gracia. La variedad es lo que hace
        //   que merezca la pena mirar qué salió.
        new Cofre_("gacha_diario", Llave.JUEGO, 0, 0, Arrays.asList(
            obj("poke_ball", 10, 240),
            obj("potion", 5, 190),
            obj("great_ball", 5, 165),
            obj("super_potion", 3, 120),
            plata(500, 105),
            obj("antidote", 3, 80),
            obj("paralyze_heal", 3, 80),
            obj("burn_heal", 3, 75),
            obj("awakening", 3, 75),
            obj("ice_heal", 3, 70),
            obj("oran_berry", 5, 70),
            obj("revive", 1, 65),
            obj("pecha_berry", 5, 60),
            obj("cheri_berry", 5, 60),
            obj("nest_ball", 5, 55),
            obj("net_ball", 5, 50),
            obj("ultra_ball", 3, 42),
            obj("exp_candy_s", 3, 38),
            obj("leppa_berry", 5, 35),
            obj("sitrus_berry", 3, 30),
            obj("hyper_potion", 2, 26),
            obj("timer_ball", 3, 22),
            obj("quick_ball", 3, 20),
            plata(2000, 14),
            obj("full_heal", 2, 12),
            obj("max_revive", 1, 9),
            obj("rare_candy", 1, 7),
            obj("heal_ball", 5, 6))),

        // ---- GACHAPÓN: lo competitivo ------------------------------------
        //
        // ⚠⚠⚠ AQUÍ ESTÁ LO QUE SIRVE PARA COMPETIR, Y ESO TIENE UN LÍMITE QUE
        //    NO SE CRUZA: objetos EQUIPABLES, vitaminas y mentas. Nada de
        //    Modificadores de estadísticas — D-019 los dejó fuera a propósito,
        //    y sigue fuera: un legendario es UNA pieza, un modificador es una
        //    mejora repetible sin techo sobre cualquier Pokémon.
        //
        // ⚠⚠ Y ninguno de estos ROMPE el equilibrio: todos existen en el juego
        //    de referencia y se pueden conseguir jugando en cualquier servidor.
        //    Lo que se compra aquí es SALTARSE LA BÚSQUEDA, que es aceleración
        //    acotada (P4) y no poder nuevo.
        //
        // ⚠ Las mentas cambian la naturaleza en combate. Se meten porque son la
        //   diferencia entre criar cincuenta huevos y usar uno — que es
        //   exactamente lo que el usuario pidió: comodidad competitiva.
        new Cofre_("gachapon", Llave.PREMIUM, 250, 40, Arrays.asList(
            obj("exp_candy_m", 3, 150),
            obj("hyper_potion", 5, 130),
            obj("ultra_ball", 5, 120),
            obj("leftovers", 1, 70),
            obj("max_revive", 2, 68),
            obj("exp_candy_l", 2, 62),
            obj("protein", 2, 46),
            obj("calcium", 2, 46),
            obj("carbos", 2, 46),
            obj("hp_up", 2, 46),
            obj("iron", 2, 44),
            obj("zinc", 2, 44),
            obj("fire_stone", 1, 40),
            obj("water_stone", 1, 40),
            obj("thunder_stone", 1, 40),
            obj("leaf_stone", 1, 40),
            obj("moon_stone", 1, 40),
            obj("sun_stone", 1, 40),
            obj("focus_sash", 1, 38),
            obj("rocky_helmet", 1, 34),
            obj("muscle_band", 1, 32),
            obj("wise_glasses", 1, 32),
            obj("expert_belt", 1, 30),
            obj("shell_bell", 1, 30),
            obj("quick_claw", 1, 28),
            obj("scope_lens", 1, 28),
            obj("light_clay", 1, 26),
            obj("safety_goggles", 1, 26),
            obj("air_balloon", 1, 24),
            obj("razor_claw", 1, 24),
            obj("razor_fang", 1, 24),
            obj("kings_rock", 1, 24),
            obj("pp_up", 2, 24),
            obj("black_sludge", 1, 22),
            obj("eviolite", 1, 22),
            obj("heavy_duty_boots", 1, 22),
            obj("weakness_policy", 1, 20),
            obj("rare_candy", 3, 20),
            obj("adamant_mint", 1, 18),
            obj("modest_mint", 1, 18),
            obj("jolly_mint", 1, 18),
            obj("timid_mint", 1, 18),
            obj("bold_mint", 1, 16),
            obj("careful_mint", 1, 16),
            obj("impish_mint", 1, 16),
            obj("assault_vest", 1, 14),
            obj("lucky_egg", 1, 12),
            obj("pp_max", 1, 10),
            // ⚠ Los cinco mayores: lo que se persigue de este cofre. Son los
            //   que la piedad garantiza a las 40 aperturas.
            objMayor("choice_band", 1, 9),
            objMayor("choice_specs", 1, 9),
            objMayor("choice_scarf", 1, 9),
            objMayor("ability_capsule", 1, 8),
            objMayor("ability_patch", 1, 5),
            objMayor("master_ball", 1, 3))),

        // ---- LEGENDARIOS ---------------------------------------------------
        new Cofre_("legendario", Llave.PREMIUM, 1200, 25, tablaLegendarios(false)),

        // ---- LEGENDARIOS SHINY ---------------------------------------------
        //
        // ⚠⚠ El shiny lo aprobó D-020 explícitamente. Queda anotado que
        //    `treasures.md` §5 proponía «nunca shiny» como mitigación y que la
        //    decisión fue la contraria, a sabiendas: dentro de seis meses nadie
        //    tiene que reconstruir el razonamiento.
        new Cofre_("legendario_shiny", Llave.PREMIUM, 3000, 25, tablaLegendarios(true))
    );

    /** El cofre con ese identificador, o {@code null}. */
    public static Cofre_ de(String id) {
        if (id == null) {
            return null;
        }
        for (Cofre_ c : TODOS) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Sortea un premio.
     *
     * @param forzarMayor si la piedad ya toca, solo entran los mayores
     *
     * <p>⚠⚠ La rueda se recorre acumulando pesos. Es la forma aburrida y es la
     * correcta: cualquier atajo con porcentajes en coma flotante deja un hueco
     * de redondeo en el que el último premio nunca sale.
     */
    public static Premio sortear(Cofre_ c, java.util.Random rnd, boolean forzarMayor) {
        var pool = new ArrayList<Premio>();
        int total = 0;
        for (Premio p : c.premios()) {
            if (forzarMayor && !p.mayor()) {
                continue;
            }
            pool.add(p);
            total += p.peso();
        }
        if (pool.isEmpty() || total <= 0) {
            // ⚠ Sin premios mayores, la piedad no puede forzar nada: se sortea
            //   normal en vez de devolver null, que dejaría al jugador con la
            //   llave gastada y sin nada.
            return forzarMayor ? sortear(c, rnd, false) : null;
        }
        int tirada = rnd.nextInt(total);
        int acumulado = 0;
        for (Premio p : pool) {
            acumulado += p.peso();
            if (tirada < acumulado) {
                return p;
            }
        }
        return pool.get(pool.size() - 1);
    }
}
