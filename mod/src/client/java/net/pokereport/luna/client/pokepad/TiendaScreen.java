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
    private static final int NAV_ALTO = 72;

    private static final int MARGEN = 14;
    private static final int CAT_ALTO = 86, CAT_AIRE = 8;
    private static final int FILA_ALTO = 62, FILA_AIRE = 6;

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
        return (PANT_H - 2 * MARGEN - 58) / (FILA_ALTO + FILA_AIRE);
    }

    private int paginas() {
        var c = actual();
        if (c == null || c.entradas().isEmpty()) {
            return 1;
        }
        return (c.entradas().size() + filasCaben() - 1) / filasCaben();
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
    private int tengo(String id) {
        if (client == null || client.player == null) {
            return 0;
        }
        var item = Registries.ITEM.get(Identifier.tryParse(id));
        int n = 0;
        for (var pila : client.player.getInventory().main) {
            if (pila.isOf(item)) {
                n += pila.getCount();
            }
        }
        return n;
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
        }

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarCategorias(ctx, rx, ry);

        // ⚠ DOS PASADAS: todo el 2D primero, luego `ctx.draw()`, y solo entonces
        //   los objetos 3D. Mezclarlos hace que el 2D se pinte ENCIMA de los
        //   modelos, porque van por lotes distintos. Regla 3 de dibujado.md.
        dibujarFilas(ctx, rx, ry, false);
        dibujarPie(ctx, rx, ry);
        ctx.draw();
        dibujarIconosCategorias(ctx);
        dibujarFilas(ctx, rx, ry, true);
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

    private int categoriaY(int i) {
        return PANEL_Y + NAV_ALTO + 14 + i * (CAT_ALTO + CAT_AIRE);
    }

    private void dibujarCategorias(DrawContext ctx, int rx, int ry) {
        var cs = categorias();
        for (int i = 0; i < cs.size(); i++) {
            var c = cs.get(i);
            int y = categoriaY(i);
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

        // El saldo, debajo de las categorías. Es el número que decide si puedes
        // comprar, así que va donde se mira antes de pulsar.
        int y = categoriaY(cs.size()) + 12;
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
        for (int i = 0; i < cs.size(); i++) {
            objeto(ctx, pila(cs.get(i).icono()), PANEL_X + 26, categoriaY(i) + 26, 34);
        }
    }

    private int filaY(int n) {
        return PANT_Y + MARGEN + n * (FILA_ALTO + FILA_AIRE);
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
        int desde = pagina * filasCaben();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;

        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= c.entradas().size()) {
                break;
            }
            var e = c.entradas().get(i);
            int y = filaY(n);

            if (objetos) {
                objeto(ctx, pila(e.item()), ax + 14, y + FILA_ALTO / 2 - 16, 32);
                continue;
            }

            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA_ALTO));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA_ALTO),
                    encima ? FILA_ENCIMA : FILA_FONDO);
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA_ALTO), FILA_BORDE, Math.max(1, pl(2)));

            String nombre = e.etiqueta().isEmpty()
                    ? pila(e.item()).getName().getString()
                    : limpio(e.etiqueta());
            texto(ctx, Text.literal(nombre), ax + 58, y + 10, 21, TEXTO_OSCURO, false, true);

            long total = e.compra() * CANTIDADES[cantidad];
            long saldo = saldoDe(e.moneda());
            boolean puede = saldo < 0 || saldo >= total;
            texto(ctx, Text.literal(String.format("%,d", e.compra()) + " "
                            + nombreMoneda(e.moneda())),
                    ax + 58, y + 36, 16, puede ? TEXTO_SUAVE : ROJO, false, false);

            int mios = tengo(e.item());
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
        for (int i = 0; i < cs.size(); i++) {
            if (dentro(rx, ry, px(PANEL_X + 16), py(categoriaY(i)),
                    pl(PANEL_W - 32), pl(CAT_ALTO))) {
                categoria = i;
                pagina = 0;
                aviso = "";
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

        return clicFilas(rx, ry) || super.mouseClicked(mx, my, boton);
    }

    private boolean clicFilas(int rx, int ry) {
        var c = actual();
        if (c == null) {
            return false;
        }
        int desde = pagina * filasCaben();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= c.entradas().size()) {
                break;
            }
            var e = c.entradas().get(i);
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
                mandar(c.id(), e.item(), true);
                return true;
            }
            if (dentro(rx, ry, px(ax + aw - 150), py(y + 13), pl(138), pl(36))) {
                if (e.venta() <= 0) {
                    poner("Esto no se puede vender.", false);
                    return true;
                }
                if (tengo(e.item()) < CANTIDADES[cantidad]) {
                    poner("No tienes " + CANTIDADES[cantidad] + ".", false);
                    return true;
                }
                mandar(c.id(), e.item(), false);
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
