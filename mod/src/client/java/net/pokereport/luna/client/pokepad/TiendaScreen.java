package net.pokereport.luna.client.pokepad;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;
import net.pokereport.luna.pokepad.PanelTienda;

/**
 * LA TIENDA: comprar y vender, por categorías.
 *
 * <h2>La categoría manda, y por eso vive en el panel izquierdo</h2>
 *
 * En Cosméticos y en Misiones las categorías son <b>pestañas arriba</b> porque
 * son cuatro o seis y caben. Aquí son cinco pero llevan <b>icono, nombre y una
 * frase que explica para qué sirve la categoría</b>, y eso en una pestaña de 150
 * píxeles no entra. El panel izquierdo mide 315×692 y estaba medio vacío en todas
 * las pantallas anteriores: aquí se gana el sueldo.
 *
 * <h2>⚠ Lo que el cliente sabe y lo que no</h2>
 *
 * Sabe los <b>precios</b> (se los mandó el servidor) y sabe <b>cuántos tiene de
 * cada cosa</b>, porque su inventario ya está sincronizado — contarlos aquí evita
 * reenviar el catálogo cada vez que el jugador recoge algo del suelo.
 *
 * <p>Lo que <b>no</b> hace es decidir nada: al pulsar COMPRAR manda «este
 * artículo, esta cantidad» y el precio lo pone el servidor mirando su propio
 * catálogo (P6). Los números de esta pantalla son para <b>leer</b>, no para
 * cobrar.
 *
 * <h2>⚠⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 *
 * Y {@code recalcular()} es <b>copia literal</b> de {@code CosmeticosScreen}.
 */
public class TiendaScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = PanelTienda.NAV_ALTO;

    private static final int MARGEN = PanelTienda.MARGEN;
    private static final int CAT_ALTO = PanelTienda.CAT_ALTO,
            CAT_AIRE = PanelTienda.CAT_AIRE;

    /**
     * ⚠⚠⚠ ESTOS NUMEROS NO ESTAN AQUI: estan en {@link PanelTienda}, que
     * vive en {@code main} para que el autotest pueda leer LOS MISMOS. Antes
     * los tenia escritos otra vez, y eso es lo que ya mordio con las medallas
     * --tres listas que eran una sola-- y con la rejilla del PokePad.
     */
    private static final int CAT_Y0 = PanelTienda.CAT_Y0;
    private static final int PAGER_ALTO = PanelTienda.PAGER_ALTO;
    private static final int CAT_POR_PAGINA = PanelTienda.porPagina();

    private static final int FILA_ALTO = PanelTienda.FILA_ALTO,
            FILA_AIRE = PanelTienda.FILA_AIRE;

    /**
     * EL BUSCADOR, Y LO QUE CUESTA.
     *
     * <p>⚠⚠⚠ SIN EL, UNA CATEGORIA GRANDE ES INALCANZABLE EN LA PRACTICA.
     *    Los peluches son <b>146</b> y los muebles <b>372</b>: a seis por
     *    pagina eran 25 y 62 paginas de pulsar una flecha. Nadie llega al
     *    final, y el sintoma no es un error -- es un articulo que existe, se
     *    paga y <b>no se encuentra</b>.
     *
     * <p>⚠⚠ CUESTA UNA FILA (de 6 a 5), y se paga a proposito: una fila mas
     *    no sirve de nada en una lista de 372.
     *
     * <p>⚠ Y VA SIEMPRE, no solo en las categorias grandes. Si apareciera y
     *   desapareciera, las filas se moverian de sitio al cambiar de categoria
     *   y pulsar dos veces seguidas fallaria la segunda.
     */
    private static final int BUSCA_ALTO = PanelTienda.BUSCA_ALTO,
            BUSCA_AIRE = PanelTienda.BUSCA_AIRE;

    /** La banda naranja del chasis, medida sobre el PNG. Igual que en Cosméticos. */
    private static final int PAG_Y = 698 + (745 - 698 - 40) / 2;
    private static final int PAG_SEP = 215;

    private static final int FILA_FONDO = 0xFFBFCBE8;
    private static final int FILA_BORDE = 0xFF7C89B4;
    private static final int FILA_ENCIMA = 0xFFFFF0DC;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int VERDE = 0xFF1F7A3C;
    private static final int ROJO = 0xFF9C3226;
    private static final int APAGADO = 0xFF6E7899;

    /**
     * ⚠ Las cantidades son las que son por un motivo medido: un mazo de
     * Minecraft son <b>64</b>, y comprar Poké Balls de una en una son 64 clics.
     * El 8 está en medio porque una Poké Ball son 400 de Plata y 64 son 25.600 —
     * más de lo que tiene casi nadie al principio.
     */
    private static final int[] CANTIDADES = { 1, 8, 64 };

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private Red.Tienda tienda;
    private int categoria = 0;
    /** Que pagina del PANEL se mira. No es `pagina`, que es la de articulos. */
    private int paginaCat = 0;
    private net.minecraft.client.gui.widget.TextFieldWidget campoBusqueda;
    /** El articulo bajo el raton, para la ventanita. Se pone al dibujar. */
    private Red.EntradaTienda bajoElRaton;
    private int pagina = 0;
    private int cantidad = 0;

    /** Lo último que contestó el servidor. Se limpia al cambiar de categoría. */
    private String aviso = "";
    private boolean avisoBueno;

    public TiendaScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.tienda"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        // ⚠ SE CONSERVA LO ESCRITO al recalcular (cambio de tamaño de ventana):
        //   `init` se vuelve a llamar y perder el filtro a media busqueda
        //   parece que la pantalla se ha reiniciado sola.
        String escrito = campoBusqueda == null ? "" : campoBusqueda.getText();
        campoBusqueda = new net.minecraft.client.gui.widget.TextFieldWidget(
                textRenderer, px(PANT_X + MARGEN), py(PANT_Y + MARGEN),
                pl(PANT_W - 2 * MARGEN), Math.max(12, pl(BUSCA_ALTO)),
                Text.literal(""));
        campoBusqueda.setMaxLength(48);
        campoBusqueda.setText(escrito);
        addSelectableChild(campoBusqueda);

        ClientPlayNetworking.send(new Red.PedirTienda());
        // El saldo se pide aparte: la tienda lo enseña arriba y tiene que estar
        // al día antes de la primera compra, no después.
        ClientPlayNetworking.send(new Red.PedirSaldo());
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

    // ---- datos -------------------------------------------------------------

    private List<Red.CategoriaTienda> categorias() {
        return tienda == null ? List.of() : tienda.categorias();
    }

    private Red.CategoriaTienda actual() {
        var cs = categorias();
        return cs.isEmpty() ? null : cs.get(Math.min(categoria, cs.size() - 1));
    }

    private int filasCaben() {
        return PanelTienda.filasPorPagina();
    }

    /**
     * Los articulos que se ven: los de la categoria, pasados por el buscador.
     *
     * <p>⚠⚠ SE FILTRA EN EL CLIENTE Y EL TEXTO NO VIAJA, igual que en el
     *    mercado y por el mismo motivo: <b>un servidor no tiene idioma</b>. Lo
     *    que el servidor tiene guardado son identificadores en ingles, asi que
     *    quien escriba «peluche» no encontraria {@code pokedoll_eevee} jamas.
     *    Aqui los nombres ya estan traducidos por el cliente.
     *
     * <p>⚠ Busca por el nombre <b>y</b> por el identificador: quien sepa que
     *   quiere un {@code eevee} no tiene por que escribirlo en español.
     */
    private List<Red.EntradaTienda> articulos() {
        var c = actual();
        if (c == null) {
            return List.of();
        }
        String q = campoBusqueda == null ? ""
                : campoBusqueda.getText().trim().toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty()) {
            return c.entradas();
        }
        var salida = new java.util.ArrayList<Red.EntradaTienda>();
        for (var e : c.entradas()) {
            String nombre = e.pila().getName().getString()
                    .toLowerCase(java.util.Locale.ROOT);
            if (nombre.contains(q)
                    || e.clave().toLowerCase(java.util.Locale.ROOT).contains(q)
                    || limpio(e.etiqueta()).toLowerCase(java.util.Locale.ROOT).contains(q)) {
                salida.add(e);
            }
        }
        return salida;
    }

    private int paginas() {
        int n = articulos().size();
        if (n == 0) {
            return 1;
        }
        return (n + filasCaben() - 1) / filasCaben();
    }

    private ItemStack pila(String id) {
        // ⚠ Un objeto que no exista devuelve AIRE, y `renderItem` con aire no
        //   dibuja nada -- ni un hueco, ni un cubo morado. Se sustituye por algo
        //   visible: un artículo invisible parecería que la tienda está rota.
        var item = Registries.ITEM.get(Identifier.tryParse(id));
        var pila = new ItemStack(item);
        return pila.isEmpty() ? new ItemStack(net.minecraft.item.Items.BARRIER) : pila;
    }

    /**
     * Cuántos tiene el jugador de ese objeto.
     *
     * <p>⚠ Lo cuenta el CLIENTE de su propio inventario, que ya está
     * sincronizado. Mandarlo desde el servidor obligaría a reenviar el catálogo
     * entero cada vez que alguien recoge algo del suelo.
     */
    /**
     * Cuántos tiene el jugador de ese artículo.
     *
     * <p>⚠⚠ SE CUENTA POR OBJETO Y NO POR PILA EXACTA, y para casi todo da
     * igual: una Poké Ball es una Poké Ball. Donde <b>sí</b> importa es en los
     * módulos de protección, que son los cinco un {@code player_head}: contando
     * por objeto, tener una Poké Ball de 15×15 dice «tienes 1» también en la
     * fila de la Master Ball.
     *
     * <p>Por eso, cuando la entrada trae componentes —o sea, cuando NO es un
     * objeto pelado— se compara la pila entera.
     */
    private int tengo(Red.EntradaTienda e) {
        if (client == null || client.player == null) {
            return 0;
        }
        var muestra = e.pila();
        boolean exacto = !muestra.getComponents().isEmpty();
        int n = 0;
        for (var pila : client.player.getInventory().main) {
            boolean casa = exacto
                    ? ItemStack.areItemsAndComponentsEqual(pila, unaDe(muestra))
                    : pila.isOf(muestra.getItem());
            if (casa) {
                n += pila.getCount();
            }
        }
        return n;
    }

    /** La misma pila con una sola unidad: comparar componentes ignora el número. */
    private static ItemStack unaDe(ItemStack pila) {
        ItemStack copia = pila.copy();
        copia.setCount(1);
        return copia;
    }

    private long saldoDe(String moneda) {
        var s = EstadoCliente.saldo();
        if (s == null) {
            return -1;
        }
        return switch (moneda) {
            case "MARK" -> s.marcas();
            case "REPORTCOIN" -> s.reportcoins();
            default -> s.pokedolares();
        };
    }

    private static String nombreMoneda(String moneda) {
        return switch (moneda) {
            case "MARK" -> "Marcas";
            case "REPORTCOIN" -> "LunaCoins";
            default -> "Plata";
        };
    }

    private static int colorMoneda(String moneda) {
        return switch (moneda) {
            case "MARK" -> 0xFF9FD0F0;
            case "REPORTCOIN" -> 0xFFFFD65C;
            default -> 0xFFFFFFFF;
        };
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        var nueva = EstadoCliente.tienda();
        if (nueva != null && nueva != tienda) {
            tienda = nueva;
            if (categoria >= categorias().size()) {
                categoria = 0;
            }
            // ⚠ La pagina del panel se recoloca SOBRE LA CATEGORIA ELEGIDA, no
            //   a cero: si el servidor manda un catalogo mas corto estando en
            //   la pagina 2, quedaria mirando una pagina que ya no existe --
            //   panel en blanco, sin un solo error.
            paginaCat = Math.min(categoria / CAT_POR_PAGINA, paginasCat() - 1);
        }

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarCategorias(ctx, rx, ry);

        // ⚠ DOS PASADAS: todo el 2D primero, luego `ctx.draw()`, y solo entonces
        //   los objetos 3D. Mezclarlos hace que el 2D se pinte ENCIMA de los
        //   modelos, porque van por lotes distintos. Regla 3 de dibujado.md.
        bajoElRaton = null;
        dibujarBuscador(ctx);
        dibujarFilas(ctx, rx, ry, false);
        dibujarPie(ctx, rx, ry);
        ctx.draw();
        dibujarIconosCategorias(ctx);
        dibujarFilas(ctx, rx, ry, true);

        // ⚠⚠ LA VENTANITA VA LA ULTIMA, y no es una preferencia: se dibuja
        //    ENCIMA de todo, modelos 3D incluidos. Puesta antes, las filas
        //    siguientes y los objetos se pintarian por encima de ella.
        dibujarVentanita(ctx, rx, ry);
    }

    /**
     * La ventanita del articulo bajo el raton.
     *
     * <p>⚠⚠ AQUI VA LO QUE NO CABE EN LA FILA. La fila tiene sitio para el
     * nombre y el precio y ya; la descripcion, cuantos tienes y por cuanto se
     * recompra se leen aqui. Antes la descripcion se metia en la fila y se
     * salia por debajo del boton.
     *
     * <p>⚠ El nombre del objeto se pide al CLIENTE ({@code getName}), que lo
     * resuelve en el idioma del jugador. El servidor no tiene idioma.
     */
    private void dibujarVentanita(DrawContext ctx, int rx, int ry) {
        var e = bajoElRaton;
        if (e == null) {
            return;
        }
        var lineas = new java.util.ArrayList<Text>();
        lineas.add(e.pila().getName().copy()
                .formatted(net.minecraft.util.Formatting.WHITE));
        if (!e.etiqueta().isEmpty()) {
            // La etiqueta trae sus propios codigos de color del catalogo.
            for (String linea : partir(e.etiqueta(), 260, 12)) {
                lineas.add(Text.literal(linea)
                        .formatted(net.minecraft.util.Formatting.GRAY));
            }
        }
        lineas.add(Text.literal(String.format("%,d", e.compra()) + " "
                + nombreMoneda(e.moneda()))
                .formatted(net.minecraft.util.Formatting.GOLD));
        if (e.venta() > 0) {
            lineas.add(Text.translatable("pokepad.lunaeternal.tienda.recompra",
                    String.format("%,d", e.venta()), nombreMoneda(e.moneda()))
                    .formatted(net.minecraft.util.Formatting.DARK_GRAY));
        }
        int mios = tengo(e);
        if (mios > 0) {
            lineas.add(Text.translatable("pokepad.lunaeternal.tienda.tienes", mios)
                    .formatted(net.minecraft.util.Formatting.DARK_GRAY));
        }
        ctx.drawTooltip(textRenderer, lineas, rx, ry);
    }

    /**
     * El texto, recortado a lo que cabe, con puntos suspensivos.
     *
     * <p>⚠ Se mide con {@link #anchoArte}, que es el ancho REAL de la fuente a
     * ese tamaño: contar caracteres no vale, porque una «i» y una «M» no miden
     * lo mismo y el recorte quedaria corto o seguiria saliendose.
     */
    private String recortar(String texto, int anchoMax, int alto) {
        if (anchoArte(texto, alto) <= anchoMax) {
            return texto;
        }
        String puntos = "…";
        int n = texto.length();
        while (n > 0 && anchoArte(texto.substring(0, n) + puntos, alto) > anchoMax) {
            n--;
        }
        return texto.substring(0, n).stripTrailing() + puntos;
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
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4, BORDE_ENCIMA, 2);
        }
    }

    /** La Y de la ranura {@code i} DENTRO DE LA PAGINA, no del catalogo. */
    private int categoriaY(int i) {
        return CAT_Y0 + i * (CAT_ALTO + CAT_AIRE);
    }

    private int paginasCat() {
        return PanelTienda.paginas(categorias().size());
    }

    /**
     * Donde acaba la lista.
     *
     * <p>⚠ Con varias paginas se reserva SIEMPRE el alto entero, aunque la
     * ultima traiga menos tarjetas: si no, el saldo y las flechas SALTARIAN al
     * pasar de pagina, y una cosa que se mueve sola se lee como una averia.
     */
    private int finDeCategorias() {
        int filas = paginasCat() > 1 ? CAT_POR_PAGINA
                : Math.min(categorias().size(), CAT_POR_PAGINA);
        return categoriaY(filas);
    }

    /** La Y del separador del saldo. Debajo del pager, si lo hay. */
    private int saldoY() {
        return finDeCategorias() + (paginasCat() > 1 ? PAGER_ALTO : 0) + 12;
    }

    private void dibujarCategorias(DrawContext ctx, int rx, int ry) {
        var cs = categorias();
        int desde = paginaCat * CAT_POR_PAGINA;
        int hasta = Math.min(cs.size(), desde + CAT_POR_PAGINA);
        for (int i = desde; i < hasta; i++) {
            var c = cs.get(i);
            int y = categoriaY(i - desde);
            int ax = PANEL_X + 16, aw = PANEL_W - 32;
            boolean activa = i == categoria;
            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(CAT_ALTO));

            ctx.fill(px(ax), py(y), px(ax + aw), py(y + CAT_ALTO),
                    activa ? 0xFFFFF0DC : (encima ? 0xFFD3DCF2 : FILA_FONDO));
            marco(ctx, px(ax), py(y), pl(aw), pl(CAT_ALTO),
                    activa ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(activa ? 3 : 2)));

            // El nombre trae su color del catálogo (§c, §a...). Se quita: sobre
            // este panel claro, un §f sería invisible y un §e ilegible. El color
            // lo pone el estado -- activa o no -- que es lo que hay que ver.
            texto(ctx, Text.literal(limpio(c.nombre())), ax + 66, y + 16, 24,
                    TEXTO_OSCURO, false, true);
            int alto = 15;
            var lineas = partir(c.descripcion(), aw - 78, alto);
            int ty = y + 44;
            for (int n = 0; n < lineas.size() && n < 2; n++) {
                texto(ctx, Text.literal(lineas.get(n)), ax + 66, ty, alto, TEXTO_SUAVE,
                        false, false);
                ty += alto + 3;
            }
        }

        // Las flechas, si el catálogo no cabe de una vez.
        dibujarPagerCategorias(ctx, rx, ry);

        // El saldo, debajo de las categorías. Es el número que decide si puedes
        // comprar, así que va donde se mira antes de pulsar.
        int y = saldoY();
        separador(ctx, y);
        var s = EstadoCliente.saldo();
        texto(ctx, Text.translatable("pokepad.lunaeternal.tienda.tu_saldo"),
                PANEL_X + PANEL_W / 2, y + 14, 16, TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(s == null ? "—" : String.format("%,d", s.pokedolares())),
                PANEL_X + PANEL_W / 2, y + 36, 30, 0xFFFFFFFF, true, false);
        texto(ctx, Text.literal("Plata"), PANEL_X + PANEL_W / 2, y + 70, 16,
                TEXTO_SUAVE, true, false);
        if (s != null && s.reportcoins() > 0) {
            texto(ctx, Text.literal(String.format("%,d LunaCoins", s.reportcoins())),
                    PANEL_X + PANEL_W / 2, y + 94, 18, 0xFFFFD65C, true, false);
        }
    }

    /** Los iconos de categoría, en la segunda pasada. */
    private void dibujarIconosCategorias(DrawContext ctx) {
        var cs = categorias();
        int desde = paginaCat * CAT_POR_PAGINA;
        int hasta = Math.min(cs.size(), desde + CAT_POR_PAGINA);
        for (int i = desde; i < hasta; i++) {
            objeto(ctx, pila(cs.get(i).icono()), PANEL_X + 26, categoriaY(i - desde) + 26, 34);
        }
    }

    /** Los rectangulos de las dos flechas del panel. */
    private int pagerX(boolean atras) {
        int cx = PANEL_X + PANEL_W / 2;
        return atras ? cx - 74 : cx + 42;
    }

    private void dibujarPagerCategorias(DrawContext ctx, int rx, int ry) {
        if (paginasCat() <= 1) {
            return;
        }
        int y = finDeCategorias() + 4;
        for (int lado = 0; lado < 2; lado++) {
            boolean atras = lado == 0;
            boolean puede = atras ? paginaCat > 0 : paginaCat < paginasCat() - 1;
            int bx = pagerX(atras);
            boolean encima = puede && dentro(rx, ry, px(bx), py(y), pl(32), pl(26));
            ctx.fill(px(bx), py(y), px(bx + 32), py(y + 26),
                    encima ? FILA_ENCIMA : FILA_FONDO);
            marco(ctx, px(bx), py(y), pl(32), pl(26),
                    encima ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(2)));
            // ⚠ APAGADA, NO ESCONDIDA: un hueco que aparece y desaparece mueve
            //   la otra flecha de sitio, y entonces pulsar dos veces seguidas
            //   falla la segunda.
            texto(ctx, Text.literal(atras ? "<" : ">"), bx + 16, y + 5, 18,
                    puede ? TEXTO_OSCURO : TEXTO_SUAVE, true, false);
        }
        texto(ctx, Text.literal((paginaCat + 1) + " / " + paginasCat()),
                PANEL_X + PANEL_W / 2, y + 5, 18, TEXTO_SUAVE, true, false);
    }

    private void dibujarBuscador(DrawContext ctx) {
        if (campoBusqueda == null) {
            return;
        }
        campoBusqueda.render(ctx, 0, 0, 0);
        if (campoBusqueda.getText().isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.tienda.buscar"),
                    PANT_X + MARGEN + 8, PANT_Y + MARGEN + 8, 14, 0xFF8892AC,
                    false, false);
        }
    }

    private int filaY(int n) {
        return PANT_Y + MARGEN + BUSCA_ALTO + BUSCA_AIRE + n * (FILA_ALTO + FILA_AIRE);
    }

    /**
     * Una fila por artículo.
     *
     * @param objetos {@code false} dibuja el 2D, {@code true} solo los modelos.
     *                Las dos pasadas recorren lo mismo para que las medidas no
     *                puedan separarse.
     */
    private void dibujarFilas(DrawContext ctx, int rx, int ry, boolean objetos) {
        var c = actual();
        if (c == null) {
            if (!objetos) {
                texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                        PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 22, TEXTO_SUAVE,
                        true, false);
            }
            return;
        }
        var visibles = articulos();
        int desde = pagina * filasCaben();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;

        // ⚠ Un filtro que no encuentra nada LO DICE. Una lista vacia sin
        //   explicacion se lee como «la tienda esta rota».
        if (visibles.isEmpty() && !objetos) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.tienda.sin_resultados"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2 - 20, 20, TEXTO_SUAVE,
                    true, false);
            return;
        }

        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= visibles.size()) {
                break;
            }
            var e = visibles.get(i);
            int y = filaY(n);

            if (objetos) {
                objeto(ctx, e.pila(), ax + 14, y + FILA_ALTO / 2 - 16, 32);
                continue;
            }

            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA_ALTO));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA_ALTO),
                    encima ? FILA_ENCIMA : FILA_FONDO);
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA_ALTO), FILA_BORDE, Math.max(1, pl(2)));

            if (encima) {
                bajoElRaton = e;
            }

            // ⚠⚠⚠ EL NOMBRE SE RECORTA, Y ANTES NO. «Máx. Revivir · para la
            //    Máquina Curativa» se metia POR DEBAJO del boton COMPRAR: el
            //    texto se dibujaba entero y el boton encima, asi que la
            //    etiqueta salia cortada a media palabra y con el fondo del
            //    boton detras. No daba ningun error -- solo se veia mal.
            //    El hueco es lo que hay ENTRE el icono y el primer boton, y se
            //    CALCULA de las dos posiciones: escrito a mano volveria a
            //    mentir el dia que un boton se mueva.
            int huecoNombre = (aw - 300) - 58 - 12;
            String nombre = e.etiqueta().isEmpty()
                    ? e.pila().getName().getString()
                    : limpio(e.etiqueta());
            texto(ctx, Text.literal(recortar(nombre, huecoNombre, 21)),
                    ax + 58, y + 10, 21, TEXTO_OSCURO, false, true);

            long total = e.compra() * CANTIDADES[cantidad];
            long saldo = saldoDe(e.moneda());
            boolean puede = saldo < 0 || saldo >= total;
            texto(ctx, Text.literal(String.format("%,d", e.compra()) + " "
                            + nombreMoneda(e.moneda())),
                    ax + 58, y + 36, 16, puede ? TEXTO_SUAVE : ROJO, false, false);

            int mios = tengo(e);
            if (mios > 0) {
                texto(ctx, Text.translatable("pokepad.lunaeternal.tienda.tienes", mios),
                        ax + 300, y + 36, 15, TEXTO_SUAVE, false, false);
            }

            // COMPRAR. Se apaga si no llega el dinero -- y se apaga en vez de
            // desaparecer: que el artículo exista y no puedas pagarlo es
            // información, que no exista es un catálogo distinto.
            boton(ctx, rx, ry, ax + aw - 300, y + 13, 140, 36,
                    Text.translatable("pokepad.lunaeternal.tienda.comprar"),
                    puede, VERDE);

            // VENDER. Se apaga si no se puede vender o si no tienes ninguno.
            boolean vendible = e.venta() > 0;
            boolean hayQueVender = vendible && mios >= CANTIDADES[cantidad];
            boton(ctx, rx, ry, ax + aw - 150, y + 13, 138, 36,
                    Text.translatable(vendible
                            ? "pokepad.lunaeternal.tienda.vender"
                            : "pokepad.lunaeternal.tienda.no_vendible"),
                    hayQueVender, ROJO);
            if (vendible) {
                texto(ctx, Text.literal("+" + String.format("%,d", e.venta())),
                        ax + aw - 150 + 69, y + 2, 13, TEXTO_SUAVE, true, false);
            }
        }
    }

    /** Cantidad, paginación y el último aviso. */
    private void dibujarPie(DrawContext ctx, int rx, int ry) {
        int y = PANT_Y + PANT_H - MARGEN - 40;
        texto(ctx, Text.translatable("pokepad.lunaeternal.tienda.cantidad"),
                PANT_X + MARGEN, y + 12, 16, TEXTO_SUAVE, false, true);

        for (int i = 0; i < CANTIDADES.length; i++) {
            int bx = PANT_X + MARGEN + 90 + i * 78;
            boolean activa = i == cantidad;
            boolean encima = dentro(rx, ry, px(bx), py(y), pl(70), pl(36));
            ctx.fill(px(bx), py(y), px(bx + 70), py(y + 36),
                    activa ? 0xFFF35C0C : (encima ? 0xFFD3DCF2 : FILA_FONDO));
            marco(ctx, px(bx), py(y), pl(70), pl(36), FILA_BORDE, Math.max(1, pl(2)));
            texto(ctx, Text.literal("x" + CANTIDADES[i]), bx + 35, y + 10, 20,
                    activa ? 0xFFFFFFFF : TEXTO_OSCURO, true, false);
        }

        if (!aviso.isEmpty()) {
            texto(ctx, Text.literal(aviso), PANT_X + MARGEN + 350, y + 12, 16,
                    avisoBueno ? VERDE : ROJO, false, true);
        }

        // Las flechas van en la banda naranja del chasis, en los huecos que deja
        // el arte. Las medidas salen de RECORRER el PNG, no de estimarlas.
        if (paginas() > 1) {
            int cx = PANT_X + PANT_W / 2;
            flecha(ctx, rx, ry, cx - PAG_SEP, PAG_Y, false, pagina > 0);
            flecha(ctx, rx, ry, cx + PAG_SEP - 40, PAG_Y, true, pagina < paginas() - 1);
            texto(ctx, Text.literal((pagina + 1) + " / " + paginas()), cx, PAG_Y + 10, 20,
                    0xFF3A2000, true, false);
        }
    }

    private void flecha(DrawContext ctx, int rx, int ry, int ax, int ay,
                        boolean derecha, boolean activa) {
        boolean encima = activa && dentro(rx, ry, px(ax), py(ay), pl(40), pl(40));
        int color = !activa ? 0xFF8A6A4A : (encima ? 0xFFFFFFFF : 0xFF3A2000);
        texto(ctx, Text.literal(derecha ? ">" : "<"), ax + 20, ay + 8, 26, color, true, false);
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

        var cs = categorias();
        int desde = paginaCat * CAT_POR_PAGINA;
        int hasta = Math.min(cs.size(), desde + CAT_POR_PAGINA);
        for (int i = desde; i < hasta; i++) {
            // ⚠⚠ LA RANURA SE CALCULA IGUAL QUE AL DIBUJAR (`i - desde`), y eso
            //    no es una coincidencia: si el dibujado y el clic la calcularan
            //    cada uno a su manera, pulsar una tarjeta abriria LA DE AL LADO.
            //    Es literalmente el fallo que ya tuvo la rejilla del PokePad.
            if (dentro(rx, ry, px(PANEL_X + 16), py(categoriaY(i - desde)),
                    pl(PANEL_W - 32), pl(CAT_ALTO))) {
                categoria = i;
                pagina = 0;
                // ⚠ El filtro es DE LA LISTA QUE MIRAS. Arrastrarlo a otra
                //   categoria la enseñaria medio vacia sin decir por que.
                if (campoBusqueda != null) {
                    campoBusqueda.setText("");
                }
                aviso = "";
                sonar();
                return true;
            }
        }

        if (paginasCat() > 1) {
            int pyCat = finDeCategorias() + 4;
            if (paginaCat > 0 && dentro(rx, ry, px(pagerX(true)), py(pyCat), pl(32), pl(26))) {
                paginaCat--;
                sonar();
                return true;
            }
            if (paginaCat < paginasCat() - 1
                    && dentro(rx, ry, px(pagerX(false)), py(pyCat), pl(32), pl(26))) {
                paginaCat++;
                sonar();
                return true;
            }
        }

        int py0 = PANT_Y + PANT_H - MARGEN - 40;
        for (int i = 0; i < CANTIDADES.length; i++) {
            if (dentro(rx, ry, px(PANT_X + MARGEN + 90 + i * 78), py(py0), pl(70), pl(36))) {
                cantidad = i;
                sonar();
                return true;
            }
        }

        if (paginas() > 1) {
            int cx = PANT_X + PANT_W / 2;
            if (pagina > 0 && dentro(rx, ry, px(cx - PAG_SEP), py(PAG_Y), pl(40), pl(40))) {
                pagina--;
                sonar();
                return true;
            }
            if (pagina < paginas() - 1
                    && dentro(rx, ry, px(cx + PAG_SEP - 40), py(PAG_Y), pl(40), pl(40))) {
                pagina++;
                sonar();
                return true;
            }
        }

        if (campoBusqueda != null && campoBusqueda.mouseClicked(mx, my, boton)) {
            setFocused(campoBusqueda);
            return true;
        }

        return clicFilas(rx, ry) || super.mouseClicked(mx, my, boton);
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        // ⚠ ESCAPE CON EL BUSCADOR ENFOCADO LIMPIA EL FILTRO en vez de cerrar
        //   la tienda: cerrar una pantalla entera por querer borrar lo escrito
        //   es de las cosas que mas molestan.
        if (tecla == 256 && getFocused() == campoBusqueda
                && !campoBusqueda.getText().isEmpty()) {
            campoBusqueda.setText("");
            pagina = 0;
            return true;
        }
        if (getFocused() == campoBusqueda
                && campoBusqueda.keyPressed(tecla, escaneo, mods)) {
            pagina = 0;
            return true;
        }
        return super.keyPressed(tecla, escaneo, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (getFocused() == campoBusqueda) {
            // ⚠⚠ VUELTA A LA PAGINA 1 EN CUANTO CAMBIA EL FILTRO. Si estabas en
            //    la pagina 9 y el filtro deja tres resultados, te quedarias
            //    mirando una pagina VACIA -- sin error, y con pinta de que no
            //    hay nada que comprar.
            boolean r = campoBusqueda.charTyped(c, mods);
            pagina = 0;
            return r;
        }
        return super.charTyped(c, mods);
    }

    private boolean clicFilas(int rx, int ry) {
        var c = actual();
        if (c == null) {
            return false;
        }
        // ⚠⚠ RECORRE `articulos()`, LO MISMO QUE EL DIBUJADO. Si el clic leyera
        //    la categoria SIN FILTRAR, con el buscador puesto comprarias el
        //    articulo que ocupa esa posicion en la lista COMPLETA -- o sea otro
        //    distinto del que estas viendo, y cobrado. Es el fallo de la
        //    rejilla del PokePad con dinero de por medio.
        var visibles = articulos();
        int desde = pagina * filasCaben();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= visibles.size()) {
                break;
            }
            var e = visibles.get(i);
            int y = filaY(n);

            if (dentro(rx, ry, px(ax + aw - 300), py(y + 13), pl(140), pl(36))) {
                long total = e.compra() * CANTIDADES[cantidad];
                long saldo = saldoDe(e.moneda());
                if (saldo >= 0 && saldo < total) {
                    // Se dice aquí en vez de mandarlo: el servidor contestaría lo
                    // mismo, y un viaje de ida y vuelta para un «no te llega» que
                    // el cliente ya sabe es medio segundo de nada por gusto.
                    poner("No te llega. Faltan " + String.format("%,d", total - saldo)
                            + " " + nombreMoneda(e.moneda()) + ".", false);
                    return true;
                }
                mandar(c.id(), e.clave(), true);
                return true;
            }
            if (dentro(rx, ry, px(ax + aw - 150), py(y + 13), pl(138), pl(36))) {
                if (e.venta() <= 0) {
                    poner("Esto no se puede vender.", false);
                    return true;
                }
                if (tengo(e) < CANTIDADES[cantidad]) {
                    poner("No tienes " + CANTIDADES[cantidad] + ".", false);
                    return true;
                }
                mandar(c.id(), e.clave(), false);
                return true;
            }
        }
        return false;
    }

    /**
     * ⚠ Se manda y se espera. El resultado llega por el chat y el saldo nuevo por
     * su propio paquete: adelantarse a pintarlo haría que un rechazo se viera como
     * un cambio que desaparece al reabrir.
     */
    private void mandar(String cat, String item, boolean comprar) {
        sonar();
        aviso = "";
        ClientPlayNetworking.send(new Red.AccionTienda(cat, item,
                CANTIDADES[cantidad], comprar));
    }

    private void poner(String texto, boolean bueno) {
        aviso = texto;
        avisoBueno = bueno;
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 0.8f);
        }
    }

    private void sonar() {
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.0f);
        }
    }

    // ---- utilidades --------------------------------------------------------

    private void boton(DrawContext ctx, int rx, int ry, int ax, int ay, int aw, int ah,
                       Text etiqueta, boolean activo, int color) {
        boolean encima = activo && dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah),
                !activo ? APAGADO : (encima ? aclarar(color) : color));
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), 0xFF20283C, Math.max(1, pl(2)));
        int alto = 18;
        while (alto > 11 && anchoArte(etiqueta.getString(), alto) > aw - 14) {
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

    /** Quita los códigos de color del catálogo. Ver el comentario en categorías. */
    private static String limpio(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    /**
     * Un objeto del juego, a tamaño de arte.
     *
     * <p>⚠ {@code renderItem} dibuja a 16×16 fijos: para agrandarlo hay que
     * escalar la matriz, no pasarle un tamaño. Y va DESPUÉS de {@code ctx.draw()}
     * o el 2D se pinta encima.
     */
    private void objeto(DrawContext ctx, ItemStack pila, int ax, int ay, int altoArte) {
        float escala = altoArte * k / 16f;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(px(ax), py(ay), 0);
        m.scale(escala, escala, 1f);
        ctx.drawItem(pila, 0, 0);
        m.pop();
    }

    private List<String> partir(String texto, int anchoArte, int altoArte) {
        var salida = new java.util.ArrayList<String>();
        var actual = new StringBuilder();
        for (String palabra : limpio(texto).split(" ")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (anchoArte(prueba, altoArte) > anchoArte && !actual.isEmpty()) {
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
