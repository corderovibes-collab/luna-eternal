package net.pokereport.luna.client.pokepad;

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
 * CURAR: el equipo, sus barras de vida y un botón.
 *
 * <h2>La pantalla más pequeña del Pad, y la que más se nota que faltaba</h2>
 *
 * Un jugador ya puede elegir inicial, capturar y comprar Poké Balls; lo que no
 * podía era <b>curar</b>, que es lo que se hace entre una cosa y la otra.
 *
 * <h2>⚠ Aquí no se decide nada, ni siquiera si hace falta curar</h2>
 *
 * {@code haceFalta} llega <b>del servidor</b>. El cliente podría deducirlo
 * mirando las barras —si alguna no está llena, hace falta— y entonces la regla
 * viviría en dos sitios y un día dejarían de decir lo mismo: bastaría con que
 * el servidor contara también los PP o los estados alterados para que el botón
 * se apagara cuando sí tocaba curar.
 *
 * <h2>⚠⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 *
 * Y la geometría ({@code recalcular}) es <b>copia literal</b> de
 * {@code CosmeticosScreen}, como en Clan y en Tienda.
 */
public class CurarScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier ICONO =
            Identifier.of("lunaeternal", "textures/gui/pokepad/curar.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    private static final int MARGEN = 14;

    /** Seis Pokémon, dos columnas de tres. Un equipo nunca pasa de seis. */
    private static final int COLS = 2, FILAS = 3;

    private static final int FILA_FONDO = 0xFFBFCBE8;
    private static final int FILA_BORDE = 0xFF7C89B4;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int VERDE = 0xFF3FBF5F;
    private static final int AMBAR = 0xFFE0A845;
    private static final int ROJO = 0xFFD8443A;
    private static final int APAGADO = 0xFF6E7899;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private Red.EstadoCura estado;

    /**
     * Cuándo se pulsó, para no dejar el botón encendido mientras vuela el
     * paquete. Se apaga solo cuando llega la respuesta o al segundo y medio.
     *
     * <p>⚠ La salida por tiempo no sobra: sin ella, un paquete perdido deja el
     * botón muerto y la pantalla no se puede usar hasta reabrirla. Es la misma
     * lección de la pantalla del inicial, que llegó a dejar atrapado a alguien.
     */
    private long pulsado;

    public CurarScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.curar"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirCura());
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
            for (Identifier t : new Identifier[] { CHASIS, ATRAS, CERRAR, ICONO }) {
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

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);

        var nuevo = EstadoCliente.cura();
        if (nuevo != null && nuevo != estado) {
            estado = nuevo;
            // Llegó respuesta: el botón vuelve a la vida.
            pulsado = 0;
        }

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanel(ctx, rx, ry);
        dibujarEquipo(ctx);
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 120, 96);
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2, pl(60) + 4, pl(48) + 4,
                    0xFFF35C0C, 2);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicio"),
                PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false, false);

        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4, 0xFFF35C0C, 2);
        }
    }

    /** Izquierda: el icono, el reloj y el botón. */
    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        int cx = PANEL_X + PANEL_W / 2;

        dibujarTextura(ctx, ICONO, px(cx - 60), py(PANEL_Y + NAV_ALTO + 20),
                pl(120), pl(120), 100, 100);

        texto(ctx, Text.translatable("pokepad.lunaeternal.curar.titulo"),
                cx, PANEL_Y + NAV_ALTO + 156, 26, 0xFFFFFFFF, true, false);
        int y = PANEL_Y + NAV_ALTO + 190;
        for (String linea : partir(
                Text.translatable("pokepad.lunaeternal.curar.explica").getString(),
                PANEL_W - 60, 15)) {
            texto(ctx, Text.literal(linea), cx, y, 15, TEXTO_SUAVE, true, false);
            y += 18;
        }

        separador(ctx, y + 12);

        long segundos = estado == null ? 0 : estado.segundos();
        boolean listo = estado != null && segundos <= 0;

        // ⚠ El reloj se enseña SIEMPRE que quede tiempo, esté el equipo sano o
        //   no. Un botón apagado sin decir por qué se lee como una avería.
        if (estado == null) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                    cx, y + 34, 18, TEXTO_SUAVE, true, false);
        } else if (!listo) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.curar.espera"),
                    cx, y + 30, 16, TEXTO_SUAVE, true, false);
            texto(ctx, Text.literal(reloj(segundos)), cx, y + 52, 34, AMBAR, true, false);
        } else if (!estado.haceFalta()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.curar.sano"),
                    cx, y + 42, 18, VERDE, true, false);
        } else {
            texto(ctx, Text.translatable("pokepad.lunaeternal.curar.gratis"),
                    cx, y + 42, 18, VERDE, true, false);
        }

        // El botón solo se enciende si el servidor dice las dos cosas: que ha
        // pasado el tiempo Y que hace falta.
        boolean activo = estado != null && listo && estado.haceFalta() && !esperando();
        int by = PANEL_Y + PANEL_H - 76;
        boolean encima = activo && dentro(rx, ry, px(PANEL_X + 40), py(by),
                pl(PANEL_W - 80), pl(46));
        ctx.fill(px(PANEL_X + 40), py(by), px(PANEL_X + PANEL_W - 40), py(by + 46),
                !activo ? APAGADO : (encima ? 0xFF4FD07A : 0xFF2E9E56));
        marco(ctx, px(PANEL_X + 40), py(by), pl(PANEL_W - 80), pl(46),
                0xFF10331E, Math.max(1, pl(2)));
        texto(ctx, Text.translatable(esperando()
                        ? "pokepad.lunaeternal.curar.curando"
                        : "pokepad.lunaeternal.curar.boton"),
                PANEL_X + PANEL_W / 2, by + 11, 24,
                activo ? 0xFFFFFFFF : 0xFFD8DEEA, true, false);
    }

    /** Derecha: las seis ranuras del equipo. */
    private void dibujarEquipo(DrawContext ctx) {
        if (estado == null) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 22, TEXTO_SUAVE, true, false);
            return;
        }
        if (estado.equipo().isEmpty()) {
            // ⚠ Un equipo vacío es un estado REAL --acabas de guardar todo en el
            //   PC-- y no un error. Se dice, en vez de dejar la pantalla en
            //   blanco, que se lee como que algo se rompió.
            texto(ctx, Text.translatable("pokepad.lunaeternal.curar.sin_equipo"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 20, TEXTO_SUAVE, true, false);
            return;
        }

        int aw = (PANT_W - 2 * MARGEN - 16) / COLS;
        int ah = (PANT_H - 2 * MARGEN - 2 * 14) / FILAS;
        for (int i = 0; i < estado.equipo().size() && i < COLS * FILAS; i++) {
            var p = estado.equipo().get(i);
            int ax = PANT_X + MARGEN + (i % COLS) * (aw + 16);
            int ay = PANT_Y + MARGEN + (i / COLS) * (ah + 14);

            ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah), FILA_FONDO);
            marco(ctx, px(ax), py(ay), pl(aw), pl(ah), FILA_BORDE, Math.max(1, pl(2)));

            String nombre = p.apodo() == null || p.apodo().isBlank()
                    ? p.especie() : p.apodo();
            texto(ctx, Text.literal(nombre), ax + 12, ay + 10, 22, TEXTO_OSCURO, false, true);
            texto(ctx, Text.literal("Nv " + p.nivel()), ax + aw - 70, ay + 12, 18,
                    TEXTO_SUAVE, false, true);

            // ---- LA BARRA DE VIDA
            int bx = ax + 12, bw = aw - 24, by = ay + 42, bh = 14;
            ctx.fill(px(bx), py(by), px(bx + bw), py(by + bh), 0xFF2A3145);
            int vidaMax = Math.max(1, p.vidaMax());
            int llena = Math.round(bw * Math.min(1f, p.vida() / (float) vidaMax));
            // ⚠ Verde, ámbar y rojo por TRAMOS, como en los juegos. Un degradado
            //   continuo se ve bonito y no dice nada: lo que hace falta saber de
            //   un vistazo es si ese Pokémon aguanta otro combate.
            float frac = p.vida() / (float) vidaMax;
            int color = frac > 0.5f ? VERDE : (frac > 0.2f ? AMBAR : ROJO);
            if (llena > 0) {
                ctx.fill(px(bx), py(by), px(bx + llena), py(by + bh), color);
            }
            marco(ctx, px(bx), py(by), pl(bw), pl(bh), 0xFF141926, Math.max(1, pl(1)));

            texto(ctx, Text.literal(p.vida() + " / " + p.vidaMax()),
                    ax + 12, ay + 62, 17,
                    p.vida() <= 0 ? ROJO : TEXTO_SUAVE, false, true);

            // ---- EL ESTADO ALTERADO
            if (p.estado() != null && !p.estado().isEmpty()) {
                String etiqueta = estadoCorto(p.estado());
                int ex = ax + aw - 12 - 54;
                ctx.fill(px(ex), py(ay + 60), px(ex + 54), py(ay + 80), colorEstado(p.estado()));
                marco(ctx, px(ex), py(ay + 60), pl(54), pl(20), 0xFF141926,
                        Math.max(1, pl(1)));
                texto(ctx, Text.literal(etiqueta), ex + 27, ay + 63, 15,
                        0xFFFFFFFF, true, false);
            } else if (p.vida() <= 0) {
                texto(ctx, Text.translatable("pokepad.lunaeternal.curar.debilitado"),
                        ax + aw - 12, ay + 62, 16, ROJO, false, true);
            }
        }
    }

    /**
     * Tres letras por estado.
     *
     * <p>⚠ Vienen en el formato de Showdown ({@code brn}, {@code par}, …) que es
     * lo que devuelve Cobblemon. Se traduce aquí y no en el servidor porque es
     * <b>presentación</b>: el día que haya que enseñarlo en inglés, se cambia una
     * tabla del cliente y no el protocolo.
     */
    private static String estadoCorto(String showdown) {
        return switch (showdown.toLowerCase()) {
            case "brn" -> "QUEM";
            case "par" -> "PARA";
            case "psn", "tox" -> "VEN";
            case "slp" -> "DORM";
            case "frz" -> "CONG";
            case "fnt" -> "DEB";
            default -> showdown.toUpperCase();
        };
    }

    private static int colorEstado(String showdown) {
        return switch (showdown.toLowerCase()) {
            case "brn" -> 0xFFC0392B;
            case "par" -> 0xFFB7950B;
            case "psn", "tox" -> 0xFF7D3C98;
            case "slp" -> 0xFF5D6D7E;
            case "frz" -> 0xFF2E86C1;
            default -> 0xFF566573;
        };
    }

    /** «9:59». Un número de segundos a secas obliga a dividir de cabeza. */
    private static String reloj(long segundos) {
        return String.format("%d:%02d", segundos / 60, segundos % 60);
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
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            if (client != null) {
                client.setScreen(anterior);
            }
            return true;
        }
        if (dentro(rx, ry, px(PANEL_X + PANEL_W - 18) - pl(80), cy - pl(32), pl(80), pl(64))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            close();
            return true;
        }

        int by = PANEL_Y + PANEL_H - 76;
        boolean activo = estado != null && estado.segundos() <= 0
                && estado.haceFalta() && !esperando();
        if (activo && dentro(rx, ry, px(PANEL_X + 40), py(by), pl(PANEL_W - 80), pl(46))) {
            // ⚠ No se pinta el resultado: se manda y se espera. El servidor
            //   reenvía el estado cure o no cure, así que la pantalla vuelve
            //   sola a la verdad. Adelantarse haría que un rechazo se viera
            //   como unas barras que se llenan y se vacían.
            pulsado = System.currentTimeMillis();
            sonar(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.2f);
            ClientPlayNetworking.send(new Red.Curar());
            return true;
        }
        return super.mouseClicked(mx, my, boton);
    }

    private void sonar(net.minecraft.sound.SoundEvent sonido, float tono) {
        if (client != null && client.player != null) {
            client.player.playSound(sonido, 0.7f, tono);
        }
    }

    // ---- utilidades --------------------------------------------------------

    private java.util.List<String> partir(String texto, int anchoArte, int altoArte) {
        var salida = new java.util.ArrayList<String>();
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
