package net.pokereport.luna.backpack;

import net.pokereport.luna.ui.Tablist.Rank;

/**
 * Las reglas de la mochila. <b>Sin estado y sin base de datos.</b>
 *
 * <p>Está aparte para que la parte que decide <i>cuánto</i> se pueda leer,
 * probar y cambiar sin tocar ni el contenedor ni la pantalla.
 */
public final class Mochila {

    private Mochila() {}

    /** Nueve huecos por fila, como el inventario. */
    public static final int COLUMNAS = 9;

    /**
     * ⚠ SIETE, y de aquí sale el porqué.
     *
     * <p>El usuario dio la progresión sumando: <i>«ENTRENADOR una fila, ÉLITE otras
     * 2 más, CAMPEÓN otra más, MAESTRO otras 2 más, LEYENDA se desbloquea
     * todas»</i>. Eso da 1 → 3 → 4 → 6, y entonces <b>«todas» tenía que ser más
     * de 6</b> o LEYENDA no desbloquearía nada que MAESTRO no tuviera ya.
     *
     * <p>Con siete, cada rango añade algo y la última frase es literal.
     */
    public static final int FILAS_MAX = 7;

    public static final int HUECOS = FILAS_MAX * COLUMNAS;

    /**
     * Cuántas filas tiene desbloqueadas ese rango.
     *
     * <h2>⚠⚠ EL CANDADO LO ABRE EL ESCALÓN, NO EL NOMBRE DEL RANGO</h2>
     *
     * Con un {@code switch} sobre el nombre, añadir un rango en medio obligaría
     * a tocar esto — y olvidarlo dejaría al rango nuevo con cero filas, sin
     * ningún error. Con el escalón, un rango nuevo entra solo.
     *
     * <p>⚠ Los de equipo (ADMIN, DEV, MODERADOR) tienen escalón −1 <b>a
     * propósito</b>, así que aquí caen en el mínimo. Es correcto: dar OP a
     * alguien para mirar una cosa no puede regalarle la mochila entera. Su
     * mochila es la de su rango de jugador, que es el que está guardado.
     */
    public static int filasDe(int escalon) {
        return switch (Math.max(1, escalon)) {
            case 1 -> 1;    // ENTRENADOR
            case 2 -> 3;    // ÉLITE     +2
            case 3 -> 4;    // CAMPEÓN   +1
            case 4 -> 6;    // MAESTRO   +2
            default -> FILAS_MAX;   // LEYENDA: todas
        };
    }

    public static int filasDe(Rank rank) {
        return filasDe(rank == null ? 1 : rank.escalon);
    }

    /**
     * El rango que hace falta para tener esa fila (0 es la primera).
     *
     * <p>Es lo que dice el cartel de «fila bloqueada». ⚠ Se calcula
     * <b>recorriendo los mismos escalones</b> en vez de con una tabla aparte:
     * dos tablas que dicen lo mismo se separan, y el día que se separen el
     * cartel prometería un rango que no desbloquea esa fila.
     */
    public static Rank rangoParaFila(int fila) {
        for (Rank r : Rank.deJugador().reversed()) {
            if (filasDe(r.escalon) > fila) {
                return r;
            }
        }
        return Rank.LEYENDA;
    }

    /** ¿Está desbloqueado ese hueco con ese escalón? */
    public static boolean abierto(int hueco, int escalon) {
        return hueco >= 0 && hueco < filasDe(escalon) * COLUMNAS;
    }
}
