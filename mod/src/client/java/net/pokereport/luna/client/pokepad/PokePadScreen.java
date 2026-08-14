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
 * {@code docs/ui/prompts-arte-pokepad.md} §8. Si el arte se regenera, ese
 * script las vuelve a calcular y hay que traerlas. Es la única forma de que la
 * rejilla caiga siempre dentro de la pantalla azul.
 */
public class PokePadScreen extends Screen {

    private static final Identifier CHASIS = tex("pokepad");
    private static final Identifier REPOSO = tex("reposo");
    private static final Identifier ENCIMA = tex("encima");
    private static final Identifier BLOQUEADA = tex("bloqueada");

    /** El tamaño en pantalla, en píxeles de interfaz. */
    private static final int ANCHO = 345, ALTO = 207;

    /**
     * Cuántas veces más grande es la textura que el hueco donde se dibuja.
     *
     * <p>Las texturas se guardan a 4× ({@code tools/gen_pokepad.py}) y se
     * dibujan en su tamaño de interfaz. Minecraft escala las interfaces según
     * el ajuste <i>GUI Scale</i>: a escala 3 estos 345 píxeles ocupan 1035 en
     * pantalla, y con una textura de 345 cada texel se estira a tres píxeles
     * gordos. Con una de 1380 hay detalle de sobra.
     */
    private static final int ESCALA = 4;

    /** La rejilla, en coordenadas del chasis. Ver §8 del documento. */
    private static final int REJ_X = 110, REJ_Y = 45;
    private static final int CELDA = 38, HUECO = 4, ICONO = 24;
    private static final int COLS = 5, FILAS = 3;

    /** Gris apagado para el icono de una celda bloqueada. */
    private static final int APAGADO = 0xFF6A6A78;

    /** Esquina superior izquierda del chasis dentro de la ventana. */
    private int x0, y0;

    public PokePadScreen() {
        super(Text.translatable("pokepad.lunaeternal.titulo"));
    }

    private static Identifier tex(String nombre) {
        return Identifier.of("lunaeternal", "textures/gui/pokepad/" + nombre + ".png");
    }

    @Override
    protected void init() {
        x0 = (width - ANCHO) / 2;
        y0 = (height - ALTO) / 2;
    }

    /** El juego sigue corriendo detrás: es un menú, no una pausa. */
    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int ratonX, int ratonY, float delta) {
        super.render(ctx, ratonX, ratonY, delta);
        dibujar(ctx, CHASIS, x0, y0, ANCHO, ALTO, 0xFFFFFFFF);

        App bajoElRaton = null;
        for (int i = 0; i < App.TODAS.length; i++) {
            App app = App.TODAS[i];
            int cx = celdaX(i), cy = celdaY(i);
            boolean encima = dentro(ratonX, ratonY, cx, cy);
            if (encima) {
                bajoElRaton = app;
            }

            Identifier fondo = !app.abierta() ? BLOQUEADA : encima ? ENCIMA : REPOSO;
            dibujar(ctx, fondo, cx, cy, CELDA, CELDA, 0xFFFFFFFF);

            // El icono de una celda bloqueada va apagado. No es solo estetica:
            // la celda bloqueada trae un candado en su esquina, y con el icono
            // a todo color los dos se pisan y no se lee ninguno.
            int tinte = app.abierta() ? 0xFFFFFFFF : APAGADO;
            dibujar(ctx, app.icono(), cx + (CELDA - ICONO) / 2,
                    cy + (CELDA - ICONO) / 2, ICONO, ICONO, tinte);
        }

        // El nombre va en la placa de arriba, no debajo de cada celda: el arte
        // no tiene sitio ahí, y con quince rótulos de tres letras no se lee
        // ninguno. Se enseña el de la celda señalada.
        if (bajoElRaton != null) {
            Text nombre = bajoElRaton.abierta()
                    ? bajoElRaton.nombre()
                    : bajoElRaton.nombre().copy().formatted(Formatting.GRAY);
            ctx.drawCenteredTextWithShadow(textRenderer, nombre,
                    x0 + ANCHO / 2, y0 + 8, 0xFFF2FAFF);
            ctx.drawTooltip(textRenderer, bajoElRaton.descripcion(), ratonX, ratonY);
        }
    }

    @Override
    public boolean mouseClicked(double ratonX, double ratonY, int boton) {
        if (boton == 0) {
            for (int i = 0; i < App.TODAS.length; i++) {
                if (!dentro((int) ratonX, (int) ratonY, celdaX(i), celdaY(i))) {
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
     * Dibuja una textura de 4× dentro de un hueco de 1×.
     *
     * <p>Hace falta la sobrecarga larga de {@code drawTexture}: la corta usa el
     * mismo número para el tamaño en pantalla y para la región de la textura,
     * así que <b>no puede escalar</b>. Esta separa las dos cosas, que es justo
     * lo que permite guardar el arte a mayor resolución sin agrandar la
     * interfaz.
     */
    private static void dibujar(DrawContext ctx, Identifier tex,
                                int x, int y, int ancho, int alto, int tinte) {
        int tw = ancho * ESCALA, th = alto * ESCALA;
        boolean tenido = tinte != 0xFFFFFFFF;
        if (tenido) {
            // El color del shader MULTIPLICA a la textura, así que se pone
            // antes de dibujar. Ponerlo después no tiñe nada, y dibujar dos
            // veces deja el original visible debajo.
            ctx.setShaderColor(((tinte >> 16) & 0xFF) / 255f,
                    ((tinte >> 8) & 0xFF) / 255f, (tinte & 0xFF) / 255f, 1f);
        }
        ctx.drawTexture(tex, x, y, ancho, alto, 0f, 0f, tw, th, tw, th);
        if (tenido) {
            ctx.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private int celdaX(int i) {
        return x0 + REJ_X + (i % COLS) * (CELDA + HUECO);
    }

    private int celdaY(int i) {
        return y0 + REJ_Y + (i / COLS) * (CELDA + HUECO);
    }

    private boolean dentro(int rx, int ry, int cx, int cy) {
        return rx >= cx && rx < cx + CELDA && ry >= cy && ry < cy + CELDA;
    }
}
