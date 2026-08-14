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
    private static final Identifier REPOSO = tex("reposo");
    private static final Identifier ENCIMA = tex("encima");
    private static final Identifier BLOQUEADA = tex("bloqueada");

    /** El chasis en las unidades en que están medidas las demás constantes. */
    private static final int ANCHO = 345, ALTO = 207;

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
    private static final int REJ_X = 113, REJ_Y = 46;
    private static final int CELDA = 37, HUECO = 4, ICONO = 24;
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

            dibujar(ctx, !app.abierta() ? BLOQUEADA : encima ? ENCIMA : REPOSO,
                    cx, cy, celda, celda, CELDA * ESCALA, CELDA * ESCALA, 0xFFFFFFFF);

            // El icono de una celda bloqueada va apagado. No es estética: la
            // celda bloqueada trae un candado en su esquina, y con el icono a
            // todo color los dos se pisan y no se lee ninguno.
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
