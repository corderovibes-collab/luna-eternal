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
    private static final int REJ_X = 495, REJ_Y = 194;
    private static final int CELDA = 124, HUECO_X = 24, HUECO_Y = 28, ICONO = 100;
    private static final int COLS = 5;

    /**
     * El nombre de cada aplicación, debajo de su icono.
     *
     * <p><b>El alto va en píxeles del arte, no en unidades de interfaz.</b> Es
     * la diferencia entre un texto que mide siempre lo mismo respecto al Pad y
     * uno que cambia de tamaño según el <i>GUI Scale</i> de cada jugador — con
     * lo segundo, a escala 4 el nombre se sale de su celda.
     *
     * <p>18 es el doble exacto de los 9 que mide la fuente de Minecraft, así
     * que cae en píxeles enteros. Cualquier otro número la emborrona, que es
     * justo lo que costó una noche arreglar en el chasis.
     */
    private static final int TEXTO_ALTO = 18, TEXTO_AIRE = 5;
    private static final int TEXTO_COLOR = 0xFFE8ECFF;
    private static final int TEXTO_CERRADO = 0xFF9AA3C8;

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
    //   avatar    x 141-308  y 141-309   (168 x 169)   ← chasis v2
    //   tarjeta   x 108-342  y 388-580   (235 x 193)
    //   saldo     x 108-273  y 652-705   (166 x  54)
    //
    // La cara va a 168 porque la cabeza de la skin son 8x8 texeles y 168 es
    // multiplo de 8: cada texel cae en 21 pixeles clavados. Con un lado que no
    // lo fuera saldria emborronada justo en lo unico que es del jugador.
    //
    // En el chasis v2 el hueco mide 168 de ancho EXACTO, que es la medida a la
    // que ya dibujabamos: el disenador lo ajusto a ella. Antes eran 173x180 y
    // la cara quedaba descuadrada dentro.
    private static final int CARA_X = 141, CARA_Y = 141, CARA_LADO = 168;
    private static final int SALDO_CX = 191, SALDO_CY = 679;

    /**
     * La barra de botones, en la franja de debajo de la pantalla.
     *
     * <p><b>Es el único sitio del chasis donde caben.</b> Medido: esa franja
     * tiene 981 × 58, la de arriba 52 de alto y el hueco cuadrado del saldo
     * 42 × 42. El arte de los botones llega a 120 × 96 y no entra en ninguna,
     * así que se dibujan a la mitad exacta —60 × 48, que sigue siendo divisible
     * entre 1, 2, 3, 4 y 6— y {@code gen_pokepad.py} los guarda ya reducidos
     * para que se dibujen 1:1.
     *
     * <p>El chasis no trae ranuras para ellos porque el prompt de §3.1 nunca
     * pidió una barra: pidió las tres ranuras de la izquierda, la placa y la
     * pantalla. Si algún día se rehace el chasis, aquí es donde va.
     */
    private static final String[] BOTONES =
            {"atras", "adelante", "inicio", "ajustes", "mas", "cerrar"};
    private static final int BOTON_W = 60, BOTON_H = 48, BOTON_SEP = 24;
    private static final int BARRA_X = 610, BARRA_Y = 715;

    /**
     * Cuál de los seis lleva a algún sitio. Hoy solo cerrar.
     *
     * <p>Los otros cinco son navegación y <b>todavía no hay a dónde ir</b>: no
     * existe ninguna sub-pantalla. Se dibujan igualmente, pero apagados y con
     * el sonido de bloqueado — <b>exactamente como las quince celdas</b>, que
     * también están todas cerradas y se entienden solas.
     *
     * <p>Esa es la diferencia que importa: un botón apagado que responde
     * «todavía no» informa; uno de aspecto normal que no hace nada enseña a no
     * pulsar los botones, y eso se paga después en las pantallas que sí
     * funcionen.
     */
    private static final int CERRAR = 5;

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
        for (String b : BOTONES) {
            client.getTextureManager().getTexture(tex("boton_" + b)).setFilter(suave, false);
        }
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

            // El nombre, debajo de su icono.
            //
            // Se pide en pixeles del ARTE --no en unidades de interfaz-- para
            // que mida siempre lo mismo respecto al Pad. Y apagado si la
            // aplicacion esta cerrada, igual que su celda: si el nombre fuera
            // a todo color, la celda apagada parecerian dos cosas distintas
            // discutiendo.
            int artX = REJ_X + (i % COLS) * (CELDA + HUECO_X) + CELDA / 2;
            int artY = REJ_Y + (i / COLS) * (CELDA + HUECO_Y) + CELDA + TEXTO_AIRE;
            texto(ctx, app.nombre(), artX, artY, TEXTO_ALTO,
                  app.abierta() ? TEXTO_COLOR : TEXTO_CERRADO);
        }

        panelLateral(ctx);
        barra(ctx, ratonX, ratonY);

        // ⚠ LA PLACA DE ARRIBA YA NO LLEVA TEXTO, Y ES A PROPOSITO.
        //
        // Ahi se escribia el nombre de la aplicacion senalada. En el chasis v2
        // el disenador metio el LOGO del servidor en esa placa, asi que el
        // texto le caeria encima -- dos cosas peleandose por el mismo hueco.
        //
        // El nombre no se pierde: sigue en el tooltip al pasar el raton, que
        // es donde ya estaba la descripcion. Queda pendiente decidir donde va
        // de forma fija; hasta entonces, mejor nada que algo pisando el logo.

        if (bajoElRaton != null) {
            ctx.drawTooltip(textRenderer, bajoElRaton.descripcion(), ratonX, ratonY);
        }
    }

    @Override
    public boolean mouseClicked(double ratonX, double ratonY, int boton) {
        int celda = Math.round(CELDA * k);
        if (boton == 0) {
            int w = Math.round(BOTON_W * k), h = Math.round(BOTON_H * k);
            int by = y0 + Math.round(BARRA_Y * k);
            for (int i = 0; i < BOTONES.length; i++) {
                int bx = botonX(i);
                if (ratonX < bx || ratonX >= bx + w
                        || ratonY < by || ratonY >= by + h) {
                    continue;
                }
                sonar(i == CERRAR);
                if (i == CERRAR) {
                    close();
                }
                return true;
            }
        }
        if (boton == 0) {
            for (int i = 0; i < App.TODAS.length; i++) {
                int cx = celdaX(i), cy = celdaY(i);
                if (ratonX < cx || ratonX >= cx + celda
                        || ratonY < cy || ratonY >= cy + celda) {
                    continue;
                }
                sonar(App.TODAS[i].abierta());
                return true;
            }
        }
        return super.mouseClicked(ratonX, ratonY, boton);
    }

    /**
     * El clic suena, lleve a algún sitio o no.
     *
     * <p>Lo bloqueado suena <b>distinto</b>, no en silencio: sin sonido el
     * jugador cree que el clic no se registró y repite. Lo usan las quince
     * celdas y los seis botones, que están en la misma situación.
     */
    private void sonar(boolean lleva) {
        if (client != null && client.player != null) {
            client.player.playSound(lleva
                    ? SoundEvents.UI_BUTTON_CLICK.value()
                    : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 1.0f);
        }
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

    }

    /** La barra de botones de abajo. */
    private void barra(DrawContext ctx, int ratonX, int ratonY) {
        int w = Math.round(BOTON_W * k), h = Math.round(BOTON_H * k);
        for (int i = 0; i < BOTONES.length; i++) {
            int bx = botonX(i), by = y0 + Math.round(BARRA_Y * k);
            boolean encima = ratonX >= bx && ratonX < bx + w
                    && ratonY >= by && ratonY < by + h;
            // Un boton sin destino se apaga; el senalado se aclara. El tinte
            // MULTIPLICA, asi que 0xFF808080 es "a media luz" y no un gris.
            int tinte = i == CERRAR
                    ? (encima ? 0xFFFFFFFF : 0xFFE0E0E0)
                    : (encima ? 0xFF9A9A9A : 0xFF808080);
            dibujar(ctx, tex("boton_" + BOTONES[i]), bx, by, w, h,
                    BOTON_W, BOTON_H, tinte);
        }
    }

    /** La esquina izquierda de un botón, en unidades de interfaz. */
    private int botonX(int i) {
        return x0 + Math.round((BARRA_X + i * (BOTON_W + BOTON_SEP)) * k);
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

    /**
     * Escribe una línea centrada, medida en PIXELES DEL ARTE.
     *
     * <p>La fuente de Minecraft mide 9 y se dibuja en unidades de interfaz, así
     * que sin esto un mismo texto sale de un tamaño distinto para cada jugador
     * según su <i>GUI Scale</i>. Aquí se escala la matriz para que el texto
     * ocupe siempre {@code alto} píxeles del arte, que es lo que hace que
     * encaje bajo la celda pase lo que pase.
     *
     * @param cx    centro horizontal, en píxeles del arte
     * @param arriba borde superior del texto, en píxeles del arte
     */
    private void texto(DrawContext ctx, net.minecraft.text.Text linea,
                       int cx, int arriba, int alto, int color) {
        float escala = alto * k / textRenderer.fontHeight;
        if (escala <= 0) {
            return;
        }
        var m = ctx.getMatrices();
        m.push();
        m.translate(x0, y0, 0);
        m.scale(escala, escala, 1f);
        // Ya dentro de la matriz escalada, las coordenadas van divididas por
        // ella: lo que se pide en pixeles del arte acaba cayendo donde toca.
        ctx.drawCenteredTextWithShadow(textRenderer, linea,
                Math.round(cx * k / escala), Math.round(arriba * k / escala), color);
        m.pop();
    }

    private int celdaX(int i) {
        return x0 + Math.round((REJ_X + (i % COLS) * (CELDA + HUECO_X)) * k);
    }

    private int celdaY(int i) {
        return y0 + Math.round((REJ_Y + (i / COLS) * (CELDA + HUECO_Y)) * k);
    }
}
