package net.pokereport.luna.cosmetics;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Qué cosméticos existen y cuánto valen. <b>Vive en el servidor y solo ahí.</b>
 *
 * <p>Con {@code D-039} —los cosméticos no se consiguen jugando, solo
 * comprándolos o en eventos— este catálogo es la única fuente de verdad que hay.
 * El cliente no lo conoce hasta que se lo mandan, y no puede inventarse una
 * entrada: un identificador que no esté aquí se rechaza al comprar.
 *
 * <h2>Por qué está en código y no en la base de datos</h2>
 *
 * Porque es <b>contenido, no estado</b>. Lo que cambia por jugador —quién tiene
 * qué— sí está en la base ({@code V011__cosmeticos.sql}); lo que existe es una
 * lista que se edita al añadir cosméticos, y meterla en la base obligaría a una
 * migración o a un panel por cada uno.
 *
 * <p>Y hay una razón más fuerte: cada identificador tiene que existir además
 * <b>como aspecto de Cobblemon</b> para poder dibujarse. Una fila en la base
 * puede apuntar a un aspecto que no existe y nadie se entera hasta que un
 * jugador compra un cosmético invisible. Aquí, al menos, están los dos datos
 * juntos y a la vista.
 *
 * <h2>⚠ LOS PRECIOS SON PROVISIONALES</h2>
 *
 * CLAUDE.md lo dice de toda la economía: se calibra con datos reales, y hasta
 * que alguien juegue de verdad todos los números son estimaciones. Estos sirven
 * para que la tienda funcione, no porque sepamos que son correctos.
 *
 * <p>Lo único que sí es una decisión y no una estimación es la <b>forma</b> de
 * la escala: hay tramos, y el tramo alto no es el doble del bajo sino casi el
 * triple. Un catálogo donde todo cuesta parecido no tiene nada que desear.
 */
public final class Catalogo {

    private Catalogo() {
    }

    /**
     * Un cosmético del catálogo.
     *
     * @param precio en LunaCoins. <b>{@code 0} significa que NO está a la
     *               venta</b> —solo se consigue en un evento—, que no es lo
     *               mismo que gratis. El servicio de compra lo rechaza
     */
    public record Pieza(String id, String categoria, String especie,
                        String aspecto, int precio) {
    }

    public static final String MASCOTAS = "mascotas";
    public static final String CAPAS = "capas";
    public static final String SOMBREROS = "sombreros";
    public static final String AURAS = "auras";

    /**
     * Las mascotas salen de {@code CobblemonMoreCosmetics} (MIT), que declara
     * sus cosméticos como <b>aspectos</b> de Cobblemon — el mismo mecanismo con
     * el que su renderizador cambia el modelo.
     *
     * <p>⚠ Esto es una <b>muestra</b> de las 66 combinaciones que trae ese
     * repositorio. Ampliarla es añadir líneas aquí; el resto de la cadena
     * —protocolo, tienda, dibujado— no se toca.
     *
     * <p>⚠⚠ Y cada línea nueva exige comprobar que el aspecto existe de verdad
     * en el pack instalado. Si no existe, Cobblemon dibuja la especie base sin
     * el cosmético: el jugador paga por un Charizard con armadura y le sale un
     * Charizard normal. No falla, no avisa, y parece un timo.
     */
    private static final List<Pieza> PIEZAS = List.of(
            new Pieza("charizard_knight", MASCOTAS, "cobblemon:charizard", "knight", 2500),
            new Pieza("eevee_valentines", MASCOTAS, "cobblemon:eevee", "valentines", 1200),
            new Pieza("snorlax_chef", MASCOTAS, "cobblemon:snorlax", "chef", 1800),
            new Pieza("mewtwo_boundary", MASCOTAS, "cobblemon:mewtwo", "boundary", 4000),
            new Pieza("articuno_steampunk", MASCOTAS, "cobblemon:articuno", "steampunk", 3500),
            new Pieza("decidueye_ninja", MASCOTAS, "cobblemon:decidueye", "ninja", 2200),
            new Pieza("cinderace_captain", MASCOTAS, "cobblemon:cinderace", "captain", 2000),
            new Pieza("weavile_skier", MASCOTAS, "cobblemon:weavile", "skier", 1500),
            new Pieza("carbink_royal", MASCOTAS, "cobblemon:carbink", "royal", 2800),
            new Pieza("blissey_easter", MASCOTAS, "cobblemon:blissey", "easter", 1600),
            new Pieza("drampa_newyear", MASCOTAS, "cobblemon:drampa", "newyear", 0),
            new Pieza("gardevoir_icedragon", MASCOTAS, "cobblemon:gardevoir", "icedragon", 0)
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

    /** Las categorías, en el orden en que salen las pestañas. */
    public static List<String> categorias() {
        return List.of(MASCOTAS, CAPAS, SOMBREROS, AURAS);
    }
}
