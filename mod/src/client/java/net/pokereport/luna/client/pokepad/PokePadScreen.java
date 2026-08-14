package net.pokereport.luna.client.pokepad;

import net.minecraft.client.MinecraftClient;
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

    /** Cuántas veces más grande es la textura. Ver {@code gen_pokepad.py}. */
    private static final int ESCALA = 4;

    /** La rejilla, en las mismas unidades. Ver §8 del documento. */
    private static final int REJ_X = 110, REJ_Y = 45;
    private static final int CELDA = 38, HUECO = 4, ICONO = 24;
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
     * Calcula el tamaño del Pad para que <b>un texel caiga en un píxel de
     * pantalla</b>.
     *
     * <p>Aquí estuvo el error que hacía que todo se viera sucio. Minecraft
     * dibuja las interfaces multiplicadas por el ajuste <i>GUI Scale</i>: a
     * escala 3, un Pad de 345 «píxeles de interfaz» ocupa 1035 píxeles reales.
     * Dándole una textura de 1380 para pintar 1035, el juego la <b>reduce con
     * vecino más próximo</b>, que se salta uno de cada cuatro texeles: las
     * líneas de un píxel se rompen y los biseles quedan con dientes.
     *
     * <p>La cuenta correcta es al revés: si la textura tiene 1380 texeles, hay
     * que pedir <b>1380 ÷ escala</b> píxeles de interfaz, que son exactamente
     * 1380 píxeles reales. Ni se estira ni se reduce.
     *
     * <p>Efecto secundario bueno: el Pad ocupa siempre la misma porción de la
     * pantalla sea cual sea el ajuste del jugador, en vez de encogerse cuando
     * alguien sube la escala.
     */
    @Override
    protected void init() {
        int gs = Math.max(1, (int) MinecraftClient.getInstance()
                .getWindow().getScaleFactor());
        ancho = ANCHO * ESCALA / gs;
        alto = ALTO * ESCALA / gs;

        // En una ventana pequeña no cabe. Se reduce a la mitad, que sigue
        // siendo una proporción entera y por tanto sigue sin dar dientes.
        while ((ancho > width || alto > height) && ancho > ANCHO / 2) {
            ancho /= 2;
            alto /= 2;
        }

        k = ancho / (float) ANCHO;
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
