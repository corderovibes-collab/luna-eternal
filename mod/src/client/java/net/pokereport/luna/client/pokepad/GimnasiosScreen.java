package net.pokereport.luna.client.pokepad;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.gym.Gimnasio;

/**
 * LA LIGA: LOS DIECISÉIS GIMNASIOS DE UN VISTAZO.
 *
 * <h2>⚠⚠ NO HAY PAQUETE NUEVO, Y ESO ES LO MEJOR DE ESTA PANTALLA</h2>
 *
 * La lista de gimnasios <b>no cambia nunca</b> y el cliente ya la tiene
 * ({@link Gimnasio#TODOS}); lo único que varía por jugador son sus medallas, y
 * eso <b>ya viaja</b> en la ficha del PokePad desde que existen. Así que esta
 * pantalla no pide nada: se dibuja con lo que hay.
 *
 * <p>Un paquete propio habría sido un ida y vuelta más, un codec más y un sitio
 * más donde la máscara puede llegar desincronizada.
 *
 * <h2>⚠⚠ LOS DE JOHTO SE ENSEÑAN AUNQUE NO EXISTAN</h2>
 *
 * Salen como <b>próximamente</b> en vez de desaparecer. Es la misma decisión que
 * las medallas apagadas del PokePad y que los artículos que no puedes pagar en
 * la tienda: <b>un hueco vacío no dice lo que falta</b>, y saber lo que viene es
 * justo lo que hace que alguien vaya a por la siguiente.
 *
 * <h2>⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 */
public class GimnasiosScreen extends Screen {

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
    private static final int CONTORNO_OSCURO = 0xFF080B12;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int ORO = 0xFFFFD65C;
    private static final int TINTA = 0xFF16203A;
    private static final int TINTA_SUAVE = 0xFF5A668C;
    private static final int VERDE = 0xFF2E9E56;
    private static final int AMBAR = 0xFFB4711A;
    private static final int GRIS = 0xFF8892AC;

    /** El tinte de una medalla que no se tiene: oscura, pero se ve que es. */
    private static final int MEDALLA_APAGADA = 0xFF3C4258;

    /**
     * UNA PESTAÑA POR REGIÓN, Y NO LOS VEINTITRÉS DE GOLPE.
     *
     * <h2>⚠⚠⚠ Petición del usuario, y arregla un problema real de sitio</h2>
     *
     * Con dieciséis cabían en dos columnas de ocho. Con veintitrés no cabían de
     * ninguna manera: probé tres columnas y salieron filas tan bajas que la
     * medalla y el nombre no entraban juntos.
     *
     * <p>Repartidos por región son <b>nueve como mucho</b> —Kanto 9, Naranja 5,
     * Johto 9— y eso entra en <b>dos columnas de cinco</b> con filas del doble
     * de alto. Además agrupa como el jugador piensa: primero Kanto entera,
     * después el Equipo Naranja, después Johto.
     *
     * <p>⚠ Las filas se siguen calculando y no se escriben. Diez huecos para
     * nueve es holgura de UNO: si algún día entra un décimo reto en una región,
     * con un número a mano se quedaría fuera del marco sin dar ningún error.
     */
    private static final Gimnasio.Region[] REGIONES = Gimnasio.Region.values();

    private static final int COLS = 2;
    private static final int FILAS = calcularFilas();

    private static int calcularFilas() {
        int mayor = 0;
        for (var r : REGIONES) {
            mayor = Math.max(mayor, Gimnasio.deRegion(r).size());
        }
        return (mayor + COLS - 1) / COLS;
    }

    /** Alto de la banda de pestañas. */
    private static final int PESTANA_ALTO = 34;

    /** Qué región se está mirando. */
    private int region = 0;

    /** Los de la región elegida, en orden de bit. */
    private List<Gimnasio.Gimnasio_> visibles() {
        return Gimnasio.deRegion(REGIONES[region]);
    }

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private int elegido = 0;

    public GimnasiosScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.gyms"));
        this.anterior = anterior;
        // Se abre en el primero que aún no tengas: es el que te interesa.
        // ⚠⚠ SE ABRE EN EL PRIMERO QUE TE FALTA, Y AHORA TAMBIÉN EN SU PESTAÑA.
        //    Sin lo segundo, a alguien con todo Kanto ganado se le abriría la
        //    pantalla en Kanto con el primero de Johto «elegido» — o sea con el
        //    panel enseñando un gimnasio que no está en la lista de al lado.
        //
        //    ⚠ Y por el BIT, no por la sala: son lo mismo hoy y el bit es el que
        //      manda en la máscara.
        int m = mascara();
        buscar:
        for (int r = 0; r < REGIONES.length; r++) {
            var lista = Gimnasio.deRegion(REGIONES[r]);
            for (int i = 0; i < lista.size(); i++) {
                if ((m & Gimnasio.bitMedalla(lista.get(i))) == 0) {
                    region = r;
                    elegido = i;
                    break buscar;
                }
            }
        }
    }

    /** Las medallas del jugador, de la ficha que ya tiene el PokePad. */
    private static int mascara() {
        var f = EstadoCliente.ficha();
        return f == null ? 0 : f.medallas();
    }

    @Override
    protected void init() {
        recalcular();
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

    // ---- estado de cada gimnasio -------------------------------------------

    /** Qué le pasa a este gimnasio para este jugador. */
    private enum Estado { GANADA, LISTO, FALTAN, PROXIMAMENTE }

    private Estado estadoDe(Gimnasio.Gimnasio_ g) {
        int m = mascara();
        if ((m & (1 << g.sala())) != 0) {
            return Estado.GANADA;
        }
        if (!Gimnasio.construido(g)) {
            return Estado.PROXIMAMENTE;
        }
        return Integer.bitCount(m) >= g.medallas() ? Estado.LISTO : Estado.FALTAN;
    }

    private int colorDe(Estado e) {
        return switch (e) {
            case GANADA -> VERDE;
            case LISTO -> AMBAR;
            case FALTAN -> GRIS;
            case PROXIMAMENTE -> TINTA_SUAVE;
        };
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO,
                0xFFFFFFFF);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanel(ctx);
        dibujarLista(ctx, rx, ry);
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60),
                pl(48), 120, 96, 0xFFFFFFFF);
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2, pl(60) + 4,
                    pl(48) + 4, BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicio"),
                PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false, 0);

        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64),
                120, 96, 0xFFFFFFFF);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4,
                    BORDE_ENCIMA, 2);
        }
    }

    /** El panel: el gimnasio elegido, en grande. */
    private void dibujarPanel(DrawContext ctx) {
        var lista = visibles();
        if (lista.isEmpty()) {
            return;
        }
        var g = lista.get(Math.max(0, Math.min(elegido, lista.size() - 1)));
        var e = estadoDe(g);
        int y = PANEL_Y + NAV_ALTO + 22;

        // La medalla, grande y del color de su estado.
        int lado = 96;
        ctx.fill(px(PANEL_X + (PANEL_W - lado - 24) / 2), py(y),
                px(PANEL_X + (PANEL_W - lado - 24) / 2 + lado + 24), py(y + lado + 24),
                0x33000000);
        dibujarTextura(ctx, g.textura(),
                px(PANEL_X + (PANEL_W - lado) / 2), py(y + 12), pl(lado), pl(lado),
                g.lado(), g.lado(),
                e == Estado.GANADA ? 0xFFFFFFFF : MEDALLA_APAGADA);
        y += lado + 34;

        texto(ctx, Text.literal(g.lider()), PANEL_X + PANEL_W / 2, y, 28,
                0xFFFFFFFF, true, CONTORNO_OSCURO);
        y += 34;
        texto(ctx, Text.translatable("gimnasio.lunaeternal.tipo." + g.id()),
                PANEL_X + PANEL_W / 2, y, 15, 0xFFC2CCE2, true, 0);
        y += 26;
        separador(ctx, y);
        y += 16;

        texto(ctx, Text.translatable("gimnasios.lunaeternal.medalla",
                        g.medalla()),
                PANEL_X + PANEL_W / 2, y, 18, ORO, true, CONTORNO_OSCURO);
        y += 30;

        var frase = switch (e) {
            case GANADA -> Text.translatable("gimnasios.lunaeternal.estado.ganada");
            case LISTO -> Text.translatable("gimnasios.lunaeternal.estado.listo");
            case PROXIMAMENTE ->
                    Text.translatable("gimnasios.lunaeternal.estado.proximamente");
            case FALTAN -> Text.translatable("gimnasios.lunaeternal.estado.faltan",
                    g.medallas() - Integer.bitCount(mascara()));
        };
        for (String l : partir(frase.getString(), PANEL_W - 60, 15)) {
            texto(ctx, Text.literal(l), PANEL_X + 30, y, 15, colorDe(e), false, 0);
            y += 21;
        }

        // Cuántas llevas, abajo del todo. Es el número que importa.
        texto(ctx, Text.translatable("gimnasios.lunaeternal.llevas",
                        Integer.bitCount(mascara()), Gimnasio.TODOS.size()),
                PANEL_X + PANEL_W / 2, PANEL_Y + PANEL_H - 52, 22, ORO, true,
                CONTORNO_OSCURO);
    }

    // ---- la lista ----------------------------------------------------------

    private int filaW() {
        return (PANT_W - 2 * MARGEN - 10) / COLS;
    }

    private int filaH() {
        return (PANT_H - MARGEN - 44 - PESTANA_ALTO - MARGEN
                - (FILAS - 1) * 4) / FILAS;
    }

    private int filaX(int i) {
        return PANT_X + MARGEN + (i / FILAS) * (filaW() + 10);
    }

    private int filaY(int i) {
        return PANT_Y + MARGEN + 44 + PESTANA_ALTO + (i % FILAS) * (filaH() + 4);
    }

    /** La caja de una pestaña: {x, y, ancho, alto}. */
    private int[] cajaPestana(int i) {
        int ancho = (PANT_W - 2 * MARGEN - (REGIONES.length - 1) * 6)
                / REGIONES.length;
        return new int[] {PANT_X + MARGEN + i * (ancho + 6),
                          PANT_Y + MARGEN + 40, ancho, PESTANA_ALTO - 6};
    }

    private void dibujarLista(DrawContext ctx, int rx, int ry) {
        texto(ctx, Text.translatable("gimnasios.lunaeternal.titulo"),
                PANT_X + MARGEN, PANT_Y + MARGEN + 6, 22, TINTA, false, 0);
        textoDer(ctx, Text.translatable("gimnasios.lunaeternal.regiones"),
                PANT_X + PANT_W - MARGEN, PANT_Y + MARGEN + 10, 14, TINTA_SUAVE);

        // Las pestañas. Cada una dice cuántas llevas de esa región: es el
        // número que hace que quieras entrar a mirar.
        for (int i = 0; i < REGIONES.length; i++) {
            int[] c = cajaPestana(i);
            boolean act = i == region;
            boolean enc = dentro(rx, ry, px(c[0]), py(c[1]), pl(c[2]), pl(c[3]));
            ctx.fill(px(c[0]), py(c[1]), px(c[0] + c[2]), py(c[1] + c[3]),
                    act ? 0x44FFFFFF : (enc ? 0x28000000 : 0x14000000));
            if (act) {
                marco(ctx, px(c[0]), py(c[1]), pl(c[2]), pl(c[3]),
                        BORDE_ENCIMA, Math.max(2, pl(2)));
            }
            var lista = Gimnasio.deRegion(REGIONES[i]);
            int tengo = 0;
            for (var g : lista) {
                if ((mascara() & Gimnasio.bitMedalla(g)) != 0) {
                    tengo++;
                }
            }
            texto(ctx, Text.literal(REGIONES[i].nombre + "  " + tengo + "/"
                            + lista.size()),
                    c[0] + c[2] / 2, c[1] + 6, 16,
                    act ? TINTA : TINTA_SUAVE, true, 0);
        }

        var lista = visibles();
        int w = filaW(), h = filaH();
        for (int i = 0; i < lista.size(); i++) {
            var g = lista.get(i);
            var e = estadoDe(g);
            int cx = filaX(i), cy = filaY(i);
            boolean enc = dentro(rx, ry, px(cx), py(cy), pl(w), pl(h));
            boolean sel = i == elegido;

            ctx.fill(px(cx), py(cy), px(cx + w), py(cy + h),
                    enc ? 0x22000000 : 0x11000000);
            if (sel) {
                marco(ctx, px(cx), py(cy), pl(w), pl(h), BORDE_ENCIMA,
                        Math.max(2, pl(2)));
            }
            // Una pastilla de color a la izquierda: el estado se ve sin leer.
            ctx.fill(px(cx), py(cy), px(cx + 5), py(cy + h), colorDe(e));

            int lado = h - 8;
            dibujarTextura(ctx, g.textura(), px(cx + 12), py(cy + 4), pl(lado),
                    pl(lado), g.lado(), g.lado(),
                    e == Estado.GANADA ? 0xFFFFFFFF : MEDALLA_APAGADA);

            int tx = cx + 12 + lado + 10;
            texto(ctx, Text.literal(g.lider()), tx, cy + 6, 17,
                    e == Estado.PROXIMAMENTE ? TINTA_SUAVE : TINTA, false, 0);
            texto(ctx, Text.translatable("gimnasio.lunaeternal.tipo." + g.id()),
                    tx, cy + 6 + 20, 12, TINTA_SUAVE, false, 0);

            // ⚠ El número que se enseña es el NIVEL al que se pelea, no el
            //   orden: el orden ya lo dice la posición, y el nivel es lo único
            //   que le dice al jugador si ese reto le viene grande.
            textoDer(ctx, Text.literal("Nv " + g.nivel()), cx + w - 10, cy + 10,
                    15, TINTA_SUAVE);
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
        for (int i = 0; i < REGIONES.length; i++) {
            int[] c = cajaPestana(i);
            if (dentro(rx, ry, px(c[0]), py(c[1]), pl(c[2]), pl(c[3]))) {
                sonar();
                // ⚠ Al cambiar de pestaña el elegido vuelve a 0. Sin esto,
                //   venir de Kanto con el noveno elegido y saltar a Naranja
                //   --que tiene cinco-- dejaría el panel leyendo fuera de la
                //   lista. Eso SÍ da error, y encima al dibujar.
                region = i;
                elegido = 0;
                return true;
            }
        }
        var lista = visibles();
        int w = filaW(), h = filaH();
        for (int i = 0; i < lista.size(); i++) {
            if (dentro(rx, ry, px(filaX(i)), py(filaY(i)), pl(w), pl(h))) {
                elegido = i;
                sonar();
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

    private java.util.List<String> partir(String s, int anchoMax, int alto) {
        var salida = new java.util.ArrayList<String>();
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

    private void marco(DrawContext ctx, int x, int y, int w, int h, int color,
                       int grosor) {
        ctx.fill(x, y, x + w, y + grosor, color);
        ctx.fill(x, y + h - grosor, x + w, y + h, color);
        ctx.fill(x, y, x + grosor, y + h, color);
        ctx.fill(x + w - grosor, y, x + w, y + h, color);
    }

    /**
     * ⚠⚠ {@code enableBlend} A MANO, SIEMPRE. Sin eso el juego trata cualquier
     * alfa &gt; 0 como opaco y las medallas salen con cerco negro. Es la regla 1
     * de {@code docs/ui/dibujado.md} y costó una noche entera.
     */
    private void dibujarTextura(DrawContext ctx, Identifier tex, int x, int y,
                                int w, int h, int nw, int nh, int tinte) {
        boolean tenido = tinte != 0xFFFFFFFF;
        if (tenido) {
            ctx.setShaderColor(((tinte >> 16) & 0xFF) / 255f,
                    ((tinte >> 8) & 0xFF) / 255f, (tinte & 0xFF) / 255f, 1f);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, nw, nh, nw, nh);
        RenderSystem.disableBlend();
        if (tenido) {
            ctx.setShaderColor(1f, 1f, 1f, 1f);
        }
    }
}
