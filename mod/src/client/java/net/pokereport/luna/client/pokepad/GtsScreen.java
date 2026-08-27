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

    /**
     * Alto de una fila.
     *
     * <h2>⚠⚠ Menos filas y más altas, no al revés</h2>
     *
     * A 54 px había que meter dentro nueve cosas —sprite, nombre, nivel,
     * vendedor, seis IVs, precio, comparación, tiempo y un botón— y el
     * resultado era una pared de texto gris donde no se distinguía nada.
     *
     * <p>A 76 caben <b>cinco</b> en vez de siete, y eso es lo que se gana: el
     * sprite se ve, el nombre se lee, y los tipos entran con su color. Una lista
     * de la que no se puede leer nada no sirve de más porque quepan dos filas
     * más.
     */
    private static final int FILA = 70;

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

    /** En qué mitad del mercado estamos. Ver `dibujarConmutador`. */
    private static final boolean ES_POKEMON = true;

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

    /**
     * Cuánto dura la oferta.
     *
     * <h2>⚠ Botones y no un número, y no es por comodidad</h2>
     *
     * Un campo libre invita a escribir «168» y a que alguien ponga 1 hora sin
     * querer. Tres opciones dicen <b>cuáles son las opciones</b>, que es medio
     * trabajo de una interfaz: el jugador no tiene que saber en qué unidad se
     * mide ni cuál es el máximo.
     *
     * <p>⚠ El servidor lo acota igual (1..168 h). Esto es comodidad; la regla
     * vive allí (P6).
     */
    private int horas = 48;

    private static final int[] DURACIONES = { 24, 48, 168 };

    /** Las seis estadísticas, en el orden FIJO del protocolo. */
    private static final String[] SIGLAS = { "PS", "AT", "DE", "SA", "SD", "VE" };

    public GtsScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.gts"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();

        // El buscador ocupa lo que dejan los seis iconos, que van pegados a la
        // derecha. Se calcula en vez de escribirse: con un icono mas, un ancho
        // a mano se solaparia sin dar ningun error.
        // ⚠ Segunda fila y a todo lo ancho: en la primera están el conmutador
        //   y los seis iconos, y un buscador espachurrado entre los dos no lo
        //   usa nadie.
        campoBusqueda = campo(PANT_X + MARGEN, PANT_Y + MARGEN + BOT + 6,
                PANT_W - 2 * MARGEN, 32);
        campoPrecio = campo(PANEL_X + 30, PANEL_Y + 524, PANEL_W - 60, 11);
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

    /**
     * Cuántas filas caben.
     *
     * <h2>⚠⚠ SE CALCULA DE {@code listaY()}, NO SE ESCRIBE A MANO</h2>
     *
     * Aquí había un {@code (PANT_H - 2*MARGEN - 56 - 30) / FILA} que <b>ya no
     * cuadraba con nada</b>: la lista había bajado (conmutador y buscador
     * nuevos) y la fórmula seguía contando desde donde estaba antes. Salían
     * cinco filas donde solo caben cuatro, y <b>la quinta se dibujaba encima de
     * la paginación</b> — que es exactamente lo que el usuario vio.
     *
     * <p>No daba ningún error. Es el mismo fallo de la rejilla del PokePad:
     * <i>cuadraba por casualidad</i> hasta que una medida cambió.
     */
    private static final int PIE = 34;

    private int filasCaben() {
        int hueco = (PANT_Y + PANT_H - MARGEN - PIE) - (listaY() + 18);
        return Math.max(1, hueco / FILA);
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
        // ⚠⚠ NADA DE 3D MIENTRAS HAY CONFIRMACION. El 3D va en su propio lote y
        //   NO respeta el orden de dibujado de la interfaz, así que se pintaba
        //   ENCIMA del diálogo -- el Pokémon salía flotando sobre el aviso. No
        //   es un problema de capas que se arregle moviendo código: la única
        //   forma es no dibujarlo.
        if (confirmando == null) {
            if (modo == Modo.VENDER) {
                dibujarMios(ctx, rx, ry, true);
            } else if (modo != Modo.FILTROS) {
                dibujarLista(ctx, rx, ry, true);
            }
            dibujarRetrato(ctx, delta);
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

    // ⚠ El retrato ocupa ahora 260 px de los 692 del panel, no 168. Era lo que
    //   más se notaba de la referencia del usuario: allí el Pokémon es lo
    //   primero que ves, y aquí era un sello pequeño en un panel medio vacío.
    private static final int RET_X = PANEL_X + 24, RET_Y = PANEL_Y + NAV_ALTO + 4;
    private static final int RET_W = PANEL_W - 48;

    /**
     * ⚠ El retrato mide distinto al comprar y al vender, y es a propósito: al
     * publicar hay que meter debajo el precio, la duración y las
     * características, y con 260 px de retrato <b>el campo del precio se
     * solapaba con el botón</b> — que es justo lo que se veía.
     */
    private int retAlto() {
        return modo == Modo.VENDER ? 190 : 220;
    }

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
                shiny ? "shiny" : "", px(RET_X), py(RET_Y), pl(RET_W), pl(retAlto()),
                0.10f, delta, true);
    }

    /** Izquierda: el detalle del elegido, o el formulario de publicar. */
    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        int cx = PANEL_X + PANEL_W / 2;
        boolean vender = modo == Modo.VENDER;
        var e = seleccionado();
        var m = mioSeleccionado();

        if ((vender && m == null) || (!vender && e == null)) {
            // ⚠ El texto DEPENDE DEL MODO. Decía «elige una oferta» también
            //   mientras elegías cuál de TUS Pokémon vender, que es justo lo
            //   contrario de lo que estabas haciendo.
            String clave = vender ? "pokepad.lunaeternal.gts.elige_tuyo"
                    : modo == Modo.MIAS ? "pokepad.lunaeternal.gts.elige_mia"
                    : "pokepad.lunaeternal.gts.elige";
            int y = PANEL_Y + 200;
            for (String linea : partir(Text.translatable(clave).getString(),
                    PANEL_W - 70, 17)) {
                texto(ctx, Text.literal(linea), cx, y, 17, TEXTO_SUAVE, true, false);
                y += 22;
            }
            // Y el saldo, que es lo único útil que se puede enseñar aquí sin
            // haber elegido nada. Un panel vacío del alto de la pantalla se ve
            // como que falta algo.
            y += 24;
            separador(ctx, y);
            texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.tu_plata"),
                    cx, y + 16, 15, TEXTO_SUAVE, true, false);
            texto(ctx, Text.literal(estado == null ? "—"
                            : String.format("%,d", estado.saldo())),
                    cx, y + 36, 26, 0xFFFFFFFF, true, false);
            return;
        }

        // Hueco del retrato: se marca aunque el modelo lo tape, para que se vea
        // que ahí va algo mientras carga.
        ctx.fill(px(RET_X), py(RET_Y), px(RET_X + RET_W), py(RET_Y + retAlto()),
                0x33000000);
        marco(ctx, px(RET_X), py(RET_Y), pl(RET_W), pl(retAlto()), 0x556A7398,
                Math.max(1, pl(2)));

        String nombre = vender
                ? (m.mote().isBlank() ? especieEs(m.especie()).getString() : m.mote())
                : (e.mote().isBlank() ? especieEs(e.especie()).getString() : e.mote());
        String especie = vender ? m.especie() : e.especie();
        int nivel = vender ? m.nivel() : e.nivel();
        boolean shiny = vender ? m.shiny() : e.shiny();

        int y = RET_Y + retAlto() + 8;
        int altoNombre = 26;
        while (altoNombre > 15 && anchoArte(nombre, altoNombre) > PANEL_W - 60) {
            altoNombre--;
        }
        texto(ctx, Text.literal(nombre), cx, y, altoNombre,
                shiny ? SHINY : 0xFFFFFFFF, true, false);
        y += altoNombre + 8;

        // Los tipos, centrados y con su color. Es lo que da identidad de un
        // vistazo -- sin ellos, todo el panel es texto gris.
        var ts = tipos(especie);
        if (!ts.isEmpty()) {
            int total = 0;
            for (var tp : ts) {
                total += anchoArte(tp.nombre(), 13) + 20;
            }
            int tx = cx - total / 2;
            for (var tp : ts) {
                tx += insignia(ctx, tp, tx, y, 20);
            }
            y += 26;
        }

        texto(ctx, Text.literal("Nv " + nivel + (shiny ? "   ✦ SHINY" : "")),
                cx, y, 16, shiny ? SHINY : TEXTO_SUAVE, true, false);
        y += 24;

        if (vender) {
            // Las características, aquí y en español. El usuario las pidió
            // justo aquí: es donde decides el precio, así que es donde hace
            // falta saber qué estás vendiendo.
            separador(ctx, y);
            y += 8;
            fila(ctx, Text.translatable("pokepad.lunaeternal.gts.f_naturaleza"),
                    naturalezaEs(m.naturaleza()), y);
            fila(ctx, Text.translatable("pokepad.lunaeternal.gts.f_habilidad"),
                    habilidadEs(m.habilidad()), y + 20);
            fila(ctx, Text.translatable("pokepad.lunaeternal.gts.f_ivs"),
                    Text.literal(perfectosDe(m.ivs()) + " × 31"), y + 40);
            fila(ctx, Text.translatable("pokepad.lunaeternal.gts.f_donde"),
                    Text.translatable("EQUIPO".equals(m.donde())
                            ? "pokepad.lunaeternal.gts.del_equipo"
                            : "pokepad.lunaeternal.gts.del_pc"), y + 60);
            y += 84;

            separador(ctx, y);
            texto(ctx, Text.translatable("pokepad.lunaeternal.gts.estimado"),
                    cx, y + 8, 13, TEXTO_SUAVE, true, false);
            texto(ctx, Text.literal(String.format("%,d", m.estimado())),
                    cx, y + 24, 24, ORO, true, false);

            texto(ctx, Text.translatable("pokepad.lunaeternal.gts.tu_precio"),
                    PANEL_X + 30, PANEL_Y + 508, 13, TEXTO_SUAVE, false, false);
            campoPrecio.render(ctx, rx, ry, 0);

            // ---- LA DURACIÓN. Tres botones, no un número escrito.
            texto(ctx, Text.translatable("pokepad.lunaeternal.gts.duracion"),
                    PANEL_X + 30, PANEL_Y + 562, 13, TEXTO_SUAVE, false, false);
            int bw = (PANEL_W - 60) / 3 - 4;
            for (int i = 0; i < DURACIONES.length; i++) {
                int bx = PANEL_X + 30 + i * (bw + 6);
                boolean act = horas == DURACIONES[i];
                boolean enc = dentro(rx, ry, px(bx), py(PANEL_Y + 578), pl(bw), pl(30));
                ctx.fill(px(bx), py(PANEL_Y + 578), px(bx + bw), py(PANEL_Y + 608),
                        act ? BORDE_ENCIMA : (enc ? 0xFF5E86D8 : 0xFF3A4560));
                marco(ctx, px(bx), py(PANEL_Y + 578), pl(bw), pl(30),
                        act ? 0xFFFFC46B : 0xFF20283C, Math.max(1, pl(2)));
                texto(ctx, Text.translatable("pokepad.lunaeternal.gts.dur_"
                                + DURACIONES[i]),
                        bx + bw / 2, PANEL_Y + 586, 14,
                        act ? 0xFF2A1C00 : 0xFFFFFFFF, true, false);
            }

            long precio = numeroLargo(campoPrecio);
            boton(ctx, rx, ry, PANEL_X + 30, PANEL_Y + PANEL_H - 74, PANEL_W - 60, 48,
                    Text.translatable("pokepad.lunaeternal.gts.publicar"),
                    precio > 0 && !esperando(), VERDE);
            return;
        }

        // El precio, que es la cifra grande de la pantalla.
        texto(ctx, Text.literal(String.format("%,d", e.precio())),
                cx, y, 32, ORO, true, false);
        y += 36;
        if (e.estimado() > 0) {
            boolean caro = e.precio() > e.estimado() * 1.3;
            boolean chollo = e.precio() < e.estimado() * 0.7;
            texto(ctx, Text.translatable(caro ? "pokepad.lunaeternal.gts.caro"
                            : chollo ? "pokepad.lunaeternal.gts.chollo"
                            : "pokepad.lunaeternal.gts.justo",
                            String.format("%,d", e.estimado())),
                    cx, y, 13, caro ? ROJO : chollo ? 0xFF4FD07A : TEXTO_SUAVE,
                    true, false);
        }
        y += 20;
        separador(ctx, y);
        y += 8;

        String[] tabs = { "EST", "IVS", "EVS" };
        int anchoTab = (PANEL_W - 60) / 3 - 4;
        for (int i = 0; i < 3; i++) {
            int bx = PANEL_X + 30 + i * (anchoTab + 6);
            boolean act = i == pestanaDetalle;
            ctx.fill(px(bx), py(y), px(bx + anchoTab), py(y + 24),
                    act ? 0xFF3A3020 : 0x33000000);
            marco(ctx, px(bx), py(y), pl(anchoTab), pl(24),
                    act ? BORDE_ENCIMA : 0x556A7398, Math.max(1, pl(2)));
            texto(ctx, Text.literal(tabs[i]), bx + anchoTab / 2, y + 5, 14,
                    act ? ORO : TEXTO_SUAVE, true, false);
        }
        y += 32;
        dibujarDetalle(ctx, e, y);

        boolean mio = modo == Modo.MIAS;
        boton(ctx, rx, ry, PANEL_X + 30, PANEL_Y + PANEL_H - 74, PANEL_W - 60, 48,
                Text.translatable(mio ? "pokepad.lunaeternal.gts.retirar"
                        : "pokepad.lunaeternal.gts.comprar"),
                !esperando(), mio ? ROJO : VERDE);
    }

    private void dibujarDetalle(DrawContext ctx, Red.EjemplarGts e, int y) {
        switch (pestanaDetalle) {
            case 1 -> barras(ctx, e.ivs(), 31, y);
            case 2 -> barras(ctx, e.evs(), 252, y);
            default -> {
                fila(ctx, Text.translatable("pokepad.lunaeternal.gts.f_naturaleza"),
                        naturalezaEs(e.naturaleza()), y);
                fila(ctx, Text.translatable("pokepad.lunaeternal.gts.f_habilidad"),
                        habilidadEs(e.habilidad()), y + 19);
                fila(ctx, Text.translatable("pokepad.lunaeternal.gts.f_genero"),
                        generoEs(e.genero()), y + 38);
                fila(ctx, Text.translatable("pokepad.lunaeternal.gts.f_tera"),
                        Text.literal(bonito(e.tera())), y + 57);
                fila(ctx, Text.translatable("pokepad.lunaeternal.gts.f_rareza"),
                        rarezaEs(e.rareza()), y + 76);
                fila(ctx, Text.translatable("pokepad.lunaeternal.gts.f_vendedor"),
                        Text.literal(e.vendedor()), y + 95);
            }
        }
    }

    private void fila(DrawContext ctx, Text etiqueta, Text valor, int y) {
        texto(ctx, etiqueta, PANEL_X + 30, y, 14, TEXTO_SUAVE, false, false);
        String v = valor.getString();
        if (v.isBlank()) {
            v = "—";
        }
        // Se encoge hasta caber en su mitad: una naturaleza larga no puede
        // empujar el valor fuera del panel.
        int alto = 14;
        while (alto > 9 && anchoArte(v, alto) > PANEL_W / 2 - 20) {
            alto--;
        }
        textoDer(ctx, Text.literal(v), PANEL_X + PANEL_W - 30, y, alto,
                0xFFE8EEF8, false);
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
            textoDer(ctx, Text.literal(String.valueOf(v)), PANEL_X + PANEL_W - 30,
                    fy + 2, 14, v >= max ? SHINY : 0xFFE8EEF8, false);
        }
    }

    // ---- la barra de arriba ------------------------------------------------

    /**
     * Un botón de la barra. <b>Icono, no texto.</b>
     *
     * <p>⚠ Los botones de texto llenaban la barra entera y la hacían parecer un
     * formulario. Una barra de herramientas se lee <b>por forma</b>, no por
     * palabra: la lupa se reconoce antes de leerse, y así caben seis en el
     * espacio en el que antes cabían tres.
     *
     * <p>Y como un icono solo no siempre se entiende, <b>cada uno dice su nombre
     * al pasar el ratón</b>. Es lo que hace que un icono sea aprendible en vez
     * de un acertijo.
     */
    private record Boton(String id, String etiqueta) {}

    private static final Boton[] BARRA = {
        new Boton("buscar", "pokepad.lunaeternal.gts.b_buscar"),
        new Boton("refrescar", "pokepad.lunaeternal.gts.b_refrescar"),
        new Boton("filtros", "pokepad.lunaeternal.gts.b_filtros"),
        new Boton("vender", "pokepad.lunaeternal.gts.b_vender"),
        new Boton("mias", "pokepad.lunaeternal.gts.b_mias"),
        new Boton("chollos", "pokepad.lunaeternal.gts.b_chollos"),
    };

    /** Lado de un botón de la barra, en unidades de arte. */
    private static final int BOT = 34;
    private static final int BOT_SEP = 8;

    private int botonX(int i) {
        // Pegados a la derecha: el buscador crece hacia ellos y así el hueco
        // sobrante queda en medio, donde no molesta.
        int total = BARRA.length * BOT + (BARRA.length - 1) * BOT_SEP;
        return PANT_X + PANT_W - MARGEN - total + i * (BOT + BOT_SEP);
    }

    private void dibujarBarra(DrawContext ctx, int rx, int ry) {
        dibujarConmutador(ctx, rx, ry);
        campoBusqueda.render(ctx, rx, ry, 0);
        if (texto(campoBusqueda).isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.gts.buscar_pista"),
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
                case "filtros" -> modo == Modo.FILTROS;
                case "chollos" -> "CHOLLO".equals(orden);
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
                case "filtros" -> Iconos.embudo(ctx, cx, cy, lado, color);
                case "vender" -> Iconos.mas(ctx, cx, cy, lado, color);
                case "mias" -> Iconos.lista(ctx, cx, cy, lado, color);
                case "chollos" -> Iconos.etiqueta(ctx, cx, cy, lado, color);
                default -> { }
            }
        }

        // La etiqueta del que está bajo el ratón, debajo de la barra. Va aquí y
        // no flotando junto al cursor porque flotando taparía la primera fila,
        // que es justo la que se está mirando.
        if (encimaDe != null) {
            // Sobre el buscador, que es la unica franja libre de esa fila.
            textoDer(ctx, Text.translatable(encimaDe),
                    PANT_X + PANT_W - MARGEN - 8, PANT_Y + MARGEN + BOT + 14, 13,
                    ORO, true);
        }
        if (!aviso.isEmpty()) {
            texto(ctx, Text.literal(aviso), PANT_X + MARGEN,
                    PANT_Y + PANT_H - 20, 15, ROJO, false, false);
        }
    }

    // ---- la lista ----------------------------------------------------------

    /** ⚠ Misma cuenta que la maqueta: fila 1 (34) + 6 + buscador (28) + 34. */
    private int listaY() {
        return PANT_Y + MARGEN + BOT + 6 + 28 + 34;
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
            int y = listaY() + 18 + n * FILA;
            int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;

            if (tercera) {
                var id = Identifier.tryParse("cobblemon:"
                        + e.especie().toLowerCase(java.util.Locale.ROOT));
                if (id != null) {
                    Mascota3D.dibujarEspecie(ctx, id,
                            "gtsfila:" + e.id(), e.shiny() ? "shiny" : "",
                            px(ax + 6), py(y + 3), pl(58), pl(58), 0.10f, 0f, false);
                }
                continue;
            }

            boolean sel = i == elegido;
            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA - 6));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA - 6),
                    sel ? FILA_SEL : (encima ? 0xFFD3DCF2 : FILA_FONDO));
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA - 6),
                    sel ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(sel ? 3 : 2)));

            // ---- IZQUIERDA: nombre y tipos. Es la identidad de la oferta.
            String nombre = e.mote().isBlank()
                    ? especieEs(e.especie()).getString() : e.mote();
            int altoN = 21;
            while (altoN > 13 && anchoArte(nombre, altoN) > 210) {
                altoN--;
            }
            texto(ctx, Text.literal(nombre + (e.shiny() ? " ✦" : "")),
                    ax + 70, y + 8, altoN, e.shiny() ? 0xFF8A6A00 : TEXTO_OSCURO,
                    false, true);
            int tx = ax + 70;
            for (var tp : tipos(e.especie())) {
                tx += insignia(ctx, tp, tx, y + 34, 17);
            }
            texto(ctx, Text.literal("Nv " + e.nivel()), ax + 300, y + 14, 16,
                    TEXTO_OSCURO, false, true);

            // ---- MEDIO: la calidad, resumida. ⚠ Antes iban los seis IVs en
            //      crudo («28/21/11/9/18/0»), que nadie lee de un vistazo. Lo
            //      que se busca de verdad es CUANTOS 31 tiene; el desglose está
            //      en el panel, a un clic.
            int perfectos = 0;
            for (int v : e.ivs()) {
                if (v >= 31) {
                    perfectos++;
                }
            }
            if (perfectos > 0) {
                int c = perfectos >= 5 ? 0xFF1F7A3C : perfectos >= 3
                        ? 0xFF8A6A00 : TEXTO_SUAVE;
                ctx.fill(px(ax + 296), py(y + 36), px(ax + 296 + 74), py(y + 53),
                        perfectos >= 5 ? 0x331F7A3C : 0x22000000);
                texto(ctx, Text.literal(perfectos + " × 31"), ax + 333, y + 38,
                        14, c, true, false);
            } else {
                texto(ctx, Text.literal("—"), ax + 333, y + 38, 14, TEXTO_SUAVE,
                        true, false);
            }

            // ---- DERECHA: el precio, grande, y qué tal está.
            int precioDer = ax + aw - 150;
            textoDer(ctx, Text.literal(String.format("%,d", e.precio())),
                    precioDer, y + 10, 21, 0xFF8A6A00, true);
            if (e.estimado() > 0) {
                double razon = e.precio() / (double) e.estimado();
                textoDer(ctx, Text.translatable(razon < 0.7
                                ? "pokepad.lunaeternal.gts.et_chollo"
                                : razon > 1.3 ? "pokepad.lunaeternal.gts.et_caro"
                                : "pokepad.lunaeternal.gts.et_justo"),
                        precioDer, y + 34, 12,
                        razon < 0.7 ? 0xFF1F7A3C : razon > 1.3 ? ROJO : TEXTO_SUAVE,
                        true);
            }
            textoDer(ctx, Text.literal(queda(e.expira())), precioDer, y + 48, 11,
                    TEXTO_SUAVE, true);

            // ⚠⚠ EL BOTON VA EN LA FILA. Antes había que seleccionar y bajar la
            //   vista al panel: dos clics y un salto de atención para lo único
            //   que se viene a hacer aquí.
            botonPeq(ctx, rx, ry, ax + aw - 132, y + 16, 124, 32,
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

    // ⚠ Las x coinciden con las de la fila. Si se separaran, la cabecera diría
    //   que ordena por una columna y la flecha señalaría a otra.
    private static final Columna[] COLUMNAS = {
        new Columna("pokepad.lunaeternal.gts.col_oferta", 74, 200, "NIVEL_ASC", "NIVEL_DESC"),
        new Columna("pokepad.lunaeternal.gts.col_ivs", 296, 90, "IVS_DESC", "IVS_DESC"),
        new Columna("pokepad.lunaeternal.gts.col_precio", 470, 140, "PRECIO_ASC", "PRECIO_DESC"),
        // ⚠⚠ Su `desc` NO puede ser "NUEVO": ese es el orden POR DEFECTO, asi que
        //    la columna salia marcada nada mas abrir --oro sobre naranja, que no
        //    se lee-- diciendo que ordenaba por caducidad cuando no lo hacia.
        new Columna("pokepad.lunaeternal.gts.col_expira", 620, 100,
                "EXPIRA_ASC", "EXPIRA_DESC"),
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
                // ⚠ La activa va OSCURA con el texto claro. Al reves --naranja
                //   claro con texto oro-- no se lee: son el mismo tono.
                ctx.fill(px(ax - 4), py(hy - 3), px(ax - 4 + c.ancho()), py(hy + 17),
                        activa ? 0xCC1E2438 : 0x22FFFFFF);
            }
            String flecha = !activa ? " —" : orden.equals(c.asc()) ? " ▲" : " ▼";
            texto(ctx, Text.translatable(c.etiqueta()).copy()
                            .append(Text.literal(flecha)),
                    ax, hy, 14, activa ? ORO : TEXTO_SUAVE, false, false);
        }
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
        textoDer(ctx, Text.translatable("pokepad.lunaeternal.gts.contador",
                        desde, hasta, n),
                PANT_X + PANT_W - MARGEN - 4, PANT_Y + PANT_H - MARGEN - 20, 13,
                TEXTO_SUAVE, true);
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
                ? especieEs(confirmando.especie()).getString() : confirmando.mote();
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
            int y = listaY() + 18 + n * FILA;
            int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;

            if (tercera) {
                var id = Identifier.tryParse("cobblemon:"
                        + m.especie().toLowerCase(java.util.Locale.ROOT));
                if (id != null) {
                    Mascota3D.dibujarEspecie(ctx, id, "gtsmio:" + m.uuid(),
                            m.shiny() ? "shiny" : "", px(ax + 6), py(y + 3),
                            pl(58), pl(58), 0.10f, 0f, false);
                }
                continue;
            }

            boolean sel = m.uuid().equals(elegidoUuid);
            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA - 6));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA - 6),
                    sel ? FILA_SEL : (encima ? 0xFFD3DCF2 : FILA_FONDO));
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA - 6),
                    sel ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(sel ? 3 : 2)));

            String nombre = m.mote().isBlank()
                    ? especieEs(m.especie()).getString() : m.mote();
            texto(ctx, Text.literal(nombre + (m.shiny() ? " ✦" : "")),
                    ax + 70, y + 8, 20, m.shiny() ? 0xFF8A6A00 : TEXTO_OSCURO,
                    false, true);
            int tx2 = ax + 70;
            for (var tp : tipos(m.especie())) {
                tx2 += insignia(ctx, tp, tx2, y + 34, 17);
            }
            texto(ctx, Text.literal("Nv " + m.nivel()), ax + 300, y + 14, 16,
                    TEXTO_OSCURO, false, true);
            // ⚠ SE DICE DE DONDE SALE. Un Pokémon del PC se puede vender igual
            //   que uno del equipo, pero saber cuál estás vendiendo evita el
            //   susto de listar el que llevabas puesto.
            texto(ctx, Text.translatable("EQUIPO".equals(m.donde())
                            ? "pokepad.lunaeternal.gts.del_equipo"
                            : "pokepad.lunaeternal.gts.del_pc"),
                    ax + 300, y + 36, 12, TEXTO_SUAVE, false, true);
            textoDer(ctx, Text.translatable("pokepad.lunaeternal.gts.vale",
                            String.format("%,d", m.estimado())),
                    ax + aw - 16, y + 22, 16, 0xFF8A6A00, true);
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

    /**
     * El conmutador POKÉMON / OBJETOS.
     *
     * <h2>⚠⚠ A LA DERECHA, y por dos motivos</h2>
     *
     * El usuario lo pidió ahí — <i>«que a la derecha se pueda alternar entre
     * objetos y pokémons»</i>. Y estando en el panel izquierdo <b>se montaba
     * encima de INICIO y de la X</b>: la fila de navegación ocupa esos 72 px, y
     * yo había puesto el conmutador justo ahí.
     *
     * <p>La maqueta no lo cazaba porque no dibujaba la navegación. Ahora sí la
     * dibuja, aunque no sea suya, precisamente para eso.
     *
     * <h2>⚠ Dos pestañas y no un icono</h2>
     *
     * Un icono es un botón que hace algo; una pestaña es <b>un sitio donde
     * estás</b>. Con dos y una marcada se ve de un vistazo que el mercado tiene
     * dos caras y en cuál estás.
     */
    private static final int CONM_W = 100;

    private void dibujarConmutador(DrawContext ctx, int rx, int ry) {
        for (int i = 0; i < 2; i++) {
            boolean act = (i == 0) == ES_POKEMON;
            int bx = PANT_X + MARGEN + i * (CONM_W + 4);
            boolean enc = dentro(rx, ry, px(bx), py(PANT_Y + MARGEN),
                    pl(CONM_W), pl(BOT));
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
                boolean quierePokemon = i == 0;
                if (quierePokemon != ES_POKEMON && client != null) {
                    sonar();
                    client.setScreen(new MercadoScreen(anterior));
                }
                return true;
            }
        }
        return false;
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

        if (clicConmutador(rx, ry)) {
            return true;
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

        for (int i = 0; i < BARRA.length; i++) {
            if (dentro(rx, ry, px(botonX(i)), py(PANT_Y + MARGEN), pl(BOT), pl(BOT))) {
                pulsarBarra(BARRA[i].id());
                return true;
            }
        }

        if (modo == Modo.FILTROS) {
            return clicFiltros(rx, ry);
        }

        // La duración
        if (modo == Modo.VENDER) {
            int bw = (PANEL_W - 60) / 3 - 4;
            for (int i = 0; i < DURACIONES.length; i++) {
                int bx = PANEL_X + 30 + i * (bw + 6);
                if (dentro(rx, ry, px(bx), py(PANEL_Y + 578), pl(bw), pl(30))) {
                    horas = DURACIONES[i];
                    sonar();
                    return true;
                }
            }
        }

        // El botón grande del panel
        if (dentro(rx, ry, px(PANEL_X + 30), py(PANEL_Y + PANEL_H - 74),
                pl(PANEL_W - 60), pl(48))) {
            return pulsarPanel();
        }

        // Pestañas del detalle
        if (modo != Modo.VENDER && seleccionado() != null) {
            int anchoTab = (PANEL_W - 60) / 3 - 4;
            for (int i = 0; i < 3; i++) {
                int bx = PANEL_X + 30 + i * (anchoTab + 6);
                if (dentro(rx, ry, px(bx), py(pestanasY()), pl(anchoTab), pl(24))) {
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
            int y = listaY() + 18 + f * FILA;
            int aw = PANT_W - 2 * MARGEN;
            // El botón de la fila va ANTES que la fila entera: si no, pulsar
            // «comprar» solo seleccionaría.
            if (modo != Modo.VENDER && dentro(rx, ry, px(PANT_X + MARGEN + aw - 132),
                    py(y + 16), pl(124), pl(32))) {
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
            if (dentro(rx, ry, px(PANT_X + MARGEN), py(y), pl(aw), pl(FILA - 6))) {
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

    /**
     * ⚠⚠ La misma cuenta que al dibujar, y depende de si hay tipos.
     *
     * <p>Si el clic y el dibujado la calcularan por separado, pulsar una pestaña
     * abriría la de al lado — y eso ya pasó una vez en la rejilla del Pad.
     */
    private int pestanasY() {
        var e = seleccionado();
        if (e == null) {
            return RET_Y + retAlto();
        }
        String nombre = e.mote().isBlank()
                ? especieEs(e.especie()).getString() : e.mote();
        int altoNombre = 26;
        while (altoNombre > 15 && anchoArte(nombre, altoNombre) > PANEL_W - 60) {
            altoNombre--;
        }
        int y = RET_Y + retAlto() + 8 + altoNombre + 8;
        if (!tipos(e.especie()).isEmpty()) {
            y += 26;
        }
        y += 24 + 36 + 20 + 8;
        return y;
    }

    private void pulsarBarra(String id) {
        sonar();
        aviso = "";
        switch (id) {
            case "buscar", "refrescar" -> {
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
            case "chollos" -> {
                orden = "CHOLLO".equals(orden) ? "NUEVO" : "CHOLLO";
                pagina = 0;
                elegido = -1;
                pedir();
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
                    m.uuid(), precio, horas));
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

    /**
     * ⚠⚠ ALINEAR A LA DERECHA DE VERDAD.
     *
     * <p>Aquí estaba la causa de casi toda la fealdad: para poner algo pegado al
     * borde derecho yo pasaba <b>la x del borde</b> con {@code centrado=false} —
     * y eso dibuja el texto <i>empezando</i> ahí, o sea <b>hacia fuera</b>. Por
     * eso «sin operaciones», «llevas 1», el contador «1-1 de 1» y el tiempo que
     * queda se salían del marco o los cortaba el botón de al lado.
     *
     * <p>No era un problema de medidas ni de espacio: era que <b>no existía la
     * alineación a la derecha</b> y yo creía estar usándola.
     */
    private void textoDer(DrawContext ctx, Text linea, int derecha, int arriba,
                          int alto, int color, boolean contorno) {
        int ancho = Math.round(textRenderer.getWidth(linea) * alto
                / (float) textRenderer.fontHeight);
        texto(ctx, linea, derecha - ancho, arriba, alto, color, false, contorno);
    }

    /**
     * Un identificador convertido en algo que se pueda leer.
     *
     * <p>⚠ Cobblemon devuelve {@code cobblemon:surge_surfer} y
     * {@code cobblemon:hardy}. Enseñar eso tal cual es lo que hacía que el panel
     * dijera «cobblemonre» y «surgesurfer»: no era un corte de texto, es que
     * <b>nunca se limpió el identificador</b>.
     */
    /**
     * Los tipos de una especie, con su color.
     *
     * <h2>⚠⚠ Se preguntan EN EL CLIENTE, y por eso no hubo que migrar nada</h2>
     *
     * El tipo es una propiedad de <b>la especie</b>, no del ejemplar en venta:
     * todos los Charizard son Fuego/Volador. Guardarlo en la fila del listado
     * sería copiar un dato que ya está en los datos de Cobblemon, y el cliente
     * los tiene igual que el servidor.
     *
     * <p>⚠ Y el color sale de {@code getHue()}, que es <b>el suyo</b>. Una tabla
     * de colores nuestra se quedaría vieja en cuanto añadan un tipo, y peor:
     * chocaría con los colores que el jugador ya ve en su Pokédex.
     */
    /**
     * La clave de una especie para los datos de Cobblemon.
     *
     * <h2>⚠⚠ Aquí estaba el Nidoran sin sprite ni tipos</h2>
     *
     * {@code getSpecies().getName()} devuelve <b>«Nidoran-M»</b>, y las claves de
     * Cobblemon son {@code nidoranm}: sin guion y en minúsculas. Buscar por el
     * nombre tal cual <b>no encuentra la especie</b>, y entonces no hay tipos ni
     * modelo — que es exactamente lo que se veía.
     *
     * <p>No fallaba ruidosamente: devolvía {@code null} y la fila se dibujaba
     * igual, solo que vacía.
     */
    private static String claveEspecie(String nombre) {
        if (nombre == null) {
            return "";
        }
        var sb = new StringBuilder();
        for (char c : nombre.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Todo lo que se enseña, EN EL IDIOMA DEL JUGADOR.
     *
     * <h2>⚠⚠ Se usan las claves DE COBBLEMON, no una tabla nuestra</h2>
     *
     * Su jar trae {@code es_es.json} con las 1.025 especies, los 18 tipos, las
     * 25 naturalezas y todas las habilidades ya traducidas. Escribir una tabla
     * propia sería traducir a mano lo que ya está hecho —y quedarse viejo en
     * cuanto añadan algo—, además de <b>contradecir lo que el jugador ve en su
     * Pokédex</b>, que es lo peor que puede pasar: dos nombres para lo mismo.
     */
    private static Text especieEs(String especie) {
        String clave = claveEspecie(especie);
        return clave.isEmpty() ? Text.literal("—")
                : Text.translatable("cobblemon.species." + clave + ".name");
    }

    private static Text naturalezaEs(String id) {
        String c = claveEspecie(sinEspacioDeNombres(id));
        return c.isEmpty() ? Text.literal("—")
                : Text.translatable("cobblemon.nature." + c);
    }

    private static Text habilidadEs(String id) {
        String c = sinEspacioDeNombres(id).toLowerCase(java.util.Locale.ROOT);
        return c.isEmpty() ? Text.literal("—")
                : Text.translatable("cobblemon.ability." + c);
    }

    private static Text generoEs(String g) {
        if (g == null || g.isBlank()) {
            return Text.literal("—");
        }
        return Text.translatable("cobblemon.gender."
                + g.toLowerCase(java.util.Locale.ROOT));
    }

    /** La rareza sí es nuestra, así que su traducción también. */
    private static Text rarezaEs(String r) {
        return Text.translatable("pokepad.lunaeternal.gts.rareza."
                + (r == null || r.isBlank() ? "COMUN" : r));
    }

    private static int perfectosDe(java.util.List<Integer> ivs) {
        int n = 0;
        for (int v : ivs) {
            if (v >= 31) {
                n++;
            }
        }
        return n;
    }

    private static String sinEspacioDeNombres(String id) {
        if (id == null) {
            return "";
        }
        int dp = id.lastIndexOf(':');
        return dp >= 0 ? id.substring(dp + 1) : id;
    }

    private record Tipo(String nombre, int color) {}

    private static java.util.List<Tipo> tipos(String especie) {
        var salida = new ArrayList<Tipo>();
        try {
            var sp = especieDe(especie);
            if (sp == null) {
                return salida;
            }
            for (var tipo : sp.getTypes()) {
                if (tipo == null) {
                    continue;
                }
                // ⚠ `getHue` da el matiz sin el alfa. Se le pone opaco: sin él
                //   el color sale con alfa 0 y la insignia no se dibuja -- que
                //   es la regla 1 de dibujado.md por otra puerta.
                //
                // ⚠ Y el NOMBRE sale de la clave de Cobblemon, así que se ve en
                //   el idioma del jugador. Antes decía «Fire» y «Poison».
                salida.add(new Tipo(Text.translatable("cobblemon.type."
                        + tipo.getName().toLowerCase(java.util.Locale.ROOT))
                        .getString(),
                        0xFF000000 | (tipo.getHue() & 0xFFFFFF)));
            }
        } catch (Throwable e) {
            // Una especie desconocida no puede tumbar la pantalla. Sin tipos se
            // dibuja igual: es adorno, no información imprescindible.
            return salida;
        }
        return salida;
    }

    /**
     * La especie de Cobblemon, buscada con la clave normalizada.
     *
     * <p>⚠ Ver {@link #claveEspecie}: buscar por «Nidoran-M» no encuentra nada.
     */
    private static com.cobblemon.mod.common.pokemon.Species especieDe(String nombre) {
        try {
            return com.cobblemon.mod.common.api.pokemon.PokemonSpecies
                    .getByName(claveEspecie(nombre));
        } catch (Throwable e) {
            return null;
        }
    }

    /** Una insignia de tipo. Devuelve lo que ha ocupado, para encadenarlas. */
    private int insignia(DrawContext ctx, Tipo tipo, int ax, int ay, int altoArte) {
        int alto = 13;
        int ancho = anchoArte(tipo.nombre(), alto) + 16;
        ctx.fill(px(ax), py(ay), px(ax + ancho), py(ay + altoArte), tipo.color());
        marco(ctx, px(ax), py(ay), pl(ancho), pl(altoArte), 0x66000000,
                Math.max(1, pl(1)));
        // ⚠ El texto va BLANCO CON CONTORNO OSCURO y no negro: los tipos van del
        //   amarillo del Eléctrico al morado del Fantasma, y un solo color de
        //   texto solo se lee sobre todos ellos si lleva contorno.
        texto(ctx, Text.literal(tipo.nombre()), ax + ancho / 2,
                ay + (altoArte - alto) / 2, alto, 0xFFFFFFFF, true, false);
        return ancho + 4;
    }

    /** ⚠ `MALE`/`FEMALE` es lo que devuelve Cobblemon; ahí se traduce. */
    private static String genero(String g) {
        if (g == null) {
            return "—";
        }
        return switch (g.toUpperCase(java.util.Locale.ROOT)) {
            case "MALE" -> "♂ Macho";
            case "FEMALE" -> "♀ Hembra";
            case "GENDERLESS" -> "Sin género";
            default -> "—";
        };
    }

    private static String bonito(String id) {
        if (id == null || id.isBlank()) {
            return "—";
        }
        String s = id;
        int dp = s.lastIndexOf(':');
        if (dp >= 0) {
            s = s.substring(dp + 1);
        }
        s = s.replace('_', ' ').trim();
        if (s.isEmpty()) {
            return "—";
        }
        // Primera en mayúscula, el resto tal cual: «Surge surfer», no
        // «Surge Surfer», que a este tamaño se lee peor.
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
