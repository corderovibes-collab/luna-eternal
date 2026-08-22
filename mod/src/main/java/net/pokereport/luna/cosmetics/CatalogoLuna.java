package net.pokereport.luna.cosmetics;

import java.util.List;

import net.pokereport.luna.cosmetics.Catalogo.Pieza;

/**
 * Los cosméticos <b>del jugador</b>: capas, sombreros y auras.
 *
 * <p>Se escriben a mano, al contrario que {@link CatalogoMascotas}, y es la
 * decisión correcta por el mismo motivo por el que aquél se genera: <b>el
 * catálogo tiene que salir de la fuente de verdad de cada cosa</b>. Los
 * disfraces de Pokémon los define un pack ajeno, así que se leen de él; estos
 * los definimos nosotros, así que la fuente es este fichero.
 *
 * <h2>⚠ Por qué son del JUGADOR y no del Pokémon</h2>
 *
 * Las cuatro pestañas vienen de la petición original: <i>«arriba va a estar un
 * diseño 3D de tu personaje… los artículos cosméticos para tu personaje,
 * mascotas, etc»</i>. «Mascotas» acabó siendo Pokémon disfrazados; las otras
 * tres siguen siendo lo que se pidió — <b>cosas que lleva el jugador</b>. Por eso
 * su equipado vive en {@code player_cosmetic_equipped} y no en el Pokémon.
 *
 * <h2>Las auras no tienen arte, y es a propósito</h2>
 *
 * Cada una es una receta de partículas <b>de vanilla</b>. Eso significa:
 *
 * <ul>
 *   <li><b>Cero ficheros que redistribuir</b> — el problema de licencia que
 *       descartó CobbleVerse (D-006) aquí ni se plantea.</li>
 *   <li><b>Cero bytes en la descarga</b> (P10): la receta son tres números.</li>
 *   <li><b>Se ven desde lejos y en movimiento</b>, que es justo lo que
 *       {@code monetization.md} pide de un cosmético: «uno que nadie ve no vale
 *       nada». Un aura se ve cruzando la plaza; una capa hay que mirarla.</li>
 * </ul>
 *
 * <p>⚠ <b>Ninguna aura tapa al jugador ni confunde con una mecánica.</b> Nada de
 * partículas de daño, de pociones, de fuego encima ni de portal a los pies: en
 * un servidor con combates, un adorno que se parece a un efecto de estado hace
 * que alguien reaccione a algo que no está pasando.
 */
public final class CatalogoLuna {

    private CatalogoLuna() {
    }

    /**
     * Cómo se dibuja un aura.
     *
     * @param particula identificador vanilla, {@code minecraft:end_rod}
     * @param color     0xRRGGBB, y <b>solo se usa si la partícula lo admite</b>
     *                  ({@code dust}). En las demás se ignora: teñir una llama
     *                  no es posible y fingir que sí llevaría a diseñar auras
     *                  que luego salen de otro color
     * @param cadencia  cada cuántos ticks se suelta una tanda. 20 = una vez por
     *                  segundo
     * @param cuantas   partículas por tanda
     * @param forma     cómo se colocan alrededor del jugador
     */
    public record Aura(String particula, int color, int cadencia, int cuantas, Forma forma) {
    }

    /** Dónde nacen las partículas. Ver {@code AuraRenderer} en el cliente. */
    public enum Forma {
        /** Un anillo a los pies, girando. La más discreta. */
        ANILLO,
        /** Una espiral que sube desde los pies hasta la cabeza. */
        ESPIRAL,
        /** Una nube suelta alrededor del cuerpo. */
        NUBE,
        /** Caen desde encima de la cabeza. */
        LLUVIA,
        /** Dos órbitas cruzadas, como electrones. */
        ORBITA
    }

    /**
     * ⚠ El identificador es la clave en la base de datos: <b>una vez vendido, no
     * se cambia</b>. Renombrarlo deja a quien lo compró sin nada, y sin ningún
     * error que lo delate — la fila sigue ahí, apuntando a algo que ya no existe.
     */
    public record PiezaAura(Pieza pieza, Aura aura) {
    }

    // ---- los colores son LOS DEL NEON, no inventados aquí ------------------
    //
    // D-032 ya estableció que los 16 colores del hormigón y del vidrio salen de
    // los 16 del neón por fórmula, «así que pegan por construcción y no porque
    // alguien los haya emparejado a ojo». Las auras usan esa misma paleta por lo
    // mismo: un jugador con aura cian delante de una fachada de neón cian tiene
    // que verse hecho a propósito.
    private static final int CIAN = 0x22E4F5;
    private static final int MAGENTA = 0xF52BC8;
    private static final int VERDE = 0x39F53C;
    private static final int AMBAR = 0xF5A623;
    private static final int VIOLETA = 0x8B4CF5;
    private static final int PLATA = 0xD8E4F0;

    public static final List<PiezaAura> AURAS = List.of(
            // ⚠ LA PRIMERA ES LA DE LA CASA Y ES LA MÁS CARA A PROPÓSITO. El
            //   servidor se llama Luna Eternal y la ciudadela es de noche
            //   permanente: esta es la que se quiere ver puesta, así que tiene
            //   que costar lo que cuesta querer llevarla.
            aura("aura_luna", "Luna", 5000,
                    "minecraft:end_rod", PLATA, 6, 2, Forma.ORBITA),

            aura("aura_neon_cian", "Neón cian", 2500,
                    "minecraft:dust", CIAN, 4, 3, Forma.ESPIRAL),
            aura("aura_neon_magenta", "Neón magenta", 2500,
                    "minecraft:dust", MAGENTA, 4, 3, Forma.ESPIRAL),
            aura("aura_neon_verde", "Neón verde", 2500,
                    "minecraft:dust", VERDE, 4, 3, Forma.ESPIRAL),

            aura("aura_brasas", "Brasas", 2000,
                    "minecraft:small_flame", AMBAR, 5, 2, Forma.NUBE),
            aura("aura_escarcha", "Escarcha", 2000,
                    "minecraft:snowflake", PLATA, 6, 3, Forma.LLUVIA),
            aura("aura_almas", "Almas", 3000,
                    "minecraft:soul_fire_flame", VIOLETA, 6, 2, Forma.ANILLO),
            aura("aura_esporas", "Esporas", 1800,
                    "minecraft:spore_blossom_air", VERDE, 8, 2, Forma.NUBE),
            aura("aura_notas", "Notas", 1500,
                    "minecraft:note", CIAN, 10, 1, Forma.NUBE),

            // ⚠ 0 = NO ESTÁ A LA VENTA, y no es lo mismo que gratis. D-039 dice
            //   que los cosméticos salen por LunaCoins O EN EVENTOS, y los
            //   eventos «no son un adorno de la decisión sino la mitad que la
            //   hace funcionar»: si todo fuera de pago, los únicos con aura
            //   serían los que pagan y el escaparate se apaga solo.
            aura("aura_corazones", "Corazones", 0,
                    "minecraft:heart", MAGENTA, 12, 1, Forma.ANILLO),
            aura("aura_destello", "Destello", 0,
                    "minecraft:electric_spark", CIAN, 4, 4, Forma.ORBITA));

    private static PiezaAura aura(String id, String nombre, int precio,
                                  String particula, int color,
                                  int cadencia, int cuantas, Forma forma) {
        return new PiezaAura(
                new Pieza(id, Catalogo.AURAS, "", nombre, precio),
                new Aura(particula, color, cadencia, cuantas, forma));
    }

    /**
     * Capas y sombreros: <b>todavía vacíos, y a la vista.</b>
     *
     * <p>Se dejan declarados en vez de no existir para que la pestaña salga
     * vacía en vez de fallar, y para que el sitio donde van esté escrito. Lo que
     * falta es <b>arte</b>: D-032 dice que se dibuja y no se baja, porque el arte
     * que circula es ARR o CC-BY-NC — la misma cláusula que descartó CobbleVerse
     * (D-006) y que choca con la venta de paquetes (D-007).
     */
    public static final List<Pieza> CAPAS = List.of();

    /**
     * Los sombreros se GENERAN, al contrario que las auras y las capas.
     *
     * <p>Y por el mismo criterio que separa las otras dos mitades del catalogo:
     * su arte viene de packs ajenos --CobbleHats y Cobblemon Accessories-- asi
     * que la fuente de verdad son esos packs, no este fichero.
     *
     * <p>⚠ Se usan sus MODELOS, no su mecanismo. Los dos los aplican con un
     * OBJETO en la cabeza (calabaza tallada con {@code CustomModelData} uno, CIT
     * Resewn sobre un casco el otro), y un objeto se cae al morir, se comercia y
     * <b>se regala</b> — con lo que el cosmetico dejaria de venir solo de
     * LunaCoins o de eventos, que es lo que dice D-039. Aqui no existe objeto:
     * el servidor dice quien lleva cual y el cliente lo dibuja.
     */
    public static final List<Pieza> SOMBREROS = CatalogoSombreros.PIEZAS;

    /** El aura de ese identificador, o {@code null}. */
    public static Aura auraDe(String id) {
        for (PiezaAura p : AURAS) {
            if (p.pieza().id().equals(id)) {
                return p.aura();
            }
        }
        return null;
    }

    static List<Pieza> piezas() {
        List<Pieza> todas = new java.util.ArrayList<>(CAPAS);
        todas.addAll(SOMBREROS);
        for (PiezaAura p : AURAS) {
            todas.add(p.pieza());
        }
        return List.copyOf(todas);
    }
}
