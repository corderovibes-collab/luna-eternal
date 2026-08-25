package net.pokereport.luna.client.pokepad;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;

/**
 * EL GTS: el mercado de ejemplares.
 *
 * <h2>Por qué esto es una pantalla aparte del libro de órdenes</h2>
 *
 * Porque son <b>dos mercados</b> (D-041). Un libro de órdenes solo funciona con
 * cosas intercambiables; un Pokémon es único, así que se mira <i>ese</i> y se
 * compra <i>ese</i>. Meter los dos en una clase mezclaría dos flujos que no se
 * parecen en nada, y el botón de arriba salta de uno a otro.
 *
 * <h2>Cuatro modos, y solo uno a la vez</h2>
 *
 * <pre>
 *   LISTA    lo que hay a la venta, con buscador
 *   FILTROS  la hoja de filtros, encima de la lista
 *   VENDER   tus Pokémon, para publicar uno
 *   MIAS     lo que has publicado tú, para retirarlo
 * </pre>
 *
 * <h2>⚠ El cliente no decide nada</h2>
 *
 * Al comprar <b>no se manda el precio</b>: lo cobra el servidor mirando su fila.
 * Si viniera de aquí, un cliente modificado compraría un shiny por 1 (P6).
 *
 * <h2>⚠⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 *
 * Y la geometría es <b>copia literal</b> de {@code CosmeticosScreen}, como en
 * todas las demás.
 */
public class GtsScreen extends Screen {

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

    /** Alto de una fila de la lista. Cabe el nombre, los IVs y el botón. */
    private static final int FILA = 54;

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
    private static final int SHINY = 0xFFFFD65C;
    private static final int APAGADO = 0xFF6E7899;

    private enum Modo { LISTA, FILTROS, VENDER, MIAS }

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private Red.EstadoGts estado;
    private Modo modo = Modo.LISTA;
    private int elegido = -1;
    private String elegidoUuid = "";
    private int pestanaDetalle = 0;
    private int pagina = 0;
    private String aviso = "";
    private long pulsado;

    private TextFieldWidget campoBusqueda;
    private TextFieldWidget campoPrecio;
    private TextFieldWidget campoNivelMin;
    private TextFieldWidget campoNivelMax;
    private TextFieldWidget campoPrecioMin;
    private TextFieldWidget campoPrecioMax;
    private final TextFieldWidget[] campoIv = new TextFieldWidget[6];
    private final TextFieldWidget[] campoEv = new TextFieldWidget[6];
    private boolean soloShiny;

    /**
     * Por qué columna se ordena.
     *
     * <h2>⚠⚠ Esto es lo que faltaba para que pareciera una tienda</h2>
     *
     * Lo primero que hace cualquiera al entrar en un mercado es <b>ordenar por
     * precio</b>. Sin ordenación hay que ir página por página comparando a ojo,
     * y con cuarenta ofertas eso ya nadie lo hace: la pantalla deja de ser una
     * tienda y pasa a ser una lista.
     */
    private String orden = "NUEVO";

    /**
     * La compra que está esperando confirmación.
     *
     * <p>⚠⚠ <b>Comprar con un solo clic es un fallo, no una comodidad.</b> Un
     * roce del ratón sobre una fila equivocada se lleva miles de Plata y no hay
     * forma de deshacerlo — el vendedor ya cobró. Todos los mercados que mueven
     * dinero de verdad confirman antes, y las pruebas de usabilidad de subastas
     * dicen justo eso: la gente espera un paso explícito.
     */
    private Red.EjemplarGts confirmando;

    /** Las seis estadísticas, en el orden FIJO del protocolo. */
    private static final String[] SIGLAS = { "PS", "AT", "DE", "SA", "SD", "VE" };

    public GtsScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.gts"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();

        campoBusqueda = campo(PANT_X + MARGEN, PANT_Y + MARGEN, 300, 32);
        campoPrecio = campo(PANEL_X + 30, PANEL_Y + 430, PANEL_W - 60, 11);
        campoNivelMin = campo(PANT_X + 30, PANT_Y + 118, 130, 3);
        campoNivelMax = campo(PANT_X + 180, PANT_Y + 118, 130, 3);
        campoPrecioMin = campo(PANT_X + 400, PANT_Y + 118, 150, 9);
        campoPrecioMax = campo(PANT_X + 570, PANT_Y + 118, 150, 9);
        for (int i = 0; i < 6; i++) {
            campoIv[i] = campo(PANT_X + 60 + i * 120, PANT_Y + 196, 70, 2);
            campoEv[i] = campo(PANT_X + 60 + i * 120, PANT_Y + 262, 70, 3);
        }
        for (var c : todos()) {
            addSelectableChild(c);
        }
        // ⚠ Se pide DESPUES de crear los campos. Antes funcionaba de casualidad
        //   --`texto()` tolera un campo nulo-- y eso es exactamente la clase de
        //   cosa que deja de funcionar el dia que alguien añade un filtro que no
        //   la tolere.
        pedir();
    }

    private List<TextFieldWidget> todos() {
        var xs = new ArrayList<TextFieldWidget>();
        xs.add(campoBusqueda);
        xs.add(campoPrecio);
        xs.add(campoNivelMin);
        xs.add(campoNivelMax);
        xs.add(campoPrecioMin);
        xs.add(campoPrecioMax);
        for (int i = 0; i < 6; i++) {
            xs.add(campoIv[i]);
            xs.add(campoEv[i]);
        }
        return xs;
    }

    private TextFieldWidget campo(int ax, int ay, int aw, int max) {
        var c = new TextFieldWidget(textRenderer, px(ax), py(ay), pl(aw),
                Math.max(12, pl(30)), Text.literal(""));
        c.setMaxLength(max);
        return c;
    }

    /** Copia literal de CosmeticosScreen. Ver el comentario de la clase. */
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

        boolean exacto = Math.round(ancho * gui) == NAT_ANCHO
                && Math.round(alto * gui) == NAT_ALTO;
        if (client != null) {
            for (Identifier t : new Identifier[] { CHASIS, ATRAS, CERRAR }) {
                client.getTextureManager().getTexture(t).setFilter(!exacto, false);
            }
        }
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

    /** Manda los filtros tal y como están escritos. El servidor los interpreta. */
    private void pedir() {
        var ivs = new ArrayList<Integer>();
        var evs = new ArrayList<Integer>();
        for (int i = 0; i < 6; i++) {
            ivs.add(numero(campoIv[i]));
            evs.add(numero(campoEv[i]));
        }
        ClientPlayNetworking.send(new Red.PedirGts(
                texto(campoBusqueda), "",
                texto(campoNivelMin), texto(campoNivelMax),
                texto(campoPrecioMin), texto(campoPrecioMax),
                List.copyOf(ivs), List.copyOf(evs), soloShiny ? "1" : "", orden));
    }

    private String texto(TextFieldWidget c) {
        return c == null ? "" : c.getText().trim();
    }

    private int numero(TextFieldWidget c) {
        try {
            String s = texto(c);
            return s.isEmpty() ? 0 : Math.max(0, Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<Red.EjemplarGts> lista() {
        if (estado == null) {
            return List.of();
        }
        return modo == Modo.MIAS ? estado.mias() : estado.ofertas();
    }

    private int filasCaben() {
        return (PANT_H - 2 * MARGEN - 56 - 30) / FILA;
    }

    private int paginas() {
        int n = modo == Modo.VENDER
                ? (estado == null ? 0 : estado.disponibles().size())
                : lista().size();
        return Math.max(1, (n + filasCaben() - 1) / filasCaben());
    }

    private Red.EjemplarGts seleccionado() {
        var l = lista();
        return elegido >= 0 && elegido < l.size() ? l.get(elegido) : null;
    }

    private Red.MioGts mioSeleccionado() {
        if (estado == null) {
            return null;
        }
        for (var m : estado.disponibles()) {
            if (m.uuid().equals(elegidoUuid)) {
                return m;
            }
        }
        return null;
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        var nuevo = EstadoCliente.gts();
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

        switch (modo) {
            case VENDER -> dibujarMios(ctx, rx, ry, false);
            case FILTROS -> dibujarFiltros(ctx, rx, ry);
            default -> dibujarLista(ctx, rx, ry, false);
        }

        // ⚠ DOS PASADAS: todo el 2D, `ctx.draw()`, y solo entonces el 3D.
        //   Mezclarlos hace que el 2D se pinte ENCIMA de los modelos, porque van
        //   por lotes distintos. Regla 3 de dibujado.md.
        ctx.draw();
        if (modo == Modo.VENDER) {
            dibujarMios(ctx, rx, ry, true);
        } else if (modo != Modo.FILTROS) {
            dibujarLista(ctx, rx, ry, true);
        }
        dibujarRetrato(ctx, delta);

        // La confirmación va la ÚLTIMA y encima de todo: es una decisión que
        // interrumpe, no un panel más.
        if (confirmando != null) {
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

    private static final int RET_X = PANEL_X + 30, RET_Y = PANEL_Y + NAV_ALTO + 6;
    private static final int RET_W = PANEL_W - 60, RET_H = 168;

    /**
     * El ejemplar en 3D.
     *
     * <p>⚠ Va en la última pasada y con su propio recorte: un modelo alto se
     * sale de su hueco y, como el 3D no respeta el orden de dibujado de la
     * interfaz, taparía también el precio y los botones.
     *
     * <p>⚠ El shiny se pasa como <b>aspecto</b>, que es como lo entiende
     * Cobblemon — no es una textura aparte.
     */
    private void dibujarRetrato(DrawContext ctx, float delta) {
        String especie = "";
        boolean shiny = false;
        if (modo == Modo.VENDER) {
            var m = mioSeleccionado();
            if (m != null) {
                especie = m.especie();
                shiny = m.shiny();
            }
        } else {
            var e = seleccionado();
            if (e != null) {
                especie = e.especie();
                shiny = e.shiny();
            }
        }
        if (especie.isEmpty()) {
            return;
        }
        var id = Identifier.tryParse("cobblemon:" + especie.toLowerCase(java.util.Locale.ROOT));
        if (id == null) {
            return;
        }
        Mascota3D.dibujarEspecie(ctx, id, "gts:" + especie + (shiny ? ":s" : ""),
                shiny ? "shiny" : "", px(RET_X), py(RET_Y), pl(RET_W), pl(RET_H),
                0.10f, delta, true);
    }

    /** Izquierda: el detalle del elegido, o el formulario de publicar. */
    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        int cx = PANEL_X + PANEL_W / 2;
        boolean vender = modo == Modo.VENDER;
        var e = seleccionado();
        var m = mioSeleccionado();

        if ((vender && m == null) || (!vender && e == null)) {
            int y = PANEL_Y + 250;
            for (String linea : partir(Text.translatable(
                    "pokepad.lunaeternal.gts.elige").getString(), PANEL_W - 70, 17)) {
                texto(ctx, Text.literal(linea), cx, y, 17, TEXTO_SUAVE, true, false);
                y += 22;
            }
            return;
        }

        // Hueco del retrato: se marca aunque el modelo lo tape, para que se vea
        // que ahí va algo mientras carga.
        marco(ctx, px(RET_X), py(RET_Y), pl(RET_W), pl(RET_H), 0x556A7398,
                Math.max(1, pl(2)));

        String nombre = vender
                ? (m.mote().isBlank() ? m.especie() : m.mote())
                : (e.mote().isBlank() ? e.especie() : e.mote());
        int nivel = vender ? m.nivel() : e.nivel();
        boolean shiny = vender ? m.shiny() : e.shiny();

        int y = RET_Y + RET_H + 8;
        texto(ctx, Text.literal(nombre), cx, y, 24, 0xFFFFFFFF, true, false);
        y += 28;
        texto(ctx, Text.literal("Nv " + nivel + (shiny ? "  ✦" : "")),
                cx, y, 18, shiny ? SHINY : TEXTO_SUAVE, true, false);
        y += 26;

        if (vender) {
            // PUBLICAR: precio + el estimado como referencia.
            separador(ctx, y);
            texto(ctx, Text.translatable("pokepad.lunaeternal.gts.estimado"),
                    cx, y + 12, 15, TEXTO_SUAVE, true, false);
            texto(ctx, Text.literal(String.format("%,d", m.estimado())),
                    cx, y + 30, 24, ORO, true, false);
            // ⚠ El estimado SUGIERE, no impone. Si el servidor fijara precios
            //   dejaría de haber mercado -- media gracia es encontrar a alguien
            //   que no sabe lo que tiene.
            texto(ctx, Text.translatable("pokepad.lunaeternal.gts.solo_referencia"),
                    cx, y + 58, 13, TEXTO_SUAVE, true, false);

            texto(ctx, Text.translatable("pokepad.lunaeternal.gts.tu_precio"),
                    PANEL_X + 30, PANEL_Y + 408, 15, TEXTO_SUAVE, false, false);
            campoPrecio.render(ctx, rx, ry, 0);

            long precio = numeroLargo(campoPrecio);
            boton(ctx, rx, ry, PANEL_X + 40, PANEL_Y + PANEL_H - 76, PANEL_W - 80, 46,
                    Text.translatable("pokepad.lunaeternal.gts.publicar"),
                    precio > 0 && !esperando(), VERDE);
            return;
        }

        // COMPRAR: precio, estimado y las pestañas.
        texto(ctx, Text.literal(String.format("%,d", e.precio())),
                cx, y, 30, ORO, true, false);
        y += 34;
        if (e.estimado() > 0) {
            // Comparar lo que pide con lo que vale es la única cifra que
            // convierte un precio en una decisión.
            boolean caro = e.precio() > e.estimado() * 1.3;
            boolean chollo = e.precio() < e.estimado() * 0.7;
            texto(ctx, Text.translatable(caro ? "pokepad.lunaeternal.gts.caro"
                            : chollo ? "pokepad.lunaeternal.gts.chollo"
                            : "pokepad.lunaeternal.gts.justo",
                            String.format("%,d", e.estimado())),
                    cx, y, 14, caro ? ROJO : chollo ? VERDE : TEXTO_SUAVE, true, false);
        }
        y += 22;
        separador(ctx, y);
        y += 8;

        String[] tabs = { "EST", "IVS", "EVS" };
        for (int i = 0; i < 3; i++) {
            int bx = PANEL_X + 30 + i * 86;
            boolean act = i == pestanaDetalle;
            ctx.fill(px(bx), py(y), px(bx + 78), py(y + 26),
                    act ? 0xFF3A3020 : 0x33000000);
            marco(ctx, px(bx), py(y), pl(78), pl(26),
                    act ? BORDE_ENCIMA : 0x556A7398, Math.max(1, pl(2)));
            texto(ctx, Text.literal(tabs[i]), bx + 39, y + 6, 15,
                    act ? ORO : TEXTO_SUAVE, true, false);
        }
        y += 34;
        dibujarDetalle(ctx, e, y);

        boolean mio = modo == Modo.MIAS;
        boton(ctx, rx, ry, PANEL_X + 40, PANEL_Y + PANEL_H - 76, PANEL_W - 80, 46,
                Text.translatable(mio ? "pokepad.lunaeternal.gts.retirar"
                        : "pokepad.lunaeternal.gts.comprar"),
                !esperando(), mio ? ROJO : VERDE);
    }

    private void dibujarDetalle(DrawContext ctx, Red.EjemplarGts e, int y) {
        switch (pestanaDetalle) {
            case 1 -> barras(ctx, e.ivs(), 31, y);
            case 2 -> barras(ctx, e.evs(), 252, y);
            default -> {
                fila(ctx, "Naturaleza", e.naturaleza(), y);
                fila(ctx, "Habilidad", e.habilidad(), y + 22);
                fila(ctx, "Género", e.genero(), y + 44);
                fila(ctx, "Tera", e.tera(), y + 66);
                fila(ctx, "Rareza", e.rareza(), y + 88);
                fila(ctx, "Vendedor", e.vendedor(), y + 110);
            }
        }
    }

    private void fila(DrawContext ctx, String etiqueta, String valor, int y) {
        texto(ctx, Text.literal(etiqueta), PANEL_X + 30, y, 14, TEXTO_SUAVE, false, false);
        String v = valor == null || valor.isBlank() ? "—" : valor;
        int alto = 14;
        while (alto > 9 && anchoArte(v, alto) > 150) {
            alto--;
        }
        texto(ctx, Text.literal(v), PANEL_X + PANEL_W - 30, y, alto, 0xFFE8EEF8,
                false, false);
    }

    /**
     * Los seis valores, en barras.
     *
     * <p>⚠ Una barra dice de un vistazo si un IV es bueno; un número obliga a
     * saberse que el máximo es 31. Y el número va igual, porque para comparar
     * dos ejemplares hace falta la cifra exacta.
     */
    private void barras(DrawContext ctx, List<Integer> valores, int max, int y) {
        for (int i = 0; i < 6 && i < valores.size(); i++) {
            int v = valores.get(i) == null ? 0 : valores.get(i);
            int fy = y + i * 24;
            texto(ctx, Text.literal(SIGLAS[i]), PANEL_X + 30, fy + 2, 14,
                    TEXTO_SUAVE, false, false);
            int bx = PANEL_X + 66, bw = PANEL_W - 66 - 66;
            ctx.fill(px(bx), py(fy), px(bx + bw), py(fy + 14), 0xFF2A3145);
            int lleno = Math.round(bw * Math.min(1f, v / (float) max));
            // Verde a tope, ámbar por encima de la mitad, gris el resto. Un 31
            // se tiene que ver DISTINTO, no un poco más largo.
            int color = v >= max ? 0xFF4FD07A : v >= max / 2 ? 0xFFE0A845 : 0xFF6E7899;
            if (lleno > 0) {
                ctx.fill(px(bx), py(fy), px(bx + lleno), py(fy + 14), color);
            }
            texto(ctx, Text.literal(String.valueOf(v)), PANEL_X + PANEL_W - 30, fy + 2,
                    14, v >= max ? SHINY : 0xFFE8EEF8, false, false);
        }
    }

    // ---- la barra de arriba ------------------------------------------------

    private record Boton(String id, String etiqueta, int x, int w) {}

    private List<Boton> botones() {
        var xs = new ArrayList<Boton>();
        int x = PANT_X + MARGEN + 312;
        xs.add(new Boton("buscar", "BUSCAR", x, 96));
        xs.add(new Boton("filtros", "FILTROS", x + 102, 96));
        xs.add(new Boton("objetos", "OBJETOS", x + 204, 104));
        xs.add(new Boton("vender", "+ VENDER", x + 314, 110));
        xs.add(new Boton("mias", "MIS OFERTAS", x + 430, 130));
        return xs;
    }

    private void dibujarBarra(DrawContext ctx, int rx, int ry) {
        campoBusqueda.render(ctx, rx, ry, 0);
        if (texto(campoBusqueda).isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.gts.buscar_pista"),
                    PANT_X + MARGEN + 8, PANT_Y + MARGEN + 9, 15, 0xFF8892AC,
                    false, false);
        }
        for (var b : botones()) {
            boolean activo = switch (b.id()) {
                case "vender" -> modo == Modo.VENDER;
                case "mias" -> modo == Modo.MIAS;
                case "filtros" -> modo == Modo.FILTROS;
                default -> false;
            };
            botonPeq(ctx, rx, ry, b.x(), PANT_Y + MARGEN, b.w(), 32,
                    Text.literal(b.etiqueta()), true, activo);
        }
        if (!aviso.isEmpty()) {
            texto(ctx, Text.literal(aviso), PANT_X + PANT_W - MARGEN,
                    PANT_Y + PANT_H - 20, 15, ROJO, false, true);
        }
    }

    // ---- la lista ----------------------------------------------------------

    private int listaY() {
        return PANT_Y + MARGEN + 44;
    }

    private void dibujarLista(DrawContext ctx, int rx, int ry, boolean tercera) {
        var l = lista();
        if (!tercera) {
            dibujarCabecera(ctx, rx, ry);
            if (l.isEmpty()) {
                texto(ctx, Text.translatable(modo == Modo.MIAS
                                ? "pokepad.lunaeternal.gts.sin_mias"
                                : "pokepad.lunaeternal.gts.sin_ofertas"),
                        PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 20, TEXTO_SUAVE,
                        true, false);
            }
        }
        int desde = pagina * filasCaben();
        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= l.size()) {
                break;
            }
            var e = l.get(i);
            int y = listaY() + 20 + n * FILA;
            int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;

            if (tercera) {
                var id = Identifier.tryParse("cobblemon:"
                        + e.especie().toLowerCase(java.util.Locale.ROOT));
                if (id != null) {
                    Mascota3D.dibujarEspecie(ctx, id,
                            "gtsfila:" + e.id(), e.shiny() ? "shiny" : "",
                            px(ax + 6), py(y + 2), pl(48), pl(48), 0.10f, 0f, false);
                }
                continue;
            }

            boolean sel = i == elegido;
            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA - 4));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA - 4),
                    sel ? FILA_SEL : (encima ? 0xFFD3DCF2 : FILA_FONDO));
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA - 4),
                    sel ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(sel ? 3 : 2)));

            String nombre = e.mote().isBlank() ? e.especie() : e.mote();
            texto(ctx, Text.literal(nombre + (e.shiny() ? " ✦" : "")),
                    ax + 60, y + 8, 19, e.shiny() ? 0xFF8A6A00 : TEXTO_OSCURO,
                    false, true);
            texto(ctx, Text.literal("Nv " + e.nivel() + "  " + e.vendedor()),
                    ax + 60, y + 30, 14, TEXTO_SUAVE, false, true);

            // Los IVs en línea: es lo que se mira antes que nada.
            StringBuilder ivs = new StringBuilder();
            int perfectos = 0;
            for (int v : e.ivs()) {
                ivs.append(ivs.length() == 0 ? "" : "/").append(v);
                if (v >= 31) {
                    perfectos++;
                }
            }
            texto(ctx, Text.literal(ivs.toString()), ax + 300, y + 10, 15,
                    perfectos >= 4 ? 0xFF1F7A3C : TEXTO_OSCURO, false, true);
            if (perfectos > 0) {
                texto(ctx, Text.literal(perfectos + " × 31"), ax + 300, y + 30, 13,
                        perfectos >= 4 ? 0xFF1F7A3C : TEXTO_SUAVE, false, true);
            }

            texto(ctx, Text.literal(String.format("%,d", e.precio())),
                    ax + 500, y + 8, 18, 0xFF8A6A00, false, true);
            // ⚠ Comparado con la tasación, EN LA PROPIA FILA. Sin esto hay que
            //   seleccionar cada oferta para saber si es cara -- y entonces
            //   comparar diez es abrir diez.
            if (e.estimado() > 0) {
                double razon = e.precio() / (double) e.estimado();
                texto(ctx, Text.literal(razon < 0.7 ? "chollo"
                                : razon > 1.3 ? "caro" : "justo"),
                        ax + 500, y + 30, 13,
                        razon < 0.7 ? 0xFF1F7A3C : razon > 1.3 ? ROJO : TEXTO_SUAVE,
                        false, true);
            }
            texto(ctx, Text.literal(queda(e.expira())), ax + 620, y + 18, 14,
                    TEXTO_SUAVE, false, true);

            // ⚠⚠ EL BOTON VA EN LA FILA. Antes había que seleccionar y bajar la
            //   vista al panel de la izquierda: dos clics y un salto de atención
            //   para lo único que se viene a hacer aquí. Todos los mercados que
            //   funcionan compran desde la lista.
            botonPeq(ctx, rx, ry, ax + aw - 124, y + 12, 116, 28,
                    Text.translatable(modo == Modo.MIAS
                            ? "pokepad.lunaeternal.gts.retirar"
                            : "pokepad.lunaeternal.gts.comprar"),
                    !esperando(), false);
        }
        if (!tercera) {
            dibujarContador(ctx);
        }

        if (!tercera && paginas() > 1) {
            int y = PANT_Y + PANT_H - MARGEN - 28;
            botonPeq(ctx, rx, ry, PANT_X + MARGEN, y, 60, 26, Text.literal("<"),
                    pagina > 0, false);
            texto(ctx, Text.literal((pagina + 1) + " / " + paginas()),
                    PANT_X + MARGEN + 100, y + 5, 15, TEXTO_SUAVE, false, true);
            botonPeq(ctx, rx, ry, PANT_X + MARGEN + 160, y, 60, 26, Text.literal(">"),
                    pagina < paginas() - 1, false);
        }
    }

    /** Las columnas por las que se puede ordenar, y dónde empieza cada una. */
    private record Columna(String etiqueta, int x, int ancho,
                           String asc, String desc) {}

    private static final Columna[] COLUMNAS = {
        new Columna("pokepad.lunaeternal.gts.col_oferta", 8, 280, "NIVEL_ASC", "NIVEL_DESC"),
        new Columna("pokepad.lunaeternal.gts.col_ivs", 300, 190, "IVS_DESC", "IVS_DESC"),
        new Columna("pokepad.lunaeternal.gts.col_precio", 500, 130, "PRECIO_ASC", "PRECIO_DESC"),
        new Columna("pokepad.lunaeternal.gts.col_expira", 640, 120, "EXPIRA_ASC", "NUEVO"),
    };

    /**
     * La cabecera de la tabla: <b>se pulsa para ordenar</b>.
     *
     * <p>⚠ Cada columna alterna entre dos ordenaciones, y la flecha dice cuál
     * está puesta. Sin la flecha, el jugador pulsa y no sabe si pasó algo — que
     * es la queja de siempre de las tablas ordenables mal hechas.
     */
    private void dibujarCabecera(DrawContext ctx, int rx, int ry) {
        int hy = listaY();
        for (var c : COLUMNAS) {
            int ax = PANT_X + MARGEN + c.x();
            boolean activa = orden.equals(c.asc()) || orden.equals(c.desc());
            boolean encima = dentro(rx, ry, px(ax - 4), py(hy - 3), pl(c.ancho()), pl(20));
            if (activa || encima) {
                ctx.fill(px(ax - 4), py(hy - 3), px(ax - 4 + c.ancho()), py(hy + 17),
                        activa ? 0x44F35C0C : 0x22FFFFFF);
            }
            String flecha = !activa ? " —" : orden.equals(c.asc()) ? " ▲" : " ▼";
            texto(ctx, Text.translatable(c.etiqueta()).copy()
                            .append(Text.literal(flecha)),
                    ax, hy, 14, activa ? ORO : TEXTO_SUAVE, false, false);
        }
        // El chollo no es una columna: es una ordenación que solo existe porque
        // hay tasador. Va aparte para que se vea que es otra cosa.
        boolean chollo = "CHOLLO".equals(orden);
        botonPeq(ctx, rx, ry, PANT_X + PANT_W - MARGEN - 130, hy - 6, 126, 24,
                Text.translatable("pokepad.lunaeternal.gts.chollos"), true, chollo);
    }

    /** Cuántas ofertas hay y cuáles se están viendo. */
    private void dibujarContador(DrawContext ctx) {
        int n = modo == Modo.VENDER
                ? (estado == null ? 0 : estado.disponibles().size())
                : lista().size();
        if (n == 0) {
            return;
        }
        int desde = pagina * filasCaben() + 1;
        int hasta = Math.min(n, (pagina + 1) * filasCaben());
        texto(ctx, Text.translatable("pokepad.lunaeternal.gts.contador",
                        desde, hasta, n),
                PANT_X + PANT_W - MARGEN, PANT_Y + PANT_H - MARGEN - 22, 14,
                TEXTO_SUAVE, false, true);
    }

    /**
     * El aviso de confirmación antes de gastar.
     *
     * <p>⚠⚠ Es una capa por encima de todo y <b>se traga el clic</b>: mientras
     * está puesta, nada de debajo responde. Si no, un clic destinado al
     * «confirmar» que cae medio píxel fuera acabaría seleccionando otra fila —
     * y el jugador creería que compró lo que no era.
     */
    private void dibujarConfirmacion(DrawContext ctx, int rx, int ry) {
        ctx.fill(x0, y0, x0 + ancho, y0 + alto, 0xC0000000);
        int aw = 480, ah = 200;
        int ax = (NAT_ANCHO - aw) / 2, ay = (NAT_ALTO - ah) / 2;
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah), 0xFF121722);
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), BORDE_ENCIMA, Math.max(1, pl(3)));

        String nombre = confirmando.mote().isBlank()
                ? confirmando.especie() : confirmando.mote();
        texto(ctx, Text.translatable("pokepad.lunaeternal.gts.confirmar_titulo"),
                ax + aw / 2, ay + 22, 22, 0xFFFFFFFF, true, false);
        texto(ctx, Text.literal(nombre + "  Nv " + confirmando.nivel()
                        + (confirmando.shiny() ? "  ✦" : "")),
                ax + aw / 2, ay + 62, 20,
                confirmando.shiny() ? SHINY : 0xFFE8EEF8, true, false);
        texto(ctx, Text.literal(String.format("%,d", confirmando.precio())),
                ax + aw / 2, ay + 92, 30, ORO, true, false);

        // Lo que te queda después. Es la cifra que de verdad decide, y la que
        // nadie calcula de cabeza con el saldo en la otra punta de la pantalla.
        if (estado != null) {
            long despues = estado.saldo() - confirmando.precio();
            texto(ctx, Text.translatable("pokepad.lunaeternal.gts.te_quedan",
                            String.format("%,d", Math.max(0, despues))),
                    ax + aw / 2, ay + 128, 15,
                    despues < 0 ? ROJO : TEXTO_SUAVE, true, false);
        }
        botonPeq(ctx, rx, ry, ax + 30, ay + ah - 52, 190, 36,
                Text.translatable("pokepad.lunaeternal.gts.cancelar"), true, false);
        boton(ctx, rx, ry, ax + aw - 220, ay + ah - 56, 190, 44,
                Text.translatable("pokepad.lunaeternal.gts.confirmar"),
                !esperando(), VERDE);
    }

    /** Tus Pokémon, para elegir cuál publicar. */
    private void dibujarMios(DrawContext ctx, int rx, int ry, boolean tercera) {
        if (estado == null) {
            return;
        }
        var l = estado.disponibles();
        if (!tercera && l.isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.gts.sin_pokemon"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 20, TEXTO_SUAVE,
                    true, false);
            return;
        }
        int desde = pagina * filasCaben();
        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= l.size()) {
                break;
            }
            var m = l.get(i);
            int y = listaY() + 20 + n * FILA;
            int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;

            if (tercera) {
                var id = Identifier.tryParse("cobblemon:"
                        + m.especie().toLowerCase(java.util.Locale.ROOT));
                if (id != null) {
                    Mascota3D.dibujarEspecie(ctx, id, "gtsmio:" + m.uuid(),
                            m.shiny() ? "shiny" : "", px(ax + 6), py(y + 2),
                            pl(48), pl(48), 0.10f, 0f, false);
                }
                continue;
            }

            boolean sel = m.uuid().equals(elegidoUuid);
            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA - 4));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA - 4),
                    sel ? FILA_SEL : (encima ? 0xFFD3DCF2 : FILA_FONDO));
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA - 4),
                    sel ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(sel ? 3 : 2)));

            String nombre = m.mote().isBlank() ? m.especie() : m.mote();
            texto(ctx, Text.literal(nombre + (m.shiny() ? " ✦" : "")),
                    ax + 60, y + 8, 19, m.shiny() ? 0xFF8A6A00 : TEXTO_OSCURO,
                    false, true);
            // ⚠ SE DICE DE DONDE SALE. Un Pokémon del PC se puede vender igual
            //   que uno del equipo, pero saber cuál estás vendiendo evita el
            //   susto de listar el que llevabas puesto.
            texto(ctx, Text.translatable("EQUIPO".equals(m.donde())
                            ? "pokepad.lunaeternal.gts.del_equipo"
                            : "pokepad.lunaeternal.gts.del_pc"),
                    ax + 60, y + 30, 13, TEXTO_SUAVE, false, true);
            texto(ctx, Text.literal("Nv " + m.nivel()), ax + 300, y + 16, 16,
                    TEXTO_OSCURO, false, true);
            texto(ctx, Text.translatable("pokepad.lunaeternal.gts.vale",
                            String.format("%,d", m.estimado())),
                    ax + 420, y + 16, 15, 0xFF8A6A00, false, true);
        }
        if (!tercera && paginas() > 1) {
            int y = PANT_Y + PANT_H - MARGEN - 28;
            botonPeq(ctx, rx, ry, PANT_X + MARGEN, y, 60, 26, Text.literal("<"),
                    pagina > 0, false);
            texto(ctx, Text.literal((pagina + 1) + " / " + paginas()),
                    PANT_X + MARGEN + 100, y + 5, 15, TEXTO_SUAVE, false, true);
            botonPeq(ctx, rx, ry, PANT_X + MARGEN + 160, y, 60, 26, Text.literal(">"),
                    pagina < paginas() - 1, false);
        }
    }

    /** La hoja de filtros, encima de la lista. */
    private void dibujarFiltros(DrawContext ctx, int rx, int ry) {
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        int ay = PANT_Y + MARGEN + 44, ah = PANT_H - MARGEN - 60;
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah), 0xF0121722);
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), BORDE_ENCIMA, Math.max(1, pl(2)));

        texto(ctx, Text.translatable("pokepad.lunaeternal.gts.filtros"),
                ax + 16, ay + 10, 20, ORO, false, false);

        texto(ctx, Text.translatable("pokepad.lunaeternal.gts.nivel"),
                PANT_X + 30, PANT_Y + 98, 15, TEXTO_SUAVE, false, false);
        campoNivelMin.render(ctx, rx, ry, 0);
        texto(ctx, Text.literal("—"), PANT_X + 168, PANT_Y + 124, 15,
                TEXTO_SUAVE, false, false);
        campoNivelMax.render(ctx, rx, ry, 0);

        texto(ctx, Text.translatable("pokepad.lunaeternal.gts.precio"),
                PANT_X + 400, PANT_Y + 98, 15, TEXTO_SUAVE, false, false);
        campoPrecioMin.render(ctx, rx, ry, 0);
        texto(ctx, Text.literal("—"), PANT_X + 558, PANT_Y + 124, 15,
                TEXTO_SUAVE, false, false);
        campoPrecioMax.render(ctx, rx, ry, 0);

        texto(ctx, Text.translatable("pokepad.lunaeternal.gts.ivs_min"),
                PANT_X + 30, PANT_Y + 176, 15, TEXTO_SUAVE, false, false);
        texto(ctx, Text.translatable("pokepad.lunaeternal.gts.evs_min"),
                PANT_X + 30, PANT_Y + 242, 15, TEXTO_SUAVE, false, false);
        for (int i = 0; i < 6; i++) {
            texto(ctx, Text.literal(SIGLAS[i]), PANT_X + 34 + i * 120, PANT_Y + 202,
                    13, TEXTO_SUAVE, false, false);
            campoIv[i].render(ctx, rx, ry, 0);
            texto(ctx, Text.literal(SIGLAS[i]), PANT_X + 34 + i * 120, PANT_Y + 268,
                    13, TEXTO_SUAVE, false, false);
            campoEv[i].render(ctx, rx, ry, 0);
        }

        // Shiny: una casilla, no un desplegable de tres estados. «Solo shiny» o
        // «todo» es la pregunta real; «solo NO shiny» no la hace nadie.
        int sy = PANT_Y + 316;
        ctx.fill(px(PANT_X + 30), py(sy), px(PANT_X + 54), py(sy + 24),
                soloShiny ? SHINY : 0x33FFFFFF);
        marco(ctx, px(PANT_X + 30), py(sy), pl(24), pl(24), 0x88FFFFFF,
                Math.max(1, pl(2)));
        texto(ctx, Text.translatable("pokepad.lunaeternal.gts.solo_shiny"),
                PANT_X + 64, sy + 5, 16, soloShiny ? SHINY : TEXTO_SUAVE, false, false);

        botonPeq(ctx, rx, ry, PANT_X + PANT_W - 320, PANT_Y + PANT_H - 96, 140, 34,
                Text.translatable("pokepad.lunaeternal.gts.limpiar"), true, false);
        botonPeq(ctx, rx, ry, PANT_X + PANT_W - 170, PANT_Y + PANT_H - 96, 140, 34,
                Text.translatable("pokepad.lunaeternal.gts.aplicar"), true, false);
    }

    private static String queda(long cuando) {
        long s = Math.max(0, (cuando - System.currentTimeMillis()) / 1000);
        if (s < 3600) {
            return (s / 60) + "m";
        }
        if (s < 86400) {
            return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
        }
        return (s / 86400) + "d " + ((s % 86400) / 3600) + "h";
    }

    // ---- interacción -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) {
            return super.mouseClicked(mx, my, boton);
        }
        int rx = (int) mx, ry = (int) my;

        // ⚠⚠ LA CONFIRMACION SE TRAGA EL CLIC. Mientras está puesta, nada de
        //   debajo responde: si no, un clic que cae medio píxel fuera del
        //   «confirmar» seleccionaría otra fila, y el jugador creería que compró
        //   lo que no era.
        if (confirmando != null) {
            return clicConfirmacion(rx, ry);
        }

        for (var c : todos()) {
            if (c != null && c.mouseClicked(mx, my, boton)) {
                setFocused(c);
                return true;
            }
        }

        int cy = py(PANEL_Y + NAV_ALTO / 2);
        if (dentro(rx, ry, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48))) {
            sonar();
            if (client != null) {
                client.setScreen(anterior);
            }
            return true;
        }
        if (dentro(rx, ry, px(PANEL_X + PANEL_W - 18) - pl(80), cy - pl(32),
                pl(80), pl(64))) {
            sonar();
            close();
            return true;
        }

        for (var b : botones()) {
            if (dentro(rx, ry, px(b.x()), py(PANT_Y + MARGEN), pl(b.w()), pl(32))) {
                pulsarBarra(b.id());
                return true;
            }
        }

        if (modo == Modo.FILTROS) {
            return clicFiltros(rx, ry);
        }

        // El botón grande del panel
        if (dentro(rx, ry, px(PANEL_X + 40), py(PANEL_Y + PANEL_H - 76),
                pl(PANEL_W - 80), pl(46))) {
            return pulsarPanel();
        }

        // Pestañas del detalle
        if (modo != Modo.VENDER && seleccionado() != null) {
            for (int i = 0; i < 3; i++) {
                int bx = PANEL_X + 30 + i * 86;
                if (dentro(rx, ry, px(bx), py(pestanasY()), pl(78), pl(26))) {
                    pestanaDetalle = i;
                    sonar();
                    return true;
                }
            }
        }

        // Cabecera: ordenar
        if (modo != Modo.VENDER) {
            int hy = listaY();
            for (var c : COLUMNAS) {
                int ax = PANT_X + MARGEN + c.x();
                if (dentro(rx, ry, px(ax - 4), py(hy - 3), pl(c.ancho()), pl(20))) {
                    // Alterna entre las dos: pulsar otra vez la misma columna
                    // invierte, que es lo que espera todo el mundo.
                    orden = orden.equals(c.asc()) ? c.desc() : c.asc();
                    pagina = 0;
                    sonar();
                    pedir();
                    return true;
                }
            }
            if (dentro(rx, ry, px(PANT_X + PANT_W - MARGEN - 130), py(hy - 6),
                    pl(126), pl(24))) {
                orden = "CHOLLO".equals(orden) ? "NUEVO" : "CHOLLO";
                pagina = 0;
                sonar();
                pedir();
                return true;
            }
        }

        // Filas
        int desde = pagina * filasCaben();
        int n = modo == Modo.VENDER
                ? estado.disponibles().size() : lista().size();
        for (int f = 0; f < filasCaben(); f++) {
            int i = desde + f;
            if (i >= n) {
                break;
            }
            int y = listaY() + 20 + f * FILA;
            int aw = PANT_W - 2 * MARGEN;
            // El botón de la fila va ANTES que la fila entera: si no, pulsar
            // «comprar» solo seleccionaría.
            if (modo != Modo.VENDER && dentro(rx, ry, px(PANT_X + MARGEN + aw - 124),
                    py(y + 12), pl(116), pl(28))) {
                elegido = i;
                var e = lista().get(i);
                sonar();
                if (modo == Modo.MIAS) {
                    pulsado = System.currentTimeMillis();
                    ClientPlayNetworking.send(
                            new Red.AccionGts("retirar", e.id(), "", 0, 0));
                } else if (estado != null && estado.saldo() < e.precio()) {
                    avisar("No te llega: hacen falta "
                            + String.format("%,d", e.precio()) + ".");
                } else {
                    confirmando = e;
                }
                return true;
            }
            if (dentro(rx, ry, px(PANT_X + MARGEN), py(y), pl(aw), pl(FILA - 4))) {
                if (modo == Modo.VENDER) {
                    elegidoUuid = estado.disponibles().get(i).uuid();
                } else {
                    elegido = i;
                }
                aviso = "";
                sonar();
                return true;
            }
        }

        if (paginas() > 1) {
            int y = PANT_Y + PANT_H - MARGEN - 28;
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
        }
        return super.mouseClicked(mx, my, boton);
    }

    private int pestanasY() {
        // Misma cuenta que al dibujar. ⚠ Si el clic y el dibujado la calcularan
        // por separado, pulsar una pestaña abriría la de al lado.
        return RET_Y + RET_H + 8 + 28 + 26 + 34 + 22 + 8;
    }

    private void pulsarBarra(String id) {
        sonar();
        aviso = "";
        switch (id) {
            case "buscar" -> {
                pagina = 0;
                elegido = -1;
                pedir();
            }
            case "filtros" -> modo = modo == Modo.FILTROS ? Modo.LISTA : Modo.FILTROS;
            case "objetos" -> {
                // Los objetos son OTRO mercado (D-041) y por eso son otra
                // pantalla: un libro de órdenes y un escaparate no se parecen.
                if (client != null) {
                    client.setScreen(new MercadoScreen(anterior));
                }
            }
            case "vender" -> {
                modo = modo == Modo.VENDER ? Modo.LISTA : Modo.VENDER;
                pagina = 0;
                elegidoUuid = "";
                pedir();
            }
            case "mias" -> {
                modo = modo == Modo.MIAS ? Modo.LISTA : Modo.MIAS;
                pagina = 0;
                elegido = -1;
            }
            default -> { }
        }
    }

    private boolean pulsarPanel() {
        if (modo == Modo.VENDER) {
            var m = mioSeleccionado();
            long precio = numeroLargo(campoPrecio);
            if (m == null) {
                return avisar("Elige un Pokémon de la lista.");
            }
            if (precio <= 0) {
                return avisar("Escribe un precio.");
            }
            pulsado = System.currentTimeMillis();
            sonar();
            ClientPlayNetworking.send(new Red.AccionGts("publicar", 0,
                    m.uuid(), precio, 48));
            return true;
        }
        var e = seleccionado();
        if (e == null) {
            return false;
        }
        if (modo == Modo.MIAS) {
            pulsado = System.currentTimeMillis();
            sonar();
            ClientPlayNetworking.send(new Red.AccionGts("retirar", e.id(), "", 0, 0));
            return true;
        }
        if (estado != null && estado.saldo() < e.precio()) {
            return avisar("No te llega: hacen falta "
                    + String.format("%,d", e.precio()) + ".");
        }
        pulsado = System.currentTimeMillis();
        sonar();
        // ⚠ NO se manda el precio: lo cobra el servidor mirando SU fila. Si
        //   viniera de aquí, un cliente modificado compraría un shiny por 1.
        ClientPlayNetworking.send(new Red.AccionGts("comprar", e.id(), "", 0, 0));
        return true;
    }

    private boolean clicConfirmacion(int rx, int ry) {
        int aw = 480, ah = 200;
        int ax = (NAT_ANCHO - aw) / 2, ay = (NAT_ALTO - ah) / 2;
        if (dentro(rx, ry, px(ax + 30), py(ay + ah - 52), pl(190), pl(36))) {
            confirmando = null;
            sonar();
            return true;
        }
        if (dentro(rx, ry, px(ax + aw - 220), py(ay + ah - 56), pl(190), pl(44))) {
            var e = confirmando;
            confirmando = null;
            pulsado = System.currentTimeMillis();
            sonar();
            // ⚠ NO se manda el precio: lo cobra el servidor mirando SU fila.
            ClientPlayNetworking.send(new Red.AccionGts("comprar", e.id(), "", 0, 0));
            return true;
        }
        // Cualquier otro clic no hace nada, pero SE CONSUME. Ver el comentario
        // de `dibujarConfirmacion`.
        return true;
    }

    private boolean clicFiltros(int rx, int ry) {
        int sy = PANT_Y + 316;
        if (dentro(rx, ry, px(PANT_X + 30), py(sy), pl(24), pl(24))) {
            soloShiny = !soloShiny;
            sonar();
            return true;
        }
        if (dentro(rx, ry, px(PANT_X + PANT_W - 320), py(PANT_Y + PANT_H - 96),
                pl(140), pl(34))) {
            for (var c : todos()) {
                if (c != campoBusqueda && c != campoPrecio) {
                    c.setText("");
                }
            }
            soloShiny = false;
            sonar();
            return true;
        }
        if (dentro(rx, ry, px(PANT_X + PANT_W - 170), py(PANT_Y + PANT_H - 96),
                pl(140), pl(34))) {
            modo = Modo.LISTA;
            pagina = 0;
            elegido = -1;
            sonar();
            pedir();
            return true;
        }
        return true;
    }

    private boolean avisar(String texto) {
        aviso = texto;
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 0.8f);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        // ⚠ ESC cancela la compra en vez de cerrar la pantalla. Una confirmación
        //   de la que solo se sale pulsando un botón es una trampa: lo primero
        //   que hace todo el mundo para arrepentirse es dar a Escape.
        if (confirmando != null) {
            if (tecla == 256) {
                confirmando = null;
                return true;
            }
            return true;
        }
        for (var c : todos()) {
            if (c != null && c.isFocused() && c.keyPressed(tecla, escaneo, mods)) {
                return true;
            }
        }
        // Enter busca: es lo que hace todo el mundo tras escribir en un buscador.
        if (tecla == 257 && campoBusqueda != null && campoBusqueda.isFocused()) {
            pulsarBarra("buscar");
            return true;
        }
        return super.keyPressed(tecla, escaneo, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        for (var campo : todos()) {
            if (campo != null && campo.isFocused()) {
                // ⚠ Solo el buscador acepta letras. En un campo de nivel o de
                //   precio, una letra solo puede ser un error -- y rechazarla al
                //   teclear avisa antes que rechazarla al pulsar.
                if (campo != campoBusqueda && !Character.isDigit(c)) {
                    return false;
                }
                return campo.charTyped(c, mods);
            }
        }
        return super.charTyped(c, mods);
    }

    private void sonar() {
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.0f);
        }
    }

    private long numeroLargo(TextFieldWidget c) {
        try {
            String s = texto(c);
            return s.isEmpty() ? 0 : Math.max(0, Long.parseLong(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- utilidades --------------------------------------------------------

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

    private List<String> partir(String texto, int anchoMax, int altoArte) {
        var salida = new ArrayList<String>();
        var actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
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
        return Math.round(textRenderer.getWidth(linea) * alto / (float) textRenderer.fontHeight);
    }

    private void separador(DrawContext ctx, int artY) {
        ctx.fill(px(PANEL_X + 28), py(artY), px(PANEL_X + PANEL_W - 28),
                py(artY) + Math.max(1, pl(2)), SEPARADOR);
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
