package net.pokereport.luna.client.pokepad;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.net.Red;

import java.util.ArrayList;
import java.util.List;

/**
 * TORRE DE BATALLA: pantalla integrada en el chasis del PokePad.
 *
 * Sigue la arquitectura estándar del PokePad (como ExplorarScreen y CurarScreen):
 * - Chasis pokepad_cosmeticos.png con Escalado.aplicar
 * - Panel izquierdo: Icono, título, reglas de combate y descripción
 * - Pantalla derecha: Las 3 tarjetas de combate (1vs1, 2vs2, Aleatorio)
 */
public class TorreScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier ICONO =
            Identifier.of("lunaeternal", "textures/gui/pokepad/torre_batalla.png");

    private static final Identifier TEX_1VS1 =
            Identifier.of("lunaeternal", "textures/gui/pokepad/torre_modo_1vs1.png");
    private static final Identifier TEX_2VS2 =
            Identifier.of("lunaeternal", "textures/gui/pokepad/torre_modo_2vs2.png");
    private static final Identifier TEX_RANDOM =
            Identifier.of("lunaeternal", "textures/gui/pokepad/torre_modo_aleatorio.png");

    // Medidas del arte del chasis (1380x828)
    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    private static final int MARGEN = 14;

    // Medidas de las 3 tarjetas en la pantalla derecha
    private static final int COLS = 3;
    private static final int GAP = 14;
    private static final int CARD_W = (PANT_W - (2 * MARGEN) - ((COLS - 1) * GAP)) / COLS; // ~248 px
    private static final int CARD_H = PANT_H - (2 * MARGEN); // 466 px

    // Colores estándar del PokePad
    private static final int BORDE_ENCIMA = 0xFFF35C0C; // Naranja acento del chasis
    private static final int BORDE_BASE = 0xFF7C89B4;
    private static final int FONDO_TARJETA = 0xFF222B3D;
    private static final int FONDO_TARJETA_ENCIMA = 0xFF2D3950;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int ORO = 0xFFFFD65C;
    private static final int VERDE_BOTON = 0xFF2E9E56;
    private static final int VERDE_BOTON_ENCIMA = 0xFF4FD07A;

    private final Screen anterior;
    private float k;
    private int ancho, alto, x0, y0;

    public TorreScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.torre_batalla"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirRecompensasTorre());
        ClientPlayNetworking.send(new Red.PedirSaldo());
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

    private int px(int a) { return x0 + Math.round(a * k); }
    private int py(int a) { return y0 + Math.round(a * k); }
    private int pl(int a) { return Math.max(1, Math.round(a * k)); }

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanel(ctx, rx, ry);
        dibujarTarjetas(ctx, rx, ry);
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;

        // Botón Atrás
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 120, 96);
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2, pl(60) + 4, pl(48) + 4,
                    BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicio"),
                PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false, false);

        // Botón Cerrar
        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4, BORDE_ENCIMA, 2);
        }
    }

    private boolean tieneAccesoTorre() {
        var ficha = net.pokereport.luna.client.EstadoCliente.ficha();
        return ficha != null && net.pokereport.luna.gym.MedallaService.tieneKantoCompleto(ficha.medallas());
    }

    /** Panel Izquierdo: Icono, título y reglas de la Torre. */
    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        int cx = PANEL_X + PANEL_W / 2;

        // Icono de la Torre
        dibujarTextura(ctx, ICONO, px(cx - 50), py(PANEL_Y + NAV_ALTO + 15),
                pl(100), pl(100), 100, 100);

        // Título de la app
        texto(ctx, Text.literal("TORRE BATALLA"),
                cx, PANEL_Y + NAV_ALTO + 130, 26, ORO, true, false);

        // Descripción general
        int y = PANEL_Y + NAV_ALTO + 165;
        String desc = "Escala la torre enfrentando entrenadores implacables en combates consecutivos.";
        for (String linea : partir(desc, PANEL_W - 50, 15)) {
            texto(ctx, Text.literal(linea), cx, y, 15, TEXTO_SUAVE, true, false);
            y += 18;
        }

        separador(ctx, y + 10);
        y += 24;

        // Sección de Reglas
        texto(ctx, Text.literal("REGLAS DE COMBATE"), cx, y, 18, 0xFFFFFFFF, true, false);
        y += 26;

        String[] reglas = {
                "• 6 Pokémon en la barra",
                "• Aleatorio: equipo vacío",
                "• Nivel 100 automático",
                "• Curación tras victoria",
                "• Escáner bloqueado"
        };
        for (String r : reglas) {
            texto(ctx, Text.literal(r), PANEL_X + 40, y, 14, 0xFFD8DEEA, false, false);
            y += 18;
        }

        separador(ctx, y + 10);
        y += 24;

        boolean acceso = tieneAccesoTorre();
        if (!acceso) {
            texto(ctx, Text.literal("🔒 ACCESO BLOQUEADO"), cx, y, 15, 0xFFFF5555, true, false);
            y += 18;
            texto(ctx, Text.literal("Requiere 8 Medallas Kanto"), cx, y, 13, 0xFFFFAAAA, true, false);
            y += 15;
            texto(ctx, Text.literal("+ vencer al Campeón Blue"), cx, y, 13, 0xFFFFAAAA, true, false);
        } else {
            // Información de ranking
            texto(ctx, Text.literal("CLASIFICACIÓN"), cx, y, 17, ORO, true, false);
            y += 22;
            texto(ctx, Text.literal("¡Compite por el Top 10!"), cx, y, 14, TEXTO_SUAVE, true, false);
            y += 18;
            texto(ctx, Text.literal("Holograma en Ciudadela"), cx, y, 13, 0xFF8FA0C8, true, false);
        }

        // Botón de Recompensas de Temporada
        int btnRecX = PANEL_X + 22;
        int btnRecY = 625;
        int btnRecW = PANEL_W - 44;
        int btnRecH = 50;

        var estadoRec = net.pokereport.luna.client.EstadoCliente.recompensasTorre();
        int pendientes = 0;
        if (estadoRec != null && estadoRec.maxRonda() >= 4) {
            for (int r = 4; r <= estadoRec.maxRonda(); r++) {
                if (!estadoRec.reclamadas().contains(r)) pendientes++;
            }
        }

        boolean hoverRec = dentro(rx, ry, px(btnRecX), py(btnRecY), pl(btnRecW), pl(btnRecH));
        int fondoBtn = hoverRec ? 0xFF2D3B55 : 0xFF1E283C;
        int bordeBtn = hoverRec ? ORO : (pendientes > 0 ? 0xFF35A854 : BORDE_BASE);

        ctx.fill(px(btnRecX), py(btnRecY), px(btnRecX + btnRecW), py(btnRecY + btnRecH), fondoBtn);
        marco(ctx, px(btnRecX), py(btnRecY), pl(btnRecW), pl(btnRecH), bordeBtn, Math.max(1, pl(hoverRec ? 2 : 1)));

        texto(ctx, Text.literal("🏆 RECOMPENSAS"), btnRecX + btnRecW / 2, btnRecY + 11, 16,
                hoverRec ? 0xFFFFFFFF : ORO, true, false);

        if (pendientes > 0) {
            texto(ctx, Text.literal("§a" + pendientes + " disponibles"), btnRecX + btnRecW / 2, btnRecY + 29, 12,
                    0xFF55FF55, true, false);
        } else {
            int numTemp = estadoRec != null ? estadoRec.temporada() : 1;
            texto(ctx, Text.literal("Temporada #" + numTemp), btnRecX + btnRecW / 2, btnRecY + 29, 11,
                    TEXTO_SUAVE, true, false);
        }
    }

    /** Pantalla Derecha: Las 3 tarjetas de modos (1vs1, 2vs2, Aleatorio). */
    private void dibujarTarjetas(DrawContext ctx, int rx, int ry) {
        String[] titulos = { "COMBATE 1 VS 1", "COMBATE 2 VS 2", "ALEATORIO" };
        String[] subtitulos = { "Individual (6v6)", "Dobles (6v6)", "Draft (6v6)" };
        String[] descripciones = {
                "Combate individual 6 vs 6. Requiere llevar 6 Pokémon en la barra.",
                "Combate doble 6 vs 6 en arena. Requiere llevar 6 Pokémon en la barra.",
                "Equipo sorpresa asignado al azar. Requiere equipo vacío (guarda en PC)."
        };
        Identifier[] texturas = { TEX_1VS1, TEX_2VS2, TEX_RANDOM };

        int ty = PANT_Y + MARGEN;

        for (int i = 0; i < COLS; i++) {
            int tx = PANT_X + MARGEN + (i * (CARD_W + GAP));
            boolean encima = dentro(rx, ry, px(tx), py(ty), pl(CARD_W), pl(CARD_H));

            // Fondo de la tarjeta
            ctx.fill(px(tx), py(ty), px(tx + CARD_W), py(ty + CARD_H),
                    encima ? FONDO_TARJETA_ENCIMA : FONDO_TARJETA);
            marco(ctx, px(tx), py(ty), pl(CARD_W), pl(CARD_H),
                    encima ? BORDE_ENCIMA : BORDE_BASE, Math.max(1, pl(encima ? 3 : 2)));

            // Imagen del modo en la parte superior (relación ~4:3)
            int imgPad = 10;
            int imgW = CARD_W - (imgPad * 2); // ~228 px
            int imgH = 210;
            dibujarTextura(ctx, texturas[i], px(tx + imgPad), py(ty + imgPad), pl(imgW), pl(imgH), 512, 512);
            marco(ctx, px(tx + imgPad) - 1, py(ty + imgPad) - 1, pl(imgW) + 2, pl(imgH) + 2,
                    encima ? BORDE_ENCIMA : 0xFF141924, 1);

            // Título del modo
            int contentY = ty + imgPad + imgH + 16;
            texto(ctx, Text.literal(titulos[i]), tx + CARD_W / 2, contentY, 20,
                    encima ? 0xFFFFFFFF : ORO, true, false);

            // Subtítulo
            contentY += 22;
            texto(ctx, Text.literal(subtitulos[i]), tx + CARD_W / 2, contentY, 14,
                    0xFF8FA0C8, true, false);

            // Separador interno
            contentY += 16;
            ctx.fill(px(tx + 25), py(contentY), px(tx + CARD_W - 25), py(contentY) + Math.max(1, pl(1)),
                    SEPARADOR);
            contentY += 12;

            // Descripción multilínea
            for (String linea : partir(descripciones[i], CARD_W - 36, 14)) {
                texto(ctx, Text.literal(linea), tx + CARD_W / 2, contentY, 14,
                        0xFFD0D8E8, true, false);
                contentY += 17;
            }

            // Botón de acción al pie de la tarjeta
            int btnPadX = 18;
            int btnW = CARD_W - (btnPadX * 2);
            int btnH = 40;
            int btnY = ty + CARD_H - btnH - 16;

            boolean acceso = tieneAccesoTorre();
            boolean btnHover = acceso && dentro(rx, ry, px(tx + btnPadX), py(btnY), pl(btnW), pl(btnH));
            int colorFondoBtn = !acceso ? 0xFF421D22 : (btnHover ? VERDE_BOTON_ENCIMA : VERDE_BOTON);
            int colorBordeBtn = !acceso ? 0xFF7A2B33 : (btnHover ? 0xFFFFFFFF : 0xFF10331E);
            String textoBtn = !acceso ? "BLOQUEADO" : "ENTRAR";
            int colorTextoBtn = !acceso ? 0xFFFF8888 : 0xFFFFFFFF;

            ctx.fill(px(tx + btnPadX), py(btnY), px(tx + btnPadX + btnW), py(btnY + btnH),
                    colorFondoBtn);
            marco(ctx, px(tx + btnPadX), py(btnY), pl(btnW), pl(btnH),
                    colorBordeBtn, Math.max(1, pl(2)));

            texto(ctx, Text.literal(textoBtn), tx + CARD_W / 2, btnY + 11, 20,
                    colorTextoBtn, true, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) return super.mouseClicked(mx, my, boton);
        int rx = (int) mx, ry = (int) my;

        // Botón Atrás
        int cy = py(PANEL_Y + NAV_ALTO / 2);
        if (dentro(rx, ry, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            if (anterior != null && client != null) {
                client.setScreen(anterior);
            } else {
                close();
            }
            return true;
        }

        // Botón Cerrar
        int cx = px(PANEL_X + PANEL_W - 18) - pl(80);
        if (dentro(rx, ry, cx, cy - pl(32), pl(80), pl(64))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            close();
            return true;
        }

        // Botón Recompensas
        int btnRecX = PANEL_X + 22;
        int btnRecY = 625;
        int btnRecW = PANEL_W - 44;
        int btnRecH = 50;
        if (dentro(rx, ry, px(btnRecX), py(btnRecY), pl(btnRecW), pl(btnRecH))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            if (client != null) {
                client.setScreen(new TorreRecompensasScreen(this));
            }
            return true;
        }

        // Clics en las tarjetas o botones de modos
        int ty = PANT_Y + MARGEN;
        for (int i = 0; i < COLS; i++) {
            int tx = PANT_X + MARGEN + (i * (CARD_W + GAP));
            if (dentro(rx, ry, px(tx), py(ty), pl(CARD_W), pl(CARD_H))) {
                seleccionarModo(i);
                return true;
            }
        }

        return super.mouseClicked(mx, my, boton);
    }

    private void seleccionarModo(int modo) {
        if (!tieneAccesoTorre()) {
            sonar(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal(
                        "§c[Torre de Batalla] ¡Acceso restringido! Debes derrotar a los 8 Líderes de Gimnasio de Kanto y al Campeón de Kanto (Blue) para acceder a la Torre."),
                        false);
            }
            return;
        }
        sonar(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.2f);
        ClientPlayNetworking.send(new Red.EntrarTorreBatalla(modo));
        close();
    }

    private void sonar(net.minecraft.sound.SoundEvent sonido, float tono) {
        if (client != null && client.player != null) {
            client.player.playSound(sonido, 0.7f, tono);
        }
    }

    // ---- Utilidades estándar de renderizado del PokePad --------------------

    private List<String> partir(String texto, int anchoArte, int altoArte) {
        var salida = new ArrayList<String>();
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
        if (escala <= 0) return;

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
        ctx.drawText(textRenderer, linea, tx, ty, color, true);
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

    private static void dibujarTextura(DrawContext ctx, Identifier tex,
                                       int x, int y, int w, int h, int natW, int natH) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
    }
}