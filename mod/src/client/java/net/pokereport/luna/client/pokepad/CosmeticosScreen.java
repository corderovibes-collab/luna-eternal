package net.pokereport.luna.client.pokepad;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * La tienda de cosméticos del PokePad.
 *
 * <p>Izquierda: el previsualizador 3D y el saldo de LunaCoins con su «+».
 * Derecha: cuatro pestañas y una rejilla de 4×2 con el cosmético en 3D, su
 * precio y su botón.
 *
 * <h2>Las medidas salen del arte, no de esta clase</h2>
 *
 * Los números de {@code PANEL_*} y {@code PANT_*} los <b>midió</b>
 * {@code tools/gen_cosmeticos.py} sobre {@code fondo_cosmeticos.png}, buscando
 * la mancha clara de la derecha y el rectángulo oscuro de la izquierda. Están
 * aquí copiados porque en tiempo de ejecución no se puede analizar el PNG, pero
 * <b>la fuente es el generador</b>: si el fondo cambia, se vuelve a ejecutar y
 * se traen los números nuevos. Escribirlos a ojo es lo que ya dejó al chasis
 * mintiendo cuatro veces.
 *
 * <h2>Lo que esta pantalla NO decide</h2>
 *
 * Nada. El catálogo, los precios, qué posees y qué llevas puesto vienen del
 * servidor (P6), y con {@code D-039} eso pasa de buena costumbre a invariante:
 * si los cosméticos <b>solo</b> se consiguen comprándolos o en un evento, un
 * cliente que pudiera concederse uno sería la única forma de saltárselo.
 *
 * <p>⚠ <b>Hoy el catálogo es de relleno</b> y está marcado como tal en
 * {@link #catalogoProvisional()}. Falta el protocolo; hasta que exista, esta
 * pantalla enseña una muestra para poder juzgarla en el juego.
 *
 * <h2>Antes de tocar el dibujado</h2>
 *
 * Lee {@code docs/ui/dibujado.md}. Son cinco reglas y <b>ninguna da error al
 * compilar</b>: se pagan en horas depurando. La primera —encender la mezcla
 * alfa a mano— costó una noche entera.
 */
public class CosmeticosScreen extends Screen {

    private static final Identifier CHASIS = tex("pokepad_cosmeticos");
    private static final Identifier MONEDA = tex("lunacoin_oro");
    private static final Identifier MAS = tex("boton_mas_luna");
    private static final Identifier ATRAS = tex("boton_atras");
    private static final Identifier CERRAR = tex("boton_cerrar");

    /** El arte, a tamaño real. Ver la regla 2 de `dibujado.md`. */
    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;

    // ---- medidas del arte, MEDIDAS por tools/gen_cosmeticos.py -------------
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;

    private static final int NAV_ALTO = 72;
    private static final int SALDO_ALTO = 147;

    private static final int COLS = 4, FILAS = 2;
    private static final int AIRE = 14, MARGEN = 12, PESTANA_ALTO = 56;

    /**
     * El pie de la celda: precio y botón en la MISMA fila.
     *
     * <p>⚠ Esto solo cabe con 4 columnas, y ese es el motivo de que sean 4. Con
     * 5 la celda medía 144: el precio con su moneda se llevaba 80 y quedaban 60
     * para el botón, donde «COMPRAR» no entra. Había que apilarlos, y apilarlos
     * costaba 72 px de <b>alto</b> que salían del 3D, dejándolo en 124×90 — un
     * Charizard ahí no se distingue.
     */
    private static final int PIE = 38;

    // ---- paleta, la misma de la pantalla principal -------------------------
    private static final int CELDA_FONDO = 0xFFBFCBE8;
    private static final int CELDA_BORDE = 0xFF7C89B4;
    private static final int CELDA_ENCIMA = 0xFFFFF0DC;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int ORO = 0xFFFFD65C;

    private static final String[] CATEGORIAS = { "mascotas", "capas", "sombreros", "auras" };

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private int pestana = 0;
    private int pagina = 0;

    /** Lo que se ve en el previsualizador. {@code null} = nada seleccionado. */
    private Cosmetico enfocado;

    private List<Cosmetico> catalogo = List.of();
    private int lunacoins = 0;

    public CosmeticosScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.cosmeticos"));
        this.anterior = anterior;
    }

    private static Identifier tex(String nombre) {
        return Identifier.of("lunaeternal", "textures/gui/pokepad/" + nombre + ".png");
    }

    @Override
    protected void init() {
        catalogo = catalogoProvisional();
        lunacoins = 12500;
        recalcular();
        // Se enfoca lo que ya llevas puesto: abrir la tienda con el
        // previsualizador vacío haría creer que no tienes nada.
        enfocado = catalogo.stream().filter(Cosmetico::equipado).findFirst().orElse(null);
    }

    /**
     * Muestra de relleno mientras no hay protocolo.
     *
     * <p>⚠ <b>Los precios NO son precios.</b> CLAUDE.md dice que la economía se
     * calibra con datos reales y que hasta que alguien juegue todo son
     * estimaciones. Estos números sirven para ver si la pantalla se lee, no
     * para cobrarlos.
     *
     * <p>Las especies y aspectos sí son reales: salen de
     * {@code CobblemonMoreCosmetics} (MIT), que declara sus cosméticos como
     * aspectos de Cobblemon.
     */
    private static List<Cosmetico> catalogoProvisional() {
        List<Cosmetico> l = new ArrayList<>();
        l.add(new Cosmetico("mascotas", "charizard_knight", "cobblemon:charizard", "knight", 2500, true, true));
        l.add(new Cosmetico("mascotas", "eevee_valentines", "cobblemon:eevee", "valentines", 1200, false, false));
        l.add(new Cosmetico("mascotas", "snorlax_chef", "cobblemon:snorlax", "chef", 1800, false, false));
        l.add(new Cosmetico("mascotas", "mewtwo_boundary", "cobblemon:mewtwo", "boundary", 4000, false, false));
        l.add(new Cosmetico("mascotas", "articuno_steampunk", "cobblemon:articuno", "steampunk", 3500, false, false));
        l.add(new Cosmetico("mascotas", "gardevoir_icedragon", "cobblemon:gardevoir", "icedragon", 0, true, false));
        l.add(new Cosmetico("mascotas", "decidueye_ninja", "cobblemon:decidueye", "ninja", 2200, false, false));
        l.add(new Cosmetico("mascotas", "cinderace_captain", "cobblemon:cinderace", "captain", 2000, false, false));
        l.add(new Cosmetico("mascotas", "weavile_skier", "cobblemon:weavile", "skier", 1500, false, false));
        l.add(new Cosmetico("mascotas", "carbink_royal", "cobblemon:carbink", "royal", 2800, false, false));
        return l;
    }

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

        // ⚠ REGLA 3: encoger con vecino más próximo TIRA filas y columnas
        // enteras. Solo si el tamaño no sale exacto se pasa a filtrado lineal,
        // que reparte el error en vez de dejar rayas cruzando el chasis.
        boolean exacto = Math.round(ancho * gui) == NAT_ANCHO
                && Math.round(alto * gui) == NAT_ALTO;
        if (client != null) {
            for (Identifier t : new Identifier[] { CHASIS, MONEDA, MAS, ATRAS, CERRAR }) {
                client.getTextureManager().getTexture(t).setFilter(!exacto, false);
            }
        }
    }

    /** El juego sigue corriendo detrás: es un menú, no una pausa. */
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
        return Math.round(largoArt * k);
    }

    /** Los cosméticos de la pestaña activa. */
    private List<Cosmetico> visibles() {
        String cat = CATEGORIAS[pestana];
        return catalogo.stream().filter(c -> c.categoria().equals(cat)).toList();
    }

    private int porPagina() {
        return COLS * FILAS;
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int ratonX, int ratonY, float delta) {
        super.render(ctx, ratonX, ratonY, delta);
        recalcular();

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);

        dibujarNavegacion(ctx, ratonX, ratonY);
        dibujarPreview(ctx, delta);
        dibujarSaldo(ctx, ratonX, ratonY);
        dibujarPestanas(ctx, ratonX, ratonY);
        dibujarRejilla(ctx, ratonX, ratonY, delta);
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = py(PANEL_Y + NAV_ALTO / 2);
        int aw = pl(60), ah = pl(48);
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), cy - ah / 2, aw, ah, 60, 48);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("pokepad.lunaeternal.inicio"),
                px(PANEL_X + 18) + aw + pl(10), cy - textRenderer.fontHeight / 2, 0xFFD2D8E8);

        int cw = pl(80), chh = pl(64);
        dibujarTextura(ctx, CERRAR, px(PANEL_X + PANEL_W - 18) - cw, cy - chh / 2, cw, chh, 80, 64);
    }

    private void dibujarPreview(DrawContext ctx, float delta) {
        int bx = px(PANEL_X + 8);
        int by = py(PANEL_Y + NAV_ALTO);
        int bw = pl(PANEL_W - 16);
        int bh = pl(PANEL_H - NAV_ALTO - SALDO_ALTO - 8);

        if (enfocado == null) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("pokepad.lunaeternal.sin_seleccion"),
                    bx + bw / 2, by + bh / 2, TEXTO_SUAVE);
            return;
        }
        // El previsualizador es grande: el modelo va al triple que en la celda.
        Mascota3D.dibujar(ctx, enfocado, bx, by, bw, bh, 62f * k, delta);

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(enfocado.aspecto()),
                bx + bw / 2, by + bh - textRenderer.fontHeight - pl(6), ORO);
    }

    private void dibujarSaldo(DrawContext ctx, int rx, int ry) {
        int cy = py(PANEL_Y + PANEL_H - SALDO_ALTO / 2);
        int m = pl(40);
        dibujarTextura(ctx, MONEDA, px(PANEL_X + 24), cy - m / 2, m, m, 100, 100);
        ctx.drawTextWithShadow(textRenderer, Text.literal(String.format("%,d", lunacoins)),
                px(PANEL_X + 24) + m + pl(14), cy - textRenderer.fontHeight / 2, ORO);

        int mw = pl(58);
        dibujarTextura(ctx, MAS, px(PANEL_X + PANEL_W - 22) - mw, cy - mw / 2, mw, mw, 58, 58);
    }

    private void dibujarPestanas(DrawContext ctx, int rx, int ry) {
        int anchoUtil = PANT_W - 2 * MARGEN;
        int pw = anchoUtil / CATEGORIAS.length;
        for (int i = 0; i < CATEGORIAS.length; i++) {
            int x = px(PANT_X + MARGEN + i * pw);
            int y = py(PANT_Y + MARGEN);
            int w = pl(pw - 6), h = pl(PESTANA_ALTO);
            boolean activa = i == pestana;
            boolean encima = dentro(rx, ry, x, y, w, h);

            ctx.fill(x, y, x + w, y + h, activa || encima ? CELDA_ENCIMA : CELDA_FONDO);
            marco(ctx, x, y, w, h, activa || encima ? BORDE_ENCIMA : CELDA_BORDE,
                    Math.max(1, pl(activa ? 4 : 2)));
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("pokepad.lunaeternal.cat." + CATEGORIAS[i]),
                    x + w / 2, y + h / 2 - textRenderer.fontHeight / 2, TEXTO_OSCURO);
        }
    }

    private void dibujarRejilla(DrawContext ctx, int rx, int ry, float delta) {
        List<Cosmetico> lista = visibles();
        int anchoUtil = PANT_W - 2 * MARGEN;
        int gy0 = PANT_Y + MARGEN + PESTANA_ALTO + AIRE;
        int altoUtil = PANT_Y + PANT_H - gy0 - MARGEN;
        int cw = (anchoUtil - (COLS - 1) * AIRE) / COLS;
        int ch = (altoUtil - (FILAS - 1) * AIRE) / FILAS;

        int desde = pagina * porPagina();
        for (int n = 0; n < porPagina(); n++) {
            int idx = desde + n;
            if (idx >= lista.size()) {
                break;
            }
            Cosmetico c = lista.get(idx);
            int ax = PANT_X + MARGEN + (n % COLS) * (cw + AIRE);
            int ay = gy0 + (n / COLS) * (ch + AIRE);
            dibujarCelda(ctx, c, px(ax), py(ay), pl(cw), pl(ch), rx, ry, delta);
        }
    }

    private void dibujarCelda(DrawContext ctx, Cosmetico c, int x, int y, int w, int h,
                              int rx, int ry, float delta) {
        boolean encima = dentro(rx, ry, x, y, w, h);
        boolean elegido = enfocado != null && enfocado.id().equals(c.id());

        ctx.fill(x, y, x + w, y + h, encima ? CELDA_ENCIMA : CELDA_FONDO);
        marco(ctx, x, y, w, h,
                encima || elegido || c.equipado() ? BORDE_ENCIMA : CELDA_BORDE,
                Math.max(1, pl(encima || elegido || c.equipado() ? 4 : 2)));

        int pie = pl(PIE);
        Mascota3D.dibujar(ctx, c, x + pl(10), y + pl(8), w - pl(20), h - pie - pl(8),
                22f * k, delta);

        // El nombre del aspecto va SOBRE el 3D y no encima de él: encima costaba
        // 28 px de alto que el modelo necesita más.
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(c.aspecto()),
                x + w / 2, y + h - pie - textRenderer.fontHeight - pl(4), TEXTO_SUAVE);

        dibujarPie(ctx, c, x, y + h - pie, w, pie);
    }

    private void dibujarPie(DrawContext ctx, Cosmetico c, int x, int y, int w, int h) {
        int cy = y + h / 2;
        int bw = pl(84);
        int bx = x + w - pl(10) - bw;

        Cosmetico.Estado est = c.estado();
        if (est == Cosmetico.Estado.COMPRAR) {
            int m = pl(22);
            dibujarTextura(ctx, MONEDA, x + pl(12), cy - m / 2, m, m, 100, 100);
            ctx.drawTextWithShadow(textRenderer, Text.literal(String.valueOf(c.precio())),
                    x + pl(38), cy - textRenderer.fontHeight / 2, TEXTO_OSCURO);
        } else {
            // Ya es tuyo: enseñar el precio otra vez invita a pagarlo dos veces.
            ctx.drawTextWithShadow(textRenderer,
                    Text.translatable("pokepad.lunaeternal.tuyo"),
                    x + pl(12), cy - textRenderer.fontHeight / 2, TEXTO_SUAVE);
        }

        int relleno = switch (est) {
            case COMPRAR -> BORDE_ENCIMA;
            case DE_EVENTO -> 0xFF6E7899;
            case EQUIPAR -> 0xFF567AC8;
            case EQUIPADO -> 0xFFCEDCF4;
        };
        String clave = switch (est) {
            case COMPRAR -> "comprar";
            case DE_EVENTO -> "evento";
            case EQUIPAR -> "equipar";
            case EQUIPADO -> "equipado";
        };
        int tinta = est == Cosmetico.Estado.EQUIPADO ? 0xFF185C34 : 0xFFFFFFFF;

        ctx.fill(bx, cy - pl(16), bx + bw, cy + pl(16), relleno);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("pokepad.lunaeternal." + clave),
                bx + bw / 2, cy - textRenderer.fontHeight / 2, tinta);
    }

    // ---- interacción -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) {
            return super.mouseClicked(mx, my, boton);
        }
        int rx = (int) mx, ry = (int) my;

        // Navegación
        int cy = py(PANEL_Y + NAV_ALTO / 2);
        if (dentro(rx, ry, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48))) {
            sonar(true);
            volver();
            return true;
        }
        if (dentro(rx, ry, px(PANEL_X + PANEL_W - 18) - pl(80), cy - pl(32), pl(80), pl(64))) {
            sonar(true);
            close();
            return true;
        }

        // Pestañas
        int pw = (PANT_W - 2 * MARGEN) / CATEGORIAS.length;
        for (int i = 0; i < CATEGORIAS.length; i++) {
            if (dentro(rx, ry, px(PANT_X + MARGEN + i * pw), py(PANT_Y + MARGEN),
                    pl(pw - 6), pl(PESTANA_ALTO))) {
                if (i != pestana) {
                    pestana = i;
                    pagina = 0;
                    sonar(true);
                }
                return true;
            }
        }

        // Celdas
        List<Cosmetico> lista = visibles();
        int anchoUtil = PANT_W - 2 * MARGEN;
        int gy0 = PANT_Y + MARGEN + PESTANA_ALTO + AIRE;
        int altoUtil = PANT_Y + PANT_H - gy0 - MARGEN;
        int cw = (anchoUtil - (COLS - 1) * AIRE) / COLS;
        int ch = (altoUtil - (FILAS - 1) * AIRE) / FILAS;
        int desde = pagina * porPagina();

        for (int n = 0; n < porPagina(); n++) {
            int idx = desde + n;
            if (idx >= lista.size()) {
                break;
            }
            int ax = PANT_X + MARGEN + (n % COLS) * (cw + AIRE);
            int ay = gy0 + (n / COLS) * (ch + AIRE);
            if (!dentro(rx, ry, px(ax), py(ay), pl(cw), pl(ch))) {
                continue;
            }
            Cosmetico c = lista.get(idx);

            // ⚠ EL CLIC EN LA CELDA SOLO PREVISUALIZA. Comprar y equipar son el
            // botón del pie, y a propósito: en una tienda, un clic en cualquier
            // parte del artículo que además COBRA es cómo se gasta dinero sin
            // querer.
            enfocado = c;
            int py0 = py(ay) + pl(ch) - pl(PIE);
            int bw = pl(84);
            int bx = px(ax) + pl(cw) - pl(10) - bw;
            if (dentro(rx, ry, bx, py0, bw, pl(PIE))) {
                accion(c);
            } else {
                sonar(true);
            }
            return true;
        }
        return super.mouseClicked(mx, my, boton);
    }

    /**
     * Pulsar el botón del pie.
     *
     * <p>⚠ <b>Aquí no se compra nada todavía, y es correcto que así sea.</b> La
     * compra la decide el servidor: descuenta, comprueba saldo y anota la
     * posesión, con clave de idempotencia (R4). Un cliente que se marcara el
     * cosmético como comprado estaría mintiéndose — y con D-039, donde la única
     * fuente es el servidor, sería además la única forma de saltárselo.
     *
     * <p>Falta el paquete. Hasta que exista, esto suena y no hace nada, que es
     * preferible a fingir una compra que no ha ocurrido.
     */
    private void accion(Cosmetico c) {
        sonar(c.estado() != Cosmetico.Estado.DE_EVENTO);
        // TODO(protocolo): mandar COMPRAR o EQUIPAR al servidor y esperar su
        // respuesta antes de cambiar nada en pantalla.
    }

    private void volver() {
        if (client != null) {
            client.setScreen(anterior);
        }
    }

    /** Lo bloqueado suena DISTINTO, no en silencio: sin sonido parece que no ha llegado el clic. */
    private void sonar(boolean lleva) {
        if (client != null && client.player != null) {
            client.player.playSound(lleva
                    ? SoundEvents.UI_BUTTON_CLICK.value()
                    : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 1.0f);
        }
    }

    // ---- utilidades --------------------------------------------------------

    private static boolean dentro(int rx, int ry, int x, int y, int w, int h) {
        return rx >= x && rx < x + w && ry >= y && ry < y + h;
    }

    /** Marco de N px por dentro de la caja. */
    private static void marco(DrawContext ctx, int x, int y, int w, int h, int color, int g) {
        ctx.fill(x, y, x + w, y + g, color);
        ctx.fill(x, y + h - g, x + w, y + h, color);
        ctx.fill(x, y, x + g, y + h, color);
        ctx.fill(x + w - g, y, x + w, y + h, color);
    }

    /**
     * ⚠ REGLA 1 de `dibujado.md`: <b>la mezcla alfa hay que encenderla a mano</b>.
     *
     * <p>Sin {@code enableBlend()} el juego trata cualquier alfa mayor que cero
     * como opaco: un píxel con alfa 1 sale a todo color. Se ve como motas de
     * colores o como cerco negro alrededor de cada icono según lo que el arte
     * guarde debajo — parecen dos fallos y es uno. Y no está en el arte: se
     * perdieron tres diagnósticos buscándolo ahí.
     */
    private static void dibujarTextura(DrawContext ctx, Identifier textura,
                                       int x, int y, int w, int h, int natW, int natH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(textura, x, y, w, h, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
    }
}
