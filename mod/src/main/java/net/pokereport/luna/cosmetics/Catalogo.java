package net.pokereport.luna.cosmetics;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Que cosmeticos existen y cuanto valen. <b>Vive en el servidor y solo ahi.</b>
 *
 * <p>⚠⚠ ESTE FICHERO SE GENERA. No se edita a mano:
 *
 * <pre>
 * python tools/gen_catalogo_cosmeticos.py &lt;zip de CobblemonMoreCosmetics&gt;
 * </pre>
 *
 * <p>Se genera <b>leyendo el pack instalado</b> porque escribirlo a mano ya
 * fallo: el catalogo prometia disfraces que el pack no tenia, se cobraban, y
 * salia el Pokemon normal. Sin error y sin nada en el log. Generandolo, el
 * catalogo no puede prometer lo que no existe.
 *
 * <p>Con {@code D-039} —los cosmeticos solo se consiguen comprandolos o en
 * eventos— este catalogo es la unica fuente que hay: un identificador que no
 * este aqui se rechaza al comprar.
 *
 * <h2>⚠⚠ POR QUE NO HAY CAMPO `objeto`, HABIENDOLO TENIDO</h2>
 *
 * El pack declara cada cosmetico con un {@code consumedItem}: `charizard_knight`
 * se aplica dando un {@code minecraft:iron_helmet}. Se leyo, se guardo aqui, y
 * se aplicaba con {@code swapCosmeticItem}. <b>Funcionaba, y por eso se tardo en
 * ver el problema:</b>
 *
 * <pre>
 * craftear un yelmo de hierro = el disfraz de 2.500 LunaCoins, gratis
 * quitarlo por el menu de Cobblemon = ademas te quedas el objeto
 * </pre>
 *
 * {@code cosmetic_items} esta pensado para conseguirse jugando, y <b>D-039 dice
 * exactamente lo contrario</b>. No se puede vigilar esa puerta desde el mod, asi
 * que <b>el datapack del pack no se instala</b> --sin el, `cosmetic_items` ni se
 * registra-- y el aspecto se fuerza directo con {@code setForcedAspects}.
 *
 * El campo se quito en vez de dejarlo sin usar: un dato que sigue ahi es una
 * invitacion a volver a usarlo.
 *
 * <h2>⚠ LOS PRECIOS SON PROVISIONALES</h2>
 *
 * CLAUDE.md lo dice de toda la economia: se calibra con datos reales. Los tramos
 * --legendario, inicial, normal-- solo evitan que todo cueste lo mismo, que es
 * lo unico que se sabe seguro que estaria mal.
 */
public final class Catalogo {

    private Catalogo() {
    }

    /**
     * Un cosmetico del catalogo.
     *
     * @param especie identificador completo, {@code cobblemon:charizard}
     * @param aspecto el aspecto que aplica, {@code knight}
     * @param precio  ver abajo
     * @param precio  en LunaCoins. <b>{@code 0} = NO esta a la venta</b>, solo
     *                sale en eventos (D-039), que no es lo mismo que gratis
     */
    public record Pieza(String id, String categoria, String especie,
                        String aspecto, int precio) {

        /** Criatura de Minecraft en vez de Pokemon: la dibuja otro codigo. */
        public boolean esDeMinecraft() {
            return especie.startsWith("minecraft:");
        }
    }

    public static final String MASCOTAS = "mascotas";
    public static final String CAPAS = "capas";
    public static final String SOMBREROS = "sombreros";
    public static final String AURAS = "auras";

    /** GENERADO. Ver la cabecera de la clase. */
    private static final List<Pieza> PIEZAS = List.of(
            new Pieza("articuno_steampunk", MASCOTAS, "cobblemon:articuno", "steampunk", 4000),
            new Pieza("blaziken_magma", MASCOTAS, "cobblemon:blaziken", "magma", 2500),
            new Pieza("blissey_easter", MASCOTAS, "cobblemon:blissey", "easter", 1500),
            new Pieza("buneary_easter", MASCOTAS, "cobblemon:buneary", "easter", 1500),
            new Pieza("carbink_royal", MASCOTAS, "cobblemon:carbink", "royal", 1500),
            new Pieza("ceruledge_mgreninja", MASCOTAS, "cobblemon:ceruledge", "mgreninja", 2500),
            new Pieza("charizard_knight", MASCOTAS, "cobblemon:charizard", "knight", 2500),
            new Pieza("charizard_sinnoh", MASCOTAS, "cobblemon:charizard", "sinnoh", 2500),
            new Pieza("cinderace_captain", MASCOTAS, "cobblemon:cinderace", "captain", 2500),
            new Pieza("cinderace_pastel", MASCOTAS, "cobblemon:cinderace", "pastel", 2500),
            new Pieza("cyclizar_ancient", MASCOTAS, "cobblemon:cyclizar", "ancient", 1500),
            new Pieza("decidueye_ninja", MASCOTAS, "cobblemon:decidueye", "ninja", 2500),
            new Pieza("decidueye_sinnoh", MASCOTAS, "cobblemon:decidueye", "sinnoh", 2500),
            new Pieza("drampa_newyear", MASCOTAS, "cobblemon:drampa", "newyear", 1500),
            new Pieza("eevee_valentines", MASCOTAS, "cobblemon:eevee", "valentines", 1500),
            new Pieza("flareon_valentines", MASCOTAS, "cobblemon:flareon", "valentines", 1500),
            new Pieza("garchomp_sinnoh", MASCOTAS, "cobblemon:garchomp", "sinnoh", 1500),
            new Pieza("gardevoir_icedragon", MASCOTAS, "cobblemon:gardevoir", "icedragon", 1500),
            new Pieza("gardevoir_sinnoh", MASCOTAS, "cobblemon:gardevoir", "sinnoh", 1500),
            new Pieza("golisopod_iceraider", MASCOTAS, "cobblemon:golisopod", "iceraider", 1500),
            new Pieza("greninja_sinnoh", MASCOTAS, "cobblemon:greninja", "sinnoh", 2500),
            new Pieza("greninja_winter", MASCOTAS, "cobblemon:greninja", "winter", 2500),
            new Pieza("grumpig_legend", MASCOTAS, "cobblemon:grumpig", "legend", 1500),
            new Pieza("haxorus_reaper", MASCOTAS, "cobblemon:haxorus", "reaper", 1500),
            new Pieza("hooh_steampunk", MASCOTAS, "cobblemon:hooh", "steampunk", 1500),
            new Pieza("incineroar_warmonger", MASCOTAS, "cobblemon:incineroar", "warmonger", 2500),
            new Pieza("jolteon_valentines", MASCOTAS, "cobblemon:jolteon", "valentines", 1500),
            new Pieza("latios_fighter", MASCOTAS, "cobblemon:latios", "fighter", 1500),
            new Pieza("lopunny_easter", MASCOTAS, "cobblemon:lopunny", "easter", 1500),
            new Pieza("lucario_covert", MASCOTAS, "cobblemon:lucario", "covert", 1500),
            new Pieza("lucario_sinnoh", MASCOTAS, "cobblemon:lucario", "sinnoh", 1500),
            new Pieza("magcargo_racer", MASCOTAS, "cobblemon:magcargo", "racer", 1500),
            new Pieza("meowscarada_darkmagician", MASCOTAS, "cobblemon:meowscarada", "darkmagician", 1500),
            new Pieza("mewtwo_boundary", MASCOTAS, "cobblemon:mewtwo", "boundary", 4000),
            new Pieza("mewtwo_covert", MASCOTAS, "cobblemon:mewtwo", "covert", 4000),
            new Pieza("mimikyu_pawmi", MASCOTAS, "cobblemon:mimikyu", "pawmi", 1500),
            new Pieza("minun_cheerleader", MASCOTAS, "cobblemon:minun", "cheerleader", 1500),
            new Pieza("moltres_steampunk", MASCOTAS, "cobblemon:moltres", "steampunk", 4000),
            new Pieza("ninetales_aurora", MASCOTAS, "cobblemon:ninetales", "aurora", 1500),
            new Pieza("ninetales_holiday", MASCOTAS, "cobblemon:ninetales", "holiday", 1500),
            new Pieza("operator_operator", MASCOTAS, "cobblemon:operator", "operator", 1500),
            new Pieza("pichu_yellowhat", MASCOTAS, "cobblemon:pichu", "yellowhat", 1500),
            new Pieza("pikachu_yellowhat", MASCOTAS, "cobblemon:pikachu", "yellowhat", 1500),
            new Pieza("plusle_cheerleader", MASCOTAS, "cobblemon:plusle", "cheerleader", 1500),
            new Pieza("raichu_yellowhat", MASCOTAS, "cobblemon:raichu", "yellowhat", 1500),
            new Pieza("roserade_valentines", MASCOTAS, "cobblemon:roserade", "valentines", 1500),
            new Pieza("sawk_festival", MASCOTAS, "cobblemon:sawk", "festival", 1500),
            new Pieza("smoliv_uva", MASCOTAS, "cobblemon:smoliv", "uva", 1500),
            new Pieza("snorlax_chef", MASCOTAS, "cobblemon:snorlax", "chef", 1500),
            new Pieza("throh_festival", MASCOTAS, "cobblemon:throh", "festival", 1500),
            new Pieza("tinkaton_eternal", MASCOTAS, "cobblemon:tinkaton", "eternal", 1500),
            new Pieza("togekiss_easter", MASCOTAS, "cobblemon:togekiss", "easter", 1500),
            new Pieza("togepi_easter", MASCOTAS, "cobblemon:togepi", "easter", 1500),
            new Pieza("togetic_easter", MASCOTAS, "cobblemon:togetic", "easter", 1500),
            new Pieza("typhlosion_chef", MASCOTAS, "cobblemon:typhlosion", "chef", 2500),
            new Pieza("umbreon_aurora", MASCOTAS, "cobblemon:umbreon", "aurora", 1500),
            new Pieza("vaporeon_valentines", MASCOTAS, "cobblemon:vaporeon", "valentines", 1500),
            new Pieza("vulpix_holiday", MASCOTAS, "cobblemon:vulpix", "holiday", 1500),
            new Pieza("weavile_skier", MASCOTAS, "cobblemon:weavile", "skier", 1500),
            new Pieza("wooper_dignified", MASCOTAS, "cobblemon:wooper", "dignified", 1500),
            new Pieza("zapdos_steampunk", MASCOTAS, "cobblemon:zapdos", "steampunk", 4000),
            new Pieza("zoroark_ghost", MASCOTAS, "cobblemon:zoroark", "ghost", 1500)
    );

    private static final Map<String, Pieza> POR_ID =
            PIEZAS.stream().collect(Collectors.toMap(Pieza::id, Function.identity()));

    public static List<Pieza> todas() {
        return PIEZAS;
    }

    /** {@code null} si no existe. Quien compre un identificador desconocido se queda sin nada. */
    public static Pieza de(String id) {
        return POR_ID.get(id);
    }

    /** Las categorias, en el orden en que salen las pestañas. */
    public static List<String> categorias() {
        return List.of(MASCOTAS, CAPAS, SOMBREROS, AURAS);
    }
}
