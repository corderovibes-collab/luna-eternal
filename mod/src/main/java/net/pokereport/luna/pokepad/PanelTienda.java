package net.pokereport.luna.pokepad;

/**
 * LA GEOMETRÍA DEL PANEL IZQUIERDO DE LA TIENDA.
 *
 * <h2>⚠⚠⚠ VIVE EN {@code main} PARA QUE NO HAYA DOS COPIAS</h2>
 *
 * Aquí no se dibuja nada: son los números con los que {@code TiendaScreen}
 * reparte las categorías, y con los que el autotest comprueba que caben. Antes
 * el autotest los tenía <b>escritos otra vez</b> —{@code 156 + n * 94 + 110}—
 * y eso es exactamente lo que ya mordió con las medallas: <b>tres listas que
 * eran una sola</b>, y nada obligaba a que coincidieran.
 *
 * <p>⚠⚠ Y se puede porque {@code src/client/} y {@code src/main/} acaban en el
 * <b>mismo jar</b>: la pantalla lee de aquí, el servidor lee de aquí, y no hay
 * forma de que se contradigan. Es mejor que una comprobación que lo detecte —
 * así no puede pasar.
 *
 * <h2>⚠⚠ CUÁNTAS CABEN SE CALCULA, NO SE ESCRIBE</h2>
 *
 * Este proyecto ha tropezado <b>cuatro veces</b> con lo mismo: la rejilla del
 * PokePad con quince aplicaciones, las paradas de viaje, las dos rejillas de La
 * Liga y los cosméticos. Siempre igual — un número que cuadraba <b>por
 * casualidad</b> hasta que alguien añadió uno más, y el síntoma nunca es un
 * error: es algo dibujado fuera del marco, <b>invisible e imposible de
 * pulsar</b>.
 */
public final class PanelTienda {

    /** El panel: empieza en 70 y mide 692, así que acaba en 762. */
    public static final int PANEL_Y = 70, PANEL_H = 692;
    /** La barra de navegación de arriba (atrás · inicio · cerrar). */
    public static final int NAV_ALTO = 72;
    /** La tarjeta de una categoría, y el aire entre dos. */
    public static final int CAT_ALTO = 86, CAT_AIRE = 8;
    /** Donde cae la primera tarjeta. */
    public static final int CAT_Y0 = PANEL_Y + NAV_ALTO + 14;
    /** Lo que ocupa el bloque del saldo, medido sobre lo que dibuja. */
    public static final int SALDO_ALTO = 112;
    /** La fila de flechas. Solo se dibuja si hay más de una página. */
    public static final int PAGER_ALTO = 34;

    private PanelTienda() {}

    /** Cuántas tarjetas entran en una página. Nunca menos de una. */
    public static int porPagina() {
        return Math.max(1, (PANEL_Y + PANEL_H - CAT_Y0 - SALDO_ALTO - PAGER_ALTO)
                / (CAT_ALTO + CAT_AIRE));
    }

    /** Cuántas páginas hacen falta para {@code n} categorías. */
    public static int paginas(int n) {
        return Math.max(1, (n + porPagina() - 1) / porPagina());
    }

    /**
     * Lo que ocupa la lista, en píxeles, con {@code filas} tarjetas dibujadas
     * y contando el pager si hace falta.
     *
     * <p>⚠ Es lo que el autotest compara contra el alto del panel. Con
     * paginación no puede desbordar —{@code porPagina()} sale de una división—
     * pero sí puede hacerlo si alguien engorda la tarjeta o el saldo, y
     * entonces la última quedaría medio fuera sin dar ningún error.
     */
    public static int altoOcupado(int filas, boolean conPager) {
        return (CAT_Y0 - PANEL_Y) + filas * (CAT_ALTO + CAT_AIRE)
                + (conPager ? PAGER_ALTO : 0) + SALDO_ALTO;
    }
}
