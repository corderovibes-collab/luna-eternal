package net.pokereport.luna.client.pokepad;

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
 * EL MERCADO: el libro de órdenes.
 *
 * <h2>Lo que hay que entender de un vistazo</h2>
 *
 * Un libro tiene <b>dos lados empujando</b>. A la izquierda quien compra, a la
 * derecha quien vende, y arriba del todo de cada lado la <b>mejor oferta</b>. Si
 * las dos mejores se cruzan, ya se habrían ejecutado — así que lo que se ve
 * siempre es un hueco, y ese hueco <i>es</i> el precio.
 *
 * <h2>⚠ El color dice el lado, no si es bueno</h2>
 *
 * Verde compra, rojo vende, y punto. La tentación es pintar «barato» en verde,
 * y entonces el mismo precio sale de un color u otro según quién mire — que es
 * como se lee mal una fila y se pulsa la equivocada.
 *
 * <h2>⚠⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 *
 * Y la geometría ({@code recalcular}) es <b>copia literal</b> de
 * {@code CosmeticosScreen}, como en Clan, Tienda y Curar.
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
    private static final int MARGEN = 14, PESTANA_ALTO = 46;

    /** Alto de una fila del catálogo de la izquierda. */
    private static final int CAT_ALTO = 40;

    private static final int FILA_FONDO = 0xFFBFCBE8;
    private static final int FILA_BORDE = 0xFF7C89B4;
    private static final int FILA_ENCIMA = 0xFFFFF0DC;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int ORO = 0xFFFFD65C;
    private static final int VERDE = 0xFF2E9E56;
    private static final int VERDE_CLARO = 0xFF4FD07A;
    private static final int ROJO = 0xFFB03A2E;
    private static final int ROJO_CLARO = 0xFFD8544A;
    private static final int APAGADO = 0xFF6E7899;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private Red.EstadoMercado estado;
    private String item = "";
    private int pestana = 0;
    private int paginaCat = 0;
    private String aviso = "";

    private TextFieldWidget campoPrecio;
    private TextFieldWidget campoCantidad;

    /** Para no dejar los botones encendidos mientras vuela el paquete. */
    private long pulsado;

    public MercadoScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.mercado"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirMercado(item));

        campoPrecio = campo(PANT_X + 110, PANT_Y + PANT_H - 46, 130, 9);
        campoCantidad = campo(PANT_X + 330, PANT_Y + PANT_H - 46, 90, 5);
        addSelectableChild(campoPrecio);
        addSelectableChild(campoCantidad);
    }

    private TextFieldWidget campo(int ax, int ay, int aw, int max) {
        var c = new TextFieldWidget(textRenderer, px(ax), py(ay),
                pl(aw), Math.max(12, pl(28)), Text.literal(""));
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

    private List<String> catalogo() {
        return estado == null ? List.of() : estado.catalogo();
    }

    private int catCaben() {
        return (PANEL_H - NAV_ALTO - 150) / CAT_ALTO;
    }

    private int catPaginas() {
        int n = catalogo().size();
        return Math.max(1, (n + catCaben() - 1) / catCaben());
    }

    private ItemStack pila(String id) {
        var i = Registries.ITEM.get(Identifier.tryParse(id));
        var p = new ItemStack(i);
        return p.isEmpty() ? new ItemStack(net.minecraft.item.Items.BARRIER) : p;
    }

    private String nombre(String id) {
        return pila(id).getName().getString();
    }

    private long leerLong(TextFieldWidget campo) {
        try {
            return Math.max(0, Long.parseLong(campo.getText().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
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
            if (item.isEmpty() && !estado.catalogo().isEmpty()) {
                // Se elige el primero solo: una pantalla que abre sin nada
                // seleccionado obliga a un clic para ver que esto hace algo.
                item = estado.catalogo().get(0);
                ClientPlayNetworking.send(new Red.PedirMercado(item));
            }
        }

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarCatalogo(ctx, rx, ry, false);
        dibujarPestanas(ctx, rx, ry);
        switch (pestana) {
            case 1 -> dibujarMias(ctx, rx, ry);
            case 2 -> dibujarHistorial(ctx);
            default -> dibujarLibro(ctx, rx, ry);
        }

        // ⚠ DOS PASADAS: todo el 2D, luego `ctx.draw()`, y solo entonces los
        //   objetos 3D. Mezclarlos hace que el 2D se pinte ENCIMA de los
        //   modelos, porque van por lotes distintos. Regla 3 de dibujado.md.
        ctx.draw();
        dibujarCatalogo(ctx, rx, ry, true);
        if (!item.isEmpty()) {
            objeto(ctx, pila(item), PANT_X + MARGEN, PANT_Y + MARGEN + 4, 26);
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

    private int catY(int i) {
        return PANEL_Y + NAV_ALTO + 34 + i * CAT_ALTO;
    }

    /**
     * La lista de la izquierda.
     *
     * <p>⚠ Son los objetos <b>que se negocian ahora</b> más <b>los que llevas
     * encima</b>. Sin lo segundo, un mercado vacío no tendría nada en que pulsar
     * — y el primero que quisiera vender algo no encontraría cómo.
     */
    private void dibujarCatalogo(DrawContext ctx, int rx, int ry, boolean objetos) {
        var cat = catalogo();
        if (!objetos) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.objetos"),
                    PANEL_X + PANEL_W / 2, PANEL_Y + NAV_ALTO + 8, 17,
                    TEXTO_SUAVE, true, false);
        }
        int desde = paginaCat * catCaben();
        for (int n = 0; n < catCaben(); n++) {
            int i = desde + n;
            if (i >= cat.size()) {
                break;
            }
            String id = cat.get(i);
            int y = catY(n);
            int ax = PANEL_X + 16, aw = PANEL_W - 32;

            if (objetos) {
                objeto(ctx, pila(id), ax + 6, y + 6, 22);
                continue;
            }
            boolean sel = id.equals(item);
            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(CAT_ALTO - 4));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + CAT_ALTO - 4),
                    sel ? FILA_ENCIMA : (encima ? 0xFFD3DCF2 : FILA_FONDO));
            marco(ctx, px(ax), py(y), pl(aw), pl(CAT_ALTO - 4),
                    sel ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(sel ? 3 : 2)));
            int alto = 17;
            String n2 = nombre(id);
            while (alto > 10 && anchoArte(n2, alto) > aw - 46) {
                alto--;
            }
            texto(ctx, Text.literal(n2), ax + 36, y + (CAT_ALTO - 4 - alto) / 2 - 1,
                    alto, TEXTO_OSCURO, false, true);
        }
        if (objetos) {
            return;
        }

        // Paginación del catálogo
        if (catPaginas() > 1) {
            int y = catY(catCaben()) + 2;
            botonPeq(ctx, rx, ry, PANEL_X + 16, y, 60, 26, Text.literal("<"),
                    paginaCat > 0);
            texto(ctx, Text.literal((paginaCat + 1) + " / " + catPaginas()),
                    PANEL_X + PANEL_W / 2, y + 5, 16, TEXTO_SUAVE, true, false);
            botonPeq(ctx, rx, ry, PANEL_X + PANEL_W - 16 - 60, y, 60, 26,
                    Text.literal(">"), paginaCat < catPaginas() - 1);
        }

        // El saldo, abajo. Es lo que decide si puedes poner una compra.
        int sy = PANEL_Y + PANEL_H - 92;
        separador(ctx, sy);
        texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.tu_plata"),
                PANEL_X + PANEL_W / 2, sy + 14, 15, TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(estado == null ? "—"
                        : String.format("%,d", estado.saldo())),
                PANEL_X + PANEL_W / 2, sy + 34, 28, 0xFFFFFFFF, true, false);
    }

    private void dibujarPestanas(DrawContext ctx, int rx, int ry) {
        String[] ids = { "libro", "mias", "historial" };
        int aw = (PANT_W - 2 * MARGEN) / 3;
        for (int i = 0; i < ids.length; i++) {
            int ax = PANT_X + MARGEN + i * aw;
            boolean activa = i == pestana;
            boolean encima = dentro(rx, ry, px(ax), py(PANT_Y + MARGEN + 34),
                    pl(aw - 6), pl(PESTANA_ALTO));
            ctx.fill(px(ax), py(PANT_Y + MARGEN + 34), px(ax + aw - 6),
                    py(PANT_Y + MARGEN + 34 + PESTANA_ALTO),
                    activa ? FILA_ENCIMA : (encima ? 0xFFD3DCF2 : FILA_FONDO));
            marco(ctx, px(ax), py(PANT_Y + MARGEN + 34), pl(aw - 6), pl(PESTANA_ALTO),
                    activa ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(activa ? 3 : 2)));
            texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.tab." + ids[i]),
                    ax + (aw - 6) / 2, PANT_Y + MARGEN + 34 + 14, 20,
                    TEXTO_OSCURO, true, false);
        }

        // La cabecera del objeto: nombre y último precio.
        if (!item.isEmpty()) {
            texto(ctx, Text.literal(nombre(item)), PANT_X + MARGEN + 34,
                    PANT_Y + MARGEN + 2, 22, TEXTO_OSCURO, false, true);
            long ultimo = estado == null ? 0 : estado.ultimoPrecio();
            texto(ctx, ultimo > 0
                            ? Text.translatable("pokepad.lunaeternal.mercado.ultimo",
                                    String.format("%,d", ultimo))
                            : Text.translatable("pokepad.lunaeternal.mercado.sin_precio"),
                    PANT_X + PANT_W - MARGEN, PANT_Y + MARGEN + 6, 17,
                    ultimo > 0 ? ORO : TEXTO_SUAVE, false, true);
        }
    }

    private int libroY() {
        return PANT_Y + MARGEN + 34 + PESTANA_ALTO + 10;
    }

    private int filasLibro() {
        return (PANT_Y + PANT_H - 66 - libroY()) / 24;
    }

    /** Las dos caras del libro, lado a lado. */
    private void dibujarLibro(DrawContext ctx, int rx, int ry) {
        if (estado == null) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 22, TEXTO_SUAVE, true, false);
            return;
        }
        int mitad = (PANT_W - 2 * MARGEN - 14) / 2;
        int y = libroY();

        texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.compran"),
                PANT_X + MARGEN + mitad / 2, y, 18, VERDE, true, false);
        texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.venden"),
                PANT_X + MARGEN + 14 + mitad + mitad / 2, y, 18, ROJO, true, false);
        y += 22;

        dibujarLado(ctx, estado.compras(), PANT_X + MARGEN, mitad, y, VERDE);
        dibujarLado(ctx, estado.ventas(), PANT_X + MARGEN + 14 + mitad, mitad, y, ROJO);

        dibujarFormulario(ctx, rx, ry);
    }

    private void dibujarLado(DrawContext ctx, List<Red.NivelMercado> niveles,
                             int ax, int aw, int y0f, int color) {
        if (niveles.isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.vacio"),
                    ax + aw / 2, y0f + 20, 16, TEXTO_SUAVE, true, false);
            return;
        }
        int y = y0f;
        for (int i = 0; i < niveles.size() && i < filasLibro(); i++) {
            var n = niveles.get(i);
            // La primera fila es la MEJOR oferta de ese lado: se resalta, porque
            // es contra la que se va a ejecutar quien llegue.
            if (i == 0) {
                ctx.fill(px(ax), py(y - 2), px(ax + aw), py(y + 20), 0x33FFFFFF);
            }
            texto(ctx, Text.literal(String.format("%,d", n.precio())),
                    ax + 8, y, 18, color, false, true);
            texto(ctx, Text.literal("x" + n.unidades()), ax + aw - 110, y, 17,
                    TEXTO_OSCURO, false, true);
            texto(ctx, Text.literal("(" + n.ordenes() + ")"), ax + aw - 34, y, 15,
                    TEXTO_SUAVE, false, true);
            y += 24;
        }
    }

    /** Precio, cantidad y los dos botones. */
    private void dibujarFormulario(DrawContext ctx, int rx, int ry) {
        int y = PANT_Y + PANT_H - 52;
        separadorPantalla(ctx, y - 10);

        texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.precio"),
                PANT_X + MARGEN, y + 8, 16, TEXTO_SUAVE, false, true);
        campoPrecio.render(ctx, rx, ry, 0);
        texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.cantidad"),
                PANT_X + 254, y + 8, 16, TEXTO_SUAVE, false, true);
        campoCantidad.render(ctx, rx, ry, 0);

        long precio = leerLong(campoPrecio);
        long cant = leerLong(campoCantidad);
        long total = precio * cant;
        boolean vale = precio > 0 && cant > 0 && !item.isEmpty() && !esperando();

        // ⚠ El total se enseña ANTES de pulsar. Es la única cifra que importa y
        //   la única que el jugador no calcula de cabeza: 37 x 1.240 no se hace
        //   mentalmente, y equivocarse cuesta dinero de verdad.
        boolean llega = estado == null || total <= estado.saldo();
        texto(ctx, total > 0
                        ? Text.translatable("pokepad.lunaeternal.mercado.total",
                                String.format("%,d", total))
                        : Text.literal(""),
                PANT_X + 440, y + 8, 16, llega ? TEXTO_SUAVE : ROJO, false, true);

        botonPeq(ctx, rx, ry, PANT_X + PANT_W - MARGEN - 300, y, 140, 34,
                Text.translatable("pokepad.lunaeternal.mercado.comprar"),
                vale && llega);
        int tengo = estado == null ? 0 : estado.tengo();
        botonPeq(ctx, rx, ry, PANT_X + PANT_W - MARGEN - 148, y, 140, 34,
                Text.translatable("pokepad.lunaeternal.mercado.vender"),
                vale && tengo >= cant);
        if (tengo > 0) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.tienes", tengo),
                    PANT_X + PANT_W - MARGEN - 78, y - 14, 14, TEXTO_SUAVE, true, false);
        }
        if (!aviso.isEmpty()) {
            texto(ctx, Text.literal(aviso), PANT_X + MARGEN, y - 26, 15, ROJO,
                    false, true);
        }
    }

    /** Mis órdenes vivas, con su botón de cancelar. */
    private void dibujarMias(DrawContext ctx, int rx, int ry) {
        if (estado == null || estado.mias().isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.sin_ordenes"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 20, TEXTO_SUAVE, true, false);
            return;
        }
        int y = libroY();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        for (int i = 0; i < estado.mias().size() && i < filasLibro(); i++) {
            var o = estado.mias().get(i);
            boolean compra = "COMPRA".equals(o.lado());
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + 32), FILA_FONDO);
            marco(ctx, px(ax), py(y), pl(aw), pl(32), FILA_BORDE, Math.max(1, pl(2)));

            texto(ctx, Text.translatable(compra
                            ? "pokepad.lunaeternal.mercado.compra"
                            : "pokepad.lunaeternal.mercado.venta"),
                    ax + 8, y + 8, 16, compra ? VERDE : ROJO, false, true);
            texto(ctx, Text.literal(nombre(o.item())), ax + 100, y + 8, 17,
                    TEXTO_OSCURO, false, true);
            texto(ctx, Text.literal(String.format("%,d", o.precio())),
                    ax + 370, y + 8, 17, TEXTO_OSCURO, false, true);
            // ⚠ Se enseña «servidas / pedidas» y no «quedan». Cuánto llevas
            //   vendido de lo que pusiste es lo que dice si tu precio es
            //   realista, y es justo lo que se pierde si solo se enseña el resto.
            texto(ctx, Text.literal(o.lleno() + " / " + o.total()),
                    ax + 500, y + 8, 17, TEXTO_SUAVE, false, true);
            botonPeq(ctx, rx, ry, ax + aw - 130, y + 3, 122, 26,
                    Text.translatable("pokepad.lunaeternal.mercado.cancelar"), true);
            y += 36;
        }
    }

    private void dibujarHistorial(DrawContext ctx) {
        if (estado == null || estado.historial().isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.mercado.sin_historial"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 20, TEXTO_SUAVE, true, false);
            return;
        }
        var h = estado.historial();
        int y = libroY();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;

        // Una barra por operación, escalada al precio máximo del tramo. Es un
        // gráfico pobre y dice lo único que hace falta: si sube o si baja.
        long max = 1;
        for (var x : h) {
            max = Math.max(max, x.precio());
        }
        for (int i = 0; i < h.size() && i < filasLibro(); i++) {
            var x = h.get(i);
            if (i % 2 == 0) {
                ctx.fill(px(ax), py(y - 2), px(ax + aw), py(y + 20), 0x22FFFFFF);
            }
            int barra = (int) Math.max(2, (aw - 300) * x.precio() / max);
            ctx.fill(px(ax + 200), py(y + 4), px(ax + 200 + barra), py(y + 14), 0x66F35C0C);
            texto(ctx, Text.literal(String.format("%,d", x.precio())),
                    ax + 8, y, 17, TEXTO_OSCURO, false, true);
            texto(ctx, Text.literal("x" + x.qty()), ax + 120, y, 16, TEXTO_SUAVE,
                    false, true);
            texto(ctx, Text.literal(hace(x.cuando())), ax + aw - 120, y, 15,
                    TEXTO_SUAVE, false, true);
            y += 24;
        }
    }

    private static String hace(long cuando) {
        long s = Math.max(0, (System.currentTimeMillis() - cuando) / 1000);
        if (s < 60) {
            return "ahora";
        }
        if (s < 3600) {
            return "hace " + (s / 60) + " min";
        }
        if (s < 86400) {
            return "hace " + (s / 3600) + " h";
        }
        return "hace " + (s / 86400) + " d";
    }

    // ---- interacción -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) {
            return super.mouseClicked(mx, my, boton);
        }
        int rx = (int) mx, ry = (int) my;
        for (var c : new TextFieldWidget[] { campoPrecio, campoCantidad }) {
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
        if (dentro(rx, ry, px(PANEL_X + PANEL_W - 18) - pl(80), cy - pl(32), pl(80), pl(64))) {
            sonar();
            close();
            return true;
        }

        // Catálogo
        var cat = catalogo();
        int desde = paginaCat * catCaben();
        for (int n = 0; n < catCaben(); n++) {
            int i = desde + n;
            if (i >= cat.size()) {
                break;
            }
            if (dentro(rx, ry, px(PANEL_X + 16), py(catY(n)), pl(PANEL_W - 32),
                    pl(CAT_ALTO - 4))) {
                item = cat.get(i);
                aviso = "";
                sonar();
                ClientPlayNetworking.send(new Red.PedirMercado(item));
                return true;
            }
        }
        if (catPaginas() > 1) {
            int y = catY(catCaben()) + 2;
            if (paginaCat > 0 && dentro(rx, ry, px(PANEL_X + 16), py(y), pl(60), pl(26))) {
                paginaCat--;
                sonar();
                return true;
            }
            if (paginaCat < catPaginas() - 1 && dentro(rx, ry,
                    px(PANEL_X + PANEL_W - 16 - 60), py(y), pl(60), pl(26))) {
                paginaCat++;
                sonar();
                return true;
            }
        }

        // Pestañas
        int aw = (PANT_W - 2 * MARGEN) / 3;
        for (int i = 0; i < 3; i++) {
            if (dentro(rx, ry, px(PANT_X + MARGEN + i * aw), py(PANT_Y + MARGEN + 34),
                    pl(aw - 6), pl(PESTANA_ALTO))) {
                pestana = i;
                aviso = "";
                sonar();
                return true;
            }
        }

        if (pestana == 0) {
            return clicFormulario(rx, ry) || super.mouseClicked(mx, my, boton);
        }
        if (pestana == 1) {
            return clicMias(rx, ry) || super.mouseClicked(mx, my, boton);
        }
        return super.mouseClicked(mx, my, boton);
    }

    private boolean clicFormulario(int rx, int ry) {
        int y = PANT_Y + PANT_H - 52;
        long precio = leerLong(campoPrecio);
        long cant = leerLong(campoCantidad);

        boolean compra = dentro(rx, ry, px(PANT_X + PANT_W - MARGEN - 300), py(y),
                pl(140), pl(34));
        boolean venta = dentro(rx, ry, px(PANT_X + PANT_W - MARGEN - 148), py(y),
                pl(140), pl(34));
        if (!compra && !venta) {
            return false;
        }
        if (item.isEmpty()) {
            return poner("Elige un objeto de la lista.");
        }
        if (precio <= 0 || cant <= 0) {
            return poner("Escribe precio y cantidad.");
        }
        if (compra && estado != null && precio * cant > estado.saldo()) {
            // Se dice aquí en vez de mandarlo: el servidor contestaría lo mismo,
            // y un viaje para un «no te llega» que el cliente ya sabe es medio
            // segundo por gusto.
            return poner("No te llega: hacen falta "
                    + String.format("%,d", precio * cant) + ".");
        }
        if (venta && estado != null && estado.tengo() < cant) {
            return poner("Solo tienes " + estado.tengo() + ".");
        }
        aviso = "";
        pulsado = System.currentTimeMillis();
        sonar();
        ClientPlayNetworking.send(new Red.AccionMercado(
                compra ? "comprar" : "vender", item, precio, (int) cant, 0));
        return true;
    }

    private boolean poner(String texto) {
        aviso = texto;
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 0.8f);
        }
        return true;
    }

    private boolean clicMias(int rx, int ry) {
        if (estado == null) {
            return false;
        }
        int y = libroY();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        for (int i = 0; i < estado.mias().size() && i < filasLibro(); i++) {
            if (dentro(rx, ry, px(ax + aw - 130), py(y + 3), pl(122), pl(26))) {
                pulsado = System.currentTimeMillis();
                sonar();
                ClientPlayNetworking.send(new Red.AccionMercado("cancelar",
                        estado.mias().get(i).item(), 0, 0, estado.mias().get(i).id()));
                return true;
            }
            y += 36;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        for (var c : new TextFieldWidget[] { campoPrecio, campoCantidad }) {
            if (c != null && c.isFocused() && c.keyPressed(tecla, escaneo, mods)) {
                return true;
            }
        }
        return super.keyPressed(tecla, escaneo, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        // ⚠ Solo dígitos. Un campo de precio que acepta letras obliga a
        //   comprobar al pulsar, y entonces el error llega tarde.
        if (!Character.isDigit(c)) {
            return false;
        }
        for (var campo : new TextFieldWidget[] { campoPrecio, campoCantidad }) {
            if (campo != null && campo.isFocused() && campo.charTyped(c, mods)) {
                return true;
            }
        }
        return super.charTyped(c, mods);
    }

    private void sonar() {
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.0f);
        }
    }

    // ---- utilidades --------------------------------------------------------

    private void botonPeq(DrawContext ctx, int rx, int ry, int ax, int ay, int aw,
                          int ah, Text etiqueta, boolean activo) {
        boolean encima = activo && dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        int base = etiqueta.getString().startsWith("COMPR") ? VERDE
                : etiqueta.getString().startsWith("VEND") ? ROJO : 0xFF4F6FB0;
        int claro = base == VERDE ? VERDE_CLARO : base == ROJO ? ROJO_CLARO : 0xFF5E86D8;
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah),
                !activo ? APAGADO : (encima ? claro : base));
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), 0xFF20283C, Math.max(1, pl(2)));
        int alto = 17;
        while (alto > 10 && anchoArte(etiqueta.getString(), alto) > aw - 12) {
            alto--;
        }
        texto(ctx, etiqueta, ax + aw / 2, ay + (ah - alto) / 2 - 1, alto,
                activo ? 0xFFFFFFFF : 0xFFD8DEEA, true, false);
    }

    private void objeto(DrawContext ctx, ItemStack p, int ax, int ay, int altoArte) {
        float escala = altoArte * k / 16f;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(px(ax), py(ay), 0);
        m.scale(escala, escala, 1f);
        ctx.drawItem(p, 0, 0);
        m.pop();
    }

    private int anchoArte(String linea, int alto) {
        return Math.round(textRenderer.getWidth(linea) * alto / (float) textRenderer.fontHeight);
    }

    private void separador(DrawContext ctx, int artY) {
        ctx.fill(px(PANEL_X + 28), py(artY), px(PANEL_X + PANEL_W - 28),
                py(artY) + Math.max(1, pl(2)), SEPARADOR);
    }

    private void separadorPantalla(DrawContext ctx, int artY) {
        ctx.fill(px(PANT_X + MARGEN), py(artY), px(PANT_X + PANT_W - MARGEN),
                py(artY) + Math.max(1, pl(2)), 0x44000000);
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
