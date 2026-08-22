package net.pokereport.luna.cosmetics;

import java.util.List;

import net.pokereport.luna.cosmetics.Catalogo.Pieza;

/**
 * Los disfraces de Pokemon.
 *
 * <p><b>ESTE FICHERO SE GENERA:</b> {@code python tools/gen_catalogo_cosmeticos.py}
 *
 * <p>Se genera <b>leyendo los packs</b>, que es lo unico que dice la verdad sobre
 * lo que se puede dibujar. Escribirlo a mano fallo tres veces, y las tres se
 * cobro un cosmetico que no se veia. Ver la cabecera del generador.
 *
 * <p>⚠ Los precios son por tramos y <b>PROVISIONALES</b>.
 */
public final class CatalogoMascotas {

    private CatalogoMascotas() {
    }

    static final List<Pieza> PIEZAS = List.of(
            new Pieza("articuno_steampunk", Catalogo.MASCOTAS, "cobblemon:articuno", "steampunk", 4000),
            new Pieza("blaziken_magma", Catalogo.MASCOTAS, "cobblemon:blaziken", "magma", 2500),
            new Pieza("blissey_easter", Catalogo.MASCOTAS, "cobblemon:blissey", "easter", 1500),
            new Pieza("buneary_easter", Catalogo.MASCOTAS, "cobblemon:buneary", "easter", 1500),
            new Pieza("carbink_royal", Catalogo.MASCOTAS, "cobblemon:carbink", "royal", 1500),
            new Pieza("ceruledge_mgreninja", Catalogo.MASCOTAS, "cobblemon:ceruledge", "mgreninja", 2500),
            new Pieza("charizard_adept", Catalogo.MASCOTAS, "cobblemon:charizard", "adept", 2500),
            new Pieza("charizard_knight", Catalogo.MASCOTAS, "cobblemon:charizard", "knight", 2500),
            new Pieza("charizard_mega_x", Catalogo.MASCOTAS, "cobblemon:charizard", "mega_x", 2500),
            new Pieza("charizard_mega_y", Catalogo.MASCOTAS, "cobblemon:charizard", "mega_y", 2500),
            new Pieza("cinderace_captain", Catalogo.MASCOTAS, "cobblemon:cinderace", "captain", 2500),
            new Pieza("cinderace_pastel", Catalogo.MASCOTAS, "cobblemon:cinderace", "pastel", 2500),
            new Pieza("cyclizar_ancient", Catalogo.MASCOTAS, "cobblemon:cyclizar", "ancient", 1500),
            new Pieza("decidueye_ninja", Catalogo.MASCOTAS, "cobblemon:decidueye", "ninja", 2500),
            new Pieza("drampa_newyear", Catalogo.MASCOTAS, "cobblemon:drampa", "newyear", 1500),
            new Pieza("eevee_valentines", Catalogo.MASCOTAS, "cobblemon:eevee", "valentines", 1500),
            new Pieza("flareon_valentines", Catalogo.MASCOTAS, "cobblemon:flareon", "valentines", 1500),
            new Pieza("gardevoir_Style", Catalogo.MASCOTAS, "cobblemon:gardevoir", "Style", 1500),
            new Pieza("gardevoir_icedragon", Catalogo.MASCOTAS, "cobblemon:gardevoir", "icedragon", 1500),
            new Pieza("golisopod_iceraider", Catalogo.MASCOTAS, "cobblemon:golisopod", "iceraider", 1500),
            new Pieza("greninja_winter", Catalogo.MASCOTAS, "cobblemon:greninja", "winter", 2500),
            new Pieza("grumpig_legend", Catalogo.MASCOTAS, "cobblemon:grumpig", "legend", 1500),
            new Pieza("haxorus_reaper", Catalogo.MASCOTAS, "cobblemon:haxorus", "reaper", 1500),
            new Pieza("hooh_steampunk", Catalogo.MASCOTAS, "cobblemon:hooh", "steampunk", 1500),
            new Pieza("incineroar_warmonger", Catalogo.MASCOTAS, "cobblemon:incineroar", "warmonger", 2500),
            new Pieza("jolteon_valentines", Catalogo.MASCOTAS, "cobblemon:jolteon", "valentines", 1500),
            new Pieza("latios_fighter", Catalogo.MASCOTAS, "cobblemon:latios", "fighter", 1500),
            new Pieza("lopunny_easter", Catalogo.MASCOTAS, "cobblemon:lopunny", "easter", 1500),
            new Pieza("lucario_covert", Catalogo.MASCOTAS, "cobblemon:lucario", "covert", 1500),
            new Pieza("lucario_mega_glove", Catalogo.MASCOTAS, "cobblemon:lucario", "mega_glove", 1500),
            new Pieza("magcargo_racer", Catalogo.MASCOTAS, "cobblemon:magcargo", "racer", 1500),
            new Pieza("meowscarada_darkmagician", Catalogo.MASCOTAS, "cobblemon:meowscarada", "darkmagician", 1500),
            new Pieza("mewtwo_armor_evo", Catalogo.MASCOTAS, "cobblemon:mewtwo", "armor_evo", 4000),
            new Pieza("mewtwo_armored", Catalogo.MASCOTAS, "cobblemon:mewtwo", "armored", 4000),
            new Pieza("mewtwo_boundary", Catalogo.MASCOTAS, "cobblemon:mewtwo", "boundary", 4000),
            new Pieza("mewtwo_covert", Catalogo.MASCOTAS, "cobblemon:mewtwo", "covert", 4000),
            new Pieza("mewtwo_mega_x", Catalogo.MASCOTAS, "cobblemon:mewtwo", "mega_x", 4000),
            new Pieza("mewtwo_mega_y", Catalogo.MASCOTAS, "cobblemon:mewtwo", "mega_y", 4000),
            new Pieza("mewtwo_shadows", Catalogo.MASCOTAS, "cobblemon:mewtwo", "shadows", 4000),
            new Pieza("mewtwo_shadows-c", Catalogo.MASCOTAS, "cobblemon:mewtwo", "shadows-c", 4000),
            new Pieza("mimikyu_pawmi", Catalogo.MASCOTAS, "cobblemon:mimikyu", "pawmi", 1500),
            new Pieza("minun_cheerleader", Catalogo.MASCOTAS, "cobblemon:minun", "cheerleader", 1500),
            new Pieza("moltres_steampunk", Catalogo.MASCOTAS, "cobblemon:moltres", "steampunk", 4000),
            new Pieza("ninetales_holiday", Catalogo.MASCOTAS, "cobblemon:ninetales", "holiday", 1500),
            new Pieza("pichu_goldhat", Catalogo.MASCOTAS, "cobblemon:pichu", "goldhat", 1500),
            new Pieza("pichu_yellowhat", Catalogo.MASCOTAS, "cobblemon:pichu", "yellowhat", 1500),
            new Pieza("pikachu_goldhat", Catalogo.MASCOTAS, "cobblemon:pikachu", "goldhat", 1500),
            new Pieza("pikachu_yellowhat", Catalogo.MASCOTAS, "cobblemon:pikachu", "yellowhat", 1500),
            new Pieza("plusle_cheerleader", Catalogo.MASCOTAS, "cobblemon:plusle", "cheerleader", 1500),
            new Pieza("raichu_goldhat", Catalogo.MASCOTAS, "cobblemon:raichu", "goldhat", 1500),
            new Pieza("raichu_yellowhat", Catalogo.MASCOTAS, "cobblemon:raichu", "yellowhat", 1500),
            new Pieza("roserade_valentines", Catalogo.MASCOTAS, "cobblemon:roserade", "valentines", 1500),
            new Pieza("sableye_holoware", Catalogo.MASCOTAS, "cobblemon:sableye", "holoware", 1500),
            new Pieza("sawk_festival", Catalogo.MASCOTAS, "cobblemon:sawk", "festival", 1500),
            new Pieza("sceptile_mega", Catalogo.MASCOTAS, "cobblemon:sceptile", "mega", 2500),
            new Pieza("smoliv_uva", Catalogo.MASCOTAS, "cobblemon:smoliv", "uva", 1500),
            new Pieza("snorlax_chef", Catalogo.MASCOTAS, "cobblemon:snorlax", "chef", 1500),
            new Pieza("throh_festival", Catalogo.MASCOTAS, "cobblemon:throh", "festival", 1500),
            new Pieza("tinkaton_eternal", Catalogo.MASCOTAS, "cobblemon:tinkaton", "eternal", 1500),
            new Pieza("togekiss_easter", Catalogo.MASCOTAS, "cobblemon:togekiss", "easter", 1500),
            new Pieza("togepi_easter", Catalogo.MASCOTAS, "cobblemon:togepi", "easter", 1500),
            new Pieza("togetic_easter", Catalogo.MASCOTAS, "cobblemon:togetic", "easter", 1500),
            new Pieza("typhlosion_chef", Catalogo.MASCOTAS, "cobblemon:typhlosion", "chef", 2500),
            new Pieza("umbreon_aurora", Catalogo.MASCOTAS, "cobblemon:umbreon", "aurora", 1500),
            new Pieza("vaporeon_valentines", Catalogo.MASCOTAS, "cobblemon:vaporeon", "valentines", 1500),
            new Pieza("vulpix_holiday", Catalogo.MASCOTAS, "cobblemon:vulpix", "holiday", 1500),
            new Pieza("weavile_skier", Catalogo.MASCOTAS, "cobblemon:weavile", "skier", 1500),
            new Pieza("wooper_dignified", Catalogo.MASCOTAS, "cobblemon:wooper", "dignified", 1500),
            new Pieza("zapdos_steampunk", Catalogo.MASCOTAS, "cobblemon:zapdos", "steampunk", 4000),
            new Pieza("zoroark_ghost", Catalogo.MASCOTAS, "cobblemon:zoroark", "ghost", 1500)
    );
}
