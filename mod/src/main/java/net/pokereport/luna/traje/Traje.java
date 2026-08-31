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
 * <h2>⚠⚠ SE PUEDE LLEVAR CUALQUIERA HASTA EL TUYO, NO SOLO EL TUYO</h2>
 *
 * Un LEYENDA puede vestirse de ENTRENADOR si le apetece. Es deliberado: un traje es
 * un disfraz, y obligar a llevar el más alto convierte una recompensa en un
 * uniforme. Además evita el efecto raro de que subir de rango te <b>quite</b> el
 * aspecto con el que la gente te conocía.
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

    // ⚠⚠⚠ LOS CINCO ESTAN A `false`, Y ES LO CORRECTO AHORA MISMO.
    //    El arte generado por script se retiro el 2026-08-28 (decision del
    //    usuario): se hara A MANO en Blockbench. Hasta que un traje tenga sus
    //    ficheros, se ve en la pantalla --que dice hacia donde va la
    //    progresion-- y NO SE PUEDE PONER.
    //    Se enciende cambiando su `false` por `true`, y nada mas.

    // ⚠ El identificador cambio con el rango (NOVATO -> ENTRENADOR). Salio
    //   gratis porque construye la ruta del arte --textures/armor/<id>/-- y
    //   ese arte todavia NO EXISTE: `listo` esta a false en los cinco.
    ENTRENADOR("entrenador", Tablist.Rank.ENTRENADOR, false),
    ELITE("elite", Tablist.Rank.ELITE, false),
    CAMPEON("campeon", Tablist.Rank.CAMPEON, false),
    MAESTRO("maestro", Tablist.Rank.MAESTRO, false),
    LEYENDA("leyenda", Tablist.Rank.LEYENDA, false);

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
     * ¿Puede este escalón ponerse este traje?
     *
     * <p>⚠⚠ EL ESCALÓN, NO EL RANGO QUE SE VE. Un operador se muestra como ADMIN
     * en el tablist pero desbloquea por lo que tiene guardado — si no, dar OP a
     * alguien para mirar una cosa le regalaría el traje de LEYENDA. Es la misma
     * separación que ya hace la mochila.
     */
    public boolean puede(int escalon) {
        return listo && escalon >= pide.escalon;
    }
}
