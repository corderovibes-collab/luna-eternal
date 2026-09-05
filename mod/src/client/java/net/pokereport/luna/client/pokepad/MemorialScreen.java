package net.pokereport.luna.client.pokepad;

import java.util.List;
import java.util.UUID;

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
 * EL MEMORIAL DE UN NICHO: la foto grande, la historia, y el boton de honrar.
 *
 * <h2>⚠⚠ SE ABRE TAMBIEN DESDE EL MUNDO, no solo desde la lista</h2>
 *
 * El clic derecho en el proyector de un nicho ocupado lleva aqui. Por eso esta
 * pantalla es DE MIRAR: no tiene campos. El dueno edita desde «mi nicho»; aqui
 * todos leen y honran.
 *
 * <h2>⚠⚠ LA CAMPANILLA SUENA SOLO SI EL SERVIDOR DICE QUE CUENTA</h2>
 *
 * El honor se manda y se espera {@code RespuestaHonor}. Si sonara al pulsar,
 * sonaria tambien cuando el servidor rechaza por el tope diario -- y eso
 * miente. El sonido es la campanilla de amatista, de vainilla: no hay ningun
 * OGG propio todavia, y mientras no llegue, esta es la voz del memorial.
 *
 * <h2>⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 */
public class MemorialScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier ICONO =
            Identifier.of("lunaeternal", "textures/gui/pokepad/santuario.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    private static final int MARGEN = 14;

    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int VERDE = 0xFF2E9E56;
    private static final int ORO = 0xFFD9A32B;
    private static final int FILA_FONDO = 0xFFBFCBE8;
    private static final int FILA_BORDE = 0xFF7C89B4;

    private final Screen anterior;
    private final String nichoId;
    private final String nombre;
    private final String dueno;

    private float k;
    private int ancho, alto, x0, y0;

    /** La ultima respuesta de honor que ya procesamos (por su contenido). */
    private long vistoTotal = -1;
    private int vistoRestantes = -1;

    public MemorialScreen(Screen anterior, Red.NichoSantuario nicho) {
        super(Text.translatable("pokepad.lunaeternal.app.santuario"));
        this.anterior = anterior;
        this.nichoId = nicho.id();
        this.nombre = nicho.nombre();
        this.dueno = nicho.estado().dueno();
        // ⚠ Se pide la foto YA: la pantalla se abre y la imagen llega sola.
        if (!nicho.memorial().foto().isEmpty()) {
            net.pokereport.luna.client.TexturasFoto.pedir(nicho.memorial().foto());
        }
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirSantuario());
        // ⚠ Un sonido suave al abrir: es el recibidor del memorial. Vainilla
        //   (resonancia de amatista), como la campanilla -- sin OGG propio aun.
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
                    0.35f, 0.7f);
        }
    }

    private void recalcular() {
        var m = Escalado.aplicar(client, width, height, CHASIS, ATRAS, CERRAR, ICONO);
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

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        super.render(ctx, rx, ry, delta);
        // ⚠ La campanilla del honor: suena cuando llega la respuesta y dice
        //   que cuenta (nunca al pulsar -- un rechazo no se celebra).
        campanillaSiToca(EstadoCliente.honor());
        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNav(ctx, rx, ry);

        var nicho = elNicho();
        if (nicho == null) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 24, TEXTO_SUAVE, true, false);
            return;
        }
        var memorial = nicho.memorial();
        int ax = PANT_X + MARGEN;

        // ---- izquierda: la foto, grande, sin deformar, con marco doble
        int fw = 340, fh = 310;
        int fx = ax + 10, fy = PANT_Y + MARGEN + 10;
        ctx.fill(px(fx), py(fy), px(fx + fw), py(fy + fh), FILA_FONDO);
        marco(ctx, px(fx) - pl(3), py(fy) - pl(3), pl(fw) + pl(6), pl(fh) + pl(6),
                0xFF20283C, pl(3));
        marco(ctx, px(fx), py(fy), pl(fw), pl(fh), FILA_BORDE, Math.max(1, pl(2)));
        if (memorial.foto().isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.santuario.sin_foto"),
                    fx + fw / 2, fy + fh / 2, 18, TEXTO_SUAVE, true, false);
        } else {
            var foto = net.pokereport.luna.client.TexturasFoto.lista(memorial.foto());
            if (foto == null) {
                net.pokereport.luna.client.TexturasFoto.pedir(memorial.foto());
                texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                        fx + fw / 2, fy + fh / 2, 18, TEXTO_SUAVE, true, false);
            } else {
                float kFoto = Math.min(fw / (float) foto.ancho(),
                        fh / (float) foto.alto());
                int dw = Math.round(foto.ancho() * kFoto);
                int dh = Math.round(foto.alto() * kFoto);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                ctx.drawTexture(foto.textura(), px(fx + (fw - dw) / 2), py(fy + (fh - dh) / 2),
                        pl(dw), pl(dh), 0f, 0f, foto.ancho(), foto.alto(),
                        foto.ancho(), foto.alto());
                RenderSystem.disableBlend();
            }
        }

        // ---- derecha: dueno arriba en oro, el titulo debajo en grande
        //      (hasta dos lineas), y la historia ocupa el resto del hueco.
        int tx = fx + fw + 26;
        int tw = PANT_W - MARGEN - (fw + 26 + 10) - MARGEN;
        int ty = PANT_Y + MARGEN + 16;
        texto(ctx, Text.translatable("pokepad.lunaeternal.santuario.memorial_de", dueno),
                tx, ty, 16, ORO, false, false);

        int tyTitulo = ty + 26;
        int tituloLineas = 0;
        for (String linea : partir(memorial.titulo().isEmpty()
                        ? Text.translatable("pokepad.lunaeternal.santuario.sin_titulo")
                                .getString()
                        : memorial.titulo(),
                tw, 26)) {
            if (tituloLineas >= 2) {
                break;
            }
            texto(ctx, Text.literal(linea), tx, tyTitulo + tituloLineas * 31, 26,
                    TEXTO_OSCURO, false, false);
            tituloLineas++;
        }

        int hy = tyTitulo + 2 * 31 + 8;
        boolean historiaVacia = memorial.descripcion() == null
                || memorial.descripcion().isBlank();
        for (String linea : partir(memorial.descripcion(), tw, 17)) {
            if (hy > fy + fh - 16) {
                break;
            }
            texto(ctx, Text.literal(linea), tx, hy, 17,
                    historiaVacia ? TEXTO_SUAVE : TEXTO_OSCURO, false, false);
            hy += 21;
        }

        // ---- abajo: los honores a la izquierda, el boton a la derecha
        int by = fy + fh + 34;
        separador(ctx, by - 16);
        int hcx = PANT_X + PANT_W / 2 - 130;
        texto(ctx, Text.literal("\u2661"), hcx, by - 4, 46, ORO, false, false);
        texto(ctx, Text.literal(String.valueOf(memorial.honores())),
                hcx + 50, by - 10, 56, TEXTO_OSCURO, false, false);
        texto(ctx, Text.translatable("pokepad.lunaeternal.santuario.honores_total"),
                hcx, by + 54, 16, TEXTO_SUAVE, false, false);

        int quedan = nicho.estado().restantes();
        boolean puedo = !nicho.estado().mio() && quedan > 0;
        boton(ctx, rx, ry, PANT_X + PANT_W - 230, by - 6, 200, 56,
                Text.translatable(nicho.estado().mio()
                        ? "pokepad.lunaeternal.santuario.es_tuyo"
                        : "pokepad.lunaeternal.santuario.honrar"),
                puedo, ORO);
        texto(ctx, Text.translatable("pokepad.lunaeternal.santuario.honrar_queda", quedan),
                PANT_X + PANT_W - 230, by + 56, 15, TEXTO_SUAVE, true, false);
    }

    private Red.NichoSantuario elNicho() {
        var e = EstadoCliente.santuario();
        if (e == null) {
            return null;
        }
        for (var n : e.nichos()) {
            if (n.id().equals(nichoId)) {
                return n;
            }
        }
        return null;
    }

    private void dibujarNav(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48), 80, 64);
        if (dentro(rx, ry, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, cy - pl(24) - 2, pl(60) + 4, pl(48) + 4,
                    BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.volver"),
                PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false, false);

        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4, BORDE_ENCIMA, 2);
        }
    }

    // ---- interaccion -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) {
            return super.mouseClicked(mx, my, boton);
        }
        int rx = (int) mx, ry = (int) my;
        int cy = py(PANEL_Y + NAV_ALTO / 2);
        if (dentro(rx, ry, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            client.setScreen(anterior);
            return true;
        }
        if (dentro(rx, ry, px(PANEL_X + PANEL_W - 18) - pl(80), cy - pl(32), pl(80), pl(64))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            close();
            return true;
        }
        var nicho = elNicho();
        if (nicho == null) {
            return false;
        }
        int by = PANT_Y + MARGEN + 10 + 310 + 34;
        boolean puedo = !nicho.estado().mio() && nicho.estado().restantes() > 0;
        if (puedo && dentro(rx, ry, px(PANT_X + PANT_W - 230), py(by - 6),
                pl(200), pl(56))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
            // ⚠ El clic manda; la campanilla llegara con RespuestaHonor si el
            //   servidor cuenta el honor. Aqui solo suena el boton.
            ClientPlayNetworking.send(new Red.HonrarNicho(
                    nichoId, UUID.randomUUID().toString()));
            return true;
        }
        return false;
    }

    /**
     * La campanilla del honor: suena cuando la respuesta llega y dice que si.
     *
     * <p>⚠ Se comprueba en el render con un testigo de contenido: la respuesta
     * no se borra de {@code EstadoCliente}, asi que lo unico que la distingue
     * de la anterior es lo que lleva dentro.
     */
    private void campanillaSiToca(Red.RespuestaHonor r) {
        if (r == null || !nichoId.equals(r.nicho()) || !r.ok()) {
            return;
        }
        if (r.total() == vistoTotal && r.restantes() == vistoRestantes) {
            return;
        }
        vistoTotal = r.total();
        vistoRestantes = r.restantes();
        if (client != null && client.player != null) {
            // ⚠ La campanilla de amatista: vainilla, suave, y sin licencias.
            client.player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    0.8f, 1.0f);
        }
    }

    private void sonar(net.minecraft.sound.SoundEvent sonido, float tono) {
        if (client != null && client.player != null) {
            client.player.playSound(sonido, 0.7f, tono);
        }
    }

    /** La linea fina que separa la foto del contador de honores. */
    private void separador(DrawContext ctx, int artY) {
        ctx.fill(px(PANT_X + MARGEN), py(artY), px(PANT_X + PANT_W - MARGEN),
                py(artY) + Math.max(1, pl(2)), 0xFF9AA6C4);
    }

    // ---- utilidades (mismas piezas que las demas pantallas) ----------------

    private List<String> partir(String texto, int anchoArte, int altoArte) {
        var salida = new java.util.ArrayList<String>();
        if (texto == null || texto.isBlank()) {
            salida.add(Text.translatable("pokepad.lunaeternal.santuario.sin_historia").getString());
            return salida;
        }
        var actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
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

    private void boton(DrawContext ctx, int rx, int ry, int ax, int ay, int aw, int ah,
                       Text etiqueta, boolean activo, int color) {
        boolean encima = activo && dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah),
                !activo ? 0xFF6E7899 : (encima ? aclarar(color) : color));
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), 0xFF20283C, Math.max(1, pl(2)));
        int t = 19;
        while (t > 12 && anchoArte(etiqueta.getString(), t) > aw - 16) {
            t--;
        }
        texto(ctx, etiqueta, ax + aw / 2, ay + ah / 2 - t / 2 - 1, t,
                activo ? 0xFFFFFFFF : 0xFFD8DEEA, true, false);
    }

    private static int aclarar(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 40);
        int b = Math.min(255, (color & 0xFF) + 40);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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
