package net.pokereport.luna.pase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * QUE DA CADA NIVEL DEL PASE, EN CADA UNA DE LAS DOS VIAS.
 *
 * <p>Vive en el codigo y no en la base, por lo mismo que {@code Cofre.java} y
 * {@code Catalogo.java}: <b>esto es contenido, no estado</b>. Meterlo en una
 * tabla obligaria a una migracion por temporada y a mantener sincronizados unos
 * identificadores que ademas tienen que existir en el registro del juego.
 *
 * <h2>&#9888;&#9888;&#9888; LAS DOS VIAS NO LLEVAN LO MISMO, Y NO ES GUSTO</h2>
 *
 * <table border="1">
 *   <tr><th>Via</th><th>Que lleva</th><th>Por que</th></tr>
 *   <tr>
 *     <td><b>LIBRE</b> (gratis)</td>
 *     <td>Plata, objetos y llaves</td>
 *     <td>Se gana <b>jugando</b>. Es una fuente mas del juego, como las Cazas o
 *         la Torre, y se calibra como tal (P3)</td>
 *   </tr>
 *   <tr>
 *     <td><b>LUNA</b> (15.000 LunaCoins)</td>
 *     <td><b>Solo cosmeticos</b></td>
 *     <td>Se <b>compra</b> con moneda premium, y el test de
 *         {@code monetization.md} &sect;6 se para en la segunda pregunta:
 *         <i>&#191;crea moneda u objetos comerciables? SI = NO SE VENDE</i>.
 *         Un Caramelo Raro en la via libre es un premio por jugar; el mismo
 *         Caramelo en la via de pago es <b>comprar progresion</b>, que es T4 y
 *         es la linea roja de D-007 y D-014</td>
 *   </tr>
 * </table>
 *
 * <p>Y no es solo cumplir una regla: {@code monetization.md} &sect;2 dice que la
 * identidad <b>debe ser el grueso de la facturacion</b> y que es lo unico que no
 * rompe nada. Un pase de cosmeticos es exactamente ese producto.
 *
 * <h2>&#9888;&#9888; POR QUE LA VIA LUNA SOLO PREMIA EN LOS NIVELES PARES</h2>
 *
 * Con un cosmetico en cada uno de los cincuenta niveles serian
 * {@code 50 x 1.200 = 60.000} LunaCoins de valor de tienda por un pase de
 * 15.000 &mdash; <b>cuatro veces lo que cuesta</b>, y entonces nadie vuelve a
 * comprar un sombrero suelto: el pase se comeria la tienda de cosmeticos, que
 * es justamente la que tiene que sostener el negocio.
 *
 * <p>Con veinticinco premios el pase devuelve <b>~2,1x</b> lo que cuesta
 * ({@link #valorTiendaLuna()}), que es bastante para que merezca la pena y poco
 * para que la tienda siga teniendo sentido. El autotest vigila ese multiplo.
 *
 * <p>&#9888; Y dos de los ultimos hitos son <b>auras que no estan a la venta</b>
 * ({@code precio 0}). Un cosmetico que ademas se puede comprar solo ahorra
 * LunaCoins; uno que no, dice donde estabas esa temporada. Es lo unico del pase
 * que no se puede conseguir de ninguna otra forma.
 *
 * <h2>&#9888; LOS IMPORTES SON PROVISIONALES, como todos los del proyecto</h2>
 *
 * La Plata de la via libre suma <b>15.300</b> por temporada de 60 dias, o sea
 * unos 255 al dia. Para comparar: completar las seis Cazas da ~10.000 al dia.
 * El pase es deliberadamente un goteo, no una fuente.
 */
public final class PaseCatalogo {

    private PaseCatalogo() {
    }

    /** La via gratuita. */
    public static final String LIBRE = "libre";

    /** La via de pago. */
    public static final String LUNA = "luna";

    /**
     * Lo que cuesta la via Luna, en LunaCoins. <b>Decision del usuario.</b>
     *
     * <p>&#9888; Es una compra POR TEMPORADA: al rotar, quien quiera la via Luna
     * de la temporada nueva vuelve a comprarla. Sin eso seria una suscripcion
     * que se paga una vez, y el pase dejaria de tener nada que ofrecer a partir
     * de la segunda temporada.
     */
    public static final long PRECIO_LUNA = 15_000;

    /**
     * La via libre, nivel a nivel. Indice 0 = nivel 1.
     *
     * <p>&#9888; Los identificadores se comprobaron contra los que YA usa el
     * proyecto ({@code Cofre.java} y {@code TorreRecompensas.java}) antes de
     * escribirlos, y el autotest los revalida contra el REGISTRO en cada
     * ejecucion. Uno mal escrito no da error: da un premio que no se entrega,
     * con el jugador habiendo hecho ya el trabajo. Es el fallo de las Cazas.
     */
    private static final Recompensa[] VIA_LIBRE = {
        /*  1 */ Recompensa.objeto("cobblemon:poke_ball", 15),
        /*  2 */ Recompensa.plata(800),
        /*  3 */ Recompensa.objeto("cobblemon:potion", 5),
        /*  4 */ Recompensa.objeto("cobblemon:oran_berry", 10),
        /*  5 */ Recompensa.objeto("cobblemon:great_ball", 10),
        /*  6 */ Recompensa.plata(900),
        /*  7 */ Recompensa.objeto("cobblemon:exp_candy_s", 3),
        /*  8 */ Recompensa.objeto("cobblemon:super_potion", 5),
        /*  9 */ Recompensa.objeto("cobblemon:cheri_berry", 8),
        /* 10 */ Recompensa.llave("gacha_diario", 2),
        /* 11 */ Recompensa.plata(1_000),
        /* 12 */ Recompensa.objeto("cobblemon:poke_ball", 15),
        /* 13 */ Recompensa.objeto("cobblemon:revive", 5),
        /* 14 */ Recompensa.objeto("cobblemon:exp_candy_s", 3),
        /* 15 */ Recompensa.objeto("cobblemon:great_ball", 10),
        /* 16 */ Recompensa.plata(1_100),
        /* 17 */ Recompensa.objeto("cobblemon:leppa_berry", 6),
        /* 18 */ Recompensa.objeto("cobblemon:nest_ball", 5),
        /* 19 */ Recompensa.objeto("cobblemon:super_potion", 5),
        /* 20 */ Recompensa.llave("gachapon", 1),
        /* 21 */ Recompensa.plata(1_200),
        /* 22 */ Recompensa.objeto("cobblemon:exp_candy_m", 3),
        /* 23 */ Recompensa.objeto("cobblemon:net_ball", 5),
        /* 24 */ Recompensa.objeto("cobblemon:hyper_potion", 8),
        /* 25 */ Recompensa.objeto("cobblemon:ultra_ball", 5),
        /* 26 */ Recompensa.plata(1_300),
        /* 27 */ Recompensa.objeto("cobblemon:timer_ball", 5),
        /* 28 */ Recompensa.objeto("cobblemon:exp_candy_m", 3),
        /* 29 */ Recompensa.objeto("cobblemon:max_revive", 3),
        /* 30 */ Recompensa.llave("gacha_diario", 3),
        /* 31 */ Recompensa.plata(1_400),
        /* 32 */ Recompensa.objeto("cobblemon:quick_ball", 5),
        /* 33 */ Recompensa.objeto("cobblemon:great_ball", 10),
        /* 34 */ Recompensa.objeto("cobblemon:exp_candy_l", 2),
        /* 35 */ Recompensa.objeto("cobblemon:ultra_ball", 8),
        /* 36 */ Recompensa.plata(1_500),
        /* 37 */ Recompensa.objeto("cobblemon:heal_ball", 5),
        /* 38 */ Recompensa.objeto("cobblemon:max_revive", 3),
        /* 39 */ Recompensa.objeto("cobblemon:exp_candy_l", 2),
        // La segunda llave de Gachapon es lo que cumple la regla dura de
        // treasures.md 4.1 --toda llave se tiene que poder conseguir jugando--
        // para un cofre que por lo demas solo se abre pagando.
        /* 40 */ Recompensa.llave("gachapon", 2),
        /* 41 */ Recompensa.plata(1_600),
        /* 42 */ Recompensa.objeto("cobblemon:ultra_ball", 10),
        /* 43 */ Recompensa.objeto("cobblemon:full_restore", 5),
        // CARAMELOS RAROS SI, Y SOLO AQUI. CLAUDE.md lo dice con todas las
        // letras: por Plata --o por jugar-- son un sink y estan bien; por
        // moneda premium serian comprar progresion con dinero real, o sea T4.
        // Por eso estan en la via LIBRE y no en la de Luna.
        /* 44 */ Recompensa.objeto("cobblemon:rare_candy", 2),
        /* 45 */ Recompensa.plata(2_000),
        /* 46 */ Recompensa.objeto("cobblemon:sitrus_berry", 10),
        /* 47 */ Recompensa.objeto("cobblemon:rare_candy", 3),
        /* 48 */ Recompensa.objeto("cobblemon:max_potion", 5),
        /* 49 */ Recompensa.plata(2_500),
        // La Master Ball es el remate de 44 dias de temporada COMO MINIMO (ver
        // PaseNivel). No se vende en la tienda y no se compra con LunaCoins.
        /* 50 */ Recompensa.objeto("cobblemon:master_ball", 1),
    };

    /**
     * La via Luna: nivel a cosmetico. Solo los pares (ver el aviso de arriba).
     *
     * <p>&#9888; Es un mapa y no un array de cincuenta con huecos: un hueco es
     * un {@code null} que hay que acordarse de mirar en el dibujado, en el
     * cobro y en el protocolo. Con el mapa, <i>no hay premio</i> es <i>no esta
     * la clave</i>.
     */
    private static final Map<Integer, Recompensa> VIA_LUNA = Map.ofEntries(
        Map.entry(2,  Recompensa.cosmetico("sombrero_bandana")),
        Map.entry(4,  Recompensa.cosmetico("sombrero_floatingstar")),
        Map.entry(6,  Recompensa.cosmetico("sombrero_foxhat")),
        Map.entry(8,  Recompensa.cosmetico("sombrero_crown")),
        Map.entry(10, Recompensa.cosmetico("aura_notas")),
        Map.entry(12, Recompensa.cosmetico("sombrero_astronaut")),
        Map.entry(14, Recompensa.cosmetico("sombrero_dragonskull")),
        Map.entry(16, Recompensa.cosmetico("sombrero_halo")),
        Map.entry(18, Recompensa.cosmetico("sombrero_vikinghatbeard")),
        Map.entry(20, Recompensa.cosmetico("gardevoir_icedragon")),
        Map.entry(22, Recompensa.cosmetico("sombrero_candleonhead")),
        Map.entry(24, Recompensa.cosmetico("sombrero_crystal_horns")),
        Map.entry(26, Recompensa.cosmetico("sombrero_jackohat")),
        Map.entry(28, Recompensa.cosmetico("sombrero_fulldiamondhelm")),
        Map.entry(30, Recompensa.cosmetico("aura_escarcha")),
        Map.entry(32, Recompensa.cosmetico("sombrero_icedragonskull")),
        Map.entry(34, Recompensa.cosmetico("sombrero_monkeyking")),
        Map.entry(36, Recompensa.cosmetico("sombrero_propelhat")),
        Map.entry(38, Recompensa.cosmetico("sombrero_ghostmask")),
        Map.entry(40, Recompensa.cosmetico("decidueye_ninja")),
        Map.entry(42, Recompensa.cosmetico("sombrero_bigcrown")),
        Map.entry(44, Recompensa.cosmetico("sombrero_rgbdragonskull")),
        // ---- los que NO se pueden comprar ---------------------------------
        Map.entry(46, Recompensa.cosmetico("aura_pase_estelar")),
        Map.entry(48, Recompensa.cosmetico("charizard_knight")),
        Map.entry(50, Recompensa.cosmetico("aura_pase_eclipse")));

    /** La recompensa libre de ese nivel. Nunca {@code null} entre 1 y 50. */
    public static Recompensa libre(int nivel) {
        if (nivel < 1 || nivel > PaseNivel.MAX) {
            return null;
        }
        return VIA_LIBRE[nivel - 1];
    }

    /** La recompensa Luna de ese nivel, o {@code null} si ese nivel no lleva. */
    public static Recompensa luna(int nivel) {
        return VIA_LUNA.get(nivel);
    }

    /** La recompensa de esa via en ese nivel, o {@code null}. */
    public static Recompensa de(int nivel, String via) {
        return LUNA.equals(via) ? luna(nivel) : libre(nivel);
    }

    /** Los niveles de la via Luna que llevan premio, ordenados. */
    public static List<Integer> nivelesLuna() {
        var xs = new ArrayList<>(VIA_LUNA.keySet());
        java.util.Collections.sort(xs);
        return List.copyOf(xs);
    }

    /** Cuanta Plata reparte la via libre en una temporada completa. */
    public static long plataDeLaViaLibre() {
        long total = 0;
        for (Recompensa r : VIA_LIBRE) {
            if (r.tipo() == Recompensa.Tipo.PLATA) {
                total += r.cantidad();
            }
        }
        return total;
    }

    /**
     * Lo que costaria en la tienda todo lo que da la via Luna.
     *
     * <p>Existe <b>para que el autotest lo vigile</b>: es el numero que decide
     * si el pase canibaliza la tienda de cosmeticos. Las piezas que no estan a
     * la venta ({@code precio 0}) suman cero, que es justo lo que se quiere:
     * su valor no es de tienda.
     */
    public static long valorTiendaLuna() {
        long total = 0;
        for (Recompensa r : VIA_LUNA.values()) {
            var pieza = net.pokereport.luna.cosmetics.Catalogo.de(r.id());
            if (pieza != null) {
                total += pieza.precio();
            }
        }
        return total;
    }
}
