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

    /** El chasis, tal cual salió del troceador. */
    private static final int ANCHO = 345, ALTO = 207;

    /** La rejilla, en coordenadas del chasis. Ver §8 del documento. */
    private static final int REJ_X = 113, REJ_Y = 46;
    private static final int CELDA = 37, HUECO = 4, ICONO = 24;
    private static final int COLS = 5, FILAS = 3;

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
        ctx.drawTexture(CHASIS, x0, y0, 0, 0, ANCHO, ALTO, ANCHO, ALTO);

        App bajoElRaton = null;
        for (int i = 0; i < App.TODAS.length; i++) {
            App app = App.TODAS[i];
            int cx = celdaX(i), cy = celdaY(i);
            boolean encima = dentro(ratonX, ratonY, cx, cy);
            if (encima) {
                bajoElRaton = app;
            }

            Identifier fondo = !app.abierta() ? BLOQUEADA : encima ? ENCIMA : REPOSO;
            ctx.drawTexture(fondo, cx, cy, 0, 0, CELDA, CELDA, CELDA, CELDA);
            ctx.drawTexture(app.icono(), cx + (CELDA - ICONO) / 2,
                    cy + (CELDA - ICONO) / 2, 0, 0, ICONO, ICONO, ICONO, ICONO);
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
