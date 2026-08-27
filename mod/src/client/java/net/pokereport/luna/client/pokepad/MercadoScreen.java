package net.pokereport.luna.client.pokepad;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;

/**
 * EL ESCAPARATE DE OBJETOS.
 *
 * <h2>⚠⚠ ESTO ERA UN LIBRO DE ÓRDENES Y YA NO LO ES</h2>
 *
 * El libro —órdenes de compra y de venta cruzándose por precio— es lo correcto
 * para un mercado con mucha gente. Con doce personas <b>no cruza nada</b>: pones
 * una orden y se queda ahí hasta que alguien pase por casualidad. Y la pantalla
 * que hacía falta para manejarlo tenía dos formas de hacer lo mismo —pestañas
 * LIBRO/MIS ÓRDENES/HISTORIAL <i>y</i> botones COMPRAR/VENDER abajo—, que es lo
 * que el usuario describió como <i>«opciones duplicadas, botones duplicados»</i>
 * y <i>«se pierde uno comprando allí»</i>.
 *
 * <p>Un escaparate es peor en teoría y muchísimo mejor de usar: cada uno pone lo
 * suyo con su precio y quien quiera lo compra de una. Y <b>es exactamente el
 * mismo flujo que el GTS de Pokémon</b>, así que las dos mitades del mercado se
 * comportan igual — que era la mitad de lo que costaba entenderlo.
 *
 * <p>⚠ {@code MarketService} no se borra: sigue escrito y probado. Lo que cambia
 * es por dónde entra el jugador.
 *
 * <h2>Tres modos, y solo uno a la vez</h2>
 *
 * <pre>
 *   LISTA   lo que hay a la venta, con buscador
 *   VENDER  tu mochila, para publicar una pila
 *   MIAS    lo que has publicado tú, para retirarlo
 * </pre>
 *
 * <h2>⚠ El cliente no decide nada</h2>
 *
 * Al comprar <b>no se manda el precio</b>: viaja el identificador de la oferta y
 * el servidor cobra mirando su fila (P6).
 *
 * <h2>⚠⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 *
 * La geometría es <b>copia literal</b> de {@code CosmeticosScreen}, y la
 * disposición es la de {@code GtsScreen} a propósito: dos pantallas hermanas que
 * se colocaran distinto serían dos pantallas que aprender.
 */
public class MercadoScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    private static final int MARGEN = 12;

    private static final int FILA = 70;
    private static final int BOT = 34, BOT_SEP = 8;
    private static final int PIE = 34;
    private static final int CONM_W = 100;

    /** Dónde empieza la columna del vendedor. La usan la fila y la cabecera. */
    private static final int COL_VENDEDOR = 320;

    private static final int FILA_FONDO = 0xFFBFCBE8;
    private static final int FILA_BORDE = 0xFF7C89B4;
    private static final int FILA_SEL = 0xFFFFF0DC;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int ORO = 0xFFFFD65C;
    private static final int VERDE = 0xFF2E9E56;
    private static final int ROJO = 0xFFB03A2E;
    private static final int APAGADO = 0xFF6E7899;

    private enum Modo { LISTA, VENDER, MIAS }

    /** Le dice al conmutador cuál de las dos mitades está puesta. */
    private static final boolean ES_POKEMON = false;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private Red.EstadoMercado estado;
    private Modo modo = Modo.LISTA;
    private int elegido = -1;
    private String elegidoItem = "";
    private int pagina = 0;
    private long pulsado;
    private String orden = "NUEVO";
    private Red.OfertaObj confirmando;

    private TextFieldWidget campoBusqueda;
    private TextFieldWidget campoPrecio;

    /**
     * ⚠ Las tres del GTS, y a propósito las mismas. Un mercado donde los Pokémon
     * duran una semana y los objetos tres días sería una regla más que recordar
     * sin ninguna razón detrás.
     */
    private static final int[] DURACIONES = { 24, 48, 168 };
    private int horas = 48;

    /**
     * Cuánto se publica.
     *
     * <p>⚠ Son CUATRO botones y no un campo de texto: teclear la cantidad es una
     * forma más de equivocarse —y de mandar un número absurdo—, y lo que la
     * gente publica de verdad es «una», «un puñado», «un mazo» o «todo».
     */
    private static final int[] CANTIDADES = { 1, 8, 64, -1 };
    private int cantidad = 1;

    public MercadoScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.mercado"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        campoBusqueda = campo(PANT_X + MARGEN, PANT_Y + MARGEN + BOT + 6,
                PANT_W - 2 * MARGEN, 32);
        campoPrecio = campo(PANEL_X + 30, PANEL_Y + 502, PANEL_W - 60, 9);
        addSelectableChild(campoBusqueda);
        addSelectableChild(campoPrecio);
        pedir();
    }

    private TextFieldWidget campo(int ax, int ay, int aw, int max) {
        var c = new TextFieldWidget(textRenderer, px(ax), py(ay), pl(aw),
                Math.max(12, pl(30)), Text.literal(""));
        c.setMaxLength(max);
        return c;
    }

    /** Copia literal de CosmeticosScreen. Ver el comentario de la clase. */
    /**
     * ⚠ Delegado en {@link Escalado} (2026-08-26). Esto era una copia
     *   literal en ONCE pantallas, y para entonces ya había seis
     *   variantes distintas: cada una había envejecido por su lado sin
     *   dar ningún error.
     */
    private void recalcular() {
        var m = Escalado.aplicar(client, width, height, CHASIS, ATRAS, CERRAR);
        k = m.k();
        ancho = m.ancho();
        alto = m.alto();
        x0 = m.x0();
        y0 = m.y0();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int px(int a) {
        return x0 + Math.round(a * k);
    }

    private int py(int a) {
        return y0 + Math.round(a * k);
    }

    private int pl(int a) {
        return Math.max(1, Math.round(a * k));
    }

    private boolean esperando() {
        return pulsado > 0 && System.currentTimeMillis() - pulsado < 1500;
    }

    // ---- datos -------------------------------------------------------------

    /**
     * ⚠⚠ EL TEXTO DE BÚSQUEDA <b>NO VIAJA</b>, y no es un olvido.
     *
     * <p>El servidor solo puede buscar por lo que tiene guardado, que está en
     * inglés (ver {@link #nombreDe}). Un jugador que escriba «cristal» no
     * encontraría «Black Stained Glass Pane» jamás.
     *
     * <p>Así que se filtra <b>aquí</b>, sobre los nombres ya traducidos. Al
     * servidor solo le va la <b>ordenación</b>, que sí sabe hacer.
     *
     * <p>⚠ El precio de esto es que se filtra sobre las 200 ofertas que manda
     * el servidor, no sobre todas. Con un escaparate pequeño da igual; el día
     * que haya miles habrá que mandar el idioma del jugador en el paquete y
     * buscar en el servidor.
     */
    private void pedir() {
        ClientPlayNetworking.send(new Red.PedirMercado("", orden));
    }

    private String texto(TextFieldWidget c) {
        return c == null ? "" : c.getText().trim();
    }

    private long numeroLargo(TextFieldWidget c) {
        try {
            String s = texto(c);
            return s.isEmpty() ? 0 : Math.max(0, Long.parseLong(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<Red.OfertaObj> lista() {
        if (estado == null) {
            return List.of();
        }
        var todas = modo == Modo.MIAS ? estado.mias() : estado.ofertas();
        return filtrar(todas, o -> nombreDe(o.item()).getString(), o -> o.item());
    }

    /** Filtra por el texto del buscador, sobre el nombre YA traducido. */
    private <X> List<X> filtrar(List<X> xs,
                                java.util.function.Function<X, String> nombre,
                                java.util.function.Function<X, String> id) {
        String q = texto(campoBusqueda).toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty()) {
            return xs;
        }
        var salida = new ArrayList<X>();
        for (X x : xs) {
            // Por el nombre traducido O por el identificador: quien sepa que
            // busca `quartz` no tiene por que escribirlo en español.
            if (nombre.apply(x).toLowerCase(java.util.Locale.ROOT).contains(q)
                    || id.apply(x).toLowerCase(java.util.Locale.ROOT).contains(q)) {
                salida.add(x);
            }
        }
        return salida;
    }

    private List<Red.MioObj> mochila() {
        if (estado == null) {
            return List.of();
        }
        return filtrar(estado.disponibles(), m -> nombreDe(m.item()).getString(),
                m -> m.item());
    }

    /**
     * Cuántas filas caben.
     *
     * <p>⚠⚠ SE CALCULA DE {@code listaY()}. Escribirlo a mano es lo que hizo que
     * en el GTS se dibujara una fila encima de la paginación: la lista bajó y la
     * fórmula se quedó contando desde donde estaba antes, <b>sin dar ningún
     * error</b>.
     */
    private int filasCaben() {
        int hueco = (PANT_Y + PANT_H - MARGEN - PIE) - (listaY() + 18);
        return Math.max(1, hueco / FILA);
    }

    private int cuantos() {
        if (estado == null) {
            return 0;
        }
        return modo == Modo.VENDER ? mochila().size() : lista().size();
    }

    private int paginas() {
        return Math.max(1, (cuantos() + filasCaben() - 1) / filasCaben());
    }

    private Red.OfertaObj seleccionada() {
        var l = lista();
        return elegido >= 0 && elegido < l.size() ? l.get(elegido) : null;
    }

    private Red.MioObj mioSeleccionado() {
        if (estado == null) {
            return null;
        }
        for (var m : mochila()) {
            if (m.item().equals(elegidoItem)) {
                return m;
            }
        }
        return null;
    }

    /** Cuántas unidades se van a publicar de verdad. */
    private int cantidadReal(Red.MioObj m) {
        if (m == null) {
            return 0;
        }
        return cantidad < 0 ? m.cantidad() : Math.min(cantidad, m.cantidad());
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        var nuevo = EstadoCliente.mercado();
        if (nuevo != null && nuevo != estado) {
            estado = nuevo;
            pulsado = 0;
            if (elegido >= lista().size()) {
                elegido = -1;
            }
        }

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanel(ctx, rx, ry);
        dibujarBarra(ctx, rx, ry);
        if (modo == Modo.VENDER) {
            dibujarMochila(ctx, rx, ry, false);
        } else {
            dibujarLista(ctx, rx, ry, false);
        }

        // ⚠ DOS PASADAS: todo el 2D, `ctx.draw()`, y solo entonces los objetos.
        //   `drawItem` va por su propio lote y NO respeta el orden de la
        //   interfaz: mezclado, el 2D se pinta encima de los iconos. Regla 3 de
        //   dibujado.md, la misma que el 3D del GTS.
        ctx.draw();
        if (confirmando == null) {
            if (modo == Modo.VENDER) {
                dibujarMochila(ctx, rx, ry, true);
            } else {
                dibujarLista(ctx, rx, ry, true);
            }
            dibujarRetrato(ctx);
        } else {
            dibujarConfirmacion(ctx, rx, ry);
        }
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 120, 96);
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2, pl(60) + 4, pl(48) + 4,
                    BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicio"),
                PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false, false);

        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4,
                    BORDE_ENCIMA, 2);
        }
    }

    // ---- el panel de la izquierda -----------------------------------------

    private static final int RET_X = PANEL_X + 24, RET_Y = PANEL_Y + NAV_ALTO + 4;
    private static final int RET_W = PANEL_W - 48;

    /**
     * ⚠ El retrato mide distinto al publicar. Publicar necesita cantidad, precio
     * y duración debajo, y con el retrato grande <b>los botones de duración se
     * quedaban enteros por debajo del de PUBLICAR</b>: invisibles y sin dar
     * ningún error. Es el mismo fallo que ya salió en el GTS.
     */
    private int retAlto() {
        return modo == Modo.VENDER ? 190 : 220;
    }

    private ItemStack pila(String id) {
        // ⚠ Un identificador que no exista devuelve AIRE, y `drawItem` con aire
        //   no dibuja NADA: ni hueco, ni cubo morado. Se sustituye por algo
        //   visible — un artículo invisible parecería que el mercado está roto.
        var item = Registries.ITEM.get(Identifier.tryParse(id));
        var p = new ItemStack(item);
        return p.isEmpty() ? new ItemStack(net.minecraft.item.Items.BARRIER) : p;
    }

    /**
     * El nombre de un objeto <b>en el idioma del jugador</b>.
     *
     * <h2>⚠⚠⚠ NO SE USA EL NOMBRE QUE MANDA EL SERVIDOR</h2>
     *
     * El servidor lo resuelve con {@code getName().getString()} y <b>ahí solo
     * existe {@code en_us}</b>: no sabe en qué idioma juega cada uno, ni puede
     * saberlo. Por eso salía «Black Stained Glass Pane» con el cliente en
     * español — y con los 607 nombres de {@code lunaneon} ya traducidos.
     *
     * <p>{@code getName()} devuelve un {@code Text} <b>traducible</b>, y aquí
     * sí hay idioma. La regla: <b>el servidor manda el identificador, el
     * cliente pone el nombre.</b>
     *
     * <p>⚠ Y arregla también lo ya publicado: el nombre inglés sigue guardado
     * en la fila, pero <b>ya no se enseña</b>.
     */
    private Text nombreDe(String itemId) {
        return pila(itemId).getName();
    }

    private String itemElegido() {
        if (modo == Modo.VENDER) {
            var m = mioSeleccionado();
            return m == null ? null : m.item();
        }
        var o = seleccionada();
        return o == null ? null : o.item();
    }

    /** El objeto, grande, en su marco. Va en la segunda pasada. */
    private void dibujarRetrato(DrawContext ctx) {
        String id = itemElegido();
        if (id == null) {
            return;
        }
        int lado = Math.min(RET_W, retAlto()) - 60;
        objeto(ctx, pila(id), RET_X + (RET_W - lado) / 2,
                RET_Y + (retAlto() - lado) / 2, lado);
    }

    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        // El marco del retrato siempre, tenga algo dentro o no: un hueco vacío
        // dice «aquí va algo» y una nada dice «esta pantalla está a medias».
        ctx.fill(px(RET_X), py(RET_Y), px(RET_X + RET_W), py(RET_Y + retAlto()),
                0xFF12161F);
        marco(ctx, px(RET_X), py(RET_Y), pl(RET_W), pl(retAlto()),
                0xFF39415C, Math.max(1, pl(2)));

        switch (modo) {
            case VENDER -> panelVender(ctx, rx, ry);
            case MIAS -> panelMia(ctx, rx, ry);
            default -> panelComprar(ctx, rx, ry);
        }
    }

    private void aviso(DrawContext ctx, String clave) {
        int y = RET_Y + retAlto() + 40;
        for (String linea : partir(Text.translatable(clave).getString(), RET_W, 15)) {
            texto(ctx, Text.literal(linea), PANEL_X + PANEL_W / 2, y, 15,
                    TEXTO_SUAVE, true, false);
            y += 20;
        }
    }

    private void panelComprar(DrawContext ctx, int rx, int ry) {
        var o = seleccionada();
        if (o == null) {
            aviso(ctx, "pokepad.lunaeternal.gts.elige");
            return;
        }
        int y = RET_Y + retAlto() + 12;
        y = tituloOferta(ctx, o, y);

        separador(ctx, y);
        y += 14;
        texto(ctx, Text.literal(String.format("%,d", o.precio())),
                PANEL_X + PANEL_W / 2, y, 34, ORO, true, true);
        y += 42;
        texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.por_unidad",
                        String.format("%,d", o.porUnidad())),
                PANEL_X + PANEL_W / 2, y, 14, TEXTO_SUAVE, true, false);
        y += 26;
        separador(ctx, y);
        y += 12;
        fila(ctx, Text.translatable("pokepad.lunaeternal.gts.f_vendedor"),
                Text.literal(o.vendedor()), y);
        y += 22;
        fila(ctx, Text.translatable("pokepad.lunaeternal.gts.col_expira"),
                Text.literal(queda(o.expira())), y);

        long saldo = estado == null ? 0 : estado.saldo();
        boolean puede = saldo >= o.precio() && !esperando();
        boton(ctx, rx, ry, PANEL_X + 30, PANEL_Y + PANEL_H - 72, PANEL_W - 60, 56,
                Text.translatable("pokepad.lunaeternal.gts.comprar"), puede, VERDE);
    }

    private void panelMia(DrawContext ctx, int rx, int ry) {
        var o = seleccionada();
        if (o == null) {
            aviso(ctx, "pokepad.lunaeternal.gts.elige_mia");
            return;
        }
        int y = RET_Y + retAlto() + 12;
        y = tituloOferta(ctx, o, y);

        separador(ctx, y);
        y += 14;
        texto(ctx, Text.literal(String.format("%,d", o.precio())),
                PANEL_X + PANEL_W / 2, y, 34, ORO, true, true);
        y += 42;
        texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.por_unidad",
                        String.format("%,d", o.porUnidad())),
                PANEL_X + PANEL_W / 2, y, 14, TEXTO_SUAVE, true, false);
        y += 26;
        separador(ctx, y);
        y += 12;
        fila(ctx, Text.translatable("pokepad.lunaeternal.gts.col_expira"),
                Text.literal(queda(o.expira())), y);
        y += 22;
        // ⚠ Se dice ANTES de pulsar: la tasa no vuelve. Un jugador que retire
        //   creyendo que recupera todo y no lo haga, no vuelve a publicar.
        for (String linea : partir(
                Text.translatable("pokepad.lunaeternal.mercado.tasa_perdida").getString(),
                RET_W, 13)) {
            texto(ctx, Text.literal(linea), PANEL_X + PANEL_W / 2, y, 13,
                    TEXTO_SUAVE, true, false);
            y += 17;
        }

        boton(ctx, rx, ry, PANEL_X + 30, PANEL_Y + PANEL_H - 72, PANEL_W - 60, 56,
                Text.translatable("pokepad.lunaeternal.gts.retirar"),
                !esperando(), ROJO);
    }

    /** Nombre y cantidad, que es lo mismo en los tres modos. */
    private int tituloOferta(DrawContext ctx, Red.OfertaObj o, int y) {
        for (Text linea : partirLim(nombreDe(o.item()).getString(), RET_W, 24, 2)) {
            texto(ctx, linea, PANEL_X + PANEL_W / 2, y, 24,
                    0xFFFFFFFF, true, false);
            y += 28;
        }
        texto(ctx, Text.literal("x" + o.cantidad()), PANEL_X + PANEL_W / 2, y, 16,
                TEXTO_SUAVE, true, false);
        return y + 26;
    }

    private void panelVender(DrawContext ctx, int rx, int ry) {
        var m = mioSeleccionado();
        if (m == null) {
            aviso(ctx, "pokepad.lunaeternal.mercado.elige_tuyo");
            return;
        }
        int y = RET_Y + retAlto() + 8;
        // ⚠ DOS líneas como mucho: debajo van cantidad, precio y duración en
        //   posiciones fijas, así que un nombre de cuatro líneas se metería
        //   dentro de ellas.
        for (Text linea : partirLim(nombreDe(m.item()).getString(), RET_W, 22, 2)) {
            texto(ctx, linea, PANEL_X + PANEL_W / 2, y, 22,
                    0xFFFFFFFF, true, false);
            y += 26;
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.tienda.tienes", m.cantidad()),
                PANEL_X + PANEL_W / 2, y, 14, TEXTO_SUAVE, true, false);

        // --- cuántas
        texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.cantidad"),
                PANEL_X + 30, PANEL_Y + 404, 13, TEXTO_SUAVE, false, false);
        int anchoBot = (PANEL_W - 60 - 3 * 6) / 4;
        for (int i = 0; i < CANTIDADES.length; i++) {
            int bx = PANEL_X + 30 + i * (anchoBot + 6);
            boolean vale = CANTIDADES[i] < 0 || CANTIDADES[i] <= m.cantidad();
            botonPeq(ctx, rx, ry, bx, PANEL_Y + 420, anchoBot, 32,
                    CANTIDADES[i] < 0
                            ? Text.translatable("pokepad.lunaeternal.mercado.todo")
                            : Text.literal("x" + CANTIDADES[i]),
                    vale, cantidad == CANTIDADES[i]);
        }
        texto(ctx, Text.literal("x" + cantidadReal(m)), PANEL_X + PANEL_W / 2,
                PANEL_Y + 460, 22, 0xFFFFFFFF, true, true);

        // --- precio
        texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.precio_total"),
                PANEL_X + 30, PANEL_Y + 486, 13, TEXTO_SUAVE, false, false);
        campoPrecio.render(ctx, rx, ry, 0);

        long precio = numeroLargo(campoPrecio);
        int cuantas = Math.max(1, cantidadReal(m));
        if (precio > 0) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.por_unidad",
                            String.format("%,d", precio / cuantas)),
                    PANEL_X + 30, PANEL_Y + 540, 13, TEXTO_SUAVE, false, false);
        }

        // --- duración
        texto(ctx, Text.translatable("pokepad.lunaeternal.gts.duracion"),
                PANEL_X + 30, PANEL_Y + 562, 13, TEXTO_SUAVE, false, false);
        int anchoDur = (PANEL_W - 60 - 2 * 6) / 3;
        for (int i = 0; i < DURACIONES.length; i++) {
            int bx = PANEL_X + 30 + i * (anchoDur + 6);
            botonPeq(ctx, rx, ry, bx, PANEL_Y + 578, anchoDur, 32,
                    Text.translatable("pokepad.lunaeternal.gts.dur_" + DURACIONES[i]),
                    true, horas == DURACIONES[i]);
        }

        boolean puede = precio > 0 && cantidadReal(m) > 0 && !esperando();
        boton(ctx, rx, ry, PANEL_X + 30, PANEL_Y + PANEL_H - 72, PANEL_W - 60, 56,
                Text.translatable("pokepad.lunaeternal.gts.publicar"), puede, VERDE);
    }

    private void fila(DrawContext ctx, Text etiqueta, Text valor, int y) {
        texto(ctx, etiqueta, PANEL_X + 30, y, 14, TEXTO_SUAVE, false, false);
        textoDer(ctx, valor, PANEL_X + PANEL_W - 30, y, 14, 0xFFE8EDF8, false);
    }

    // ---- la barra de arriba ------------------------------------------------

    private record Boton(String id, String etiqueta) {}

    /**
     * ⚠ CUATRO Y NO SEIS. El GTS tiene filtros y chollos porque un Pokémon tiene
     * IVs, naturaleza y una tasación; una pila de veinte piedras <b>no tiene
     * nada que filtrar</b>. Copiar los seis dejaría dos botones que no hacen
     * nada, que es justo de lo que se quejó el usuario.
     */
    private static final Boton[] BARRA = {
        new Boton("buscar", "pokepad.lunaeternal.gts.b_buscar"),
        new Boton("refrescar", "pokepad.lunaeternal.gts.b_refrescar"),
        new Boton("vender", "pokepad.lunaeternal.mercado.b_vender"),
        new Boton("mias", "pokepad.lunaeternal.gts.b_mias"),
    };

    private int botonX(int i) {
        int total = BARRA.length * BOT + (BARRA.length - 1) * BOT_SEP;
        return PANT_X + PANT_W - MARGEN - total + i * (BOT + BOT_SEP);
    }

    private void dibujarBarra(DrawContext ctx, int rx, int ry) {
        dibujarConmutador(ctx, rx, ry);
        campoBusqueda.render(ctx, rx, ry, 0);
        if (texto(campoBusqueda).isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.buscar_pista"),
                    PANT_X + MARGEN + 8, PANT_Y + MARGEN + BOT + 14, 14,
                    0xFF8892AC, false, false);
        }

        String encimaDe = null;
        for (int i = 0; i < BARRA.length; i++) {
            var b = BARRA[i];
            int ax = botonX(i), ay = PANT_Y + MARGEN;
            boolean marcado = switch (b.id()) {
                case "vender" -> modo == Modo.VENDER;
                case "mias" -> modo == Modo.MIAS;
                default -> false;
            };
            boolean encima = dentro(rx, ry, px(ax), py(ay), pl(BOT), pl(BOT));
            if (encima) {
                encimaDe = b.etiqueta();
            }
            ctx.fill(px(ax), py(ay), px(ax + BOT), py(ay + BOT),
                    marcado ? BORDE_ENCIMA : (encima ? 0xFF5E86D8 : 0xFF3A4560));
            marco(ctx, px(ax), py(ay), pl(BOT), pl(BOT),
                    marcado ? 0xFFFFC46B : 0xFF20283C, Math.max(1, pl(2)));

            int cx = px(ax + BOT / 2), cy = py(ay + BOT / 2);
            int lado = pl(BOT - 14);
            int color = marcado ? 0xFF2A1C00 : 0xFFFFFFFF;
            switch (b.id()) {
                case "buscar" -> Iconos.lupa(ctx, cx, cy, lado, color);
                case "refrescar" -> Iconos.refrescar(ctx, cx, cy, lado, color);
                case "vender" -> Iconos.mas(ctx, cx, cy, lado, color);
                case "mias" -> Iconos.lista(ctx, cx, cy, lado, color);
                default -> { }
            }
        }
        if (encimaDe != null) {
            // Sobre el buscador, que es la única franja libre de esa fila.
            textoDer(ctx, Text.translatable(encimaDe),
                    PANT_X + PANT_W - MARGEN - 8, PANT_Y + MARGEN + BOT + 14, 13,
                    ORO, true);
        }
    }

    /**
     * El conmutador POKÉMON / OBJETOS. <b>A la derecha</b>, donde lo pidió el
     * usuario, y en la misma posición que en el GTS.
     *
     * <p>⚠ Dos pestañas y no un icono: un icono es un botón que hace algo, una
     * pestaña es <b>un sitio donde estás</b>.
     */
    private void dibujarConmutador(DrawContext ctx, int rx, int ry) {
        for (int i = 0; i < 2; i++) {
            boolean act = (i == 0) == ES_POKEMON;
            int bx = PANT_X + MARGEN + i * (CONM_W + 4);
            boolean enc = dentro(rx, ry, px(bx), py(PANT_Y + MARGEN), pl(CONM_W), pl(BOT));
            ctx.fill(px(bx), py(PANT_Y + MARGEN), px(bx + CONM_W),
                    py(PANT_Y + MARGEN + BOT),
                    act ? BORDE_ENCIMA : (enc ? 0xFF4F6FB0 : 0xFF2A3145));
            marco(ctx, px(bx), py(PANT_Y + MARGEN), pl(CONM_W), pl(BOT),
                    act ? 0xFFFFC46B : 0xFF20283C, Math.max(1, pl(2)));
            texto(ctx, Text.translatable(i == 0
                            ? "pokepad.lunaeternal.gts.c_pokemon"
                            : "pokepad.lunaeternal.gts.c_objetos"),
                    bx + CONM_W / 2, PANT_Y + MARGEN + 10, 14,
                    act ? 0xFF2A1C00 : 0xFFC9D2E6, true, false);
        }
    }

    private boolean clicConmutador(int rx, int ry) {
        for (int i = 0; i < 2; i++) {
            int bx = PANT_X + MARGEN + i * (CONM_W + 4);
            if (dentro(rx, ry, px(bx), py(PANT_Y + MARGEN), pl(CONM_W), pl(BOT))) {
                if ((i == 0) != ES_POKEMON && client != null) {
                    sonar();
                    client.setScreen(new GtsScreen(anterior));
                }
                return true;
            }
        }
        return false;
    }

    // ---- la lista ----------------------------------------------------------

    /** ⚠ Misma cuenta que el GTS: fila 1 (34) + 6 + buscador (28) + cabecera. */
    private int listaY() {
        return PANT_Y + MARGEN + BOT + 6 + 28 + 34;
    }

    private record Columna(String etiqueta, int x, int ancho, String asc, String desc) {}

    // ⚠ Las x coinciden con las de la fila. Si se separaran, la cabecera diría
    //   que ordena por una columna y la flecha señalaría a otra.
    private static final Columna[] COLUMNAS = {
        new Columna("pokepad.lunaeternal.mercado.col_objeto", 74, 220, "NUEVO", "NUEVO"),
        new Columna("pokepad.lunaeternal.mercado.col_unidad", COL_VENDEDOR, 150,
                "UNIDAD_ASC", "UNIDAD_ASC"),
        new Columna("pokepad.lunaeternal.gts.col_precio", 480, 140,
                "PRECIO_ASC", "PRECIO_DESC"),
    };

    private void dibujarCabecera(DrawContext ctx, int rx, int ry) {
        int hy = listaY();
        for (var c : COLUMNAS) {
            int ax = PANT_X + MARGEN + c.x();
            boolean activa = orden.equals(c.asc()) || orden.equals(c.desc());
            boolean encima = dentro(rx, ry, px(ax - 4), py(hy - 3), pl(c.ancho()), pl(20));
            if (activa || encima) {
                // ⚠ La activa va OSCURA con el texto claro. Al revés —naranja
                //   claro con texto oro— no se lee: son el mismo tono.
                ctx.fill(px(ax - 4), py(hy - 3), px(ax - 4 + c.ancho()), py(hy + 17),
                        activa ? 0xCC1E2438 : 0x22FFFFFF);
            }
            String flecha = !activa ? " —" : orden.equals(c.asc()) ? " ▲" : " ▼";
            texto(ctx, Text.translatable(c.etiqueta()).copy()
                            .append(Text.literal(flecha)),
                    ax, hy, 14, activa ? ORO : TEXTO_SUAVE, false, false);
        }
    }

    /**
     * Las ofertas.
     *
     * <p>⚠ Dos pasadas: {@code objetos=false} pinta cajas y texto, y
     * {@code objetos=true} solo los iconos de los objetos, después de
     * {@code ctx.draw()}.
     */
    private void dibujarLista(DrawContext ctx, int rx, int ry, boolean objetos) {
        if (!objetos) {
            dibujarCabecera(ctx, rx, ry);
        }
        var l = lista();
        if (l.isEmpty()) {
            if (!objetos) {
                texto(ctx, Text.translatable(modo == Modo.MIAS
                                ? "pokepad.lunaeternal.gts.sin_mias"
                                : "pokepad.lunaeternal.mercado.sin_ofertas"),
                        PANT_X + PANT_W / 2, listaY() + 60, 16, TEXTO_SUAVE, true, false);
            }
            return;
        }

        int desde = pagina * filasCaben();
        int aw = PANT_W - 2 * MARGEN;
        for (int n = 0; n < filasCaben() && desde + n < l.size(); n++) {
            var o = l.get(desde + n);
            int y = listaY() + 18 + n * FILA;
            int ax = PANT_X + MARGEN;

            if (objetos) {
                objeto(ctx, pila(o.item()), ax + 10, y + 12, 40);
                continue;
            }

            boolean sel = desde + n == elegido;
            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA - 6));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA - 6),
                    sel ? FILA_SEL : (encima ? 0xFFD3DDF3 : FILA_FONDO));
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA - 6),
                    sel ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(2)));

            // ⚠ 240 es lo que hay hasta la columna del vendedor (320 - 70 - 10).
            //   No se escribe suelto: si el vendedor se moviera, esto tendría
            //   que moverse con él.
            texto(ctx, recortar(nombreDe(o.item()).getString(),
                            COL_VENDEDOR - 70 - 10, 20),
                    ax + 70, y + 10, 20, TEXTO_OSCURO, false, false);
            texto(ctx, Text.literal("x" + o.cantidad()), ax + 70, y + 36, 15,
                    TEXTO_SUAVE, false, false);
            texto(ctx, recortar(o.vendedor(), 140, 14), ax + COL_VENDEDOR, y + 12, 14,
                    TEXTO_SUAVE, false, false);
            texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.por_unidad",
                            String.format("%,d", o.porUnidad())),
                    ax + COL_VENDEDOR, y + 36, 14, TEXTO_SUAVE, false, false);

            int precioDer = ax + aw - 150;
            textoDer(ctx, Text.literal(String.format("%,d", o.precio())),
                    precioDer, y + 10, 21, 0xFF8A6A00, true);
            textoDer(ctx, Text.literal(queda(o.expira())), precioDer, y + 40, 12,
                    TEXTO_SUAVE, true);

            botonPeq(ctx, rx, ry, ax + aw - 132, y + 16, 124, 32,
                    Text.translatable(modo == Modo.MIAS
                            ? "pokepad.lunaeternal.gts.retirar"
                            : "pokepad.lunaeternal.gts.comprar"),
                    !esperando(), false);
        }
        if (!objetos) {
            dibujarContador(ctx);
            dibujarPaginacion(ctx, rx, ry);
        }
    }

    /** Tu mochila, para elegir qué publicar. */
    private void dibujarMochila(DrawContext ctx, int rx, int ry, boolean objetos) {
        var disp = mochila();
        if (disp.isEmpty()) {
            if (!objetos) {
                texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.mochila_vacia"),
                        PANT_X + PANT_W / 2, listaY() + 60, 16, TEXTO_SUAVE, true, false);
            }
            return;
        }
        int desde = pagina * filasCaben();
        int aw = PANT_W - 2 * MARGEN;
        for (int n = 0; n < filasCaben() && desde + n < disp.size(); n++) {
            var m = disp.get(desde + n);
            int y = listaY() + 18 + n * FILA;
            int ax = PANT_X + MARGEN;

            if (objetos) {
                objeto(ctx, pila(m.item()), ax + 10, y + 12, 40);
                continue;
            }

            boolean sel = m.item().equals(elegidoItem);
            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA - 6));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA - 6),
                    sel ? FILA_SEL : (encima ? 0xFFD3DDF3 : FILA_FONDO));
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA - 6),
                    sel ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(2)));

            texto(ctx, recortar(nombreDe(m.item()).getString(), aw - 70 - 90, 20),
                    ax + 70, y + 12, 20, TEXTO_OSCURO, false, false);
            texto(ctx, Text.translatable("pokepad.lunaeternal.tienda.tienes",
                            m.cantidad()),
                    ax + 70, y + 38, 14, TEXTO_SUAVE, false, false);
            textoDer(ctx, Text.literal("x" + m.cantidad()), ax + aw - 16, y + 22, 20,
                    TEXTO_OSCURO, true);
        }
        dibujarContador(ctx);
        dibujarPaginacion(ctx, rx, ry);
    }

    private void dibujarContador(DrawContext ctx) {
        int n = cuantos();
        if (n == 0) {
            return;
        }
        int desde = pagina * filasCaben() + 1;
        int hasta = Math.min(n, (pagina + 1) * filasCaben());
        textoDer(ctx, Text.translatable("pokepad.lunaeternal.gts.contador",
                        desde, hasta, n),
                PANT_X + PANT_W - MARGEN - 4, PANT_Y + PANT_H - MARGEN - 20, 13,
                TEXTO_SUAVE, true);
    }

    private int paginacionY() {
        return PANT_Y + PANT_H - MARGEN - 28;
    }

    private void dibujarPaginacion(DrawContext ctx, int rx, int ry) {
        if (paginas() <= 1) {
            return;
        }
        int y = paginacionY();
        botonPeq(ctx, rx, ry, PANT_X + MARGEN, y, 60, 26, Text.literal("<"),
                pagina > 0, false);
        texto(ctx, Text.literal((pagina + 1) + " / " + paginas()),
                PANT_X + MARGEN + 100, y + 5, 15, TEXTO_SUAVE, false, true);
        botonPeq(ctx, rx, ry, PANT_X + MARGEN + 160, y, 60, 26, Text.literal(">"),
                pagina < paginas() - 1, false);
    }

    /**
     * El aviso antes de gastar.
     *
     * <h2>⚠ Solo al COMPRAR, y es una línea deliberada</h2>
     *
     * Comprar es lo único que <b>no se puede deshacer</b>: el dinero se va y el
     * objeto es tuyo. Publicar sí se deshace —se retira y los objetos vuelven,
     * solo se pierde la tasa—, así que pedir confirmación ahí sería un clic de
     * más en la acción que más se repite.
     *
     * <p>⚠⚠ Y <b>se traga el clic</b>: mientras está puesta, nada de debajo
     * responde. Si no, un clic destinado al «confirmar» que cae medio píxel
     * fuera acabaría comprando otra cosa.
     */
    private void dibujarConfirmacion(DrawContext ctx, int rx, int ry) {
        ctx.fill(x0, y0, x0 + ancho, y0 + alto, 0xC0000000);
        int aw = 620, ah = 260;
        int ax = (NAT_ANCHO - aw) / 2, ay = (NAT_ALTO - ah) / 2;
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah), 0xFF161B29);
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), ORO, Math.max(2, pl(3)));

        texto(ctx, Text.translatable("pokepad.lunaeternal.gts.confirmar_titulo"),
                ax + aw / 2, ay + 26, 22, ORO, true, false);
        texto(ctx, recortar(nombreDe(confirmando.item()).getString()
                        + "  x" + confirmando.cantidad(),
                        aw - 60, 24),
                ax + aw / 2, ay + 70, 24, 0xFFFFFFFF, true, false);
        texto(ctx, Text.literal(String.format("%,d", confirmando.precio())),
                ax + aw / 2, ay + 110, 30, ORO, true, false);

        long saldo = estado == null ? 0 : estado.saldo();
        texto(ctx, Text.translatable("pokepad.lunaeternal.gts.te_quedan",
                        String.format("%,d", saldo - confirmando.precio())),
                ax + aw / 2, ay + 152, 14, TEXTO_SUAVE, true, false);

        boton(ctx, rx, ry, ax + 30, ay + ah - 76, aw / 2 - 45, 52,
                Text.translatable("pokepad.lunaeternal.gts.cancelar"), true, 0xFF4A5268);
        boton(ctx, rx, ry, ax + aw / 2 + 15, ay + ah - 76, aw / 2 - 45, 52,
                Text.translatable("pokepad.lunaeternal.gts.confirmar"),
                saldo >= confirmando.precio(), VERDE);
    }

    private static String queda(long cuando) {
        long ms = cuando - System.currentTimeMillis();
        if (ms <= 0) {
            return "—";
        }
        long horas = ms / 3_600_000L;
        if (horas >= 24) {
            return (horas / 24) + "d " + (horas % 24) + "h";
        }
        if (horas >= 1) {
            return horas + "h";
        }
        return Math.max(1, ms / 60_000L) + "m";
    }

    // ---- interacción -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        int rx = (int) mx, ry = (int) my;

        if (confirmando != null) {
            clicConfirmacion(rx, ry);
            return true;
        }

        int cy = PANEL_Y + NAV_ALTO / 2;
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            sonar();
            if (client != null) {
                client.setScreen(anterior);
            }
            return true;
        }
        int cx = PANEL_X + PANEL_W - 18 - 80;
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            sonar();
            close();
            return true;
        }

        if (clicConmutador(rx, ry)) {
            return true;
        }

        for (int i = 0; i < BARRA.length; i++) {
            if (dentro(rx, ry, px(botonX(i)), py(PANT_Y + MARGEN), pl(BOT), pl(BOT))) {
                pulsarBarra(BARRA[i].id());
                return true;
            }
        }

        if (campoBusqueda.mouseClicked(mx, my, boton)) {
            setFocused(campoBusqueda);
            return true;
        }
        if (modo == Modo.VENDER && campoPrecio.mouseClicked(mx, my, boton)) {
            setFocused(campoPrecio);
            return true;
        }

        if (clicCabecera(rx, ry)) {
            return true;
        }
        if (modo == Modo.VENDER ? clicPanelVender(rx, ry) : clicPanelOferta(rx, ry)) {
            return true;
        }
        if (clicFilas(rx, ry)) {
            return true;
        }
        if (clicPaginacion(rx, ry)) {
            return true;
        }
        return super.mouseClicked(mx, my, boton);
    }

    private boolean clicCabecera(int rx, int ry) {
        if (modo == Modo.VENDER) {
            return false;
        }
        int hy = listaY();
        for (var c : COLUMNAS) {
            int ax = PANT_X + MARGEN + c.x();
            if (dentro(rx, ry, px(ax - 4), py(hy - 3), pl(c.ancho()), pl(20))) {
                // ⚠ Alterna entre las dos ordenaciones de esa columna. Sin
                //   alternar, ordenar por precio solo serviría para una
                //   dirección y la flecha mentiría.
                orden = orden.equals(c.asc()) ? c.desc() : c.asc();
                pagina = 0;
                sonar();
                pedir();
                return true;
            }
        }
        return false;
    }

    private boolean clicFilas(int rx, int ry) {
        int aw = PANT_W - 2 * MARGEN;
        int desde = pagina * filasCaben();

        if (modo == Modo.VENDER) {
            var disp = mochila();
            for (int n = 0; n < filasCaben() && desde + n < disp.size(); n++) {
                int y = listaY() + 18 + n * FILA;
                if (dentro(rx, ry, px(PANT_X + MARGEN), py(y), pl(aw), pl(FILA - 6))) {
                    var m = disp.get(desde + n);
                    elegidoItem = m.item();
                    // ⚠ Al cambiar de objeto se reajusta la cantidad: dejar «x64»
                    //   puesto sobre una pila de 3 dibujaría un botón marcado que
                    //   no se puede pulsar.
                    if (cantidad > 0 && cantidad > m.cantidad()) {
                        cantidad = 1;
                    }
                    sonar();
                    return true;
                }
            }
            return false;
        }

        var l = lista();
        for (int n = 0; n < filasCaben() && desde + n < l.size(); n++) {
            int y = listaY() + 18 + n * FILA;
            if (!dentro(rx, ry, px(PANT_X + MARGEN), py(y), pl(aw), pl(FILA - 6))) {
                continue;
            }
            elegido = desde + n;
            sonar();
            // El botón de la fila hace lo mismo que el del panel: es un atajo,
            // no una segunda función.
            if (dentro(rx, ry, px(PANT_X + MARGEN + aw - 132), py(y + 16),
                    pl(124), pl(32)) && !esperando()) {
                if (modo == Modo.MIAS) {
                    retirar(l.get(elegido));
                } else {
                    confirmando = l.get(elegido);
                }
            }
            return true;
        }
        return false;
    }

    private boolean clicPaginacion(int rx, int ry) {
        if (paginas() <= 1) {
            return false;
        }
        int y = paginacionY();
        if (pagina > 0 && dentro(rx, ry, px(PANT_X + MARGEN), py(y), pl(60), pl(26))) {
            pagina--;
            sonar();
            return true;
        }
        if (pagina < paginas() - 1 && dentro(rx, ry, px(PANT_X + MARGEN + 160),
                py(y), pl(60), pl(26))) {
            pagina++;
            sonar();
            return true;
        }
        return false;
    }

    private boolean clicPanelOferta(int rx, int ry) {
        var o = seleccionada();
        if (o == null || esperando()) {
            return false;
        }
        if (!dentro(rx, ry, px(PANEL_X + 30), py(PANEL_Y + PANEL_H - 72),
                pl(PANEL_W - 60), pl(56))) {
            return false;
        }
        sonar();
        if (modo == Modo.MIAS) {
            retirar(o);
        } else if (estado != null && estado.saldo() >= o.precio()) {
            confirmando = o;
        }
        return true;
    }

    private boolean clicPanelVender(int rx, int ry) {
        var m = mioSeleccionado();
        if (m == null) {
            return false;
        }
        int anchoBot = (PANEL_W - 60 - 3 * 6) / 4;
        for (int i = 0; i < CANTIDADES.length; i++) {
            int bx = PANEL_X + 30 + i * (anchoBot + 6);
            if (dentro(rx, ry, px(bx), py(PANEL_Y + 420), pl(anchoBot), pl(32))) {
                if (CANTIDADES[i] < 0 || CANTIDADES[i] <= m.cantidad()) {
                    cantidad = CANTIDADES[i];
                    sonar();
                }
                return true;
            }
        }
        int anchoDur = (PANEL_W - 60 - 2 * 6) / 3;
        for (int i = 0; i < DURACIONES.length; i++) {
            int bx = PANEL_X + 30 + i * (anchoDur + 6);
            if (dentro(rx, ry, px(bx), py(PANEL_Y + 578), pl(anchoDur), pl(32))) {
                horas = DURACIONES[i];
                sonar();
                return true;
            }
        }
        if (dentro(rx, ry, px(PANEL_X + 30), py(PANEL_Y + PANEL_H - 72),
                pl(PANEL_W - 60), pl(56))) {
            long precio = numeroLargo(campoPrecio);
            if (precio > 0 && cantidadReal(m) > 0 && !esperando()) {
                sonar();
                pulsado = System.currentTimeMillis();
                ClientPlayNetworking.send(new Red.AccionMercado(
                        "vender", 0, m.item(), cantidadReal(m), precio, horas));
                campoPrecio.setText("");
                elegidoItem = "";
            }
            return true;
        }
        return false;
    }

    private void retirar(Red.OfertaObj o) {
        pulsado = System.currentTimeMillis();
        ClientPlayNetworking.send(new Red.AccionMercado(
                "retirar", o.id(), o.item(), 0, 0, 0));
    }

    private void clicConfirmacion(int rx, int ry) {
        int aw = 620, ah = 260;
        int ax = (NAT_ANCHO - aw) / 2, ay = (NAT_ALTO - ah) / 2;
        if (dentro(rx, ry, px(ax + 30), py(ay + ah - 76), pl(aw / 2 - 45), pl(52))) {
            sonar();
            confirmando = null;
            return;
        }
        if (dentro(rx, ry, px(ax + aw / 2 + 15), py(ay + ah - 76),
                pl(aw / 2 - 45), pl(52))) {
            sonar();
            // ⚠ Solo viaja el identificador. El precio lo pone el servidor
            //   mirando su fila (P6).
            ClientPlayNetworking.send(new Red.AccionMercado(
                    "comprar", confirmando.id(), confirmando.item(), 0, 0, 0));
            pulsado = System.currentTimeMillis();
            confirmando = null;
        }
    }

    private void pulsarBarra(String id) {
        sonar();
        switch (id) {
            case "buscar" -> {
                setFocused(campoBusqueda);
                campoBusqueda.setFocused(true);
                pagina = 0;
                pedir();
            }
            case "refrescar" -> pedir();
            case "vender" -> {
                modo = modo == Modo.VENDER ? Modo.LISTA : Modo.VENDER;
                elegido = -1;
                pagina = 0;
                pedir();
            }
            case "mias" -> {
                modo = modo == Modo.MIAS ? Modo.LISTA : Modo.MIAS;
                elegido = -1;
                pagina = 0;
                pedir();
            }
            default -> { }
        }
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        if (confirmando != null) {
            if (tecla == 256) {
                confirmando = null;
                return true;
            }
            return true;
        }
        if (tecla == 256) {
            if (client != null) {
                client.setScreen(anterior);
            }
            return true;
        }
        if (tecla == 257 && getFocused() == campoBusqueda) {
            pagina = 0;
            pedir();
            return true;
        }
        if (getFocused() == campoBusqueda && campoBusqueda.keyPressed(tecla, escaneo, mods)) {
            pagina = 0;
            return true;
        }
        if (getFocused() == campoPrecio && campoPrecio.keyPressed(tecla, escaneo, mods)) {
            return true;
        }
        return super.keyPressed(tecla, escaneo, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (confirmando != null) {
            return true;
        }
        if (getFocused() == campoBusqueda) {
            // ⚠ Vuelta a la página 1: si estabas en la 5 y el filtro deja tres
            //   resultados, te quedarías mirando una página vacía.
            boolean r = campoBusqueda.charTyped(c, mods);
            pagina = 0;
            return r;
        }
        // ⚠ Solo dígitos en el precio. Un `setMaxLength` no lo impide: deja
        //   escribir letras y luego `parseLong` devuelve 0, que se ve como un
        //   botón apagado sin decir por qué.
        if (getFocused() == campoPrecio) {
            return Character.isDigit(c) && campoPrecio.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
    }

    private void sonar() {
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.0f);
        }
    }

    // ---- utilidades --------------------------------------------------------

    /**
     * Un objeto del juego, a tamaño de arte.
     *
     * <p>⚠ {@code drawItem} dibuja a 16×16 fijos: para agrandarlo hay que escalar
     * la matriz, no pasarle un tamaño. Y va DESPUÉS de {@code ctx.draw()}.
     */
    private void objeto(DrawContext ctx, ItemStack p, int ax, int ay, int altoArte) {
        float escala = altoArte * k / 16f;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(px(ax), py(ay), 0);
        m.scale(escala, escala, 1f);
        ctx.drawItem(p, 0, 0);
        m.pop();
    }

    private void boton(DrawContext ctx, int rx, int ry, int ax, int ay, int aw,
                       int ah, Text etiqueta, boolean activo, int color) {
        boolean encima = activo && dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah),
                !activo ? APAGADO : (encima ? aclarar(color) : color));
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), 0xFF10331E, Math.max(1, pl(2)));
        texto(ctx, etiqueta, ax + aw / 2, ay + ah / 2 - 12, 24,
                activo ? 0xFFFFFFFF : 0xFFD8DEEA, true, false);
    }

    private void botonPeq(DrawContext ctx, int rx, int ry, int ax, int ay, int aw,
                          int ah, Text etiqueta, boolean activo, boolean marcado) {
        boolean encima = activo && dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah),
                !activo ? APAGADO : marcado ? BORDE_ENCIMA
                        : (encima ? 0xFF5E86D8 : 0xFF4F6FB0));
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), 0xFF20283C, Math.max(1, pl(2)));
        int alto = 15;
        while (alto > 9 && anchoArte(etiqueta.getString(), alto) > aw - 10) {
            alto--;
        }
        texto(ctx, etiqueta, ax + aw / 2, ay + (ah - alto) / 2 - 1, alto,
                activo ? 0xFFFFFFFF : 0xFFD8DEEA, true, false);
    }

    private static int aclarar(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 48);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 48);
        int b = Math.min(255, (color & 0xFF) + 48);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Corta un texto para que quepa, con puntos suspensivos.
     *
     * <h2>⚠⚠ HACE FALTA PORQUE LOS NOMBRES DE MINECRAFT SON LARGOS</h2>
     *
     * «Escaleras de ladrillos de piedra» mide 364 px de arte y la columna del
     * nombre tiene 240: sin cortarlo se metía <b>encima del vendedor</b>. Y no
     * daba ningún error — se veía como dos textos superpuestos, que es
     * exactamente de lo que se quejó el usuario.
     *
     * <p>⚠ Un Pokémon no tiene este problema («Charizard» cabe siempre), así
     * que esto es propio del escaparate de objetos y no se copió del GTS.
     */
    private Text recortar(String s, int anchoMax, int alto) {
        if (anchoArte(s, alto) <= anchoMax) {
            return Text.literal(s);
        }
        int corte = s.length();
        while (corte > 1 && anchoArte(s.substring(0, corte) + "…", alto) > anchoMax) {
            corte--;
        }
        return Text.literal(s.substring(0, corte).trim() + "…");
    }

    /**
     * Parte en líneas, con un tope.
     *
     * <p>⚠ El tope es lo que impide que un nombre larguísimo empuje hacia abajo
     * todo lo que va debajo del título hasta meterlo en el botón. La última
     * línea se corta con puntos suspensivos en vez de desaparecer sin más.
     */
    private List<Text> partirLim(String s, int anchoMax, int alto, int maxLineas) {
        var crudas = partir(s, anchoMax, alto);
        var salida = new ArrayList<Text>();
        for (int i = 0; i < crudas.size() && i < maxLineas; i++) {
            boolean ultima = i == maxLineas - 1 && crudas.size() > maxLineas;
            salida.add(ultima ? recortar(crudas.get(i) + "…", anchoMax, alto)
                    : Text.literal(crudas.get(i)));
        }
        return salida;
    }

    private List<String> partir(String s, int anchoMax, int altoArte) {
        var salida = new ArrayList<String>();
        var actual = new StringBuilder();
        for (String palabra : s.split(" ")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (anchoArte(prueba, altoArte) > anchoMax && !actual.isEmpty()) {
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

    private int anchoArte(String linea, int alto) {
        return Math.round(textRenderer.getWidth(linea) * alto
                / (float) textRenderer.fontHeight);
    }

    private void separador(DrawContext ctx, int artY) {
        ctx.fill(px(PANEL_X + 28), py(artY), px(PANEL_X + PANEL_W - 28),
                py(artY) + Math.max(1, pl(2)), SEPARADOR);
    }

    /**
     * ⚠⚠ ALINEAR A LA DERECHA DE VERDAD: pasar la x del borde con
     * {@code centrado=false} dibuja el texto <b>hacia fuera</b>. Ahí estaba la
     * causa de casi toda la fealdad de la versión anterior.
     */
    private void textoDer(DrawContext ctx, Text linea, int derecha, int arriba,
                          int alto, int color, boolean contorno) {
        int a = Math.round(textRenderer.getWidth(linea) * alto
                / (float) textRenderer.fontHeight);
        texto(ctx, linea, derecha - a, arriba, alto, color, false, contorno);
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

    /** ⚠ `enableBlend()` a mano: regla 1 de dibujado.md. */
    private static void dibujarTextura(DrawContext ctx, Identifier tex,
                                       int x, int y, int w, int h, int natW, int natH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
    }
}
