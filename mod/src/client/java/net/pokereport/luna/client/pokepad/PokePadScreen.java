package net.pokereport.luna.client.pokepad;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
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

    /**
     * El chasis en PIXELES DEL ARTE. Todo lo demás se mide en estas unidades.
     *
     * <p>Los dos números son divisibles entre 1, 2, 3, 4 y 6, que son los
     * valores que puede tomar el ajuste <i>GUI Scale</i>. Dibujado a su tamaño
     * real, un texel cae en un píxel sea cual sea el ajuste del jugador.
     */
    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;

    /** El azul de la pantalla, para morder las esquinas de las celdas. */
    private static final int PANTALLA = 0xFF8A93D0;

    /** La rejilla, en píxeles del arte. */
    // Los da `tools/gen_pokepad.py` al preparar el arte: los imprime al final.
    // No se escriben a ojo, se copian de ahi.
    private static final int REJ_X = 495, REJ_Y = 212;
    private static final int CELDA = 124, HUECO = 24, ICONO = 100;
    private static final int COLS = 5;

    // El borde de la celda y la esquina mordida, tambien en pixeles del arte.
    // A 1 px sobre una celda de 124 no se ve ninguno de los dos.
    private static final int BORDE_GROSOR = 4, MORDIDA = 4;

    // Una celda cerrada se apaga a si misma; el ICONO va siempre a todo color.
    //
    // Antes se apagaba el icono, y tenia sentido cuando la celda llevaba un
    // candado encima: sin apagar, los dos se pisaban. Quitados los candados,
    // apagar el icono solo conseguia que la pantalla entera pareciera muerta
    // --y hoy las quince aplicaciones estan cerradas--. Es el fondo el que
    // debe recular, no el dibujo.
    private static final int CELDA_CERRADA = 0xFF6A72A8;
    private static final int BORDE_CERRADA = 0xFF9AA3C8;

    // Las ranuras del panel izquierdo, MEDIDAS sobre el chasis HD y no puestas
    // a ojo, ni escaladas de las viejas: el chasis nuevo NO es el viejo por
    // cuatro. El alto si (828/207 = 4) pero el ancho no (1380/346 = 3,9884),
    // porque al pasar a HD la proporcion se corrigio a 5:3 exacto.
    //
    //   avatar    x 136-308  y 139-318   (173 x 180)
    //   tarjeta   x 108-342  y 388-580   (235 x 193)
    //   saldo     x 108-273  y 652-705   (166 x  54)
    //
    // La cara va a 168 y no a 173, que es lo que cabe: la cabeza de la skin son
    // 8x8 texeles, y 168 es multiplo de 8. Con 173 cada texel caeria en 21,6
    // pixeles y saldria emborronada justo en lo unico que es del jugador.
    private static final int CARA_X = 138, CARA_Y = 145, CARA_LADO = 168;
    private static final int SALDO_CX = 191, SALDO_CY = 679;

    /** El centro de la placa de arriba, donde va el nombre de la aplicación. */
    private static final int PLACA_CY = 52;

    // Cerrar. ⚠ PROVISIONAL: el chasis HD no trae ranura para el aspa --esa
    // esquina la ocupan las lineas cian-- y el prompt del arte nunca pidio una.
    // Se queda arriba a la derecha, encima del cuerpo, hasta que la fase de los
    // botones decida el sitio de los seis. La textura es 120x96, asi que el
    // hueco mantiene esa proporcion para no deformarla.
    private static final int CERRAR_X = 1240, CERRAR_Y = 24;
    private static final int CERRAR_W = 100, CERRAR_H = 80;

    /** El tamaño real de las PNG de los botones. */
    private static final int BOTON_NAT_W = 120, BOTON_NAT_H = 96;

    private int x0, y0, ancho, alto;
    private float k;

    public PokePadScreen() {
        super(Text.translatable("pokepad.lunaeternal.titulo"));
    }

    private static Identifier tex(String nombre) {
        return Identifier.of("lunaeternal", "textures/gui/pokepad/" + nombre + ".png");
    }

    /**
     * Centra el Pad y decide a qué tamaño se dibuja.
     *
     * <p><b>El objetivo es un texel por píxel real de pantalla.</b> Antes se
     * dibujaba a 346 y se dejaba que Minecraft lo multiplicara por el
     * <i>GUI Scale</i>: eso es ampliar una imagen pequeña, y era exactamente lo
     * que se veía borroso. Ahora el arte mide 1380×828 y se pide ese mismo
     * tamaño en pantalla, así que no hay ampliación que emborrone.
     */
    @Override
    protected void init() {
        // Se pide el saldo cada vez que se abre, en vez de que el servidor lo
        // empuje en cada movimiento de la economia: asi el numero siempre esta
        // fresco y un Pad cerrado no cuesta nada.
        ClientPlayNetworking.send(new Red.PedirSaldo());

        // `k` convierte un pixel del arte en unidades de interfaz, que es en lo
        // que dibuja DrawContext. Dividir por el GUI Scale es lo que cancela la
        // ampliacion del juego y deja el Pad a tamano real.
        double gui = client != null ? client.getWindow().getScaleFactor() : 1;

        // Y no siempre cabe: 1380x828 pixeles REALES no entran en un portatil
        // de 1366x768. Antes de recortarlo se prefiere verlo entero y algo
        // reducido, que en arte suavizado se nota mucho menos que en pixel art.
        //
        // ⚠ SIN MARGEN, A PROPOSITO. Aqui habia un 0,98 "para que no pegue con
        // el borde" y salia caro: con una ventana de 1382x825 --tres pixeles
        // corta-- el Pad se encogia igualmente, y encoger es lo que rompe el
        // dibujo. El margen convertia en borrosas todas las ventanas de entre
        // 1380 y 1409 de ancho a cambio de un hueco que nadie mira. Se prefiere
        // que toque el borde y se vea exacto.
        double cabe = client == null ? 1 : Math.min(
                client.getWindow().getFramebufferWidth() / (double) NAT_ANCHO,
                client.getWindow().getFramebufferHeight() / (double) NAT_ALTO);
        k = (float) (Math.min(1.0, cabe) / gui);

        ancho = Math.round(NAT_ANCHO * k);
        alto = Math.round(NAT_ALTO * k);
        x0 = (width - ancho) / 2;
        y0 = (height - alto) / 2;

        // ¿Ha salido un texel por pixel, o ha habido que encoger?
        //
        // ⚠ ESTO NO ES COSMETICO. Minecraft dibuja las texturas con el vecino
        // mas proximo, y encoger con vecino mas proximo NO suaviza: TIRA filas
        // y columnas enteras. A 0,98 se pierde una de cada cincuenta, y eso se
        // ve como rayas finas cruzando el chasis y como un cerco alrededor de
        // cada icono --justo lo que se vio en el juego la primera vez--.
        //
        // Asi que cuando no cabe a tamano real se pasa a filtrado lineal, que
        // reparte el error en vez de tirar lineas. Encoger arte suavizado con
        // lineal se ve bien; a tamano exacto se deja el vecino mas proximo,
        // que es lo que da el borde limpio.
        boolean exacto = Math.round(ancho * gui) == NAT_ANCHO
                && Math.round(alto * gui) == NAT_ALTO;
        filtrar(!exacto);
    }

    /**
     * Elige cómo se remuestrean nuestras texturas: lineal o vecino más próximo.
     *
     * <p>Se aplica a todas de una vez —chasis, iconos y botón— porque todas se
     * dibujan con el mismo factor. Mezclar filtros dejaría el chasis suave y
     * los iconos con cerco, que es peor que cualquiera de las dos.
     */
    private void filtrar(boolean suave) {
        if (client == null) {
            return;
        }
        client.getTextureManager().getTexture(CHASIS).setFilter(suave, false);
        client.getTextureManager().getTexture(BOTON_CERRAR).setFilter(suave, false);
        for (App app : App.TODAS) {
            client.getTextureManager().getTexture(app.icono()).setFilter(suave, false);
        }
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
                NAT_ANCHO, NAT_ALTO, 0xFFFFFFFF);

        int celda = Math.round(CELDA * k);
        int icono = Math.round(ICONO * k);
        // Nunca por debajo de 1: a GUI Scale alto y ventana pequena, redondear
        // a cero borraria el borde de todas las celdas.
        int grosor = Math.max(1, Math.round(BORDE_GROSOR * k));
        int mordida = Math.max(1, Math.round(MORDIDA * k));

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
            celda(ctx, cx, cy, celda, grosor, mordida, fondo, borde);

            dibujar(ctx, app.icono(), cx + (celda - icono) / 2,
                    cy + (celda - icono) / 2, icono, icono,
                    ICONO, ICONO, 0xFFFFFFFF);
        }

        panelLateral(ctx);

        // La placa de arriba: el nombre de la app senalada, o el del Pad.
        // Centrado en su hueco: el texto se dibuja desde arriba, asi que se
        // sube media linea para que quede a media altura de la placa.
        Text placa = bajoElRaton != null ? bajoElRaton.nombre() : getTitle();
        ctx.drawCenteredTextWithShadow(textRenderer, placa, x0 + ancho / 2,
                y0 + Math.round(PLACA_CY * k) - textRenderer.fontHeight / 2,
                0xFFC8D2F0);

        if (bajoElRaton != null) {
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
        // Centrado en su ranura, en los dos ejes.
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(texto),
                x0 + Math.round(SALDO_CX * k),
                y0 + Math.round(SALDO_CY * k) - textRenderer.fontHeight / 2,
                0xFFFFE12E);

        // Solo se dibuja el aspa de cerrar, que es el unico boton que hoy hace
        // algo. Ni atras, ni adelante, ni inicio, ni ajustes, ni el "+" de la
        // tienda: **todavia no hay a donde ir**, y un boton que no responde
        // ensena a no pulsar los botones. Eso se paga luego, en las pantallas
        // que si funcionen.
        //
        // El "+" ya SI cabria --su ranura en el chasis HD tiene 42 px de
        // interior, no 9-- pero sigue sin llevar a ningun sitio.
        dibujar(ctx, BOTON_CERRAR,
                x0 + Math.round(CERRAR_X * k), y0 + Math.round(CERRAR_Y * k),
                Math.round(CERRAR_W * k), Math.round(CERRAR_H * k),
                BOTON_NAT_W, BOTON_NAT_H, 0xFFFFFFFF);
    }

    /**
     * Una celda: rectángulo plano con las esquinas mordidas.
     *
     * <p>Morder las cuatro esquinas es como se redondea en pixel art. Sin eso,
     * quince rectángulos de esquina viva se ven como una hoja de cálculo.
     */
    private static void celda(DrawContext ctx, int x, int y, int lado,
                              int grosor, int mordida, int fondo, int borde) {
        ctx.fill(x, y, x + lado, y + lado, borde);
        ctx.fill(x + grosor, y + grosor, x + lado - grosor, y + lado - grosor, fondo);
        // Las cuatro esquinas, PINTADAS DEL COLOR DE LA PANTALLA.
        //
        // Antes se rellenaban con 0x00000000 y no hacian nada: `fill` mezcla, y
        // mezclar un color con alfa cero deja el pixel exactamente igual. La
        // esquina no se mordia; solo lo parecia porque a 1 px no se distingue
        // el efecto de su ausencia. A 4 px si se distingue, asi que hay que
        // pintar de verdad --y del azul de la pantalla, que es lo que se veria
        // si el rectangulo no estuviera.
        ctx.fill(x, y, x + mordida, y + mordida, PANTALLA);
        ctx.fill(x + lado - mordida, y, x + lado, y + mordida, PANTALLA);
        ctx.fill(x, y + lado - mordida, x + mordida, y + lado, PANTALLA);
        ctx.fill(x + lado - mordida, y + lado - mordida, x + lado, y + lado, PANTALLA);
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
        // ⚠ LA MEZCLA ALFA HAY QUE ENCENDERLA A MANO. Sin esto, el juego trata
        // CUALQUIER alfa mayor que cero como opaco: un pixel con alfa 1 se
        // dibuja a todo color.
        //
        // Medido sobre una captura del juego, icono a icono: con alfa 0 el
        // dibujado era correcto, y de alfa 1 en adelante el pixel salia con su
        // color CRUDO. Eso es lo que se veia primero como motas de colores
        // --el arte guardaba verde y rojo puros en pixeles invisibles-- y
        // despues, ya limpio el arte, como un cerco negro alrededor de cada
        // icono: el mismo fallo pintando el color del contorno.
        //
        // El arte no tenia la culpa. Cobblemon hace esto mismo en cada dibujo
        // de interfaz (api/gui/GuiUtils.kt), y por eso a ellos no les pasa.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // `natW/natH` son el tamaño REAL de la PNG, y hay que pasarlos: con el
        // tamaño en pantalla, Minecraft tomaría solo esa esquina de la textura
        // en lugar de la textura entera.
        ctx.drawTexture(textura, x, y, ancho, alto, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
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
