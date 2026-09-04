package net.pokereport.luna.traje;

import java.util.Arrays;
import java.util.List;

import net.pokereport.luna.ui.Tablist;

/**
 * LOS TRAJES DE RANGO.
 *
 * <h2>⚠⚠⚠ CERO PROTECCIÓN, Y NO ES UN OLVIDO</h2>
 *
 * El traje de Diosesmon da <b>3/8/6/3</b> — que es exactamente diamante, medido
 * en su {@code ModArmorMaterials}. O sea que su traje de pago protege. Aquí no:
 * D-007 y D-014 dicen que se vende <b>identidad</b>, no poder competitivo, y una
 * armadura que protege es poder competitivo aunque venga de un rango.
 *
 * <p>Aquí no hay armadura. <b>No hay ni objeto.</b> El servidor dice quién lleva
 * cuál y el cliente lo dibuja encima del jugador, igual que los sombreros.
 *
 * <h2>⚠⚠⚠ CADA TRAJE SE ADQUIERE POR SEPARADO</h2>
 *
 * Decisión del usuario (2026-09-03). Aquí ponía lo contrario —«se puede llevar
 * cualquiera hasta el tuyo»— y el código lo hacía: un LEYENDA podía vestirse de
 * los cinco. <b>Ya no.</b> Comprar LEYENDA da LEYENDA y nada más; los demás se
 * compran aparte, y quien suba de ÉLITE a CAMPEÓN <b>se queda con los dos</b>.
 * El ENTRENADOR es gratis para todo el mundo.
 *
 * <p>⚠⚠ Lo que sí se conserva del diseño anterior: <b>puedes llevar cualquiera
 * de los que tengas</b>, no solo el más alto. Un traje es un disfraz, y obligar
 * al más alto convierte una recompensa en un uniforme.
 *
 * <p>⚠ Los precios y los descuentos son de Tebex; el mod solo necesita saber
 * qué tienes, y eso vive en {@code player_suit_owned} (V028).
 *
 * <h2>⚠⚠ Y `listo` NO ES UNA BANDERA DE DESARROLLO</h2>
 *
 * Es lo que impide <b>vender humo</b>. Un traje sin arte se puede declarar,
 * equipar y sincronizar sin dar un solo error — y el jugador vería exactamente
 * nada. Es el mismo fallo que los 62 cosméticos que no existían: se compraban,
 * se cobraban, y salía el Pokémon normal. Con esto, un traje sin arte se ve en
 * la pantalla (que es información: dice hacia dónde vas) pero <b>no se puede
 * poner</b>.
 */
public enum Traje {

    // ⚠⚠⚠ `listo` SE ENCIENDE CUANDO EL ARTE ESTA EN EL JAR DEL CLIENTE, no
    //    cuando esta dibujado. El cliente busca `trajes/<id>/<id>_<pieza>.geo.json`
    //    y `textures/armor/<id>/`, y el <id> es EL DE AQUI: si no coincide, el
    //    traje se equipa, se sincroniza, no da ningun error Y NO SE VE NADA.
    //    Es el fallo de los 62 cosmeticos que no existian, y ya nos mordio: la
    //    primera version del importador registro el traje de Arceus como
    //    «arceus» en vez de «leyenda».
    //    Los genera `python tools/gen_trajes.py --generar`.

    // ⚠ El identificador cambio con el rango (NOVATO -> ENTRENADOR). Salio
    //   gratis porque construye la ruta del arte --textures/armor/<id>/-- y
    //   ese arte todavia no existia.
    // ⚠⚠⚠ EL ENTRENADOR NO ES UN TRAJE COSMETICO, ES UN KIT DE OBJETOS
    //    (decision del usuario, 2026-09-03): son las cuatro piezas de COTA DE
    //    MALLA de vainilla, que el jugador se quita, guarda y pierde al morir.
    //    Por eso `listo` es false --no hay nada que dibujar encima-- y por eso
    //    existe `esKit()`: la pantalla ensena RECLAMAR en vez de PONER.
    //    ⚠ Se eligio la malla porque en vainilla NO SE CRAFTEA: solo se
    //      consigue por comando o comerciando, asi que darla no compite con
    //      ninguna receta.
    ENTRENADOR("entrenador", Tablist.Rank.ENTRENADOR, false),
    ELITE("elite", Tablist.Rank.ELITE, false),
    // ⚠ CAMPEON y LEYENDA vienen de los .bbmodel del usuario, que estan en el
    //   repo (arte/trajes/). Los otros tres siguen sin arte.
    CAMPEON("campeon", Tablist.Rank.CAMPEON, true),
    MAESTRO("maestro", Tablist.Rank.MAESTRO, false),
    LEYENDA("leyenda", Tablist.Rank.LEYENDA, true);

    private final String id;
    private final Tablist.Rank pide;
    private final boolean listo;

    Traje(String id, Tablist.Rank pide, boolean listo) {
        this.id = id;
        this.pide = pide;
        this.listo = listo;
    }

    public String id() {
        return id;
    }

    public Tablist.Rank pide() {
        return pide;
    }

    /** ¿Tiene arte? Ver el javadoc de la clase: sin esto se vende humo. */
    public boolean listo() {
        return listo;
    }

    /** Todos, del más bajo al más alto. Es el orden en que se dibujan. */
    public static List<Traje> todos() {
        return Arrays.asList(values());
    }

    /**
     * El traje con ese identificador, o {@code null}.
     *
     * <p>⚠ Devuelve {@code null} y no el primero: el identificador llega <b>del
     * cliente</b>, y caer al más bajo ante un valor desconocido significaría que
     * un cliente modificado siempre acierta con algo (P6). Aquí, si no existe,
     * no pasa nada.
     */
    public static Traje de(String id) {
        if (id == null) {
            return null;
        }
        for (Traje t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    /**
     * ¿Es gratis para todo el mundo?
     *
     * <h2>⚠⚠⚠ CADA TRAJE SE ADQUIERE POR SEPARADO (decisión del usuario, 2026-09-03)</h2>
     *
     * Aquí había {@code puede(int escalon)}, que devolvía {@code escalon >= }
     * el del traje: un LEYENDA podía ponerse <b>los cinco</b>. Ya no. Comprar
     * LEYENDA da LEYENDA y nada más; los demás se compran aparte, y quien suba
     * de ÉLITE a CAMPEÓN <b>se queda con los dos</b>.
     *
     * <p>⚠⚠ Eso no se puede derivar del rango, y por eso existe
     * {@code player_suit_owned}: un jugador tiene <b>un</b> rango y puede tener
     * <b>varios</b> trajes. Quién tiene qué lo responde
     * {@link net.pokereport.luna.traje.TrajeService#tiene}.
     *
     * <p>⚠ El ENTRENADOR es la excepción y vive aquí, no en la tabla: un traje
     * gratis es una <b>regla</b>, no un dato. En la tabla obligaría a insertar
     * una fila por jugador, incluidos los que no han entrado nunca, y esa fila
     * es justo la que un día falta.
     */
    public boolean gratis() {
        return this == ENTRENADOR;
    }

    /**
     * ¿Es un kit de objetos en vez de un traje que se dibuja?
     *
     * <p>⚠⚠ El ENTRENADOR entrega <b>cota de malla de verdad</b>: se quita, se
     * guarda, se comercia y se pierde al morir. Los otros cuatro son capas que
     * se dibujan encima y no existen como objeto.
     *
     * <p>⚠ Y por eso este es el único que <b>protege</b>. Es gratis para todo el
     * mundo, así que no cruza la línea de D-007/D-014 —lo que esa línea prohíbe
     * es <b>vender</b> poder—, pero conviene tenerlo presente: es armadura
     * gratis para todos, y eso toca el equilibrio de combate.
     */
    public boolean esKit() {
        return this == ENTRENADOR;
    }
}
