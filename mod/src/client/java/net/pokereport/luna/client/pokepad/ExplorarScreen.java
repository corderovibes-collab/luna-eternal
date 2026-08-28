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
 * EXPLORAR: los dos mundos.
 *
 * <pre>
 *   MUNDO HOGAR     tu casa. Protecciones, pocos Pokémon, cero legendarios
 *   MUNDO SALVAJE   sin protecciones, lleno de Pokémon, con legendarios
 * </pre>
 *
 * <h2>⚠⚠ TRES MUNDOS SALVAJES, Y EL REPARTO ES DE DENSIDAD, NO DE LAG</h2>
 *
 * Merece decirlo aquí porque la pantalla lo enseña y se presta a confusión:
 * <b>todas las dimensiones se tickean en el mismo hilo</b>, así que repartir
 * gente entre mundos <i>no baja el lag</i>. Cuarenta jugadores repartidos por
 * tres mundos cargan los mismos chunks que cuarenta repartidos por uno.
 *
 * <p>Lo que sí arregla —y es muy real— es que cuarenta personas se peleen por
 * el mismo legendario. Por eso el texto de la pantalla habla de <b>gente</b> y
 * nunca de rendimiento: prometer lo segundo sería mentir.
 *
 * <h2>⚠ El cliente NO elige mundo</h2>
 *
 * Manda «salvaje» y el servidor decide cuál según el reparto. Si el número
 * viniera de aquí, todo el mundo se metería siempre en el mismo (P6).
 */
public class ExplorarScreen extends Screen {

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

    private static final int TARJETA_W = 384, TARJETA_H = 210;

    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int CONTORNO_CLARO = 0xFFF2F6FF;
    private static final int CONTORNO_OSCURO = 0xFF080B12;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int ORO = 0xFFFFD65C;
    private static final int VERDE = 0xFF2E9E56;
    private static final int APAGADO = 0xFF6E7899;

    /** ⚠ Los dos colores dicen de qué va cada mundo antes de leer una palabra. */
    private static final int AZUL_HOGAR = 0xFF2C4A78;
    private static final int VERDE_SALVAJE = 0xFF2C5C3A;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private Red.EstadoExplorar estado;
    private boolean salvajeElegido = true;
    private String companero = "";
    private long pulsado;

    public ExplorarScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.explorar"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirExplorar());
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

    private List<Red.MundoSalvaje> mundos() {
        return estado == null ? List.of() : estado.mundos();
    }

    private List<Red.Companero> companeros() {
        return estado == null ? List.of() : estado.companeros();
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        var nuevo = EstadoCliente.explorar();
        if (nuevo != null && nuevo != estado) {
            estado = nuevo;
            pulsado = 0;
        }

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanel(ctx, rx, ry);
        dibujarTarjetas(ctx, rx, ry);
        dibujarCompaneros(ctx, rx, ry);
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

    // ---- las dos tarjetas --------------------------------------------------

    private int tarjetaX(boolean salvaje) {
        return PANT_X + MARGEN + (salvaje ? TARJETA_W + 9 : 0);
    }

    private void dibujarTarjetas(DrawContext ctx, int rx, int ry) {
        int ty = PANT_Y + MARGEN;
        for (int i = 0; i < 2; i++) {
            boolean salvaje = i == 1;
            int tx = tarjetaX(salvaje);
            boolean sel = salvaje == salvajeElegido;
            boolean enc = dentro(rx, ry, px(tx), py(ty), pl(TARJETA_W), pl(TARJETA_H));

            ctx.fill(px(tx), py(ty), px(tx + TARJETA_W), py(ty + TARJETA_H),
                    salvaje ? VERDE_SALVAJE : AZUL_HOGAR);
            // ⚠ El resalte va en el MARCO y no en el relleno: cambiar el relleno
            //   perdería el color que dice de qué mundo es cada tarjeta.
            marco(ctx, px(tx), py(ty), pl(TARJETA_W), pl(TARJETA_H),
                    sel ? BORDE_ENCIMA : (enc ? 0xFF8FA0C8 : 0xFF20283C),
                    Math.max(2, pl(sel ? 4 : 2)));

            texto(ctx, Text.translatable(salvaje
                            ? "pokepad.lunaeternal.explorar.salvaje"
                            : "pokepad.lunaeternal.explorar.hogar"),
                    tx + TARJETA_W / 2, ty + 20, 30, 0xFFFFFFFF, true, CONTORNO_OSCURO);

            int y = ty + 66;
            for (String l : partir(Text.translatable(salvaje
                            ? "pokepad.lunaeternal.explorar.salvaje_desc"
                            : "pokepad.lunaeternal.explorar.hogar_desc").getString(),
                    TARJETA_W - 40, 15)) {
                texto(ctx, Text.literal(l), tx + TARJETA_W / 2, y, 15,
                        0xFFC9D6EE, true, 0);
                y += 20;
            }

            if (salvaje) {
                dibujarMundos(ctx, tx, ty + TARJETA_H - 62);
            } else {
                texto(ctx, Text.translatable("pokepad.lunaeternal.explorar.hogar_pista"),
                        tx + TARJETA_W / 2, ty + TARJETA_H - 34, 14, 0xFF9FB6D8,
                        true, 0);
            }
        }
    }

    /**
     * Los tres mundos con su gente.
     *
     * <p>⚠ Se dicen los NÚMEROS aunque el jugador no elija cuál. Saber que hay
     * tres y cuánta gente hay en cada uno es lo que explica por qué a veces
     * apareces con gente y a veces solo — sin eso, el reparto parece azar.
     */
    private void dibujarMundos(DrawContext ctx, int tx, int ty) {
        var ms = mundos();
        if (ms.isEmpty()) {
            return;
        }
        int ancho = (TARJETA_W - 40 - (ms.size() - 1) * 8) / ms.size();
        for (int i = 0; i < ms.size(); i++) {
            var m = ms.get(i);
            int bx = tx + 20 + i * (ancho + 8);
            boolean yoAqui = estado != null && estado.miMundo() == m.numero();
            ctx.fill(px(bx), py(ty), px(bx + ancho), py(ty + 44),
                    m.lleno() ? 0xFF5A3030 : 0xFF1D3326);
            marco(ctx, px(bx), py(ty), pl(ancho), pl(44),
                    yoAqui ? ORO : 0xFF3D5C48, Math.max(1, pl(2)));
            texto(ctx, Text.literal("#" + m.numero()), bx + ancho / 2, ty + 5, 15,
                    0xFFD8E4F2, true, 0);
            texto(ctx, Text.translatable("pokepad.lunaeternal.explorar.gente",
                            m.jugadores()),
                    bx + ancho / 2, ty + 24, 14,
                    m.lleno() ? 0xFFFF9A8A : 0xFF9FE0B4, true, 0);
        }
    }

    // ---- el panel ----------------------------------------------------------

    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        int y = PANEL_Y + NAV_ALTO + 20;
        texto(ctx, Text.translatable(salvajeElegido
                        ? "pokepad.lunaeternal.explorar.salvaje"
                        : "pokepad.lunaeternal.explorar.hogar"),
                PANEL_X + PANEL_W / 2, y, 28, 0xFFFFFFFF, true, CONTORNO_OSCURO);
        y += 44;
        separador(ctx, y);
        y += 14;

        String clave = salvajeElegido
                ? "pokepad.lunaeternal.explorar.salvaje_largo"
                : "pokepad.lunaeternal.explorar.hogar_largo";
        for (String l : partir(Text.translatable(clave).getString(), PANEL_W - 60, 15)) {
            texto(ctx, Text.literal(l), PANEL_X + 30, y, 15, 0xFFC2CCE2, false, 0);
            y += 21;
        }

        if (salvajeElegido) {
            y += 12;
            separador(ctx, y);
            y += 12;
            // ⚠ Se dice EXPLICITAMENTE que reparte gente y no que va mas rapido.
            //   Prometer rendimiento seria mentir: las dimensiones comparten hilo.
            for (String l : partir(
                    Text.translatable("pokepad.lunaeternal.explorar.reparto").getString(),
                    PANEL_W - 60, 13)) {
                texto(ctx, Text.literal(l), PANEL_X + 30, y, 13, TEXTO_SUAVE, false, 0);
                y += 18;
            }
        }

        boton(ctx, rx, ry, PANEL_X + 30, PANEL_Y + PANEL_H - 72, PANEL_W - 60, 56,
                Text.translatable("pokepad.lunaeternal.explorar.viajar"),
                !esperando(), VERDE);
    }

    // ---- los compañeros ----------------------------------------------------

    private int companerosY() {
        return PANT_Y + MARGEN + TARJETA_H + 14;
    }

    private void dibujarCompaneros(DrawContext ctx, int rx, int ry) {
        int y = companerosY();
        var cs = companeros();
        texto(ctx, Text.translatable("pokepad.lunaeternal.explorar.con_clan"),
                PANT_X + MARGEN, y, 15, 0xFF9FB6D8, false, 0);
        y += 24;
        if (cs.isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.explorar.sin_clan"),
                    PANT_X + MARGEN, y + 4, 14, TEXTO_SUAVE, false, 0);
            return;
        }
        int aw = PANT_W - 2 * MARGEN;
        int caben = (PANT_Y + PANT_H - MARGEN - y) / 34;
        for (int i = 0; i < cs.size() && i < caben; i++) {
            var c = cs.get(i);
            int fy = y + i * 34;
            boolean sel = c.nombre().equals(companero);
            boolean enc = dentro(rx, ry, px(PANT_X + MARGEN), py(fy), pl(aw), pl(30));
            ctx.fill(px(PANT_X + MARGEN), py(fy), px(PANT_X + MARGEN + aw), py(fy + 30),
                    sel ? 0xFF3D4E2E : (enc ? 0xFF2E3750 : 0xFF232A3C));
            marco(ctx, px(PANT_X + MARGEN), py(fy), pl(aw), pl(30),
                    sel ? BORDE_ENCIMA : 0xFF39415C, Math.max(1, pl(2)));
            texto(ctx, Text.literal(c.nombre()), PANT_X + MARGEN + 12, fy + 8, 16,
                    0xFFE8EDF8, false, 0);
            textoDer(ctx, Text.translatable("pokepad.lunaeternal.explorar.en_mundo",
                            c.mundo()),
                    PANT_X + MARGEN + aw - 12, fy + 9, 14, 0xFF9FE0B4);
        }
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

        int ty = PANT_Y + MARGEN;
        for (int i = 0; i < 2; i++) {
            boolean salvaje = i == 1;
            if (dentro(rx, ry, px(tarjetaX(salvaje)), py(ty), pl(TARJETA_W), pl(TARJETA_H))) {
                salvajeElegido = salvaje;
                // ⚠ Elegir HOGAR borra el compañero elegido: si no, pulsar
                //   VIAJAR mandaría al salvaje pese a decir «Hogar» arriba.
                if (!salvaje) {
                    companero = "";
                }
                sonar();
                return true;
            }
        }

        var cs = companeros();
        int y = companerosY() + 24;
        int aw = PANT_W - 2 * MARGEN;
        for (int i = 0; i < cs.size(); i++) {
            int fy = y + i * 34;
            if (dentro(rx, ry, px(PANT_X + MARGEN), py(fy), pl(aw), pl(30))) {
                var n = cs.get(i).nombre();
                companero = n.equals(companero) ? "" : n;
                salvajeElegido = true;
                sonar();
                return true;
            }
        }

        if (dentro(rx, ry, px(PANEL_X + 30), py(PANEL_Y + PANEL_H - 72),
                pl(PANEL_W - 60), pl(56)) && !esperando()) {
            sonar();
            pulsado = System.currentTimeMillis();
            // ⚠ Si hay compañero elegido manda SU NOMBRE, que es lo que salta el
            //   reparto. Si no, «salvaje» y decide el servidor.
            String destino = !salvajeElegido ? "hogar"
                    : (companero.isEmpty() ? "salvaje" : companero);
            ClientPlayNetworking.send(new Red.AccionExplorar(destino));
            return true;
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
        int r = Math.min(255, ((color >> 16) & 0xFF) + 48);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 48);
        int b = Math.min(255, (color & 0xFF) + 48);
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
