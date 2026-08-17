package net.pokereport.luna.client.pokepad;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
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
    //
    // ⚠⚠ EN EL CHASIS v4 LA PANTALLA PASO DE AZUL OSCURO A CASI BLANCA, Y ESO
    // DA LA VUELTA A TODO LO DE DENTRO. No es un retoque de tono: es que cada
    // decision de contraste apuntaba al reves.
    //
    //   las celdas  eran mas CLARAS que el fondo -> ahora mas OSCURAS
    //   el texto    era BLANCO con contorno negro -> ahora NEGRO con contorno
    //               claro
    //   el resalte  era el ambar del chasis -> ahora el NARANJA FUERTE, el
    //               unico acento del v4 que contrasta sobre claro
    //
    // Todos los valores estan MUESTREADOS del propio arte, no elegidos a ojo.
    private static final int CELDA_FONDO = 0xFFBFCBE8;
    private static final int CELDA_BORDE = 0xFF7C89B4;
    // ⚠ EL RESALTE NO ES BLANCO PURO, Y ES POR UN MOTIVO MEDIDO.
    //
    // Lo fue, y sobre el candado se veia el fallo: su arco es blanco puro
    // (255,255,255), asi que sobre una celda blanca DESAPARECIA. Un tinte
    // calido --a juego con el naranja del bisel, que ya es el acento del
    // resalte-- baja la celda a luma 241 y deja que cualquier dibujo con
    // blancos siga leyendose encima.
    private static final int CELDA_ENCIMA = 0xFFFFF0DC;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;

    /**
     * El chasis en PIXELES DEL ARTE. Todo lo demás se mide en estas unidades.
     *
     * <p>Los dos números son divisibles entre 1, 2, 3, 4 y 6, que son los
     * valores que puede tomar el ajuste <i>GUI Scale</i>. Dibujado a su tamaño
     * real, un texel cae en un píxel sea cual sea el ajuste del jugador.
     */
    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;

    /**
     * Cuánto puede DESBORDAR el Pad la ventana sin perder nada visible.
     *
     * <p>Medido sobre el chasis: sus 12 columnas de cada lado y sus 4 filas de
     * arriba son <b>enteramente transparentes</b> — la esquina redondeada. Lo
     * que caiga fuera dentro de ese margen no se ve porque no hay nada.
     *
     * <p><b>Y esto es lo que arregla el bisel naranja «de baja calidad».</b> El
     * problema no estaba en el arte: el v4 tiene el doble de colores que el v3 y
     * los bordes más suaves. Estaba en que una ventana de 1373 de ancho —siete
     * píxeles corta— obligaba a encoger el Pad un 0,5 %, y encoger enciende el
     * filtrado lineal, que mezcla cada píxel con su vecino. Sobre el bisel del
     * v4, que es una banda naranja enorme con esquinas achaflanadas duras, esa
     * mezcla se ve como escalones sucios; sobre el v3, cuyo acento eran líneas
     * finas, apenas se notaba.
     *
     * <p>Así que cuando lo que falta cabe en el margen transparente, <b>no se
     * encoge</b>: se dibuja a tamaño real y sobresale. Perder tres píxeles de
     * una esquina que ya era transparente no se ve; emborronar el Pad entero,
     * sí.
     */
    private static final int MARGEN_X = 12, MARGEN_Y = 4;

    /** El color de la pantalla, para morder las esquinas de las celdas. */
    private static final int PANTALLA = 0xFFE2EBFD;

    /** La rejilla, en píxeles del arte. */
    // Los da `tools/gen_pokepad.py` al preparar el arte: los imprime al final.
    // No se escriben a ojo, se copian de ahi.
    private static final int REJ_X = 502, REJ_Y = 236;
    private static final int CELDA = 124, HUECO_X = 24, HUECO_Y = 19, ICONO = 100;
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
    private static final int TEXTO_ALTO = 18;

    /**
     * Cuánto SUBE el nombre dentro de su celda.
     *
     * <p>A la mitad de su alto, así que queda montado a caballo sobre la línea
     * de abajo de la celda en vez de colgando en el hueco. Es lo que lo ata a
     * su icono: suelto en medio de dos filas, el ojo duda de a cuál pertenece.
     */
    private static final int TEXTO_SOLAPE = TEXTO_ALTO / 2;

    // ⚠ EL NOMBRE SE INVIERTE CON EL CHASIS v4: OSCURO CON CONTORNO CLARO.
    //
    // Era blanco con contorno negro, y sigue siendo la misma decision del
    // usuario --"que se lea"-- solo que aplicada a un fondo que se ha dado la
    // vuelta: la pantalla del v4 es casi blanca, y blanco sobre blanco no es
    // legible con contorno ni sin el.
    //
    // Lo que NO cambia es que el color es el mismo este la aplicacion abierta o
    // cerrada. Hubo un tono apagado para las cerradas y salio mal por un motivo
    // que no se ve al escribirlo: HOY LAS QUINCE ESTAN CERRADAS, asi que los
    // quince nombres salian atenuados y no se leia ninguno. Atenuar algo solo
    // comunica si hay al lado un hermano encendido con el que compararlo.
    //
    // Lo que distingue una celda cerrada sigue siendo su FONDO, que ya recula
    // por su cuenta.
    private static final int TEXTO_COLOR = 0xFF16203A;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;

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
    private static final int CELDA_CERRADA = 0xFFC2CCE4;
    private static final int BORDE_CERRADA = 0xFF96A1C0;

    // Las tres ranuras del panel izquierdo, MEDIDAS sobre el chasis y no
    // puestas a ojo. Las mide `medir_cajas()` de gen_pokepad.py buscando el gris
    // de su moldura, y el script imprime estos números al terminar.
    //
    //   cara     x 114-322  y 115-324   hueco útil 181 x 182
    //   botones  x  80-356  y 360-595   hueco útil 249 x 208
    //   saldo    x  80-287  y 624-719   hueco útil 180 x  68
    //
    // La cara va a 168 porque la cabeza de la skin son 8x8 texeles y 168 es
    // múltiplo de 8: cada texel cae en 21 píxeles clavados. Con un lado que no
    // lo fuera saldría emborronada justo en lo único que es del jugador.
    private static final int CARA_X = 134, CARA_Y = 136, CARA_LADO = 168;
    private static final int SALDO_CX = 184, SALDO_CY = 672;

    /**
     * Los seis botones, cada uno en su sitio. <b>Ya no viven juntos</b>, y es
     * decisión del usuario sobre el chasis v4:
     *
     * <pre>
     *   atras / adelante        en el BISEL NARANJA de abajo, uno por mitad
     *   cerrar                  arriba a la derecha, junto al logo
     *   inicio / ajustes / mas  apilados en la ranura mediana
     * </pre>
     *
     * <p>Los tres sitios los <b>mide</b> {@code gen_pokepad.py} sobre el chasis
     * y de ahí sale esta tabla; no se escriben a ojo. Y son de dos tamaños
     * porque los manda el sitio: 80 × 64 —dos tercios del arte— en la ranura y
     * en el panel, y <b>45 × 36 en la banda</b>, que mide 37 px de alto medidos
     * y es lo que cabe sin invadir ni la pantalla ni el chasis. Esa escala ya la
     * proponía el propio arte: es el tamaño que tenía la carita verde que había
     * justo ahí.
     *
     * <p>{@code gen_pokepad.py} guarda cada uno <b>ya reducido</b> a su tamaño
     * para que se dibuje 1:1 (regla 2 de {@code docs/ui/dibujado.md}).
     */
    private static final String[] BOTONES =
            {"atras", "adelante", "ajustes", "cerrar"};

    /** x, y, ancho, alto — en píxeles del arte, en el orden de {@link #BOTONES}. */
    private static final int[][] BOTON = {
            { 610, 692, 60, 48},   // atras
            {1040, 692, 60, 48},   // adelante
            {1107,  85, 80, 64},   // ajustes
            {1207,  85, 80, 64},   // cerrar
    };

    private static final int ATRAS = 0, ADELANTE = 1, AJUSTES = 2, CERRAR = 3;

    /**
     * El zócalo del «+», medido sobre el chasis: 48 × 48 en 302,651.
     *
     * <p><b>La cruz se DIBUJA, no es un botón con textura.</b> El chasis ya trae
     * ese zócalo, así que meterle dentro un botón con su propio marco serían dos
     * marcos, uno metido en otro. Lo único que falta ahí es la cruz.
     */
    private static final int CUADRO_X = 302, CUADRO_Y = 651, CUADRO = 48;
    private static final int CRUZ_LARGO = 22, CRUZ_GRUESO = 6;

    /**
     * A dónde lleva el «+» de las LunaCoins.
     *
     * <p><b>Vacío hasta que exista la tienda.</b> Mientras lo esté, el botón se
     * dibuja apagado y responde «todavía no» — igual que las quince celdas y que
     * la segunda página. Un «+» de aspecto normal que no hace nada enseña a no
     * pulsar los botones, y eso se paga en las pantallas que sí funcionen.
     *
     * <p>Cuando la haya, se pone aquí la dirección y ya está: el botón abre
     * <b>la pantalla de confirmación de Minecraft</b>, no el navegador
     * directamente. Es la que avisa de que se va a salir del juego y deja copiar
     * el enlace, y saltársela para «ahorrar un clic» es justo lo que enseña a la
     * gente a confiar en enlaces que aparecen solos.
     */
    private static final String TIENDA = "";

    /**
     * Cuántas páginas tiene la rejilla.
     *
     * <p>La segunda está entera bloqueada: quince candados y «Próximamente».
     * <b>Enseñar que hay más sitio es información</b>; no enseñar nada haría
     * creer que el Pad se acaba en quince.
     */
    private static final int PAGINAS = 2;

    /** Un solo candado repetido, no quince distintos: lo que dice es «aquí no
     *  hay nada todavía», y quince dibujos distintos dirían que hay quince
     *  cosas distintas esperando. */
    private static final Identifier CANDADO = tex("candado");

    private int x0, y0, ancho, alto;
    private float k;

    /** Qué página se está viendo. 0 = las aplicaciones, 1 = «Próximamente». */
    private int pagina;

    /** El orden del jugador, que puede no ser el de fábrica. */
    private App[] orden = App.TODAS.clone();

    /**
     * El modo de ordenar, que es para lo que sirve el botón de ajustes.
     *
     * <p>Funciona a dos clics —coges una y la sueltas sobre otra, y se
     * intercambian— y no arrastrando. No es por comodidad de programación: <b>a
     * dos clics no hay forma de soltar un icono fuera de la rejilla</b> y
     * perderlo, ni de que un tirón del ratón deshaga el orden entero sin
     * querer. Y sigue siendo todo clics (D-012).
     */
    private boolean ordenando;

    /** Qué celda está cogida, o -1. */
    private int cogida = -1;

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
        orden = OrdenPad.leer();

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
        int ventanaW = client == null ? NAT_ANCHO : client.getWindow().getFramebufferWidth();
        int ventanaH = client == null ? NAT_ALTO : client.getWindow().getFramebufferHeight();

        // Lo que falta para que quepa a tamaño real, y si ese sobrante entra en
        // el margen transparente del chasis. Ver MARGEN_X/MARGEN_Y: es lo que
        // evita encoger un 0,5 % y emborronarlo todo por siete píxeles.
        boolean desborda = (NAT_ANCHO - ventanaW) <= MARGEN_X * 2
                && (NAT_ALTO - ventanaH) <= MARGEN_Y * 2;
        double cabe = Math.min(ventanaW / (double) NAT_ANCHO,
                ventanaH / (double) NAT_ALTO);
        k = (float) ((desborda ? 1.0 : Math.min(1.0, cabe)) / gui);

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
        client.getTextureManager().getTexture(CANDADO).setFilter(suave, false);
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
        for (int i = 0; i < orden.length; i++) {
            boolean apps = pagina == 0;
            App app = apps ? orden[i] : null;
            int cx = celdaX(i), cy = celdaY(i);
            boolean encima = ratonX >= cx && ratonX < cx + celda
                    && ratonY >= cy && ratonY < cy + celda;
            if (encima && apps) {
                bajoElRaton = app;
            }

            // La celda cogida se queda resaltada aunque el ratón se haya ido:
            // es la que estás moviendo, y perderla de vista al apartar el ratón
            // haría dudar de si el clic contó.
            boolean marcada = encima || (ordenando && i == cogida);
            int fondo = marcada ? CELDA_ENCIMA
                    : app != null && app.abierta() ? CELDA_FONDO : CELDA_CERRADA;
            int borde = marcada ? BORDE_ENCIMA
                    : app != null && app.abierta() ? CELDA_BORDE : BORDE_CERRADA;
            celda(ctx, cx, cy, celda, grosor, mordida, fondo, borde);

            dibujar(ctx, apps ? app.icono() : CANDADO,
                    cx + (celda - icono) / 2, cy + (celda - icono) / 2,
                    icono, icono, ICONO, ICONO, 0xFFFFFFFF);

            // El nombre, debajo de su icono.
            //
            // Se pide en pixeles del ARTE --no en unidades de interfaz-- para
            // que mida siempre lo mismo respecto al Pad.
            int artX = REJ_X + (i % COLS) * (CELDA + HUECO_X) + CELDA / 2;
            int artY = REJ_Y + (i / COLS) * (CELDA + HUECO_Y) + CELDA - TEXTO_SOLAPE;
            texto(ctx, apps ? app.nombre() : PROXIMAMENTE,
                    artX, artY, TEXTO_ALTO, TEXTO_COLOR);
        }

        panelLateral(ctx, ratonX, ratonY);
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

        // En modo de ordenar, la ayuda dice qué hacer en vez de qué es cada
        // aplicación: ahí no vas a abrir nada, vas a moverlo.
        if (ordenando) {
            ctx.drawTooltip(textRenderer,
                    Text.translatable(cogida < 0 ? "pokepad.lunaeternal.ordenar.coge"
                                                 : "pokepad.lunaeternal.ordenar.suelta"),
                    ratonX, ratonY);
        } else if (bajoElRaton != null) {
            ctx.drawTooltip(textRenderer, bajoElRaton.descripcion(), ratonX, ratonY);
        }
    }

    private static final Text PROXIMAMENTE =
            Text.translatable("pokepad.lunaeternal.proximamente");

    /**
     * Si un botón lleva a algún sitio ahora mismo.
     *
     * <p>Un botón apagado que responde «todavía no» informa; uno de aspecto
     * normal que no hace nada enseña a no pulsar los botones, y eso se paga
     * después en las pantallas que sí funcionen.
     */
    private boolean activo(int i) {
        return switch (i) {
            case ATRAS -> pagina > 0;
            case ADELANTE -> pagina < PAGINAS - 1;
            // Ordenar solo tiene sentido donde hay iconos que ordenar.
            case AJUSTES -> pagina == 0;
            case CERRAR -> true;
            default -> false;
        };
    }

    @Override
    public boolean mouseClicked(double ratonX, double ratonY, int boton) {
        int celda = Math.round(CELDA * k);
        if (boton == 0) {
            for (int i = 0; i < BOTONES.length; i++) {
                int bx = botonX(i), by = botonY(i);
                int w = Math.round(BOTON[i][2] * k), h = Math.round(BOTON[i][3] * k);
                if (ratonX < bx || ratonX >= bx + w
                        || ratonY < by || ratonY >= by + h) {
                    continue;
                }
                sonar(activo(i));
                if (!activo(i)) {
                    return true;
                }
                switch (i) {
                    case CERRAR -> close();
                    // Cambiar de pagina suelta lo que estuvieras moviendo: si
                    // no, cogerias en una pagina y soltarias en otra.
                    case ATRAS -> { pagina--; cogida = -1; }
                    case ADELANTE -> { pagina++; cogida = -1; }
                    case AJUSTES -> {
                        ordenando = !ordenando;
                        cogida = -1;
                        if (!ordenando) {
                            OrdenPad.guardar(orden);
                        }
                    }
                    default -> { }
                }
                return true;
            }
        }
        if (boton == 0 && enCuadro(ratonX, ratonY)) {
            sonar(!TIENDA.isEmpty());
            abrirTienda();
            return true;
        }
        if (boton == 0) {
            for (int i = 0; i < orden.length; i++) {
                int cx = celdaX(i), cy = celdaY(i);
                if (ratonX < cx || ratonX >= cx + celda
                        || ratonY < cy || ratonY >= cy + celda) {
                    continue;
                }
                if (ordenando && pagina == 0) {
                    intercambiar(i);
                } else {
                    sonar(pagina == 0 && orden[i].abierta());
                }
                return true;
            }
        }
        return super.mouseClicked(ratonX, ratonY, boton);
    }

    /** Abre la tienda de LunaCoins, pasando por el aviso de Minecraft. */
    private void abrirTienda() {
        if (client == null || TIENDA.isEmpty()) {
            return;
        }
        client.setScreen(new ConfirmLinkScreen(abrir -> {
            if (abrir) {
                Util.getOperatingSystem().open(TIENDA);
            }
            client.setScreen(this);
        }, TIENDA, false));
    }

    /**
     * Coge una celda, o la suelta sobre otra intercambiándolas.
     *
     * <p>Volver a pulsar la que ya está cogida la <b>suelta</b> en vez de
     * intercambiarla consigo misma: es la forma de arrepentirse sin tener que
     * salir del modo.
     */
    private void intercambiar(int i) {
        if (cogida < 0) {
            cogida = i;
            sonar(true);
            return;
        }
        if (cogida != i) {
            App tmp = orden[cogida];
            orden[cogida] = orden[i];
            orden[i] = tmp;
            // Se guarda en cada movimiento, no al salir del modo: si el juego
            // se cierra a lo bruto, el orden que el jugador ya veía en pantalla
            // es el que se encuentra al volver.
            OrdenPad.guardar(orden);
        }
        cogida = -1;
        sonar(true);
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
    private void panelLateral(DrawContext ctx, int ratonX, int ratonY) {
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

        tarjeta(ctx);
        cruz(ctx, ratonX, ratonY);

        Red.Saldo saldo = EstadoCliente.saldo();
        // Guiones mientras no ha llegado la respuesta: "no lo sé" y "tienes
        // cero" no son lo mismo, y un cero falso en un saldo asusta.
        //
        // AQUÍ ABAJO SOLO VAN LAS LUNACOINS. Decisión del usuario, y tiene
        // sentido: es la única moneda que se compra, así que es la única que
        // necesita un «+» al lado. Los PokéDólares se ganan jugando y no hay
        // nada que pulsar para tener más.
        // 27 y no 18: es TRES veces los 9 que mide la fuente de Minecraft, así
        // que sigue cayendo en píxeles enteros y no se emborrona. Es el único
        // número de la pantalla y a 18 se perdía dentro de su ranura.
        texto(ctx, Text.literal(saldo == null ? "- - -"
                        : String.format("%,d", saldo.reportcoins())),
                SALDO_CX, SALDO_CY - 14, 27, LUNA, true, false);
    }

    /** El azul luna de los LunaCoins, el único saldo que se enseña abajo. */
    private static final int LUNA = 0xFF9FC8FF;

    /**
     * La tarjeta, en píxeles del arte.
     *
     * <p><b>Los nombres van todos en BLANCO</b>, no cada Vía en su color. Cinco
     * colores distintos en cinco líneas seguidas compiten entre sí y convierten
     * una tabla en un semáforo: el color deja de significar algo justo porque
     * todo lo tiene. Lo que separa una fila de otra es su nombre y cuántas
     * estrellas lleva, que es lo que hay que leer.
     */
    private static final int TARJ_X = 100, TARJ_Y = 383, TARJ_FILA = 38;
    private static final int TARJ_DER = 336;
    private static final int TARJ_COLOR = 0xFFFFFFFF;

    /**
     * Las estrellas de nivel.
     *
     * <p><b>Estrellas y no cuadrados.</b> Una estrella dice «tres de cinco» sin
     * leer nada —es lo que usa cualquier juego para un nivel—; un cuadrado hay
     * que aprendérselo. Las dibuja {@code gen_pokepad.py} a cuatro veces su
     * tamaño y las reduce: una estrella de cinco puntas trazada directamente a
     * 15 px sale con las puntas dentadas.
     */
    private static final Identifier ESTRELLA = tex("estrella");
    private static final Identifier ESTRELLA_VACIA = tex("estrella_vacia");
    private static final int ESTRELLA_LADO = 15, ESTRELLA_SEP = 4;
    private static final int VIAS = 5;

    /**
     * La tarjeta de entrenador: las cinco Vías con su nivel en estrellas.
     *
     * <p><b>Va aquí y no un número único porque no hay número único.</b> El
     * proyecto decidió a propósito que no existe un «nivel de jugador»: cinco
     * reputaciones independientes hacen que el progreso sea un <i>perfil</i> y
     * no una cifra, y que dos jugadores con el mismo tiempo jugado sean personas
     * distintas. Eso es lo que este panel enseña, y hasta ahora no se veía en
     * ninguna pantalla.
     *
     * <p>Debajo de la cara a propósito: la cara dice quién eres, la tarjeta qué
     * has hecho y el saldo qué tienes. Los tres huecos del panel se leen de
     * arriba abajo como una sola cosa.
     */
    private void tarjeta(DrawContext ctx) {
        Red.Ficha ficha = EstadoCliente.ficha();
        int lado = Math.max(1, Math.round(ESTRELLA_LADO * k));
        int paso = Math.round((ESTRELLA_LADO + ESTRELLA_SEP) * k);

        for (int i = 0; i < VIAS; i++) {
            int artY = TARJ_Y + i * TARJ_FILA;
            texto(ctx, Text.translatable("pokepad.lunaeternal.via." + i),
                    TARJ_X, artY, TEXTO_ALTO, TARJ_COLOR, false, false);

            // Mientras no llegue la ficha, las cinco salen vacías. Es lo mismo
            // que hacen los guiones del saldo: no se inventa un cero.
            int nivel = ficha == null || i >= ficha.vias().size()
                    ? 0 : ficha.vias().get(i);
            for (int e = 0; e < VIAS; e++) {
                // Alineadas por la DERECHA: así las cinco columnas caen en el
                // mismo sitio aunque los nombres midan distinto, que es lo que
                // deja leerlas como una tabla y no como cinco líneas sueltas.
                int px = x0 + Math.round(TARJ_DER * k) - (VIAS - e) * paso;
                int py = y0 + Math.round((artY + 2) * k);
                dibujar(ctx, e < nivel ? ESTRELLA : ESTRELLA_VACIA,
                        px, py, lado, lado,
                        ESTRELLA_LADO, ESTRELLA_LADO, 0xFFFFFFFF);
            }
        }
    }

    /**
     * La cruz del «+», dentro del zócalo que ya trae el chasis.
     *
     * <p>Dos rectángulos y nada más: el marco lo pone el arte. Se aclara al
     * pasar el ratón y va apagada mientras no haya tienda a la que ir, igual
     * que las quince celdas.
     */
    private void cruz(DrawContext ctx, int ratonX, int ratonY) {
        boolean encima = enCuadro(ratonX, ratonY);
        int color = TIENDA.isEmpty() ? 0xFF5A6480 : (encima ? 0xFFFFFFFF : LUNA);
        int cx = x0 + Math.round((CUADRO_X + CUADRO / 2) * k);
        int cy = y0 + Math.round((CUADRO_Y + CUADRO / 2) * k);
        int largo = Math.max(2, Math.round(CRUZ_LARGO * k)) / 2;
        int grueso = Math.max(1, Math.round(CRUZ_GRUESO * k)) / 2;
        ctx.fill(cx - largo, cy - grueso, cx + largo, cy + grueso, color);
        ctx.fill(cx - grueso, cy - largo, cx + grueso, cy + largo, color);
    }

    private boolean enCuadro(double ratonX, double ratonY) {
        int px = x0 + Math.round(CUADRO_X * k), py = y0 + Math.round(CUADRO_Y * k);
        int lado = Math.round(CUADRO * k);
        return ratonX >= px && ratonX < px + lado
                && ratonY >= py && ratonY < py + lado;
    }

    /** Los seis botones, cada uno donde le toca. */
    private void barra(DrawContext ctx, int ratonX, int ratonY) {
        for (int i = 0; i < BOTONES.length; i++) {
            int bx = botonX(i), by = botonY(i);
            int w = Math.round(BOTON[i][2] * k), h = Math.round(BOTON[i][3] * k);
            boolean encima = ratonX >= bx && ratonX < bx + w
                    && ratonY >= by && ratonY < by + h;
            // Un boton sin destino se apaga; el senalado se aclara. El tinte
            // MULTIPLICA, asi que 0xFF808080 es "a media luz" y no un gris.
            //
            // Y `ajustes` se queda ENCENDIDO mientras dura el modo de ordenar,
            // aunque no tengas el raton encima: es lo unico que dice que estas
            // dentro de un modo y que hay que volver a pulsarlo para salir.
            boolean vivo = activo(i) || (i == AJUSTES && ordenando);
            int tinte = vivo
                    ? (encima || (i == AJUSTES && ordenando) ? 0xFFFFFFFF : 0xFFE0E0E0)
                    : (encima ? 0xFF9A9A9A : 0xFF808080);
            dibujar(ctx, tex("boton_" + BOTONES[i]), bx, by, w, h,
                    BOTON[i][2], BOTON[i][3], tinte);
        }
    }

    /** La esquina de un botón, en unidades de interfaz. */
    private int botonX(int i) {
        return x0 + Math.round(BOTON[i][0] * k);
    }

    private int botonY(int i) {
        return y0 + Math.round(BOTON[i][1] * k);
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
        texto(ctx, linea, cx, arriba, alto, color, true, true);
    }

    /**
     * @param centrado {@code false} para alinear por la izquierda desde {@code cx}
     * @param contorno {@code false} sobre fondo oscuro, donde no hace falta y
     *                 además engorda la letra
     */
    private void texto(DrawContext ctx, net.minecraft.text.Text linea,
                       int cx, int arriba, int alto, int color,
                       boolean centrado, boolean contorno) {
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
        // Y el centrado se hace a mano porque la version "conSombra" no deja
        // apagar la sombra, y aqui hace falta contorno en vez de sombra.
        int px = Math.round(cx * k / escala)
                - (centrado ? textRenderer.getWidth(linea) / 2 : 0);
        int py = Math.round(arriba * k / escala);

        // ⚠ CONTORNO, NO SOMBRA.
        //
        // La sombra de Minecraft es una copia desplazada en diagonal: sobre un
        // fondo oscuro se lee, pero estas celdas son CLARAS y el nombre quedaba
        // gris sobre claro, ilegible.
        //
        // Un contorno cierra la letra sobre CUALQUIER fondo, que es la unica
        // garantia que sirve aqui: la celda cambia de color al pasar el raton, y
        // encima cada aplicacion tendra el suyo algun dia.
        //
        // El color del contorno es el CONTRARIO del texto, y por eso los dos son
        // constantes: en el chasis v4 pasaron de negro/blanco a claro/oscuro de
        // golpe, y si el negro estuviera escrito aqui a mano se habria quedado.
        //
        // EN CRUZ Y NO EN LAS OCHO DIRECCIONES. Con las diagonales el contorno
        // sale grueso y el nombre se emborrona; en cruz cierra igual la letra y
        // pesa la mitad.
        if (contorno) {
            ctx.drawText(textRenderer, linea, px - 1, py, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, px + 1, py, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, px, py - 1, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, px, py + 1, TEXTO_CONTORNO, false);
        }
        ctx.drawText(textRenderer, linea, px, py, color, false);
        m.pop();
    }

    private int celdaX(int i) {
        return x0 + Math.round((REJ_X + (i % COLS) * (CELDA + HUECO_X)) * k);
    }

    private int celdaY(int i) {
        return y0 + Math.round((REJ_Y + (i / COLS) * (CELDA + HUECO_Y)) * k);
    }
}
