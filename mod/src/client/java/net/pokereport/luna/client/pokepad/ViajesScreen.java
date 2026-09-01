package net.pokereport.luna.client.pokepad;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;

/**
 * VIAJES: el moto taxi de la ciudadela.
 *
 * <h2>Por qué esto es una pantalla y no un trozo de Explorar</h2>
 *
 * Empezó siéndolo, y estaba mal. <b>Explorar</b> responde a «¿a qué mundo voy?»
 * — dos opciones grandes, una decisión con consecuencias, un viaje que cambia
 * las reglas del juego. <b>Viajes</b> responde a «¿a qué esquina de la
 * ciudadela voy?» — siete destinos equivalentes, sin consecuencias, algo que se
 * hace veinte veces al día.
 *
 * <p>Mezclarlas obligaba a que la segunda cupiera en el hueco que dejaba la
 * primera, que es como acabaron siendo una fila de botoncitos apretados.
 *
 * <h2>⚠⚠ SE VIAJA DESDE CUALQUIER MUNDO (orden del usuario)</h2>
 *
 * Empezó siendo solo desde la ciudadela. Fuera de ella la pantalla <b>no se
 * apaga</b>: lo único que cambia es que se avisa de que el viaje te <b>saca del
 * mundo en el que estás</b> — que es lo que un jugador necesita saber antes de
 * pulsar, no después de aparecer en otro sitio.
 *
 * <h2>⚠⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 *
 * La geometría sale de {@link Escalado}, como todas.
 */
public class ViajesScreen extends Screen {

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

    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int CONTORNO_OSCURO = 0xFF080B12;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int ORO = 0xFFFFD65C;
    private static final int VERDE = 0xFF2E9E56;
    private static final int APAGADO = 0xFF6E7899;

    /**
     * Las paradas, y <b>el mismo orden que {@code Paradas.TODAS}</b> en el
     * servidor.
     *
     * <p>⚠ El color es el que las distingue de un vistazo. No es adorno: con
     * siete destinos del mismo tono, elegir obliga a leerlos todos cada vez —
     * con color, a la tercera visita ya vas al cuadro naranja sin leer.
     */
    private record Parada(String id, int color) {}

    /**
     * LAS SIETE, cada una con su ilustración propia.
     *
     * <h2>⚠⚠ Ya no es un icono sobre un cuadro de color: es la ficha entera</h2>
     *
     * Empezaron siendo formas dibujadas a mano (se veían mal), después objetos
     * del juego (se veían bien pero eran de Minecraft, no del servidor), y hoy
     * son <b>ilustraciones propias</b> que el usuario generó con IA a partir de
     * los prompts de {@code docs/ui/prompts-viajes.md}.
     *
     * <p>⚠ EL COLOR SE QUEDA aunque ya no pinte el fondo. Sigue haciendo dos
     * cosas: es el color del borde al pasar por encima, y es <b>el que se le
     * pidió al arte</b> — cada ilustración tiene ese tono como dominante, así
     * que la serie pega por construcción y no porque alguien la emparejara a
     * ojo. Es la misma idea que los 16 colores del neón y del hormigón (D-032).
     *
     * <p>⚠⚠ Y las siete son de NOCHE con la misma luna, a propósito: la
     * ciudadela tiene noche permanente y el servidor se llama Luna Eternal. Sin
     * eso serían siete dibujos sueltos en vez de una serie.
     */
    private static final Parada[] PARADAS = {
        new Parada("torre_batalla",   0xFF8C3A2E),
        new Parada("laboratorio",     0xFF2E6E8C),
        new Parada("palacio",         0xFF7A5C1E),
        new Parada("monumentos",      0xFF5A5A6E),
        new Parada("torre_comercial", 0xFF2E7A4E),
        new Parada("centro_curacion", 0xFF9E3A5C),
        new Parada("montana",         0xFF4A6E8C),
    };

    /** Cuánto mide el PNG de cada parada. Las siete son cuadradas de 512. */
    private static final int ARTE_LADO = 512;

    /** La ruta del arte de cada parada, resuelta una vez. */
    private static final java.util.Map<String, Identifier> ARTE =
            new java.util.HashMap<>();

    static {
        for (Parada p : PARADAS) {
            ARTE.put(p.id(), Identifier.of("lunaeternal",
                    "textures/gui/viajes/" + p.id() + ".png"));
        }
    }

    private static final int COLS = 4;
    private static final int SEP = 10;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private Red.EstadoViajes estado;
    private int elegida = 0;
    private long pulsado;

    public ViajesScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.warps"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirViajes());
    }

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

    /**
     * ¿Está en la ciudadela?
     *
     * <p>⚠ Ya <b>no decide si se puede viajar</b> — se viaja desde cualquier
     * mundo. Solo decide <b>qué aviso se enseña</b>: desde fuera, el viaje te
     * saca del mundo en el que estás, y eso hay que decirlo antes.
     */
    private boolean dentroCiudadela() {
        return estado == null || estado.enCiudadela();
    }

    /** El estado ya llegó: hasta entonces no se deja pulsar. */
    private boolean listo() {
        return estado != null;
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        var nuevo = EstadoCliente.viajes();
        if (nuevo != null && nuevo != estado) {
            estado = nuevo;
            pulsado = 0;
        }

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanel(ctx, rx, ry);
        dibujarRejilla(ctx, rx, ry);
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 120, 96);
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2, pl(60) + 4, pl(48) + 4,
                    BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicio"),
                PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false, 0);

        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4,
                    BORDE_ENCIMA, 2);
        }
    }

    // ---- la rejilla de destinos --------------------------------------------

    private int rejillaY() {
        return PANT_Y + MARGEN + 46;
    }

    private int celdaW() {
        return (PANT_W - 2 * MARGEN - (COLS - 1) * SEP) / COLS;
    }

    private int celdaH() {
        // ⚠ 200 y no 168: la rejilla ocupa el alto que hay. Con 168 sobraban 90
        //   px muertos abajo y las fichas parecían apretadas contra el título.
        //   Comprobado: 262 + 200 + 10 + 200 = 672, y el marco acaba en 686.
        return 200;
    }

    private int celdaX(int i) {
        return PANT_X + MARGEN + (i % COLS) * (celdaW() + SEP);
    }

    private int celdaY(int i) {
        return rejillaY() + (i / COLS) * (celdaH() + SEP);
    }

    private void dibujarRejilla(DrawContext ctx, int rx, int ry) {
        boolean dentro = listo();

        texto(ctx, Text.translatable("pokepad.lunaeternal.viajes.titulo"),
                PANT_X + MARGEN, PANT_Y + MARGEN + 6, 22,
                dentro ? 0xFF16203A : 0xFF6E7899, false, 0);
        if (dentro && !dentroCiudadela()) {
            // ⚠ Un aviso, no un candado: el viaje SE PUEDE hacer y lo que hay
            //   que decir es que te saca de donde estás.
            textoDer(ctx, Text.translatable("pokepad.lunaeternal.viajes.fuera"),
                    PANT_X + PANT_W - MARGEN, PANT_Y + MARGEN + 10, 14, 0xFFD98A2B);
        }

        int w = celdaW(), h = celdaH();
        for (int i = 0; i < PARADAS.length; i++) {
            var p = PARADAS[i];
            int cx = celdaX(i), cy = celdaY(i);
            boolean sel = i == elegida;
            boolean enc = dentro && dentro(rx, ry, px(cx), py(cy), pl(w), pl(h));

            // ⚠⚠ EL ARTE LLENA LA FICHA, RECORTADO Y NO ESTIRADO. La ficha es
            //    186x200 y el PNG es cuadrado: dibujarlo tal cual lo estiraria
            //    un 7% a lo alto. Se recorta por los lados, y el recorte SE
            //    CALCULA de w y h -- escrito a mano dejaria de cuadrar el dia
            //    que la rejilla cambie de tamaño, y nadie lo notaria.
            arte(ctx, p, px(cx), py(cy), pl(w), pl(h), w, h);
            if (!dentro) {
                // Fuera de la ciudadela se apaga, no se esconde: que exista y
                // no se pueda usar ES informacion.
                ctx.fill(px(cx), py(cy), px(cx + w), py(cy + h), 0xB4101828);
            } else if (enc) {
                ctx.fill(px(cx), py(cy), px(cx + w), py(cy + h), 0x22FFFFFF);
            }
            // ⚠ Una banda más oscura abajo para el nombre: sobre un color plano
            //   el texto se lee, pero la banda es lo que separa «el cuadro» de
            //   «lo que dice el cuadro» y hace que la rejilla se lea como fichas.
            ctx.fill(px(cx), py(cy + h - 46), px(cx + w), py(cy + h), 0x66000000);
            marco(ctx, px(cx), py(cy), pl(w), pl(h),
                    sel && dentro ? BORDE_ENCIMA
                                  : (dentro ? aclarar(p.color()) : 0xFF2A3040),
                    Math.max(2, pl(sel ? 4 : 2)));

            var et = Text.translatable("pokepad.lunaeternal.parada." + p.id());
            int alto = 16;
            while (alto > 10 && anchoArte(et.getString(), alto) > w - 14) {
                alto--;
            }
            // Cabe en dos líneas si hace falta: «Palacio de Entrenadores» no
            // entra en una a un tamaño legible.
            var lineas = partir(et.getString(), w - 14, alto);
            int ty = cy + h - 46 + (46 - lineas.size() * (alto + 3)) / 2;
            for (String l : lineas) {
                texto(ctx, Text.literal(l), cx + w / 2, ty, alto,
                        dentro ? 0xFFFFFFFF : 0xFF8892AC, true, CONTORNO_OSCURO);
                ty += alto + 3;
            }
        }
    }

    /**
     * EL ARTE DE UNA PARADA, recortado para llenar un hueco sin deformarse.
     *
     * <p>El PNG es cuadrado y el hueco no tiene por qué serlo, así que se coge
     * <b>el trozo centrado</b> del PNG que tiene la misma proporción que el
     * hueco. Estirarlo sería más corto de escribir y se notaría: en la rejilla
     * la luna saldría ovalada.
     *
     * @param destW,destH el tamaño del hueco en píxeles del CHASIS, que es de
     *                    donde sale la proporción. Los otros son ya de pantalla
     */
    private void arte(DrawContext ctx, Parada p, int x, int y, int w, int h,
                      int destW, int destH) {
        Identifier tex = ARTE.get(p.id());
        if (tex == null) {
            return;
        }
        int regW = ARTE_LADO, regH = ARTE_LADO;
        if (destW > destH) {
            // más ancho que alto: se recorta arriba y abajo
            regH = Math.max(1, ARTE_LADO * destH / destW);
        } else {
            regW = Math.max(1, ARTE_LADO * destW / destH);
        }
        int u = (ARTE_LADO - regW) / 2, v = (ARTE_LADO - regH) / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, u, v, regW, regH,
                ARTE_LADO, ARTE_LADO);
        RenderSystem.disableBlend();
    }

    // ---- el panel ----------------------------------------------------------

    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        var p = PARADAS[Math.max(0, Math.min(elegida, PARADAS.length - 1))];
        int y = PANEL_Y + NAV_ALTO + 22;

        // Un recuadro con el color y el icono del destino: es lo que ata el
        // panel a la ficha que acabas de pulsar.
        int rw = PANEL_W - 60, rh = 120;
        ctx.fill(px(PANEL_X + 30), py(y), px(PANEL_X + 30 + rw), py(y + rh),
                listo() ? p.color() : 0xFF3A4050);
        marco(ctx, px(PANEL_X + 30), py(y), pl(rw), pl(rh), 0xFF20283C,
                Math.max(2, pl(2)));
        // ⚠ Aquí el hueco es APAISADO (255x120) y el PNG cuadrado, así que se
        //   recorta por arriba y por abajo. Se dibuja DENTRO del marco, con 3
        //   px de holgura, para que el borde no quede pisado por el arte.
        arte(ctx, p, px(PANEL_X + 33), py(y + 3), pl(rw - 6), pl(rh - 6),
                rw - 6, rh - 6);
        y += rh + 18;

        for (String l : partir(Text.translatable(
                "pokepad.lunaeternal.parada." + p.id()).getString(), PANEL_W - 60, 26)) {
            texto(ctx, Text.literal(l), PANEL_X + PANEL_W / 2, y, 26, 0xFFFFFFFF,
                    true, CONTORNO_OSCURO);
            y += 30;
        }
        y += 8;
        separador(ctx, y);
        y += 14;
        for (String l : partir(Text.translatable(
                "pokepad.lunaeternal.parada." + p.id() + ".desc").getString(),
                PANEL_W - 60, 15)) {
            texto(ctx, Text.literal(l), PANEL_X + 30, y, 15, 0xFFC2CCE2, false, 0);
            y += 21;
        }

        if (listo() && !dentroCiudadela()) {
            y += 12;
            for (String l : partir(Text.translatable(
                    "pokepad.lunaeternal.viajes.fuera_largo").getString(),
                    PANEL_W - 60, 13)) {
                texto(ctx, Text.literal(l), PANEL_X + 30, y, 13, TEXTO_SUAVE, false, 0);
                y += 18;
            }
        }

        boton(ctx, rx, ry, PANEL_X + 30, PANEL_Y + PANEL_H - 72, PANEL_W - 60, 56,
                Text.translatable("pokepad.lunaeternal.viajes.ir"),
                listo() && !esperando(), VERDE);
    }

    // ---- interacción -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        int rx = (int) mx, ry = (int) my;

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

        if (listo()) {
            int w = celdaW(), h = celdaH();
            for (int i = 0; i < PARADAS.length; i++) {
                if (dentro(rx, ry, px(celdaX(i)), py(celdaY(i)), pl(w), pl(h))) {
                    // ⚠ Un clic ELIGE; para viajar hay que pulsar el botón. Con
                    //   el viaje en el propio cuadro, un clic despistado te
                    //   manda al otro lado de la ciudadela sin querer.
                    elegida = i;
                    sonar();
                    return true;
                }
            }
            if (dentro(rx, ry, px(PANEL_X + 30), py(PANEL_Y + PANEL_H - 72),
                    pl(PANEL_W - 60), pl(56)) && !esperando()) {
                sonar();
                pulsado = System.currentTimeMillis();
                ClientPlayNetworking.send(new Red.AccionViaje(PARADAS[elegida].id()));
                return true;
            }
        }
        return super.mouseClicked(mx, my, boton);
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        if (tecla == 256) {
            if (client != null) {
                client.setScreen(anterior);
            }
            return true;
        }
        return super.keyPressed(tecla, escaneo, mods);
    }

    private void sonar() {
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.0f);
        }
    }

    // ---- utilidades --------------------------------------------------------

    private void boton(DrawContext ctx, int rx, int ry, int ax, int ay, int aw,
                       int ah, Text etiqueta, boolean activo, int color) {
        boolean enc = activo && dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah),
                !activo ? APAGADO : (enc ? aclarar(color) : color));
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), 0xFF10331E, Math.max(1, pl(2)));
        texto(ctx, etiqueta, ax + aw / 2, ay + ah / 2 - 12, 24,
                activo ? 0xFFFFFFFF : 0xFFD8DEEA, true, 0);
    }

    private static int aclarar(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 40);
        int b = Math.min(255, (color & 0xFF) + 40);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private List<String> partir(String s, int anchoMax, int alto) {
        var salida = new ArrayList<String>();
        var actual = new StringBuilder();
        for (String palabra : s.split(" ")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (anchoArte(prueba, alto) > anchoMax && !actual.isEmpty()) {
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

    private void textoDer(DrawContext ctx, Text linea, int derecha, int arriba,
                          int alto, int color) {
        int a = Math.round(textRenderer.getWidth(linea) * alto
                / (float) textRenderer.fontHeight);
        texto(ctx, linea, derecha - a, arriba, alto, color, false, 0);
    }

    private void texto(DrawContext ctx, Text linea, int cx, int arriba, int alto,
                       int color, boolean centrado, int contorno) {
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
        if (contorno != 0) {
            ctx.drawText(textRenderer, linea, tx - 1, ty, contorno, false);
            ctx.drawText(textRenderer, linea, tx + 1, ty, contorno, false);
            ctx.drawText(textRenderer, linea, tx, ty - 1, contorno, false);
            ctx.drawText(textRenderer, linea, tx, ty + 1, contorno, false);
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
