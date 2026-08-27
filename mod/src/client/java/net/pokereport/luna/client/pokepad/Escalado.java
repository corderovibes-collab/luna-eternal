package net.pokereport.luna.client.pokepad;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * DÓNDE Y A QUÉ TAMAÑO SE DIBUJA EL CHASIS.
 *
 * <h2>⚠⚠ ESTABA COPIADO EN ONCE PANTALLAS, Y YA HABÍA DERIVADO</h2>
 *
 * Cada pantalla llevaba su propio {@code recalcular()} — «copia literal de
 * {@code CosmeticosScreen}», decían todas. Medido el 2026-08-26: <b>seis
 * variantes distintas</b>. Ninguna daba error; simplemente cada una había
 * envejecido por su lado.
 *
 * <p>Aquí vive una sola, y arreglar el escalado deja de ser once ediciones.
 *
 * <h2>⚠⚠⚠ EL FALLO QUE ARREGLA: LA INTERFAZ NO CRECÍA CON LA PANTALLA</h2>
 *
 * La versión anterior tenía {@code Math.min(1.0, cabe)}, o sea <b>nunca
 * ampliaba</b>. El chasis se dibujaba siempre a 1.380 píxeles físicos, así que
 * cuanto más grande el monitor, más pequeño se veía. Medido:
 *
 * <pre>
 *   1920x1080   72 % del ancho     ← «se ve bien», dijo el usuario
 *   2560x1440   54 %
 *   3840x2160   36 %               ← la queja
 *   5120x2880   27 %
 * </pre>
 *
 * <h2>La solución: MEDIOS PASOS, y por qué no enteros ni libre</h2>
 *
 * <table>
 *   <tr><td><b>Libre</b></td>
 *       <td>llenaría siempre, pero cambiaría 1080p de un 1:1 nítido a un 1,22×
 *           borroso — y 1080p <b>ya se veía bien</b>. No se arregla una
 *           pantalla estropeando otra</td></tr>
 *   <tr><td><b>Enteros</b></td>
 *       <td>nítido siempre, pero 1440p no llega a 2× (1440/828 = 1,74) y se
 *           quedaría en el mismo 54 % de ahora</td></tr>
 *   <tr><td><b>Medios</b></td>
 *       <td>1080p y menos <b>no cambian nada</b>; 1440p pasa a 81 % y 4K a
 *           90 %</td></tr>
 * </table>
 *
 * <p>⚠ Y un 1,5× no es un problema aquí porque <b>el chasis no es pixel art</b>:
 * es una ilustración HD (ver D-029 y el bloque PokePad de CLAUDE.md). Una
 * ilustración se reescala bien con filtrado lineal — que es lo que enciende
 * {@link #aplicar} cuando la escala no es exacta.
 *
 * <h2>⚠ El ajuste de GUI del jugador es el TECHO</h2>
 *
 * {@code min(gui, loQueCabe)}. Así, quien pone la interfaz pequeña la sigue
 * teniendo pequeña en un 4K, y quien no la toca recibe lo más grande que quepa.
 * Antes el ajuste no influía <b>en absoluto</b>: se dividía por él exactamente,
 * de modo que el tamaño físico salía igual lo pusieras como lo pusieras.
 */
public final class Escalado {

    private Escalado() {}

    /** El arte del chasis. Divisible entre 1,2,3,4,6 — ver CLAUDE.md. */
    public static final int NAT_ANCHO = 1380, NAT_ALTO = 828;

    /**
     * ⚠⚠⚠ SIN MARGEN, Y ESTA LECCIÓN YA SE PAGÓ UNA VEZ.
     *
     * <p>Aquí llegué a poner un 0,98 «para que no pegue con el borde», y
     * {@code PokePadScreen} ya lo había probado y retirado: con una ventana de
     * 1382×825 —tres píxeles corta— el margen la encoge igualmente, y
     * <b>encoger es lo que emborrona</b>. Convertía en borrosas todas las
     * ventanas de entre 1380 y 1409 de ancho a cambio de un hueco que nadie
     * mira.
     *
     * <p>Y con los medios pasos el margen ya no hace falta para nada: al
     * redondear hacia abajo casi siempre sobra aire de todos modos.
     *
     * <h2>El margen transparente del chasis</h2>
     *
     * Lo que sí se conserva de {@code PokePadScreen}: si lo que falta para que
     * quepa a tamaño real <b>cabe dentro del borde transparente del arte</b>,
     * se dibuja a tamaño real y que sobresalga. Perder tres píxeles de una
     * esquina que ya era transparente no se ve; emborronar el chasis entero,
     * sí.
     */
    private static final int MARGEN_X = 12, MARGEN_Y = 4;

    /**
     * Lo que una pantalla necesita saber para dibujarse.
     *
     * <p>⚠ {@code exacto} sale fuera porque el PokePad filtra <b>sus propias
     * texturas</b> —iconos y botones, que se descubren en tiempo de ejecución—
     * y no puede pasarlas en una lista fija. Devolver la decisión, y no solo
     * aplicarla, es lo que permite que haya un único sitio que la tome.
     */
    public record Medidas(float k, int ancho, int alto, int x0, int y0,
                          boolean exacto) {}

    /**
     * Calcula las medidas y ajusta el filtrado de las texturas.
     *
     * @param anchoPantalla {@code width} de la pantalla, en unidades de GUI
     * @param texturas las que dibuja esa pantalla; se les pone el filtro
     */
    public static Medidas aplicar(MinecraftClient client, int anchoPantalla,
                                  int altoPantalla, Identifier... texturas) {
        double gui = client != null ? client.getWindow().getScaleFactor() : 1;
        int ventanaW = client == null
                ? NAT_ANCHO : client.getWindow().getFramebufferWidth();
        int ventanaH = client == null
                ? NAT_ALTO : client.getWindow().getFramebufferHeight();

        // ⚠ Lo que falta para que quepa a tamaño real, y si ese sobrante entra
        //   en el borde transparente del arte. Es lo que evita encoger un 0,5 %
        //   y emborronarlo todo por siete píxeles.
        boolean casiCabe = (NAT_ANCHO - ventanaW) <= MARGEN_X * 2
                && (NAT_ALTO - ventanaH) <= MARGEN_Y * 2;
        double cabe = Math.min(ventanaW / (double) NAT_ANCHO,
                               ventanaH / (double) NAT_ALTO);
        double escala = casiCabe && cabe < 1
                ? 1.0
                : escalaDe(Math.min(gui, cabe));

        float k = (float) (escala / gui);
        int ancho = Math.round(NAT_ANCHO * k);
        int alto = Math.round(NAT_ALTO * k);
        int x0 = (anchoPantalla - ancho) / 2;
        int y0 = (altoPantalla - alto) / 2;

        // ⚠ El filtrado se enciende cuando la escala NO es un entero. A 1× o 2×
        //   cada texel cae en un número redondo de píxeles y `nearest` sale
        //   nítido; a 1,5× hace falta interpolar o se ve dentado.
        boolean exacto = Math.abs(escala - Math.round(escala)) < 1e-6;
        if (client != null) {
            for (Identifier t : texturas) {
                client.getTextureManager().getTexture(t).setFilter(!exacto, false);
            }
        }
        return new Medidas(k, ancho, alto, x0, y0, exacto);
    }

    /**
     * Redondea hacia abajo a medio paso: 1 · 1,5 · 2 · 2,5 …
     *
     * <p>⚠ Por debajo de 1 se deja tal cual. En una pantalla donde el chasis no
     * cabe entero hay que encogerlo lo que haga falta, y saltar a un medio paso
     * lo dejaría fuera del marco.
     */
    static double escalaDe(double deseada) {
        if (deseada < 1) {
            return Math.max(0.1, deseada);
        }
        return Math.floor(deseada * 2) / 2.0;
    }
}
