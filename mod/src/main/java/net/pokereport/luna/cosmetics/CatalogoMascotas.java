package net.pokereport.luna.cosmetics;

import java.util.List;

import net.pokereport.luna.cosmetics.Catalogo.Pieza;

/**
 * Los disfraces de Pokemon. <b>ESTE FICHERO SE GENERA:</b>
 *
 * <pre>
 * python tools/gen_catalogo_cosmeticos.py &lt;zip de CobblemonMoreCosmetics&gt;
 * </pre>
 *
 * <p>Se genera <b>leyendo los RESOLVERS del pack</b>, que es lo unico que dice
 * la verdad sobre lo que se puede dibujar. Escribirlo a mano fallo tres veces
 * seguidas, y las tres con el mismo sintoma --se cobraba y salia el Pokemon
 * normal-- por tres causas distintas:
 *
 * <ol>
 *   <li>Se copio del repositorio (HEAD), que declara los cosmeticos como
 *       {@code species_features}; la version publicada usa {@code cosmetic_items}.</li>
 *   <li>{@code 26sinnohbundle} declara seis cosmeticos cuyo arte se vende aparte,
 *       y {@code pangoro_operator.json} pone {@code "pokemon": ["operator"]},
 *       que es una errata suya.</li>
 *   <li>{@code pangoro_operator} tenia resolver, modelo y textura pero <b>no
 *       poser</b>, y Cobblemon no cae al de la especie: dibuja un bulto.</li>
 * </ol>
 *
 * <p>Hoy se exigen <b>las cuatro piezas</b>. Lo que el pack declara y no puede
 * dibujar se imprime al generar, en vez de acabar en la tienda.
 *
 * <p>⚠ Los precios son por tramos y <b>PROVISIONALES</b>: CLAUDE.md dice que
 * toda la economia se calibra con datos reales.
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
            new Pieza("charizard_knight", Catalogo.MASCOTAS, "cobblemon:charizard", "knight", 2500),
            new Pieza("cinderace_captain", Catalogo.MASCOTAS, "cobblemon:cinderace", "captain", 2500),
            new Pieza("cinderace_pastel", Catalogo.MASCOTAS, "cobblemon:cinderace", "pastel", 2500),
            new Pieza("cyclizar_ancient", Catalogo.MASCOTAS, "cobblemon:cyclizar", "ancient", 1500),
            new Pieza("decidueye_ninja", Catalogo.MASCOTAS, "cobblemon:decidueye", "ninja", 2500),
            new Pieza("drampa_newyear", Catalogo.MASCOTAS, "cobblemon:drampa", "newyear", 1500),
            new Pieza("eevee_valentines", Catalogo.MASCOTAS, "cobblemon:eevee", "valentines", 1500),
            new Pieza("flareon_valentines", Catalogo.MASCOTAS, "cobblemon:flareon", "valentines", 1500),
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
            new Pieza("magcargo_racer", Catalogo.MASCOTAS, "cobblemon:magcargo", "racer", 1500),
            new Pieza("meowscarada_darkmagician", Catalogo.MASCOTAS, "cobblemon:meowscarada", "darkmagician", 1500),
            new Pieza("mewtwo_boundary", Catalogo.MASCOTAS, "cobblemon:mewtwo", "boundary", 4000),
            new Pieza("mewtwo_covert", Catalogo.MASCOTAS, "cobblemon:mewtwo", "covert", 4000),
            new Pieza("mimikyu_pawmi", Catalogo.MASCOTAS, "cobblemon:mimikyu", "pawmi", 1500),
            new Pieza("minun_cheerleader", Catalogo.MASCOTAS, "cobblemon:minun", "cheerleader", 1500),
            new Pieza("moltres_steampunk", Catalogo.MASCOTAS, "cobblemon:moltres", "steampunk", 4000),
            new Pieza("ninetales_holiday", Catalogo.MASCOTAS, "cobblemon:ninetales", "holiday", 1500),
            new Pieza("pichu_yellowhat", Catalogo.MASCOTAS, "cobblemon:pichu", "yellowhat", 1500),
            new Pieza("pikachu_yellowhat", Catalogo.MASCOTAS, "cobblemon:pikachu", "yellowhat", 1500),
            new Pieza("plusle_cheerleader", Catalogo.MASCOTAS, "cobblemon:plusle", "cheerleader", 1500),
            new Pieza("raichu_yellowhat", Catalogo.MASCOTAS, "cobblemon:raichu", "yellowhat", 1500),
            new Pieza("roserade_valentines", Catalogo.MASCOTAS, "cobblemon:roserade", "valentines", 1500),
            new Pieza("sawk_festival", Catalogo.MASCOTAS, "cobblemon:sawk", "festival", 1500),
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
