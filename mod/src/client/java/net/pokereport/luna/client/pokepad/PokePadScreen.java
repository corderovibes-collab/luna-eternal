package net.pokereport.luna.client.pokepad;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;

/**
 * La pantalla principal del PokePad: el chasis y su rejilla de aplicaciones.
 *
 * <p><b>Las medidas no se eligen aquí, se heredan del arte.</b> Están medidas
 * sobre el chasis real por {@code tools/gen_pokepad.py} y anotadas en
 * {@code docs/ui/prompts-arte-pokepad.md} §8.
 */
public class PokePadScreen extends Screen {

    private static final Identifier CHASIS = tex("pokepad");
    private static final Identifier BOTON_CERRAR = tex("boton_cerrar");
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
    private static final int REJ_X = 124, REJ_Y = 53;
    private static final int CELDA = 31, HUECO = 6, ICONO = 25;
    private static final int COLS = 5;

    // Una celda cerrada se apaga a si misma; el ICONO va siempre a todo color.
    //
    // Antes se apagaba el icono, y tenia sentido cuando la celda llevaba un
    // candado encima: sin apagar, los dos se pisaban. Quitados los candados,
    // apagar el icono solo conseguia que la pantalla entera pareciera muerta
    // --y hoy las quince aplicaciones estan cerradas--. Es el fondo el que
    // debe recular, no el dibujo.
    private static final int CELDA_CERRADA = 0xFF6A72A8;
    private static final int BORDE_CERRADA = 0xFF9AA3C8;

    // Las ranuras del panel izquierdo, MEDIDAS sobre el chasis y no puestas a
    // ojo. La primera version tenia la cara en 30,34 de 40 px y se salia del
    // hueco por arriba y por la izquierda.
    //
    //   avatar    x 30-77   y  31-80    (47 x 49)
    //   tarjeta   x 23-86   y  93-145   (63 x 52)
    //   saldo     x 23-69   y 159-177   (46 x 18)
    private static final int CARA_X = 32, CARA_Y = 33, CARA_LADO = 44;
    private static final int SALDO_CX = 46, SALDO_Y = 164;

    /** Cerrar, arriba a la derecha del chasis. */
    private static final int CERRAR_X = 316, CERRAR_Y = 5, CERRAR_W = 20, CERRAR_H = 16;

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
        // Se pide el saldo cada vez que se abre, en vez de que el servidor lo
        // empuje en cada movimiento de la economia: asi el numero siempre esta
        // fresco y un Pad cerrado no cuesta nada.
        ClientPlayNetworking.send(new Red.PedirSaldo());

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

            int fondo = encima ? CELDA_ENCIMA
                    : app.abierta() ? CELDA_FONDO : CELDA_CERRADA;
            int borde = encima ? BORDE_ENCIMA
                    : app.abierta() ? CELDA_BORDE : BORDE_CERRADA;
            celda(ctx, cx, cy, celda, fondo, borde);

            dibujar(ctx, app.icono(), cx + (celda - icono) / 2,
                    cy + (celda - icono) / 2, icono, icono,
                    ICONO * ESCALA, ICONO * ESCALA, 0xFFFFFFFF);
        }

        panelLateral(ctx);

        // La placa de arriba: el nombre de la app senalada, o el del Pad.
        Text placa = bajoElRaton != null ? bajoElRaton.nombre() : getTitle();
        ctx.drawCenteredTextWithShadow(textRenderer, placa,
                x0 + ancho / 2, y0 + Math.round(11 * k), 0xFFC8D2F0);

        if (bajoElRaton != null) {
            Text nombre = bajoElRaton.abierta()
                    ? bajoElRaton.nombre()
                    : bajoElRaton.nombre().copy().formatted(Formatting.GRAY);
            ctx.drawTooltip(textRenderer, bajoElRaton.descripcion(), ratonX, ratonY);
        }
    }

    @Override
    public boolean mouseClicked(double ratonX, double ratonY, int boton) {
        int celda = Math.round(CELDA * k);
        if (boton == 0 && dentroDe(ratonX, ratonY, CERRAR_X, CERRAR_Y,
                                   CERRAR_W, CERRAR_H)) {
            close();
            return true;
        }
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
     * El panel de la izquierda: quién eres y cuánto tienes.
     *
     * <p>Las tres ranuras ya vienen dibujadas en el chasis; aquí solo se rellena
     * lo que cambia.
     */
    private void panelLateral(DrawContext ctx) {
        if (client == null || client.player == null) {
            return;
        }
        // La cabeza del jugador. Sale de su propia skin, así que no hace falta
        // pedirle nada al servidor.
        int lado = Math.round(CARA_LADO * k);
        ctx.drawTexture(client.player.getSkinTextures().texture(),
                x0 + Math.round(CARA_X * k), y0 + Math.round(CARA_Y * k),
                lado, lado, 8f, 8f, 8, 8, 64, 64);

        // Y el sombrero, la segunda capa. Sin ella, a quien lleve gorra en la
        // skin se le ve la cabeza pelada.
        ctx.drawTexture(client.player.getSkinTextures().texture(),
                x0 + Math.round(CARA_X * k), y0 + Math.round(CARA_Y * k),
                lado, lado, 40f, 8f, 8, 8, 64, 64);

        Red.Saldo saldo = EstadoCliente.saldo();
        // Guiones mientras no ha llegado la respuesta: "no lo sé" y "tienes
        // cero" no son lo mismo, y un cero falso en un saldo asusta.
        String texto = saldo == null ? "- - -" : String.format("%,d", saldo.pokedolares());
        // Centrado en su ranura, no pegado al borde izquierdo.
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(texto),
                x0 + Math.round(SALDO_CX * k), y0 + Math.round(SALDO_Y * k),
                0xFFFFE12E);

        // Solo se dibuja el aspa de cerrar, que es el unico boton que hoy hace
        // algo. Ni atras, ni adelante, ni inicio, ni ajustes, ni el "+" de la
        // tienda: **todavia no hay a donde ir**, y un boton que no responde
        // ensena a no pulsar los botones. Eso se paga luego, en las pantallas
        // que si funcionen.
        //
        // El "+" ademas no cabria: su ranura en el chasis tiene 9 pixeles de
        // interior, y meter ahi una textura de 30x24 la deforma.
        dibujar(ctx, BOTON_CERRAR,
                x0 + Math.round(CERRAR_X * k), y0 + Math.round(CERRAR_Y * k),
                Math.round(CERRAR_W * k), Math.round(CERRAR_H * k), 30, 24, 0xFFFFFFFF);
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

    /** ¿Cae el ratón dentro de un rectángulo dado en unidades del chasis? */
    private boolean dentroDe(double rx, double ry, int cx, int cy, int cw, int ch) {
        int x = x0 + Math.round(cx * k), y = y0 + Math.round(cy * k);
        return rx >= x && rx < x + Math.round(cw * k)
                && ry >= y && ry < y + Math.round(ch * k);
    }

    private int celdaX(int i) {
        return x0 + Math.round((REJ_X + (i % COLS) * (CELDA + HUECO)) * k);
    }

    private int celdaY(int i) {
        return y0 + Math.round((REJ_Y + (i / COLS) * (CELDA + HUECO)) * k);
    }
}
