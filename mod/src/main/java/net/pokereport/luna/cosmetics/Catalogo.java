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
 * <h2>Como se aplica</h2>
 *
 * Cobblemon 1.7 los aplica por OBJETO, no por bandera: se le da a un Pokemon el
 * {@code objeto} y el motor le pone el aspecto. Por eso cada pieza lo lleva.
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
     * @param objeto  el objeto que Cobblemon consume para aplicarlo
     * @param precio  en LunaCoins. <b>{@code 0} = NO esta a la venta</b>, solo
     *                sale en eventos (D-039), que no es lo mismo que gratis
     */
    public record Pieza(String id, String categoria, String especie,
                        String aspecto, String objeto, int precio) {

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
            new Pieza("articuno_steampunk", MASCOTAS, "cobblemon:articuno", "steampunk", "minecraft:copper_ingot", 4000),
            new Pieza("blaziken_magma", MASCOTAS, "cobblemon:blaziken", "magma", "minecraft:magma_block", 2500),
            new Pieza("blissey_easter", MASCOTAS, "cobblemon:blissey", "easter", "cobblemon:lucky_egg", 1500),
            new Pieza("buneary_easter", MASCOTAS, "cobblemon:buneary", "easter", "cobblemon:lucky_egg", 1500),
            new Pieza("carbink_royal", MASCOTAS, "cobblemon:carbink", "royal", "cobblemon:kings_rock", 1500),
            new Pieza("ceruledge_mgreninja", MASCOTAS, "cobblemon:ceruledge", "mgreninja", "cobblemon:mystic_water", 2500),
            new Pieza("charizard_knight", MASCOTAS, "cobblemon:charizard", "knight", "minecraft:iron_helmet", 2500),
            new Pieza("charizard_sinnoh", MASCOTAS, "cobblemon:charizard", "sinnoh", "cobblemon:fire_gem", 2500),
            new Pieza("cinderace_captain", MASCOTAS, "cobblemon:cinderace", "captain", "minecraft:black_wool", 2500),
            new Pieza("cinderace_pastel", MASCOTAS, "cobblemon:cinderace", "pastel", "cobblemon:lucky_egg", 2500),
            new Pieza("cyclizar_ancient", MASCOTAS, "cobblemon:cyclizar", "ancient", "minecraft:red_wool", 1500),
            new Pieza("decidueye_ninja", MASCOTAS, "cobblemon:decidueye", "ninja", "minecraft:blue_wool", 2500),
            new Pieza("decidueye_sinnoh", MASCOTAS, "cobblemon:decidueye", "sinnoh", "cobblemon:grass_gem", 2500),
            new Pieza("drampa_newyear", MASCOTAS, "cobblemon:drampa", "newyear", "minecraft:lantern", 1500),
            new Pieza("eevee_valentines", MASCOTAS, "cobblemon:eevee", "valentines", "cobblemon:love_sweet", 1500),
            new Pieza("flareon_valentines", MASCOTAS, "cobblemon:flareon", "valentines", "cobblemon:love_sweet", 1500),
            new Pieza("garchomp_sinnoh", MASCOTAS, "cobblemon:garchomp", "sinnoh", "cobblemon:dragon_gem", 1500),
            new Pieza("gardevoir_icedragon", MASCOTAS, "cobblemon:gardevoir", "icedragon", "minecraft:snow_block", 1500),
            new Pieza("gardevoir_sinnoh", MASCOTAS, "cobblemon:gardevoir", "sinnoh", "cobblemon:fairy_gem", 1500),
            new Pieza("golisopod_iceraider", MASCOTAS, "cobblemon:golisopod", "iceraider", "minecraft:snow_block", 1500),
            new Pieza("greninja_sinnoh", MASCOTAS, "cobblemon:greninja", "sinnoh", "cobblemon:water_gem", 2500),
            new Pieza("greninja_winter", MASCOTAS, "cobblemon:greninja", "winter", "minecraft:snow_block", 2500),
            new Pieza("grumpig_legend", MASCOTAS, "cobblemon:grumpig", "legend", "minecraft:potato", 1500),
            new Pieza("haxorus_reaper", MASCOTAS, "cobblemon:haxorus", "reaper", "minecraft:skeleton_skull", 1500),
            new Pieza("hooh_steampunk", MASCOTAS, "cobblemon:hooh", "steampunk", "minecraft:gold_ingot", 1500),
            new Pieza("incineroar_warmonger", MASCOTAS, "cobblemon:incineroar", "warmonger", "minecraft:iron_ingot", 2500),
            new Pieza("jolteon_valentines", MASCOTAS, "cobblemon:jolteon", "valentines", "cobblemon:love_sweet", 1500),
            new Pieza("latios_fighter", MASCOTAS, "cobblemon:latios", "fighter", "minecraft:iron_ingot", 1500),
            new Pieza("lopunny_easter", MASCOTAS, "cobblemon:lopunny", "easter", "cobblemon:lucky_egg", 1500),
            new Pieza("lucario_covert", MASCOTAS, "cobblemon:lucario", "covert", "cobblemon:covert_cloak", 1500),
            new Pieza("lucario_sinnoh", MASCOTAS, "cobblemon:lucario", "sinnoh", "cobblemon:fighting_gem", 1500),
            new Pieza("magcargo_racer", MASCOTAS, "cobblemon:magcargo", "racer", "minecraft:iron_helmet", 1500),
            new Pieza("meowscarada_darkmagician", MASCOTAS, "cobblemon:meowscarada", "darkmagician", "cobblemon:dusk_stone", 1500),
            new Pieza("mewtwo_boundary", MASCOTAS, "cobblemon:mewtwo", "boundary", "minecraft:snow_block", 4000),
            new Pieza("mewtwo_covert", MASCOTAS, "cobblemon:mewtwo", "covert", "cobblemon:covert_cloak", 4000),
            new Pieza("mimikyu_pawmi", MASCOTAS, "cobblemon:mimikyu", "pawmi", "minecraft:orange_wool", 1500),
            new Pieza("minun_cheerleader", MASCOTAS, "cobblemon:minun", "cheerleader", "minecraft:lime_wool", 1500),
            new Pieza("moltres_steampunk", MASCOTAS, "cobblemon:moltres", "steampunk", "minecraft:copper_ingot", 4000),
            new Pieza("ninetales_aurora", MASCOTAS, "cobblemon:ninetales", "aurora", "cobblemon:star_sweet", 1500),
            new Pieza("ninetales_holiday", MASCOTAS, "cobblemon:ninetales", "holiday", "minecraft:snow_block", 1500),
            new Pieza("operator_operator", MASCOTAS, "cobblemon:operator", "operator", "minecraft:lever", 1500),
            new Pieza("pichu_yellowhat", MASCOTAS, "cobblemon:pichu", "yellowhat", "minecraft:yellow_wool", 1500),
            new Pieza("pikachu_yellowhat", MASCOTAS, "cobblemon:pikachu", "yellowhat", "minecraft:yellow_wool", 1500),
            new Pieza("plusle_cheerleader", MASCOTAS, "cobblemon:plusle", "cheerleader", "minecraft:pink_wool", 1500),
            new Pieza("raichu_yellowhat", MASCOTAS, "cobblemon:raichu", "yellowhat", "minecraft:yellow_wool", 1500),
            new Pieza("roserade_valentines", MASCOTAS, "cobblemon:roserade", "valentines", "cobblemon:love_sweet", 1500),
            new Pieza("sawk_festival", MASCOTAS, "cobblemon:sawk", "festival", "cobblemon:black_belt", 1500),
            new Pieza("smoliv_uva", MASCOTAS, "cobblemon:smoliv", "uva", "minecraft:sweet_berries", 1500),
            new Pieza("snorlax_chef", MASCOTAS, "cobblemon:snorlax", "chef", "minecraft:white_wool", 1500),
            new Pieza("throh_festival", MASCOTAS, "cobblemon:throh", "festival", "cobblemon:black_belt", 1500),
            new Pieza("tinkaton_eternal", MASCOTAS, "cobblemon:tinkaton", "eternal", "minecraft:wither_rose", 1500),
            new Pieza("togekiss_easter", MASCOTAS, "cobblemon:togekiss", "easter", "cobblemon:lucky_egg", 1500),
            new Pieza("togepi_easter", MASCOTAS, "cobblemon:togepi", "easter", "cobblemon:lucky_egg", 1500),
            new Pieza("togetic_easter", MASCOTAS, "cobblemon:togetic", "easter", "cobblemon:lucky_egg", 1500),
            new Pieza("typhlosion_chef", MASCOTAS, "cobblemon:typhlosion", "chef", "minecraft:white_wool", 2500),
            new Pieza("umbreon_aurora", MASCOTAS, "cobblemon:umbreon", "aurora", "minecraft:snow_block", 1500),
            new Pieza("vaporeon_valentines", MASCOTAS, "cobblemon:vaporeon", "valentines", "cobblemon:love_sweet", 1500),
            new Pieza("vulpix_holiday", MASCOTAS, "cobblemon:vulpix", "holiday", "minecraft:snow_block", 1500),
            new Pieza("weavile_skier", MASCOTAS, "cobblemon:weavile", "skier", "minecraft:snow_block", 1500),
            new Pieza("wooper_dignified", MASCOTAS, "cobblemon:wooper", "dignified", "minecraft:emerald", 1500),
            new Pieza("zapdos_steampunk", MASCOTAS, "cobblemon:zapdos", "steampunk", "minecraft:copper_ingot", 4000),
            new Pieza("zoroark_ghost", MASCOTAS, "cobblemon:zoroark", "ghost", "cobblemon:spell_tag", 1500)
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
