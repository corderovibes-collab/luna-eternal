package net.pokereport.luna.pase;

import java.util.List;

import net.pokereport.luna.pase.Recompensa.Rareza;

/**
 * QUE DA CADA UNO DE LOS CIEN NIVELES DEL PASE.
 *
 * <p>Vive en el codigo y no en la base, por lo mismo que {@code Cofre.java} y
 * {@code Catalogo.java}: <b>esto es contenido, no estado</b>. Meterlo en una
 * tabla obligaria a una migracion por temporada y a mantener sincronizados unos
 * identificadores que ademas tienen que existir en el registro del juego.
 *
 * <h2>&#9888;&#9888;&#9888; UNA SOLA VIA, Y ES DE PAGO (D-046, revoca D-045)</h2>
 *
 * Orden del usuario: <i>«este pase de batalla si o si es comprando lunacoins,
 * no hay via libre ni nada»</i>. Quien no compra el pase <b>gana XP igual</b> y
 * ve su nivel subir, pero no puede cobrar nada; al comprarlo, se le abre todo
 * lo que ya tenia por nivel.
 *
 * <p>&#9888;&#9888; QUEDA ESCRITO QUE ESTO CRUZA T4, y no para discutirlo otra
 * vez: {@code monetization.md} §2 pone <b>shinies</b> y <b>objetos competitivos
 * exclusivos</b> en la lista de «nunca, bajo ninguna circunstancia», y el test
 * de §6 se para en la primera pregunta. La decision se tomo <b>a sabiendas</b>,
 * igual que D-020 con los legendarios de los cofres. El riesgo que queda vivo no
 * es interno: son las reglas comerciales de Mojang ({@code B-008},
 * {@code SEC-004}), que prohiben vender ventaja de juego.
 *
 * <p>&#9888; LO UNICO QUE SE HA DEJADO FUERA A PROPOSITO ES LA MONEDA. El pase
 * no da ni un PokeDolar: un objeto entra en la economia de objetos, pero moneda
 * vendida por dinero real <b>saltea TODOS los sumideros a la vez</b> (P3), y esa
 * es la unica averia de esta lista que no se puede deshacer bajando un numero.
 *
 * <h2>Los cinco tramos</h2>
 *
 * El usuario pidio <i>«que los determines por categoria, mientras mas nivel
 * tenga puede reclamar mejores recompensas»</i>. Cada veinte niveles cambia el
 * tipo de premio, y el orden no es decorativo: es <b>el orden en que un jugador
 * necesita las cosas</b> &mdash; primero jugar, luego criar, luego entrenar lo
 * criado, luego equiparlo para combatir, y al final afinarlo.
 *
 * <pre>
 *    1- 20  PREPARACION    balls, curacion, bayas, caramelos
 *   21- 40  CRIANZA        Destiny Knot, Everstone, los SEIS objetos de poder
 *   41- 60  ENTRENAMIENTO  vitaminas, PP Up, Caramelos Raros y XL
 *   61- 80  COMBATE        Restos, Vidasfera, las tres Elegidas, Eviolita...
 *   81-100  MAESTRIA       mentas, Capsula y Parche de Habilidad, Master Ball
 * </pre>
 *
 * <h2>&#9888;&#9888;&#9888; HAY DOS POKEMON Y SON EXACTAMENTE LOS QUE PIDIO EL
 * USUARIO</h2>
 *
 * Nivel 1 un <b>Charizard de nivel 15</b> y nivel 100 un <b>Charizard shiny de
 * nivel 50</b>. Y no hay mas.
 *
 * <p>&#9888;&#9888; LA PRIMERA VERSION METIA TRES MAS &mdash;Gengar en el 25,
 * Tyranitar en el 50 y Dragonite en el 75&mdash; «para que la mitad del carril
 * tuviera a donde mirar». <b>Nadie los habia pedido.</b> El usuario lo corrigio:
 * <i>«no te dije que me dieras mas pokemons, solo charizard y charizard
 * variocolor»</i>, y tenia razon: ensanchar el encargo por tu cuenta es
 * exactamente igual de malo que recortarlo.
 *
 * <p>Los tres huecos que dejaron son ahora hitos de OBJETO del tramo que les
 * toca: Huevo Suerte (crianza), 15 Caramelos Raros (entrenamiento) y dos Capas
 * Furtivas (combate).
 *
 * <p>&#9888; El autotest comprueba que sean <b>EXACTAMENTE DOS</b> y que esten
 * en el 1 y en el 100. Sin ese numero exacto, meter un cuarto Pokemon dentro de
 * seis meses no daria ningun error &mdash; y volveria a ser una decision que no
 * es mia.
 */
public final class PaseCatalogo {

    private PaseCatalogo() {
    }

    /**
     * Lo que cuesta el pase, en LunaCoins. <b>Decision del usuario.</b>
     *
     * <p>&#9888; Es una compra POR TEMPORADA: al rotar, quien lo quiera vuelve a
     * comprarlo. Sin eso seria una suscripcion que se paga una vez, y el pase
     * dejaria de tener nada que ofrecer a partir de la segunda temporada.
     */
    public static final long PRECIO = 15_000;

    /** Un tramo del carril: veinte niveles con un tema y un color. */
    public record Tramo(String nombre, String lema, int desde, int hasta, int color) {
    }

    /**
     * Los cinco tramos, en orden.
     *
     * <p>&#9888; El color va aqui y no en la pantalla: es lo que pinta la
     * cabecera del carril y el fondo de las tarjetas de ese tramo, y en dos
     * sitios acabarian discrepando &mdash; que es como el jugador ve un tramo
     * «azul» con tarjetas verdes.
     */
    public static final List<Tramo> TRAMOS = List.of(
            new Tramo("PREPARACION", "Para empezar a jugar en serio",
                    1, 20, 0xFF4FA8FF),
            new Tramo("CRIANZA", "Lo que decide que hereda una cria",
                    21, 40, 0xFF4FD07A),
            new Tramo("ENTRENAMIENTO", "EVs, niveles y PP",
                    41, 60, 0xFFF3A712),
            new Tramo("COMBATE", "Lo que se equipa para pelear",
                    61, 80, 0xFFF35C0C),
            new Tramo("MAESTRIA", "Naturaleza, habilidad y el premio mayor",
                    81, 100, 0xFFB98CFF));

    /** El tramo al que pertenece ese nivel. Nunca {@code null} entre 1 y 100. */
    public static Tramo tramoDe(int nivel) {
        for (Tramo t : TRAMOS) {
            if (nivel >= t.desde() && nivel <= t.hasta()) {
                return t;
            }
        }
        return TRAMOS.get(0);
    }

    // ---- atajos para que la tabla de abajo se pueda leer -------------------

    private static Recompensa o(String id, int n, Rareza r) {
        return Recompensa.objeto("cobblemon:" + id, n, r);
    }

    private static Recompensa p(String especie, int nivel, boolean shiny) {
        return Recompensa.pokemon(especie, nivel, shiny, Rareza.LEGENDARIA);
    }

    /**
     * LOS CIEN NIVELES. Indice 0 = nivel 1.
     *
     * <p>&#9888;&#9888; LOS IDENTIFICADORES SE VALIDARON CONTRA EL JAR ANTES DE
     * ESCRIBIRLOS ({@code tools/gen_tienda.py}, que lee el jar del manifiesto
     * publicado), y el autotest los revalida contra el REGISTRO en cada
     * ejecucion. Uno mal escrito no da error: da un premio que no se entrega,
     * con el jugador habiendo pagado el pase y hecho el trabajo. Es el fallo de
     * las Cazas y el de los 62 cosmeticos que no existian.
     */
    private static final Recompensa[] NIVELES = {
        // ================= 1-20 · PREPARACION ==============================
        /*   1 */ p("charizard", 15, false),
        /*   2 */ o("poke_ball", 20, Rareza.COMUN),
        /*   3 */ o("potion", 10, Rareza.COMUN),
        /*   4 */ o("oran_berry", 16, Rareza.COMUN),
        /*   5 */ o("great_ball", 15, Rareza.COMUN),
        /*   6 */ o("super_potion", 10, Rareza.COMUN),
        /*   7 */ o("exp_candy_s", 8, Rareza.COMUN),
        /*   8 */ o("cheri_berry", 12, Rareza.COMUN),
        /*   9 */ o("revive", 6, Rareza.COMUN),
        /*  10 */ o("ultra_ball", 15, Rareza.RARA),
        /*  11 */ o("hyper_potion", 8, Rareza.COMUN),
        /*  12 */ o("leppa_berry", 12, Rareza.COMUN),
        /*  13 */ o("exp_candy_m", 6, Rareza.COMUN),
        /*  14 */ o("heal_ball", 10, Rareza.COMUN),
        /*  15 */ o("max_revive", 5, Rareza.RARA),
        /*  16 */ o("full_heal", 8, Rareza.COMUN),
        /*  17 */ o("sitrus_berry", 12, Rareza.COMUN),
        /*  18 */ o("max_potion", 6, Rareza.COMUN),
        /*  19 */ o("exp_candy_m", 6, Rareza.COMUN),
        /*  20 */ o("luxury_ball", 10, Rareza.EPICA),

        // ================= 21-40 · CRIANZA =================================
        //
        // ⚠ Los SEIS objetos de poder van repartidos del 26 al 35 a proposito:
        //   sirven de uno en uno --cada uno fija una estadistica-- asi que
        //   darlos juntos convertiria diez niveles en uno.
        /*  21 */ o("everstone", 2, Rareza.RARA),
        /*  22 */ o("lum_berry", 10, Rareza.COMUN),
        /*  23 */ o("destiny_knot", 1, Rareza.EPICA),
        /*  24 */ o("love_ball", 8, Rareza.RARA),
        /*  25 */ o("lucky_egg", 1, Rareza.EPICA),
        /*  26 */ o("power_weight", 1, Rareza.RARA),
        /*  27 */ o("friend_ball", 8, Rareza.COMUN),
        /*  28 */ o("power_bracer", 1, Rareza.RARA),
        /*  29 */ o("exp_candy_l", 4, Rareza.RARA),
        /*  30 */ o("power_belt", 1, Rareza.RARA),
        /*  31 */ o("moon_ball", 8, Rareza.COMUN),
        /*  32 */ o("power_lens", 1, Rareza.RARA),
        /*  33 */ o("mirror_herb", 1, Rareza.EPICA),
        /*  34 */ o("power_band", 1, Rareza.RARA),
        /*  35 */ o("power_anklet", 1, Rareza.RARA),
        /*  36 */ o("everstone", 2, Rareza.COMUN),
        /*  37 */ o("link_cable", 2, Rareza.RARA),
        /*  38 */ o("exp_candy_l", 4, Rareza.RARA),
        /*  39 */ o("destiny_knot", 1, Rareza.EPICA),
        /*  40 */ o("dream_ball", 5, Rareza.EPICA),

        // ================= 41-60 · ENTRENAMIENTO ===========================
        /*  41 */ o("hp_up", 6, Rareza.RARA),
        /*  42 */ o("exp_candy_l", 5, Rareza.RARA),
        /*  43 */ o("protein", 6, Rareza.RARA),
        /*  44 */ o("pp_up", 4, Rareza.RARA),
        /*  45 */ o("iron", 6, Rareza.RARA),
        /*  46 */ o("exp_candy_xl", 2, Rareza.EPICA),
        /*  47 */ o("calcium", 6, Rareza.RARA),
        /*  48 */ o("rare_candy", 5, Rareza.EPICA),
        /*  49 */ o("zinc", 6, Rareza.RARA),
        /*  50 */ o("rare_candy", 15, Rareza.LEGENDARIA),
        /*  51 */ o("carbos", 6, Rareza.RARA),
        /*  52 */ o("exp_candy_xl", 2, Rareza.EPICA),
        /*  53 */ o("pp_up", 4, Rareza.RARA),
        /*  54 */ o("hp_up", 6, Rareza.RARA),
        /*  55 */ o("exp_candy_xl", 4, Rareza.EPICA),
        /*  56 */ o("protein", 6, Rareza.RARA),
        /*  57 */ o("max_elixir", 6, Rareza.RARA),
        /*  58 */ o("iron", 6, Rareza.RARA),
        /*  59 */ o("calcium", 6, Rareza.RARA),
        /*  60 */ o("exp_candy_xl", 5, Rareza.EPICA),

        // ================= 61-80 · COMBATE =================================
        /*  61 */ o("leftovers", 1, Rareza.EPICA),
        /*  62 */ o("focus_sash", 2, Rareza.RARA),
        /*  63 */ o("zinc", 6, Rareza.RARA),
        /*  64 */ o("rocky_helmet", 1, Rareza.RARA),
        /*  65 */ o("life_orb", 1, Rareza.EPICA),
        /*  66 */ o("expert_belt", 1, Rareza.RARA),
        /*  67 */ o("carbos", 6, Rareza.RARA),
        /*  68 */ o("assault_vest", 1, Rareza.EPICA),
        /*  69 */ o("muscle_band", 1, Rareza.RARA),
        /*  70 */ o("choice_band", 1, Rareza.EPICA),
        /*  71 */ o("wise_glasses", 1, Rareza.RARA),
        /*  72 */ o("eviolite", 1, Rareza.EPICA),
        /*  73 */ o("quick_claw", 1, Rareza.RARA),
        /*  74 */ o("choice_specs", 1, Rareza.EPICA),
        /*  75 */ o("covert_cloak", 2, Rareza.LEGENDARIA),
        /*  76 */ o("scope_lens", 1, Rareza.RARA),
        /*  77 */ o("heavy_duty_boots", 1, Rareza.EPICA),
        /*  78 */ o("shell_bell", 1, Rareza.RARA),
        /*  79 */ o("choice_scarf", 1, Rareza.EPICA),
        /*  80 */ o("leftovers", 2, Rareza.EPICA),

        // ================= 81-100 · MAESTRIA ===============================
        //
        // ⚠ Las mentas cambian la NATURALEZA sin recriar, y la Capsula y el
        //   Parche cambian la HABILIDAD. Son lo ultimo del pase porque son lo
        //   ultimo que se le hace a un Pokemon: primero lo crias, lo entrenas y
        //   lo equipas; afinarlo es el paso que solo tiene sentido al final.
        /*  81 */ o("adamant_mint", 1, Rareza.EPICA),
        /*  82 */ o("safety_goggles", 1, Rareza.RARA),
        /*  83 */ o("modest_mint", 1, Rareza.EPICA),
        /*  84 */ o("light_clay", 1, Rareza.RARA),
        /*  85 */ o("jolly_mint", 1, Rareza.EPICA),
        /*  86 */ o("razor_claw", 1, Rareza.RARA),
        /*  87 */ o("timid_mint", 1, Rareza.EPICA),
        /*  88 */ o("air_balloon", 1, Rareza.RARA),
        /*  89 */ o("bold_mint", 1, Rareza.EPICA),
        /*  90 */ o("ability_capsule", 1, Rareza.LEGENDARIA),
        /*  91 */ o("careful_mint", 1, Rareza.EPICA),
        /*  92 */ o("razor_fang", 1, Rareza.RARA),
        /*  93 */ o("impish_mint", 1, Rareza.EPICA),
        /*  94 */ o("weakness_policy", 1, Rareza.RARA),
        /*  95 */ o("ability_patch", 1, Rareza.LEGENDARIA),
        /*  96 */ o("calm_mint", 1, Rareza.EPICA),
        /*  97 */ o("pp_max", 2, Rareza.LEGENDARIA),
        /*  98 */ o("beast_ball", 3, Rareza.EPICA),
        /*  99 */ o("master_ball", 1, Rareza.LEGENDARIA),
        // ⚠⚠ EL PREMIO MAYOR, y es orden directa del usuario.
        /* 100 */ p("charizard", 50, true),
    };

    /** La recompensa de ese nivel. Nunca {@code null} entre 1 y 100. */
    public static Recompensa de(int nivel) {
        if (nivel < 1 || nivel > PaseNivel.MAX) {
            return null;
        }
        return NIVELES[nivel - 1];
    }

    /** Cuantos niveles llevan Pokemon. Para el autotest y para el comando. */
    public static int cuantosPokemon() {
        int n = 0;
        for (Recompensa r : NIVELES) {
            if (r.tipo() == Recompensa.Tipo.POKEMON) {
                n++;
            }
        }
        return n;
    }

    /** Cuantos premios hay de esa rareza. */
    public static int cuantosDe(Rareza rareza) {
        int n = 0;
        for (Recompensa r : NIVELES) {
            if (r.rareza() == rareza) {
                n++;
            }
        }
        return n;
    }
}
