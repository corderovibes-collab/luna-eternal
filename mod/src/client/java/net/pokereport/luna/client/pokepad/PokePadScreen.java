package net.pokereport.luna.client.pokepad;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * La pantalla principal del PokePad: el chasis y su rejilla de aplicaciones.
 *
 * <p><b>Las medidas no se eligen aquí, se heredan del arte.</b> Están medidas
 * sobre el chasis real por {@code tools/gen_pokepad.py} y anotadas en
 * {@code docs/ui/prompts-arte-pokepad.md} §8.
 */
public class PokePadScreen extends Screen {

    private static final Identifier CHASIS = tex("pokepad");
    // Las celdas NO son texturas: las dibuja el código.
    //
    // Lo eran, y era el motivo de que la pantalla se viera sucia: una celda con
    // bisel en relieve, estampada quince veces, es mucho ruido para un hueco de
    // 37 píxeles. El PokePad de referencia no tiene ni una textura de celda —su
    // fondo llega con la pantalla VACÍA— y las pinta como rectángulos planos un
    // tono más claros. Se ve limpio justo por eso.
    private static final int CELDA_FONDO = 0xFF7A83C8;
    private static final int CELDA_BORDE = 0xFFC8D2F0;
    private static final int CELDA_ENCIMA = 0xFF9AA3E8;
    private static final int BORDE_ENCIMA = 0xFF16F2E6;

    /** El chasis en las unidades en que están medidas las demás constantes. */
    private static final int ANCHO = 346, ALTO = 207;

    /**
     * La textura mide lo mismo que el hueco donde se dibuja: 1×.
     *
     * <p>Se probó a 4× creyendo que más resolución daría más nitidez, y salió
     * peor. La prueba está en el mod de referencia: su chasis es de 346×207 con
     * <b>21 colores</b>, el nuestro era de 1380×828 con <b>18 905</b>.
     *
     * <p>Minecraft amplía las interfaces con vecino más próximo. A un dibujo de
     * veintitantos colores eso le sienta de maravilla —sale nítido y con
     * aspecto deliberado— y a una ilustración suavizada la deja sucia a
     * cualquier tamaño. El arreglo no era subir la resolución sino
     * <b>cuantizar</b>, que es lo que hace ahora {@code gen_pokepad.py}.
     */
    private static final int ESCALA = 1;

    /** La rejilla, en las mismas unidades. Ver §8 del documento. */
    // Los da `tools/gen_pokepad.py` al preparar el arte: los imprime al final.
    // No se escriben a ojo, se copian de ahi.
    private static final int REJ_X = 118, REJ_Y = 49;
    private static final int CELDA = 35, HUECO = 4, ICONO = 25;
    private static final int COLS = 5;

    /** Gris apagado para el icono de una celda bloqueada. */
    private static final int APAGADO = 0xFF6A6A78;

    private int x0, y0, ancho, alto;
    private float k;

    public PokePadScreen() {
        super(Text.translatable("pokepad.lunaeternal.titulo"));
    }

    private static Identifier tex(String nombre) {
        return Identifier.of("lunaeternal", "textures/gui/pokepad/" + nombre + ".png");
    }

    /**
     * Centra el Pad.
     *
     * <p>Se dibuja al tamaño de la textura y se deja que Minecraft lo amplíe
     * por el ajuste <i>GUI Scale</i>, que es como funciona cualquier interfaz
     * del juego —y como lo hace el Pad de referencia—. Con la textura ya
     * cuantizada a pixel art, ampliar con vecino más próximo da bordes limpios.
     */
    @Override
    protected void init() {
        ancho = ANCHO;
        alto = ALTO;
        k = 1f;
        x0 = (width - ancho) / 2;
        y0 = (height - alto) / 2;
    }

    /** El juego sigue corriendo detrás: es un menú, no una pausa. */
    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int ratonX, int ratonY, float delta) {
        super.render(ctx, ratonX, ratonY, delta);
        dibujar(ctx, CHASIS, x0, y0, ancho, alto,
                ANCHO * ESCALA, ALTO * ESCALA, 0xFFFFFFFF);

        int celda = Math.round(CELDA * k);
        int icono = Math.round(ICONO * k);

        App bajoElRaton = null;
        for (int i = 0; i < App.TODAS.length; i++) {
            App app = App.TODAS[i];
            int cx = celdaX(i), cy = celdaY(i);
            boolean encima = ratonX >= cx && ratonX < cx + celda
                    && ratonY >= cy && ratonY < cy + celda;
            if (encima) {
                bajoElRaton = app;
            }

            celda(ctx, cx, cy, celda,
                    encima ? CELDA_ENCIMA : CELDA_FONDO,
                    encima ? BORDE_ENCIMA : CELDA_BORDE);

            // El icono de una aplicación cerrada va apagado en vez de llevar un
            // candado encima. Quince candados en una rejilla de quince es más
            // ruido que información, y ademas tapan el propio icono.
            dibujar(ctx, app.icono(), cx + (celda - icono) / 2,
                    cy + (celda - icono) / 2, icono, icono,
                    ICONO * ESCALA, ICONO * ESCALA,
                    app.abierta() ? 0xFFFFFFFF : APAGADO);
        }

        if (bajoElRaton != null) {
            Text nombre = bajoElRaton.abierta()
                    ? bajoElRaton.nombre()
                    : bajoElRaton.nombre().copy().formatted(Formatting.GRAY);
            ctx.drawCenteredTextWithShadow(textRenderer, nombre,
                    x0 + ancho / 2, y0 + Math.round(9 * k), 0xFFF2FAFF);
            ctx.drawTooltip(textRenderer, bajoElRaton.descripcion(), ratonX, ratonY);
        }
    }

    @Override
    public boolean mouseClicked(double ratonX, double ratonY, int boton) {
        int celda = Math.round(CELDA * k);
        if (boton == 0) {
            for (int i = 0; i < App.TODAS.length; i++) {
                int cx = celdaX(i), cy = celdaY(i);
                if (ratonX < cx || ratonX >= cx + celda
                        || ratonY < cy || ratonY >= cy + celda) {
                    continue;
                }
                App app = App.TODAS[i];
                // Una celda bloqueada suena distinto y no hace nada. Sin sonido,
                // el jugador cree que el clic no se registró y repite.
                if (client != null && client.player != null) {
                    client.player.playSound(app.abierta()
                            ? SoundEvents.UI_BUTTON_CLICK.value()
                            : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 1.0f);
                }
                return true;
            }
        }
        return super.mouseClicked(ratonX, ratonY, boton);
    }

    /**
     * Una celda: rectángulo plano con las esquinas mordidas.
     *
     * <p>Morder las cuatro esquinas es como se redondea en pixel art. Sin eso,
     * quince rectángulos de esquina viva se ven como una hoja de cálculo.
     */
    private static void celda(DrawContext ctx, int x, int y, int lado,
                              int fondo, int borde) {
        ctx.fill(x, y, x + lado, y + lado, borde);
        ctx.fill(x + 1, y + 1, x + lado - 1, y + lado - 1, fondo);
        // Las cuatro esquinas, del color de la pantalla que hay debajo.
        int p = 0x00000000;
        ctx.fill(x, y, x + 1, y + 1, p);
        ctx.fill(x + lado - 1, y, x + lado, y + 1, p);
        ctx.fill(x, y + lado - 1, x + 1, y + lado, p);
        ctx.fill(x + lado - 1, y + lado - 1, x + lado, y + lado, p);
    }

    /**
     * Dibuja una textura entera en el hueco indicado.
     *
     * <p>Hace falta la sobrecarga larga de {@code drawTexture}: la corta usa el
     * mismo número para el tamaño en pantalla y para la región de la textura,
     * así que no puede escalar.
     */
    private static void dibujar(DrawContext ctx, Identifier textura, int x, int y,
                                int ancho, int alto, int natW, int natH, int tinte) {
        boolean tenido = tinte != 0xFFFFFFFF;
        if (tenido) {
            // El color del shader MULTIPLICA a la textura, así que va antes de
            // dibujar. Después no tiñe nada.
            ctx.setShaderColor(((tinte >> 16) & 0xFF) / 255f,
                    ((tinte >> 8) & 0xFF) / 255f, (tinte & 0xFF) / 255f, 1f);
        }
        // `natW/natH` son el tamaño REAL de la PNG, y hay que pasarlos: con el
        // tamaño en pantalla, Minecraft tomaría solo esa esquina de la textura
        // en lugar de la textura entera.
        ctx.drawTexture(textura, x, y, ancho, alto, 0f, 0f, natW, natH, natW, natH);
        if (tenido) {
            ctx.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private int celdaX(int i) {
        return x0 + Math.round((REJ_X + (i % COLS) * (CELDA + HUECO)) * k);
    }

    private int celdaY(int i) {
        return y0 + Math.round((REJ_Y + (i / COLS) * (CELDA + HUECO)) * k);
    }
}
