package net.pokereport.luna.cosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Qué cosméticos existen y cuánto valen. <b>Vive en el servidor y solo ahí.</b>
 *
 * <p>Con {@code D-039} —los cosméticos solo se consiguen comprándolos o en
 * eventos— este catálogo es la única fuente que hay: un identificador que no
 * esté aquí se rechaza al comprar.
 *
 * <h2>⚠ Está partido en dos, y las dos mitades son distintas a propósito</h2>
 *
 * <table border="1">
 *   <tr><th>Mitad</th><th>De dónde sale</th><th>Por qué</th></tr>
 *   <tr>
 *     <td>{@link CatalogoMascotas}</td>
 *     <td><b>GENERADO</b> del zip de CobblemonMoreCosmetics</td>
 *     <td>Lo define un pack ajeno. Escribirlo a mano falló tres veces, y las
 *         tres se cobró un disfraz que no se veía</td>
 *   </tr>
 *   <tr>
 *     <td>{@link CatalogoLuna}</td>
 *     <td><b>A MANO</b></td>
 *     <td>Lo definimos nosotros, así que la fuente de verdad es ese fichero</td>
 *   </tr>
 * </table>
 *
 * <p>La regla que comparten no es «generar» ni «escribir»: es que <b>cada
 * catálogo sale de donde vive la verdad de lo que describe</b>.
 *
 * <h2>⚠ LOS PRECIOS SON PROVISIONALES</h2>
 *
 * CLAUDE.md lo dice de toda la economía: se calibra con datos reales.
 */
public final class Catalogo {

    private Catalogo() {
    }

    /**
     * Un cosmético del catálogo.
     *
     * @param especie para las mascotas, el Pokémon al que le va
     *                ({@code cobblemon:charizard}); <b>vacío</b> en los
     *                cosméticos del jugador, que no dependen de ninguno
     * @param aspecto para las mascotas, el aspecto que aplica ({@code knight});
     *                para los del jugador, <b>el nombre que se enseña</b>
     * @param precio  en LunaCoins. <b>{@code 0} = NO está a la venta</b>: solo
     *                sale en eventos (D-039), que no es lo mismo que gratis
     */
    public record Pieza(String id, String categoria, String especie,
                        String aspecto, int precio) {

        /** Se le pone a un Pokémon (y por tanto necesita tenerlo). */
        public boolean esDePokemon() {
            return !especie.isEmpty();
        }
    }

    public static final String MASCOTAS = "mascotas";
    public static final String CAPAS = "capas";
    public static final String SOMBREROS = "sombreros";
    public static final String AURAS = "auras";

    private static final List<Pieza> PIEZAS = unir();

    private static List<Pieza> unir() {
        List<Pieza> todas = new ArrayList<>(CatalogoMascotas.PIEZAS);
        todas.addAll(CatalogoLuna.piezas());
        // ⚠ SE COMPRUEBA QUE NO HAYA IDENTIFICADORES REPETIDOS, y revienta el
        //   arranque si los hay. Suena drástico y es lo correcto: el
        //   identificador es la clave en `player_cosmetics`, así que dos piezas
        //   con el mismo id serían la misma compra — comprar una te daría la
        //   otra. Una mitad se genera y la otra se escribe a mano, o sea que
        //   nadie las está mirando juntas: esto es lo único que lo haría.
        var vistos = new java.util.HashSet<String>();
        var choques = new ArrayList<String>();
        for (Pieza p : todas) {
            if (!vistos.add(p.id())) {
                choques.add(p.id());
            }
        }
        if (!choques.isEmpty()) {
            throw new IllegalStateException(
                    "Hay cosmeticos con el mismo identificador en las dos mitades "
                            + "del catalogo, y el identificador es la clave en la "
                            + "base de datos: " + choques);
        }
        return List.copyOf(todas);
    }

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
