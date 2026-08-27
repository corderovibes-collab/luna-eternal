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
 * ELEGIR EL PRIMER COMPAÑERO.
 *
 * <h2>Por qué esta pantalla desbloquea el proyecto</h2>
 *
 * {@code feature-gap-analysis.md} lo describió hace meses y seguía abierto: <b>un
 * jugador nuevo no tenía ningún Pokémon</b>, y sin Pokémon nada de lo construido
 * servía — ni capturar, ni la Pokédex, ni la tienda, ni el GTS, ni los
 * cosméticos. La lógica ({@code StarterService}) estaba escrita y probada desde
 * el principio; lo que faltaba era <b>quién la llamara</b>.
 *
 * <h2>⚠ Se abre SOLA, y esa es la mitad del arreglo</h2>
 *
 * Un icono más en el PokePad no habría servido: quien acaba de entrar no sabe que
 * el PokePad existe. La pantalla aparece al entrar si el servidor dice que aún no
 * has elegido, y <b>el servidor es quien lo dice</b>: la verdad está en
 * {@code kit_claim}, no en «¿tengo algún Pokémon?» —que daría falso positivo con
 * quien guarde su equipo en el PC—.
 *
 * <h2>⚠ NO se puede cerrar sin elegir</h2>
 *
 * Es la única pantalla del proyecto con {@code shouldCloseOnEsc()} en falso, y
 * está justificado: cerrarla deja al jugador exactamente donde estaba el problema
 * —dentro del servidor y sin nada que hacer— y sin ninguna pista de cómo volver.
 * Elegir cuesta un clic; quedarse fuera cuesta la partida.
 */
public class InicialScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;

    private static final int COLS = 3, FILAS = 2;
    private static final int MARGEN = 16, AIRE = 14;

    private static final int CELDA_FONDO = 0xFFBFCBE8;
    private static final int CELDA_BORDE = 0xFF7C89B4;
    private static final int CELDA_ENCIMA = 0xFFFFF0DC;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;
    private static final int SEPARADOR = 0xFF3C4250;

    private float k;
    private int ancho, alto, x0, y0;
    private List<Red.OpcionInicial> opciones = List.of();
    private Red.OpcionInicial elegida;
    /** Se pone al pulsar: impide mandar dos veces por doble clic. */
    private boolean enviado;
    /** Cuándo se pulsó, para no esperar eternamente. Ver `render`. */
    private long pulsadoEn;
    private boolean falloEntrega;

    /**
     * Lo que se espera a una respuesta antes de rendirse.
     *
     * <p>⚠⚠ ESTO EXISTE PORQUE LA PANTALLA DEJO A UN JUGADOR ATRAPADO. El
     * servidor no contestaba —{@code conceder} es asíncrono y se le preguntaba
     * antes de tiempo— y aquí se quedaba «ENTREGANDO…» para siempre, sin poder
     * cerrarse.
     *
     * <p>La causa está arreglada, y aun así esto se queda: <b>una pantalla que no
     * se puede cerrar tiene que tener siempre una salida.</b> El fallo de hoy era
     * mío, pero el siguiente puede ser un corte de red, y el resultado para quien
     * está dentro es el mismo — se queda mirando una palabra.
     */
    private static final long ESPERA_MAX_MS = 6000;

    public InicialScreen() {
        super(Text.translatable("pokepad.lunaeternal.inicial.titulo"));
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirInicial());
    }

    /**
     * ⚠ Delegado en {@link Escalado} (2026-08-26). Esto era una copia
     *   literal en ONCE pantallas, y para entonces ya había seis
     *   variantes distintas: cada una había envejecido por su lado sin
     *   dar ningún error.
     */
    private void recalcular() {
        var m = Escalado.aplicar(client, width, height, CHASIS);
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

    /**
     * Ver el comentario de la clase: sin inicial no hay partida.
     *
     * <p>⚠ Pero SÍ se puede cerrar si la entrega ha fallado. Encerrar a alguien
     * con un botón que no responde no le acerca a tener un inicial: solo le quita
     * también la opción de salir y volver a entrar.
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return falloEntrega;
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
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        leerDelServidor();

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);

        // ⚠ PRIMERA PASADA: todo lo plano. Ver dibujado.md — mezclar 2D y 3D deja
        //   el orden al azar y los modelos titilan.
        // ⚠ Si se pulsó y no llega respuesta, se suelta el botón y se dice. El
        //   servidor tiene que contestar en todos los caminos --entregado, ya
        //   elegido o fallo-- así que pasar de aquí significa que algo se perdió.
        if (enviado && !falloEntrega
                && System.currentTimeMillis() - pulsadoEn > ESPERA_MAX_MS) {
            enviado = false;
            falloEntrega = true;
        }

        dibujarPanel(ctx, rx, ry);
        dibujarRejilla(ctx, rx, ry);

        ctx.draw();

        dibujarModelos(ctx, delta, rx, ry);
    }

    private void leerDelServidor() {
        Red.Iniciales i = EstadoCliente.iniciales();
        if (i == null) {
            return;
        }
        // ⚠ SI YA ELIGIO, SE CIERRA SOLA. Es lo que hace que al pulsar no haga
        //   falta adivinar si funcionó: se espera a que el servidor lo confirme.
        if (i.yaEligio()) {
            if (client != null) {
                client.setScreen(null);
            }
            return;
        }
        if (opciones != i.opciones()) {
            opciones = i.opciones();
            if (elegida == null) {
                elegida = opciones.isEmpty() ? null : opciones.get(0);
            }
        }
    }

    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        int cx = PANEL_X + PANEL_W / 2;
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicial.titulo"),
                cx, PANEL_Y + 30, 26, 0xFFFFFFFF, true, false);
        texto(ctx, Text.translatable(falloEntrega
                        ? "pokepad.lunaeternal.inicial.fallo"
                        : "pokepad.lunaeternal.inicial.aviso"),
                cx, PANEL_Y + 62, 17,
                falloEntrega ? 0xFFE06060 : TEXTO_SUAVE, true, false);

        if (elegida == null) {
            return;
        }
        // El hueco del modelo está entre el título y la ficha. El modelo se pinta
        // en la segunda pasada; aquí solo se reserva.
        int y = PANEL_Y + 420;
        separador(ctx, y - 16);
        texto(ctx, Text.literal(elegida.nombre()), cx, y, 34, 0xFFFFD65C, true, false);
        texto(ctx, Text.literal(elegida.tipo()), cx, y + 40, 20, 0xFF9FD0F0, true, false);
        texto(ctx, Text.literal(elegida.region()), cx, y + 66, 18, TEXTO_SUAVE, true, false);

        y += 96;
        separador(ctx, y - 10);
        for (String linea : partir(elegida.consejo(), PANEL_W - 44, 18)) {
            texto(ctx, Text.literal(linea), cx, y, 18, 0xFFC9D2E6, true, false);
            y += 22;
        }

        dibujarBoton(ctx, rx, ry);
    }

    private void dibujarBoton(DrawContext ctx, int rx, int ry) {
        int bx = PANEL_X + 40, by = PANEL_Y + PANEL_H - 76, bw = PANEL_W - 80, bh = 46;
        boolean encima = !enviado && dentro(rx, ry, px(bx), py(by), pl(bw), pl(bh));
        ctx.fill(px(bx), py(by), px(bx + bw), py(by + bh),
                enviado ? 0xFF6E7899 : (encima ? 0xFFFFD65C : 0xFFE8A317));
        marco(ctx, px(bx), py(by), pl(bw), pl(bh), 0xFF8A5C00, Math.max(1, pl(2)));
        texto(ctx, Text.translatable(enviado
                        ? "pokepad.lunaeternal.inicial.enviando"
                        : "pokepad.lunaeternal.inicial.elegir"),
                PANEL_X + PANEL_W / 2, by + 13, 24,
                enviado ? 0xFFD8DEEA : 0xFF2A1C00, true, false);
    }

    private void dibujarRejilla(DrawContext ctx, int rx, int ry) {
        int[] c = celda();
        for (int i = 0; i < opciones.size() && i < COLS * FILAS; i++) {
            int ax = c[0] + (i % COLS) * (c[2] + AIRE);
            int ay = c[1] + (i / COLS) * (c[3] + AIRE);
            var op = opciones.get(i);
            boolean encima = dentro(rx, ry, px(ax), py(ay), pl(c[2]), pl(c[3]));
            boolean puesta = elegida != null && elegida.especie().equals(op.especie());

            ctx.fill(px(ax), py(ay), px(ax + c[2]), py(ay + c[3]),
                    encima ? CELDA_ENCIMA : CELDA_FONDO);
            marco(ctx, px(ax), py(ay), pl(c[2]), pl(c[3]),
                    encima || puesta ? BORDE_ENCIMA : CELDA_BORDE,
                    Math.max(1, pl(encima || puesta ? 4 : 2)));

            texto(ctx, Text.literal(op.nombre()), ax + c[2] / 2, ay + c[3] - 46, 22,
                    TEXTO_OSCURO, true, true);
            texto(ctx, Text.literal(op.tipo()), ax + c[2] / 2, ay + c[3] - 22, 16,
                    TEXTO_SUAVE, true, true);
        }
    }

    /** Segunda pasada: solo modelos. Ni un rectángulo ni una letra aquí. */
    private void dibujarModelos(DrawContext ctx, float delta, int rx, int ry) {
        if (elegida != null) {
            // El grande del panel. Su clave lleva el prefijo `panel:` para que NO
            // comparta estado de animación con la celda del mismo Pokémon: si lo
            // compartieran, se pisarían la orientación y titilarían.
            var id = Identifier.tryParse("cobblemon:" + elegida.especie());
            if (id != null) {
                Mascota3D.dibujarEspecie(ctx, id, "panel:" + elegida.especie(), "",
                        px(PANEL_X + 20), py(PANEL_Y + 96),
                        pl(PANEL_W - 40), pl(300), 0.30f, delta, true);
            }
        }
        int[] c = celda();
        for (int i = 0; i < opciones.size() && i < COLS * FILAS; i++) {
            int ax = c[0] + (i % COLS) * (c[2] + AIRE);
            int ay = c[1] + (i / COLS) * (c[3] + AIRE);
            var op = opciones.get(i);
            var id = Identifier.tryParse("cobblemon:" + op.especie());
            if (id == null) {
                continue;
            }
            boolean encima = dentro(rx, ry, px(ax), py(ay), pl(c[2]), pl(c[3]));
            Mascota3D.dibujarEspecie(ctx, id, "celda:" + op.especie(), "",
                    px(ax + 8), py(ay + 8), pl(c[2] - 16), pl(c[3] - 60),
                    0.06f, delta, encima);
        }
    }

    /** {x, y, ancho, alto} de la primera celda. Todas miden lo mismo. */
    private int[] celda() {
        int anchoUtil = PANT_W - 2 * MARGEN;
        int altoUtil = PANT_H - 2 * MARGEN;
        int cw = (anchoUtil - (COLS - 1) * AIRE) / COLS;
        int ch = (altoUtil - (FILAS - 1) * AIRE) / FILAS;
        return new int[] { PANT_X + MARGEN, PANT_Y + MARGEN, cw, ch };
    }

    // ---- interacción -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0 || enviado) {
            return super.mouseClicked(mx, my, boton);
        }
        int rx = (int) mx, ry = (int) my;

        int bx = PANEL_X + 40, by = PANEL_Y + PANEL_H - 76, bw = PANEL_W - 80, bh = 46;
        if (elegida != null && dentro(rx, ry, px(bx), py(by), pl(bw), pl(bh))) {
            // ⚠ NO SE CIERRA AQUI. Se manda y se espera: el servidor entrega y
            //   contesta que ya elegiste, y `leerDelServidor` cierra al verlo.
            //   Cerrar al pulsar dejaria sin Pokemon y sin pantalla a quien se
            //   encuentre un fallo de entrega.
            enviado = true;
            falloEntrega = false;
            pulsadoEn = System.currentTimeMillis();
            sonar(true);
            ClientPlayNetworking.send(new Red.ElegirInicial(elegida.especie()));
            return true;
        }

        int[] c = celda();
        for (int i = 0; i < opciones.size() && i < COLS * FILAS; i++) {
            int ax = c[0] + (i % COLS) * (c[2] + AIRE);
            int ay = c[1] + (i / COLS) * (c[3] + AIRE);
            if (dentro(rx, ry, px(ax), py(ay), pl(c[2]), pl(c[3]))) {
                elegida = opciones.get(i);
                sonar(true);
                return true;
            }
        }
        return super.mouseClicked(mx, my, boton);
    }

    private void sonar(boolean lleva) {
        if (client != null && client.player != null) {
            client.player.playSound(lleva
                    ? SoundEvents.UI_BUTTON_CLICK.value()
                    : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 1.0f);
        }
    }

    // ---- utilidades --------------------------------------------------------

    private List<String> partir(String texto, int anchoArte, int altoArte) {
        List<String> salida = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (Math.round(textRenderer.getWidth(prueba) * altoArte
                    / (float) textRenderer.fontHeight) > anchoArte && !actual.isEmpty()) {
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
