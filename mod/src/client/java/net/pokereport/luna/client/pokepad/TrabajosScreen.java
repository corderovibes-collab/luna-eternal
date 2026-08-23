package net.pokereport.luna.client.pokepad;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;
import net.pokereport.luna.progression.Path;

/**
 * TRABAJOS: las cinco Vías del jugador y cuánto le falta en cada una.
 *
 * <h2>Qué enseña, y por qué eso</h2>
 *
 * Una Vía no es una barra: es <b>una promesa</b>. El jugador tiene que poder
 * contestar tres preguntas de un vistazo, y las tres están en cada fila:
 *
 * <ol>
 *   <li><b>Dónde estoy</b> — el nivel en romanos y la barra.</li>
 *   <li><b>Cuánto falta</b> — {@code 1.240 / 3.000}, en números y no en tanto por
 *       ciento. Un 41 % no dice si eso son dos capturas o doscientas.</li>
 *   <li><b>Qué gano</b> — la línea de lo que desbloquea, siempre visible.</li>
 * </ol>
 *
 * <p>⚠ <b>Las cinco salen siempre, incluidas las que están a cero.</b> Enseñar
 * solo las empezadas escondería justo las que el jugador no sabe que existen, que
 * son las que hay que enseñarle. Es la misma razón por la que el PokePad dibuja
 * las quince aplicaciones con candado en vez de ocultarlas.
 *
 * <h2>⚠ El fondo es el del menú principal</h2>
 *
 * Decisión del usuario. No es solo estética: ese chasis trae la ranura de la
 * <b>cara</b> en el panel izquierdo, y aquí se usa para lo que pide el sitio —el
 * jugador y su Vía dominante— en vez de dejarla vacía.
 *
 * <p>⚠⚠ ANTES DE TOCAR ESTA PANTALLA, LEE {@code docs/ui/dibujado.md}. Son ocho
 * reglas y ninguna da error al compilar. Las dos que más pesan aquí: hay que
 * encender {@code RenderSystem.enableBlend()} a mano, y <b>lo plano y lo 3D no se
 * intercalan</b> — todo el 2D primero, un {@code ctx.draw()}, y luego los
 * modelos.
 */
public class TrabajosScreen extends Screen {

    // ⚠ El de COSMETICOS, no el del menu principal. El principal trae la ranura
    //   de la cara --181x182-- y con ella el personaje se queda pequeño y el
    //   resto del panel vacio. Este no tiene ranura, asi que el panel entero es
    //   nuestro y el jugador cabe al doble.
    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");

    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;

    // ---- medidas del arte, las mismas que usa el resto del Pad --------------
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    // ---- el reparto del panel izquierdo, de arriba abajo -------------------
    //
    // Sin ranura de cara, el panel util va de PANEL_Y+NAV_ALTO (142) a
    // PANEL_Y+PANEL_H (762): 620 px de alto y 315 de ancho.
    // ⚠ ESTAS CUATRO CIFRAS SE SUMAN A MANO, ASI QUE AQUI ESTA LA CUENTA. La
    //   primera version solapaba por DOS pixeles --el romano acababa en 586 y el
    //   bloque siguiente empezaba en 584-- y dos pixeles no se ven en una captura:
    //   se ven cuando alguien tiene la Via al maximo y el texto se pisa.
    //
    //     panel util        142 .. 762
    //     previsualizador   152 .. 442
    //     via               446 .. 570   (separador, titulo, nombre, romano)
    //     como se sube      574 .. 658   (separador, titulo, hasta 2 lineas)
    //     nivel total       660 .. 734
    private static final int PREV_Y = 152, PREV_ALTO = 290;
    private static final int VIA_Y = 464;
    private static final int SUBE_Y = 590;
    private static final int TOTAL_Y = 676;
    /** Las lineas finas que separan bloques dentro del panel oscuro. */
    private static final int SEPARADOR = 0xFF3C4250;

    private static final int MARGEN = 14, AIRE = 8;
    private static final int FILAS = 5;

    // ---- paleta, la misma de las otras pantallas ---------------------------
    private static final int FILA_FONDO = 0xFFBFCBE8;
    private static final int FILA_BORDE = 0xFF7C89B4;
    private static final int FILA_ENCIMA = 0xFFFFF0DC;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;
    private static final int BARRA_HUECO = 0xFF8E9AC0;
    private static final int ORO = 0xFFFFD65C;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private List<Red.ViaEstado> vias = List.of();

    public TrabajosScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.trabajos"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        // Se PIDE al abrir, no se empuja. Mismo trato que el saldo y el catálogo
        // de cosméticos: el servidor no manda nada que nadie esté mirando.
        ClientPlayNetworking.send(new Red.PedirTrabajos());
    }

    /**
     * ⚠⚠ HAY QUE DIVIDIR POR LA ESCALA DE LA INTERFAZ, Y ME LO SALTE.
     *
     * <p>{@code getFramebufferWidth()} son PIXELES REALES; {@code width} y
     * {@code height} de una {@code Screen} van en coordenadas ya escaladas por el
     * GUI Scale del jugador. Mezclar los dos sin dividir hace que todo salga
     * multiplicado por esa escala: con GUI Scale 4, cuatro veces más grande. El
     * usuario lo vio así —«se ve demasiado grande»— y en la captura cabía una
     * fila y media.
     *
     * <p>Y el {@code Math.min(1.0, ...)} tampoco sobra: sin él, en una pantalla
     * grande el chasis se AMPLÍA por encima de su tamaño nativo y el arte se ve
     * borroso.
     *
     * <p><b>Esto estaba resuelto en {@code CosmeticosScreen} y lo reescribí de
     * cero en vez de copiarlo.</b> La lección no es la fórmula: es que una
     * pantalla nueva del Pad empieza copiando la geometría de una que ya
     * funciona, no escribiéndola otra vez.
     */
    private void recalcular() {
        double gui = client != null ? client.getWindow().getScaleFactor() : 1;
        int ventanaW = client == null ? NAT_ANCHO : client.getWindow().getFramebufferWidth();
        int ventanaH = client == null ? NAT_ALTO : client.getWindow().getFramebufferHeight();
        double cabe = Math.min(ventanaW / (double) NAT_ANCHO, ventanaH / (double) NAT_ALTO);
        k = (float) (Math.min(1.0, cabe) / gui);
        ancho = Math.round(NAT_ANCHO * k);
        alto = Math.round(NAT_ALTO * k);
        x0 = (width - ancho) / 2;
        y0 = (height - alto) / 2;

        // ⚠ REGLA 3 de dibujado.md: encoger con vecino más próximo TIRA filas y
        // columnas enteras. Solo si el tamaño no sale exacto se pasa a filtrado
        // lineal, que reparte el error en vez de dejar rayas cruzando el chasis.
        boolean exacto = Math.round(ancho * gui) == NAT_ANCHO
                && Math.round(alto * gui) == NAT_ALTO;
        if (client != null) {
            for (Identifier tex : new Identifier[] { CHASIS, ATRAS, CERRAR }) {
                client.getTextureManager().getTexture(tex).setFilter(!exacto, false);
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- geometría, en coordenadas de pantalla -----------------------------

    private int px(int artX) {
        return x0 + Math.round(artX * k);
    }

    private int py(int artY) {
        return y0 + Math.round(artY * k);
    }

    private int pl(int largoArt) {
        return Math.max(1, Math.round(largoArt * k));
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int ratonX, int ratonY, float delta) {
        recalcular();
        renderBackground(ctx, ratonX, ratonY, delta);
        leerDelServidor();

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);

        // ⚠ PRIMERA PASADA: TODO lo plano. Ver el comentario de la clase y
        //   dibujado.md — mezclar 2D y 3D deja el orden al azar y el modelo
        //   titila.
        dibujarNavegacion(ctx, ratonX, ratonY);
        dibujarResumen(ctx);
        dibujarFilas(ctx, ratonX, ratonY);

        ctx.draw();

        // SEGUNDA PASADA: solo el modelo.
        dibujarJugador(ctx);
    }

    private void leerDelServidor() {
        Red.Trabajos t = EstadoCliente.trabajos();
        if (t != null) {
            vias = t.vias();
        }
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        boolean sobreAtras = dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48));
        dibujarTextura(ctx, ATRAS,
                px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 120, 96);
        if (sobreAtras) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2,
                    pl(60) + 4, pl(48) + 4, BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicio"),
                PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false, false);

        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR,
                px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4, BORDE_ENCIMA, 2);
        }
    }

    /**
     * El panel izquierdo: quién eres, en qué destacas y qué hacer para subirlo.
     *
     * <p>⚠ <b>La línea de «cómo se sube» está aquí y no en las filas de la
     * derecha, y es deliberado.</b> Cada Vía tiene dos textos —{@code howToRaise}
     * y {@code unlocks}— y meter los dos en cada fila daría cinco filas de cuatro
     * líneas: un muro que no se lee. Las filas enseñan <b>qué ganas</b>, que es lo
     * que hace comparar; el panel enseña <b>qué hacer</b>, pero solo de la Vía en
     * la que ya vas por delante. Ese es el consejo que sirve.
     *
     * <p>⚠ La <b>Vía dominante</b> se calcula aquí, en el cliente, y es correcto:
     * es ordenar datos que el servidor ya mandó, no un permiso ni un saldo. P6
     * habla de validación económica; nadie gana nada mintiéndose sobre cuál es su
     * vía más alta.
     */
    private void dibujarResumen(DrawContext ctx) {
        Red.ViaEstado mejor = null;
        int suma = 0;
        for (Red.ViaEstado v : vias) {
            suma += v.nivel();
            if (mejor == null || v.nivel() > mejor.nivel()
                    || (v.nivel() == mejor.nivel() && v.xp() > mejor.xp())) {
                mejor = v;
            }
        }
        Path via = mejor == null ? null : porNombre(mejor.id());
        boolean empezado = via != null && (mejor.nivel() > 0 || mejor.xp() > 0);

        separador(ctx, VIA_Y - 18);
        texto(ctx, Text.translatable("pokepad.lunaeternal.via_principal"),
                PANEL_X + PANEL_W / 2, VIA_Y, 20, TEXTO_SUAVE, true, false);

        // Sin datos, o todo a cero: se DICE, no se deja en blanco. Un hueco vacío
        // parece que algo falló; «Ninguna todavía» dice que estás al principio,
        // que es la verdad.
        texto(ctx, empezado ? Text.literal(via.displayName)
                        : Text.translatable("pokepad.lunaeternal.via_ninguna"),
                PANEL_X + PANEL_W / 2, VIA_Y + 26, 32,
                empezado ? color(via) : TEXTO_SUAVE, true, false);

        if (empezado && mejor.nivel() > 0) {
            texto(ctx, Text.literal(Path.roman(mejor.nivel())),
                    PANEL_X + PANEL_W / 2, VIA_Y + 64, 42, ORO, true, false);
        }

        if (empezado) {
            separador(ctx, SUBE_Y - 16);
            texto(ctx, Text.translatable("pokepad.lunaeternal.como_se_sube"),
                    PANEL_X + PANEL_W / 2, SUBE_Y, 18, TEXTO_SUAVE, true, false);
            // ⚠ SE PARTE EN LÍNEAS MIDIENDO, no cortando por un número de
            //   caracteres: «Descubrimientos, biomas y rutas» y «Cría, IV/EV y
            //   linajes» no ocupan lo mismo aunque tengan letras parecidas.
            int y = SUBE_Y + 24;
            for (String linea : partir(via.howToRaise, PANEL_W - 40, 19)) {
                texto(ctx, Text.literal(linea), PANEL_X + PANEL_W / 2, y, 19,
                        0xFFD4DCEC, true, false);
                y += 22;
            }
        }

        // El total, abajo. Es el número que resume una cuenta de un vistazo, y el
        // único que sirve para compararse con otro jugador.
        separador(ctx, TOTAL_Y - 16);
        texto(ctx, Text.translatable("pokepad.lunaeternal.nivel_total"),
                PANEL_X + PANEL_W / 2, TOTAL_Y, 20, TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(suma + " / " + (FILAS * Path.MAX_LEVEL)),
                PANEL_X + PANEL_W / 2, TOTAL_Y + 24, 34, 0xFFFFFFFF, true, false);
    }

    /** Una línea fina de lado a lado del panel. Separa sin meter una caja más. */
    private void separador(DrawContext ctx, int artY) {
        ctx.fill(px(PANEL_X + 28), py(artY), px(PANEL_X + PANEL_W - 28),
                py(artY) + Math.max(1, pl(2)), SEPARADOR);
    }

    /**
     * Parte un texto en líneas que quepan en {@code anchoArte} píxeles del arte.
     *
     * <p>Se mide con el {@code textRenderer} en vez de cortar por un número de
     * caracteres: la fuente de Minecraft no es de ancho fijo, así que contar
     * letras deja unas líneas cortas y otras salidas del panel.
     */
    private List<String> partir(String texto, int anchoArte, int altoArte) {
        List<String> salida = new java.util.ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (anchoArte(Text.literal(prueba), altoArte) > anchoArte && !actual.isEmpty()) {
                salida.add(actual.toString());
                actual = new StringBuilder(palabra);
            } else {
                actual = new StringBuilder(prueba);
            }
        }
        if (!actual.isEmpty()) {
            salida.add(actual.toString());
        }
        return salida;
    }

    /** Ancho de un texto EN PIXELES DEL ARTE, para poder comprobar que cabe. */
    private int anchoArte(Text linea, int alto) {
        return Math.round(textRenderer.getWidth(linea) * alto / (float) textRenderer.fontHeight);
    }

    private void dibujarFilas(DrawContext ctx, int rx, int ry) {
        Path[] todas = Path.values();
        int anchoUtil = PANT_W - 2 * MARGEN;
        int altoUtil = PANT_H - 2 * MARGEN;
        int fh = (altoUtil - (FILAS - 1) * AIRE) / FILAS;

        for (int i = 0; i < todas.length && i < FILAS; i++) {
            Path via = todas[i];
            int ax = PANT_X + MARGEN;
            int ay = PANT_Y + MARGEN + i * (fh + AIRE);
            dibujarFila(ctx, via, buscar(via), ax, ay, anchoUtil, fh, rx, ry);
        }
    }

    private void dibujarFila(DrawContext ctx, Path via, Red.ViaEstado estado,
                             int ax, int ay, int aw, int ah, int rx, int ry) {
        int x = px(ax), y = py(ay), w = pl(aw), h = pl(ah);
        boolean encima = dentro(rx, ry, x, y, w, h);
        int nivel = estado == null ? 0 : estado.nivel();

        ctx.fill(x, y, x + w, y + h, encima ? FILA_ENCIMA : FILA_FONDO);
        marco(ctx, x, y, w, h, encima ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(encima ? 4 : 2)));

        // ⚠ Una PESTAÑA DE COLOR a la izquierda, no la fila entera teñida. Cinco
        //   filas de cinco colores distintos convierten la pantalla en un
        //   semáforo y el texto deja de leerse sobre la mitad de ellos.
        ctx.fill(x, y, x + pl(8), y + h, color(via));

        // El icono de la Vía: un objeto de verdad, dibujado por el juego. Así
        // hereda su propio modelo 3D y no hace falta arte nuevo.
        dibujarObjeto(ctx, new ItemStack(via.icon), ax + 26, ay + ah / 2 - 24, 48);

        int tx = ax + 92;
        texto(ctx, Text.literal(via.displayName), tx, ay + 10, 28, TEXTO_OSCURO, false, true);

        // El nivel en romanos, a la derecha del todo y grande: es el dato que se
        // busca primero.
        Text rom = Text.literal(nivel > 0 ? Path.roman(nivel) : "—");
        texto(ctx, rom, ax + aw - 40, ay + 8, 34, nivel > 0 ? ORO : TEXTO_SUAVE, true, true);

        // La barra.
        int bx = tx, bw = aw - (tx - ax) - 96, by = ay + 46, bh = 16;
        dibujarBarra(ctx, estado, via, bx, by, bw, bh);

        // Qué desbloquea. Siempre visible: es la promesa, y una promesa escondida
        // no motiva a nadie.
        texto(ctx, Text.literal(via.unlocks), tx, ay + ah - 26, 18, TEXTO_SUAVE, false, true);
    }

    private void dibujarBarra(DrawContext ctx, Red.ViaEstado estado, Path via,
                              int ax, int ay, int aw, int ah) {
        int x = px(ax), y = py(ay), w = pl(aw), h = pl(ah);
        ctx.fill(x, y, x + w, y + h, BARRA_HUECO);

        long xp = estado == null ? 0 : estado.xp();
        long falta = estado == null ? 0 : estado.xpSiguiente();
        boolean tope = estado != null && estado.alMaximo();

        // ⚠ Al máximo la barra va LLENA, no vacía. Con `xpSiguiente == 0` una
        //   división daría cero y el nivel V se vería como si estuvieras
        //   empezando, que es lo contrario de lo que ha pasado.
        double frac = tope ? 1.0 : (falta <= 0 ? 0.0 : Math.min(1.0, xp / (double) falta));
        if (frac > 0) {
            ctx.fill(x, y, x + (int) Math.round(w * frac), y + h, color(via));
        }
        marco(ctx, x, y, w, h, FILA_BORDE, Math.max(1, pl(2)));

        Text etiqueta = tope
                ? Text.translatable("pokepad.lunaeternal.via_maxima")
                : Text.literal(String.format("%,d / %,d", xp, falta));
        texto(ctx, etiqueta, ax + aw / 2, ay - 1, 16, TEXTO_OSCURO, true, true);
    }

    /**
     * Segunda pasada: solo el jugador. Ni un rectángulo ni una letra aquí.
     *
     * <p>Ver el comentario de la clase: en cuanto se mezcle algo plano con el
     * modelo, vuelve el titileo que costó cuatro intentos en la pantalla de
     * cosméticos.
     */
    private void dibujarJugador(DrawContext ctx) {
        if (client == null || client.player == null) {
            return;
        }
        // ⚠ La caja es ALTA Y ESTRECHA (299x306), asi que la escala se saca del
        //   LADO MENOR. Con el mayor, el personaje se sale por arriba y por
        //   abajo -- y `drawEntity` recorta, asi que se veria decapitado en vez
        //   de dar un error.
        int x = px(PANEL_X + 8), y = py(PREV_Y);
        int w = pl(PANEL_W - 16), h = pl(PREV_ALTO);
        int cx = x + w / 2, cy = y + h / 2;
        InventoryScreen.drawEntity(ctx, x, y, x + w, y + h,
                Math.round(Math.min(w, h) * 0.40f), 0.0f, cx, cy, client.player);
    }

    // ---- interacción -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) {
            return super.mouseClicked(mx, my, boton);
        }
        int rx = (int) mx, ry = (int) my;
        int cy = py(PANEL_Y + NAV_ALTO / 2);
        if (dentro(rx, ry, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48))) {
            sonar();
            if (client != null) {
                client.setScreen(anterior);
            }
            return true;
        }
        if (dentro(rx, ry, px(PANEL_X + PANEL_W - 18) - pl(80), cy - pl(32), pl(80), pl(64))) {
            sonar();
            close();
            return true;
        }
        return super.mouseClicked(mx, my, boton);
    }

    private void sonar() {
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.0f);
        }
    }

    // ---- utilidades --------------------------------------------------------

    private Red.ViaEstado buscar(Path via) {
        for (Red.ViaEstado v : vias) {
            if (v.id().equals(via.name())) {
                return v;
            }
        }
        return null;
    }

    private static Path porNombre(String nombre) {
        for (Path p : Path.values()) {
            if (p.name().equals(nombre)) {
                return p;
            }
        }
        return null;
    }

    /**
     * El color de una Vía, en ARGB.
     *
     * <p>⚠ Se traduce del código de chat que ya declara {@code Path}, en vez de
     * mantener una segunda tabla de colores aquí. Dos tablas se desincronizan: el
     * día que alguien cambie el color del Explorador en el enum, el chat diría
     * una cosa y esta pantalla otra, y nadie lo notaría hasta verlos juntos.
     */
    private static int color(Path via) {
        char c = via.color.length() >= 2 ? via.color.charAt(1) : 'f';
        return switch (c) {
            case 'a' -> 0xFF55E06A;      // verde  · Explorador
            case 'c' -> 0xFFE05555;      // rojo   · Entrenador
            case 'b' -> 0xFF55D6E0;      // cian   · Coleccionista
            case '6' -> 0xFFE0A845;      // ámbar  · Comerciante
            case 'd' -> 0xFFE067C8;      // rosa   · Criador
            default -> 0xFFB8C2DA;
        };
    }

    /** Un objeto dibujado a un tamaño distinto de 16: `drawItem` no escala solo. */
    private void dibujarObjeto(DrawContext ctx, ItemStack pila, int ax, int ay, int lado) {
        float escala = pl(lado) / 16f;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(px(ax), py(ay), 0);
        m.scale(escala, escala, 1f);
        ctx.drawItem(pila, 0, 0);
        m.pop();
    }

    private void texto(DrawContext ctx, Text linea, int cx, int arriba, int alto,
                       int color, boolean centrado, boolean contorno) {
        float escala = alto * k / textRenderer.fontHeight;
        if (escala <= 0) {
            return;
        }
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x0, y0, 0);
        m.scale(escala, escala, 1f);
        int anchoTexto = textRenderer.getWidth(linea);
        int tx = Math.round(cx * k / escala) - (centrado ? anchoTexto / 2 : 0);
        int ty = Math.round(arriba * k / escala);
        if (contorno) {
            ctx.drawText(textRenderer, linea, tx - 1, ty, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, tx + 1, ty, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, tx, ty - 1, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, tx, ty + 1, TEXTO_CONTORNO, false);
        }
        ctx.drawText(textRenderer, linea, tx, ty, color, false);
        m.pop();
    }

    private static boolean dentro(int rx, int ry, int x, int y, int w, int h) {
        return rx >= x && rx < x + w && ry >= y && ry < y + h;
    }

    private static void marco(DrawContext ctx, int x, int y, int w, int h, int color, int g) {
        ctx.fill(x, y, x + w, y + g, color);
        ctx.fill(x, y + h - g, x + w, y + h, color);
        ctx.fill(x, y, x + g, y + h, color);
        ctx.fill(x + w - g, y, x + w, y + h, color);
    }

    /**
     * ⚠⚠ {@code enableBlend()} A MANO. Sin él, el juego trata cualquier alfa
     * mayor que cero como opaco y el chasis sale con cerco negro. Es la regla 1
     * de dibujado.md y costó una noche entera.
     */
    private static void dibujarTextura(DrawContext ctx, Identifier textura,
                                       int x, int y, int w, int h, int natW, int natH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(textura, x, y, w, h, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
    }
}
