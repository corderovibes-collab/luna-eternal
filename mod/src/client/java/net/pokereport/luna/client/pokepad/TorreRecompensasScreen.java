package net.pokereport.luna.client.pokepad;

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
import net.pokereport.luna.torrebatalla.TorreRecompensas;

import java.util.List;

/**
 * PANTALLA DE RECOMPENSAS DE TEMPORADA DE LA TORRE DE BATALLA.
 *
 * <p>Diseño de alta definición y máxima legibilidad:
 * - Organización en 2 filas internas por tarjeta: fila superior para títulos y badges, fila inferior para huecos de premios y botón.
 * - Cero colisiones o solapamientos entre texto e iconos.
 * - Tipografía ampliada con sombra nativa de Minecraft (sin contornos borrosos).
 * - Huecos rehundidos para cada premio con indicador de cantidad nítido.
 * - Tarjeta Hero oscura con alto contraste (sin fondos amarillos invasivos).
 * - Tooltip nativo completo al pasar el ratón por cualquier premio.
 */
public class TorreRecompensasScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier ICONO =
            Identifier.of("lunaeternal", "textures/gui/pokepad/torre_batalla.png");

    // Medidas nativas del chasis (1380x828)
    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    private static final int MARGEN = 14;

    // Paleta estándar PokéPad
    private static final int BORDE_ENCIMA = 0xFFF35C0C; // Naranja acento
    private static final int BORDE_BASE = 0xFF7C89B4;
    private static final int TEXTO_SUAVE = 0xFF8FA0C8;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int ORO = 0xFFFFD65C;
    private static final int CIAN = 0xFF55FFFF;
    private static final int VERDE_BOTON = 0xFF2E9E56;
    private static final int VERDE_BOTON_ENCIMA = 0xFF4FD07A;
    private static final int VERDE_TEXTO = 0xFF4FD07A;
    private static final int GRIS_INACTIVO = 0xFF1D2433;

    private final Screen anterior;
    private float k;
    private int ancho, alto, x0, y0;

    private int pestana = 0; // 0: 1-20, 1: 21-40, 2: 41-60, 3: 61-80, 4: 81-100, 5: 101+
    private int pagina = 0;  // Paginador de decena

    private ItemStack hoveredStack = null;

    public TorreRecompensasScreen(Screen anterior) {
        super(Text.literal("Recompensas Torre de Batalla"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirRecompensasTorre());
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

        hoveredStack = null;

        // 1. Chasis general y botones de navegación
        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);

        // 2. PASADA 1 (2D): Todo el fondo, tarjetas, textos, botones y casillas
        dibujarPanelIzquierdo(ctx, rx, ry);
        dibujarPestanas(ctx, rx, ry);

        if (pestana == 5) {
            dibujarSeccionInfinita2D(ctx, rx, ry);
        } else {
            dibujarParrillaRondas2D(ctx, rx, ry);
        }

        // 3. FLUSH: vaciar el buffer 2D antes de pintar modelos 3D
        ctx.draw();

        // 4. PASADA 2 (3D): Dibujar los objetos dentro de sus huecos
        if (pestana == 5) {
            dibujarSeccionInfinitaItems(ctx);
        } else {
            dibujarParrillaRondasItems(ctx);
        }

        // 5. PASADA 3: Tooltip flotante si el cursor está sobre algún objeto
        if (hoveredStack != null && !hoveredStack.isEmpty()) {
            ctx.drawItemTooltip(textRenderer, hoveredStack, rx, ry);
        }
    }

    // =========================================================================
    // NAVEGACIÓN Y PANEL IZQUIERDO
    // =========================================================================

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;

        // Botón Atrás
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 120, 96);
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2, pl(60) + 4, pl(48) + 4,
                    BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.literal("TORRE"), PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false, true);

        // Botón Cerrar
        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4, BORDE_ENCIMA, 2);
        }
    }

    private void dibujarPanelIzquierdo(DrawContext ctx, int rx, int ry) {
        int cx = PANEL_X + PANEL_W / 2;
        var estado = EstadoCliente.recompensasTorre();
        int temp = estado != null ? estado.temporada() : 1;
        int maxRonda = estado != null ? estado.maxRonda() : 0;
        int r1 = estado != null ? estado.ronda1v1() : 0;
        int r2 = estado != null ? estado.ronda2v2() : 0;
        int ra = estado != null ? estado.rondaAleatorio() : 0;

        int pendientes = contarDisponibles(estado);

        // Icono de la Torre
        dibujarTextura(ctx, ICONO, px(cx - 38), py(PANEL_Y + NAV_ALTO + 4),
                pl(76), pl(76), 100, 100);

        // Título de Recompensas
        texto(ctx, Text.literal("RECOMPENSAS"), cx, PANEL_Y + NAV_ALTO + 86, 26, ORO, true, true);
        texto(ctx, Text.literal("TEMPORADA #" + temp), cx, PANEL_Y + NAV_ALTO + 116, 15, CIAN, true, true);

        separador(ctx, PANEL_Y + NAV_ALTO + 138);

        // Tarjeta Récord de Temporada
        int cardY = PANEL_Y + NAV_ALTO + 150;
        int cardW = PANEL_W - 36;
        int cardH = 78;
        int cardX = PANEL_X + 18;

        ctx.fill(px(cardX), py(cardY), px(cardX + cardW), py(cardY + cardH), 0xFF141C2B);
        marco(ctx, px(cardX), py(cardY), pl(cardW), pl(cardH), 0xFF2C394F, 1);

        texto(ctx, Text.literal("RÉCORD DE TEMPORADA"), cx, cardY + 9, 14, TEXTO_SUAVE, true, true);
        String txtRecord = maxRonda > 0 ? "Ronda " + maxRonda + " ★" : "Sin récord";
        texto(ctx, Text.literal(txtRecord), cx, cardY + 26, 24, maxRonda > 0 ? ORO : 0xFFA0AEC0, true, true);

        String breakdown = "1v1: " + r1 + "  ·  2v2: " + r2 + "  ·  Draft: " + ra;
        texto(ctx, Text.literal(breakdown), cx, cardY + 56, 13, 0xFF8FA0C8, true, true);

        // Botón "Reclamar Todo"
        int btnW = PANEL_W - 36;
        int btnH = 48;
        int btnX = PANEL_X + 18;
        int btnY = cardY + cardH + 14;

        boolean puedeReclamarTodo = pendientes > 0;
        boolean hoverBtn = dentro(rx, ry, px(btnX), py(btnY), pl(btnW), pl(btnH));

        int colorBtn = puedeReclamarTodo
                ? (hoverBtn ? VERDE_BOTON_ENCIMA : VERDE_BOTON)
                : GRIS_INACTIVO;
        int bordeBtn = puedeReclamarTodo ? (hoverBtn ? 0xFFFFFFFF : 0xFF144D25) : 0xFF2D384D;

        ctx.fill(px(btnX), py(btnY), px(btnX + btnW), py(btnY + btnH), colorBtn);
        marco(ctx, px(btnX), py(btnY), pl(btnW), pl(btnH), bordeBtn, Math.max(1, pl(hoverBtn ? 2 : 1)));

        String txtBoton = puedeReclamarTodo
                ? "RECLAMAR TODO (" + pendientes + ")"
                : "TODO RECLAMADO (0)";
        texto(ctx, Text.literal(txtBoton), cx, btnY + 16, 17,
                puedeReclamarTodo ? 0xFFFFFFFF : 0xFF7A869E, true, true);

        separador(ctx, btnY + btnH + 16);

        // Reglas de la Temporada
        int regY = btnY + btnH + 28;
        texto(ctx, Text.literal("REGLAS DE TEMPORADA"), cx, regY, 16, 0xFFFFFFFF, true, true);
        regY += 22;

        String[] reglas = {
                "• 1 reclamo por temporada",
                "• Desbloqueo por récord",
                "• Cada 5 rondas: +1,000 Plata",
                "• Cada 30 rondas: +50 LunaCoins",
                "• R101+: 1 Master Ball fija"
        };
        for (String r : reglas) {
            texto(ctx, Text.literal(r), PANEL_X + 26, regY, 13, 0xFFD2D9E8, false, true);
            regY += 18;
        }
    }

    // =========================================================================
    // PESTAÑAS
    // =========================================================================

    private void dibujarPestanas(DrawContext ctx, int rx, int ry) {
        String[] titulosPestanas = { "1 - 20", "21 - 40", "41 - 60", "61 - 80", "81 - 100", "101+ ★" };
        int tabY = PANT_Y + 10;
        int tabH = 32;
        int totalW = PANT_W - (2 * MARGEN);
        int gap = 6;
        int tabW = (totalW - ((titulosPestanas.length - 1) * gap)) / titulosPestanas.length;

        for (int i = 0; i < titulosPestanas.length; i++) {
            int tabX = PANT_X + MARGEN + (i * (tabW + gap));
            boolean activa = (i == pestana);
            boolean hover = dentro(rx, ry, px(tabX), py(tabY), pl(tabW), pl(tabH));

            int fondo = activa ? 0xFF2A374F : (hover ? 0xFF202A3C : 0xFF141D2B);
            int borde = activa ? BORDE_ENCIMA : (hover ? BORDE_BASE : 0xFF253145);

            ctx.fill(px(tabX), py(tabY), px(tabX + tabW), py(tabY + tabH), fondo);
            marco(ctx, px(tabX), py(tabY), pl(tabW), pl(tabH), borde, Math.max(1, pl(activa ? 2 : 1)));

            int colorTexto = activa ? ORO : (hover ? 0xFFFFFFFF : 0xFF8FA0C8);
            texto(ctx, Text.literal(titulosPestanas[i]), tabX + tabW / 2, tabY + 8, 16,
                    colorTexto, true, true);
        }
    }

    // =========================================================================
    // RONDAS 1 A 100: PASADA 2D (Fondos, Huecos, Textos, Botones)
    // =========================================================================

    private void dibujarParrillaRondas2D(DrawContext ctx, int rx, int ry) {
        var estado = EstadoCliente.recompensasTorre();
        int maxRonda = estado != null ? estado.maxRonda() : 0;
        List<Integer> reclamadas = estado != null ? estado.reclamadas() : List.of();

        int rondaInicio = (pestana * 20) + (pagina * 10) + 1;
        int cols = 2;
        int gridX = PANT_X + MARGEN;
        int gridY = PANT_Y + 50;
        int gapX = 12;
        int gapY = 7;
        int cardW = (PANT_W - (2 * MARGEN) - gapX) / cols; // ~380 px
        int cardH = 74;

        for (int index = 0; index < 10; index++) {
            int r = rondaInicio + index;
            if (r > 100) break;

            int col = index % cols;
            int row = index / cols;
            int cx = gridX + (col * (cardW + gapX));
            int cy = gridY + (row * (cardH + gapY));

            TorreRecompensas.InfoRecompensa info = TorreRecompensas.obtenerInfo(r);

            boolean esReclamada = reclamadas.contains(r);
            boolean esDesbloqueada = (maxRonda >= r && r >= 4);
            boolean puedeReclamar = esDesbloqueada && !esReclamada;
            boolean cardHover = dentro(rx, ry, px(cx), py(cy), pl(cardW), pl(cardH));

            // Fondo y marco según estado
            int fondoCard = puedeReclamar
                    ? (cardHover ? 0xFF24334C : 0xFF1C273C)
                    : (esReclamada ? (cardHover ? 0xFF18202D : 0xFF141924) : 0xFF11151F);
            int bordeCard = puedeReclamar
                    ? (cardHover ? BORDE_ENCIMA : ORO)
                    : (esReclamada ? 0xFF242F42 : 0xFF1E2636);

            ctx.fill(px(cx), py(cy), px(cx + cardW), py(cy + cardH), fondoCard);
            marco(ctx, px(cx), py(cy), pl(cardW), pl(cardH), bordeCard, Math.max(1, pl(puedeReclamar ? 2 : 1)));

            // Tira vertical de estado en el borde izquierdo (4 px)
            int colorTira = puedeReclamar ? ORO : (esReclamada ? 0xFF38A169 : 0xFF2D3748);
            ctx.fill(px(cx), py(cy), px(cx + 4), py(cy + cardH), colorTira);

            // =================================================================
            // FILA SUPERIOR: Encabezado (Ronda, Hito y Bonus de Divisa)
            // (Completamente separada de los objetos de abajo: NUNCA se sobrepone)
            // =================================================================
            int tx = cx + 14;
            int colorTitulo = puedeReclamar ? ORO : (esReclamada ? VERDE_TEXTO : 0xFFA0AEC0);
            texto(ctx, Text.literal("RONDA " + r), tx, cy + 7, 18, colorTitulo, false, true);
            tx += anchoTexto(Text.literal("RONDA " + r), 18) + 10;

            if (info.tituloHito() != null) {
                int colorHito = r == 100 ? ORO : (r == 50 || r == 70 || r == 90 ? CIAN : 0xFFFFA07A);
                int bgHito = r == 100 ? 0x30FFD65C : (r == 50 || r == 70 || r == 90 ? 0x3055FFFF : 0x30FFA07A);
                int bdrHito = r == 100 ? 0x80FFD65C : (r == 50 || r == 70 || r == 90 ? 0x8055FFFF : 0x80FFA07A);
                Text hitoText = Text.literal(info.tituloHito());
                int hitoW = anchoTexto(hitoText, 12) + 10;
                ctx.fill(px(tx), py(cy + 7), px(tx + hitoW), py(cy + 21), bgHito);
                marco(ctx, px(tx), py(cy + 7), pl(hitoW), pl(14), bdrHito, 1);
                texto(ctx, hitoText, tx + 5, cy + 8, 12, colorHito, false, true);
                tx += hitoW + 6;
            }

            if (info.plata() > 0 || info.lunacoins() > 0) {
                String bonoStr = "";
                if (info.plata() > 0) bonoStr += "+1k Plata ";
                if (info.lunacoins() > 0) bonoStr += "+50 LC";
                Text bonoText = Text.literal(bonoStr.trim());
                int bonoW = anchoTexto(bonoText, 12) + 10;
                ctx.fill(px(tx), py(cy + 7), px(tx + bonoW), py(cy + 21), 0x3048BB78);
                marco(ctx, px(tx), py(cy + 7), pl(bonoW), pl(14), 0x8048BB78, 1);
                texto(ctx, bonoText, tx + 5, cy + 8, 12, 0xFF68D391, false, true);
            } else if (r < 4) {
                texto(ctx, Text.literal("Calentamiento"), tx, cy + 8, 12, 0xFF718096, false, true);
            }

            // =================================================================
            // FILA INFERIOR: Huecos de Objetos (Izquierda) y Botón (Derecha)
            // =================================================================
            int nItems = info.items().size();
            if (nItems > 0) {
                int sw = nItems >= 5 ? 28 : 34;
                int sh = sw;
                int gapSlot = nItems >= 5 ? 3 : 6;
                int startX = cx + 14;
                int startY = cy + 32;

                for (int itIdx = 0; itIdx < Math.min(5, nItems); itIdx++) {
                    int sx = startX + (itIdx * (sw + gapSlot));
                    int sy = startY;

                    boolean slotHover = dentro(rx, ry, px(sx), py(sy), pl(sw), pl(sh));
                    if (slotHover) {
                        hoveredStack = info.items().get(itIdx).crearStack();
                    }

                    // Fondo de hueco de objeto (rehundido)
                    ctx.fill(px(sx), py(sy), px(sx + sw), py(sy + sh), 0xFF0D121B);
                    marco(ctx, px(sx), py(sy), pl(sw), pl(sh),
                            slotHover ? 0xFFFFFFFF : 0xFF28364D, 1);

                    // Indicador de cantidad en pastilla oscura si es > 1
                    var premio = info.items().get(itIdx);
                    if (premio.cantidad() > 1) {
                        int pw = 16;
                        int ph = 11;
                        int px1 = sx + sw - pw;
                        int py1 = sy + sh - ph;
                        ctx.fill(px(px1), py(py1), px(sx + sw - 1), py(sy + sh - 1), 0xDD000000);
                        texto(ctx, Text.literal(String.valueOf(premio.cantidad())),
                                px1 + pw / 2, py1 + 1, 10, 0xFFFFFFFF, true, true);
                    }
                }
            } else {
                texto(ctx, Text.literal("Sin recompensa física"), cx + 14, cy + 41, 13, 0xFF606E82, false, true);
            }

            // Botón de Acción a la derecha
            int btnW = 90;
            int btnH = 34;
            int btnX = cx + cardW - btnW - 12;
            int btnY = cy + 32;

            if (r < 4) {
                ctx.fill(px(btnX), py(btnY), px(btnX + btnW), py(btnY + btnH), 0xFF121620);
                marco(ctx, px(btnX), py(btnY), pl(btnW), pl(btnH), 0xFF1E2634, 1);
                texto(ctx, Text.literal("✔ Superada"), btnX + btnW / 2, btnY + 11, 12,
                        0xFF718096, true, true);
            } else if (esReclamada) {
                ctx.fill(px(btnX), py(btnY), px(btnX + btnW), py(btnY + btnH), 0xFF14202D);
                marco(ctx, px(btnX), py(btnY), pl(btnW), pl(btnH), 0xFF24364D, 1);
                texto(ctx, Text.literal("✔ RECLAMADO"), btnX + btnW / 2, btnY + 11, 12,
                        0xFF4FD07A, true, true);
            } else if (puedeReclamar) {
                boolean btnHover = dentro(rx, ry, px(btnX), py(btnY), pl(btnW), pl(btnH));
                ctx.fill(px(btnX), py(btnY), px(btnX + btnW), py(btnY + btnH),
                        btnHover ? VERDE_BOTON_ENCIMA : VERDE_BOTON);
                marco(ctx, px(btnX), py(btnY), pl(btnW), pl(btnH),
                        btnHover ? 0xFFFFFFFF : 0xFF144D25, Math.max(1, pl(btnHover ? 2 : 1)));
                texto(ctx, Text.literal("¡RECLAMAR!"), btnX + btnW / 2, btnY + 10, 14,
                        0xFFFFFFFF, true, true);
            } else {
                ctx.fill(px(btnX), py(btnY), px(btnX + btnW), py(btnY + btnH), 0xFF121620);
                marco(ctx, px(btnX), py(btnY), pl(btnW), pl(btnH), 0xFF1E2634, 1);
                texto(ctx, Text.literal("🔒 Bloqueado"), btnX + btnW / 2, btnY + 5, 11,
                        0xFF718096, true, true);
                texto(ctx, Text.literal("Ronda " + r), btnX + btnW / 2, btnY + 18, 10,
                        0xFF4A5568, true, true);
            }
        }

        // --- Paginador Inferior ---
        int pagY = PANT_Y + PANT_H - 38;
        int btnPagW = 110;
        int btnPagH = 26;

        // Botón Anterior
        boolean puedeAnt = pagina > 0;
        boolean hoverAnt = puedeAnt && dentro(rx, ry, px(PANT_X + MARGEN), py(pagY), pl(btnPagW), pl(btnPagH));
        ctx.fill(px(PANT_X + MARGEN), py(pagY), px(PANT_X + MARGEN + btnPagW), py(pagY + btnPagH),
                hoverAnt ? 0xFF24334C : (puedeAnt ? 0xFF182232 : 0xFF121722));
        marco(ctx, px(PANT_X + MARGEN), py(pagY), pl(btnPagW), pl(btnPagH),
                hoverAnt ? BORDE_ENCIMA : (puedeAnt ? 0xFF2F3E56 : 0xFF1E2736), 1);
        texto(ctx, Text.literal("◄ ANTERIOR"), PANT_X + MARGEN + btnPagW / 2, pagY + 7, 14,
                puedeAnt ? (hoverAnt ? 0xFFFFFFFF : 0xFFC0CADC) : 0xFF4A5568, true, true);

        // Indicador central de páginas
        int centroX = PANT_X + PANT_W / 2;
        int rFin = Math.min(100, rondaInicio + 9);
        texto(ctx, Text.literal("Rondas " + rondaInicio + " - " + rFin + " (Página " + (pagina + 1) + " de 2)"),
                centroX, pagY + 7, 16, ORO, true, true);

        // Botón Siguiente
        int nextX = PANT_X + PANT_W - MARGEN - btnPagW;
        boolean puedeSig = pagina < 1;
        boolean hoverSig = puedeSig && dentro(rx, ry, px(nextX), py(pagY), pl(btnPagW), pl(btnPagH));
        ctx.fill(px(nextX), py(pagY), px(nextX + btnPagW), py(pagY + btnPagH),
                hoverSig ? 0xFF24334C : (puedeSig ? 0xFF182232 : 0xFF121722));
        marco(ctx, px(nextX), py(pagY), pl(btnPagW), pl(btnPagH),
                hoverSig ? BORDE_ENCIMA : (puedeSig ? 0xFF2F3E56 : 0xFF1E2736), 1);
        texto(ctx, Text.literal("SIGUIENTE ►"), nextX + btnPagW / 2, pagY + 7, 14,
                puedeSig ? (hoverSig ? 0xFFFFFFFF : 0xFFC0CADC) : 0xFF4A5568, true, true);
    }

    // =========================================================================
    // RONDAS 1 A 100: PASADA 3D (Objetos dentro de sus slots)
    // =========================================================================

    private void dibujarParrillaRondasItems(DrawContext ctx) {
        int rondaInicio = (pestana * 20) + (pagina * 10) + 1;
        int cols = 2;
        int gridX = PANT_X + MARGEN;
        int gridY = PANT_Y + 50;
        int gapX = 12;
        int gapY = 7;
        int cardW = (PANT_W - (2 * MARGEN) - gapX) / cols;
        int cardH = 74;

        for (int index = 0; index < 10; index++) {
            int r = rondaInicio + index;
            if (r > 100) break;

            int col = index % cols;
            int row = index / cols;
            int cx = gridX + (col * (cardW + gapX));
            int cy = gridY + (row * (cardH + gapY));

            TorreRecompensas.InfoRecompensa info = TorreRecompensas.obtenerInfo(r);
            int nItems = info.items().size();
            if (nItems <= 0) continue;

            int sw = nItems >= 5 ? 28 : 34;
            int gapSlot = nItems >= 5 ? 3 : 6;
            int itemSize = nItems >= 5 ? 20 : 26;
            int startX = cx + 14;
            int startY = cy + 32;

            for (int itIdx = 0; itIdx < Math.min(5, nItems); itIdx++) {
                int sx = startX + (itIdx * (sw + gapSlot));
                int sy = startY;
                int ix = sx + (sw - itemSize) / 2;
                int iy = sy + (sw - itemSize) / 2;

                ItemStack st = info.items().get(itIdx).crearStack();
                objeto(ctx, st, ix, iy, itemSize);
            }
        }
    }

    // =========================================================================
    // SECCIÓN INFINITA (RONDA 101+): PASADA 2D Y PASADA 3D
    // =========================================================================

    private void dibujarSeccionInfinita2D(DrawContext ctx, int rx, int ry) {
        var estado = EstadoCliente.recompensasTorre();
        int maxRonda = estado != null ? estado.maxRonda() : 0;
        List<Integer> reclamadas = estado != null ? estado.reclamadas() : List.of();

        // Tarjeta Hero: Explicación de la Ronda 101+
        int heroX = PANT_X + MARGEN;
        int heroY = PANT_Y + 50;
        int heroW = PANT_W - (2 * MARGEN);
        int heroH = 92;

        ctx.fill(px(heroX), py(heroY), px(heroX + heroW), py(heroY + heroH), 0xFF141C2B);
        marco(ctx, px(heroX), py(heroY), pl(heroW), pl(heroH), 0xFF354664, 1);

        // Header interior
        ctx.fill(px(heroX + 1), py(heroY + 1), px(heroX + heroW - 1), py(heroY + 28), 0xFF1C273A);
        texto(ctx, Text.literal("🏆 MAESTRÍA INFINITA (RONDAS 101 EN ADELANTE)"),
                heroX + 16, heroY + 6, 18, ORO, false, true);

        // Slot para la Master Ball en el Hero
        int hSlotX = heroX + 16;
        int hSlotY = heroY + 36;
        int hSlotS = 46;
        ctx.fill(px(hSlotX), py(hSlotY), px(hSlotX + hSlotS), py(hSlotY + hSlotS), 0xFF0D121B);
        marco(ctx, px(hSlotX), py(hSlotY), pl(hSlotS), pl(hSlotS), 0xFF2D3C56, 1);

        if (dentro(rx, ry, px(hSlotX), py(hSlotY), pl(hSlotS), pl(hSlotS))) {
            hoveredStack = new ItemStack(Registries.ITEM.get(Identifier.of("cobblemon", "master_ball")));
        }

        texto(ctx, Text.literal("• 1 Master Ball fija garantizada por cada victoria consecutiva"),
                heroX + 74, heroY + 42, 15, 0xFFFFFFFF, false, true);
        texto(ctx, Text.literal("• Bonificaciones de divisa: +1,000 Plata (c/5 rds) y +50 LunaCoins (c/30 rds)"),
                heroX + 74, heroY + 64, 14, 0xFF48BB78, false, true);

        // Tarjetas individuales 101+ (6 tarjetas en 3 filas x 2 columnas)
        int listY = heroY + heroH + 12;
        int cardW = (heroW - 12) / 2;
        int cardH = 76;
        int gapX = 12;
        int gapY = 8;
        int cols = 2;

        int startR = 101 + (pagina * 6);

        for (int i = 0; i < 6; i++) {
            int r = startR + i;
            int col = i % cols;
            int row = i / cols;
            int cx = heroX + (col * (cardW + gapX));
            int cy = listY + (row * (cardH + gapY));

            boolean esReclamada = reclamadas.contains(r);
            boolean esDesbloqueada = (maxRonda >= r);
            boolean puedeReclamar = esDesbloqueada && !esReclamada;
            boolean cardHover = dentro(rx, ry, px(cx), py(cy), pl(cardW), pl(cardH));

            int fondoCard = puedeReclamar
                    ? (cardHover ? 0xFF24334C : 0xFF1C273C)
                    : (esReclamada ? 0xFF141924 : 0xFF11151F);
            int bordeCard = puedeReclamar ? (cardHover ? BORDE_ENCIMA : ORO) : 0xFF1E2636;

            ctx.fill(px(cx), py(cy), px(cx + cardW), py(cy + cardH), fondoCard);
            marco(ctx, px(cx), py(cy), pl(cardW), pl(cardH), bordeCard, Math.max(1, pl(puedeReclamar ? 2 : 1)));

            int colorTira = puedeReclamar ? ORO : (esReclamada ? 0xFF38A169 : 0xFF2D3748);
            ctx.fill(px(cx), py(cy), px(cx + 4), py(cy + cardH), colorTira);

            // FILA 1: Textos (Ronda, Infinito y Bonus)
            int tx = cx + 14;
            int colorTitulo = puedeReclamar ? ORO : (esReclamada ? VERDE_TEXTO : 0xFFA0AEC0);
            texto(ctx, Text.literal("RONDA " + r), tx, cy + 8, 18, colorTitulo, false, true);
            tx += anchoTexto(Text.literal("RONDA " + r), 18) + 10;

            Text hitoText = Text.literal("⭐ INFINITO");
            int hitoW = anchoTexto(hitoText, 12) + 10;
            ctx.fill(px(tx), py(cy + 8), px(tx + hitoW), py(cy + 22), 0x3055FFFF);
            marco(ctx, px(tx), py(cy + 8), pl(hitoW), pl(14), 0x8055FFFF, 1);
            texto(ctx, hitoText, tx + 5, cy + 9, 12, CIAN, false, true);
            tx += hitoW + 6;

            TorreRecompensas.InfoRecompensa info = TorreRecompensas.obtenerInfo(r);
            if (info.plata() > 0 || info.lunacoins() > 0) {
                String bono = "";
                if (info.plata() > 0) bono += "+1k Plata ";
                if (info.lunacoins() > 0) bono += "+50 LC";
                Text bonoText = Text.literal(bono.trim());
                int bonoW = anchoTexto(bonoText, 12) + 10;
                ctx.fill(px(tx), py(cy + 8), px(tx + bonoW), py(cy + 22), 0x3048BB78);
                marco(ctx, px(tx), py(cy + 8), pl(bonoW), pl(14), 0x8048BB78, 1);
                texto(ctx, bonoText, tx + 5, cy + 9, 12, 0xFF68D391, false, true);
            }

            // FILA 2: Hueco Master Ball (Izquierda)
            int sx = cx + 14;
            int sy = cy + 34;
            int ss = 34;
            ctx.fill(px(sx), py(sy), px(sx + ss), py(sy + ss), 0xFF0D121B);
            marco(ctx, px(sx), py(sy), pl(ss), pl(ss), 0xFF28364D, 1);

            if (dentro(rx, ry, px(sx), py(sy), pl(ss), pl(ss))) {
                hoveredStack = new ItemStack(Registries.ITEM.get(Identifier.of("cobblemon", "master_ball")));
            }

            texto(ctx, Text.literal("1x Master Ball"), sx + ss + 8, sy + 11, 13, 0xFFD8DEEA, false, true);

            // FILA 2: Botón (Derecha)
            int btnW = 90;
            int btnH = 34;
            int btnX = cx + cardW - btnW - 12;
            int btnY = cy + 34;

            if (esReclamada) {
                ctx.fill(px(btnX), py(btnY), px(btnX + btnW), py(btnY + btnH), 0xFF14202D);
                marco(ctx, px(btnX), py(btnY), pl(btnW), pl(btnH), 0xFF24364D, 1);
                texto(ctx, Text.literal("✔ RECLAMADO"), btnX + btnW / 2, btnY + 11, 12,
                        0xFF4FD07A, true, true);
            } else if (puedeReclamar) {
                boolean btnHover = dentro(rx, ry, px(btnX), py(btnY), pl(btnW), pl(btnH));
                ctx.fill(px(btnX), py(btnY), px(btnX + btnW), py(btnY + btnH),
                        btnHover ? VERDE_BOTON_ENCIMA : VERDE_BOTON);
                marco(ctx, px(btnX), py(btnY), pl(btnW), pl(btnH),
                        btnHover ? 0xFFFFFFFF : 0xFF144D25, 1);
                texto(ctx, Text.literal("¡RECLAMAR!"), btnX + btnW / 2, btnY + 10, 14,
                        0xFFFFFFFF, true, true);
            } else {
                ctx.fill(px(btnX), py(btnY), px(btnX + btnW), py(btnY + btnH), 0xFF121620);
                marco(ctx, px(btnX), py(btnY), pl(btnW), pl(btnH), 0xFF1E2634, 1);
                texto(ctx, Text.literal("🔒 Bloqueado"), btnX + btnW / 2, btnY + 5, 11,
                        0xFF718096, true, true);
                texto(ctx, Text.literal("Ronda " + r), btnX + btnW / 2, btnY + 18, 10,
                        0xFF4A5568, true, true);
            }
        }
    }

    private void dibujarSeccionInfinitaItems(DrawContext ctx) {
        int heroX = PANT_X + MARGEN;
        int heroY = PANT_Y + 50;
        int heroW = PANT_W - (2 * MARGEN);
        int heroH = 92;

        ItemStack masterBall = new ItemStack(Registries.ITEM.get(Identifier.of("cobblemon", "master_ball")));

        // Objeto en el Hero
        int hSlotX = heroX + 16;
        int hSlotY = heroY + 36;
        objeto(ctx, masterBall, hSlotX + 6, hSlotY + 6, 34);

        // Objetos en las tarjetas
        int listY = heroY + heroH + 12;
        int cardW = (heroW - 12) / 2;
        int cardH = 76;
        int gapX = 12;
        int gapY = 8;
        int cols = 2;

        for (int i = 0; i < 6; i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = heroX + (col * (cardW + gapX));
            int cy = listY + (row * (cardH + gapY));

            int sx = cx + 14;
            int sy = cy + 34;
            objeto(ctx, masterBall, sx + 4, sy + 4, 26);
        }
    }

    // =========================================================================
    // INTERACCIÓN
    // =========================================================================

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

        // Botón "Reclamar Todo"
        var estado = EstadoCliente.recompensasTorre();
        int pendientes = contarDisponibles(estado);
        int cardY = PANEL_Y + NAV_ALTO + 150;
        int cardH = 78;
        int btnW = PANEL_W - 36;
        int btnH = 48;
        int btnX = PANEL_X + 18;
        int btnY = cardY + cardH + 14;
        if (pendientes > 0 && dentro(rx, ry, px(btnX), py(btnY), pl(btnW), pl(btnH))) {
            sonar(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.2f);
            ClientPlayNetworking.send(new Red.ReclamarRecompensaTorre(-1));
            return true;
        }

        // Clics en Pestañas
        int tabY = PANT_Y + 10;
        int tabH = 32;
        int totalW = PANT_W - (2 * MARGEN);
        int gap = 6;
        int tabW = (totalW - (5 * gap)) / 6;

        for (int i = 0; i < 6; i++) {
            int tabX = PANT_X + MARGEN + (i * (tabW + gap));
            if (dentro(rx, ry, px(tabX), py(tabY), pl(tabW), pl(tabH))) {
                if (pestana != i) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
                    pestana = i;
                    pagina = 0;
                }
                return true;
            }
        }

        // Clics en Paginador (Pestañas 1 a 5)
        if (pestana < 5) {
            int pagY = PANT_Y + PANT_H - 38;
            int btnPagW = 110;
            int btnPagH = 26;

            // Anterior
            if (pagina > 0 && dentro(rx, ry, px(PANT_X + MARGEN), py(pagY), pl(btnPagW), pl(btnPagH))) {
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                pagina = 0;
                return true;
            }

            // Siguiente
            int nextX = PANT_X + PANT_W - MARGEN - btnPagW;
            if (pagina < 1 && dentro(rx, ry, px(nextX), py(pagY), pl(btnPagW), pl(btnPagH))) {
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                pagina = 1;
                return true;
            }
        }

        // Clics en Botones ¡RECLAMAR! de las tarjetas
        int maxRonda = estado != null ? estado.maxRonda() : 0;
        List<Integer> reclamadas = estado != null ? estado.reclamadas() : List.of();

        if (pestana == 5) {
            int heroW = PANT_W - (2 * MARGEN);
            int heroH = 92;
            int listY = PANT_Y + 50 + heroH + 12;
            int cW = (heroW - 12) / 2;
            int cH = 76;
            int gapX = 12;
            int gapY = 8;
            int cols = 2;
            int startR = 101 + (pagina * 6);

            for (int i = 0; i < 6; i++) {
                int r = startR + i;
                int col = i % cols;
                int row = i / cols;
                int cardX = PANT_X + MARGEN + (col * (cW + gapX));
                int cardItemY = listY + (row * (cH + gapY));

                boolean puedeReclamar = (maxRonda >= r) && !reclamadas.contains(r);
                if (puedeReclamar) {
                    int bW = 90;
                    int bH = 34;
                    int bX = cardX + cW - bW - 12;
                    int bY = cardItemY + 34;

                    if (dentro(rx, ry, px(bX), py(bY), pl(bW), pl(bH))) {
                        sonar(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
                        ClientPlayNetworking.send(new Red.ReclamarRecompensaTorre(r));
                        return true;
                    }
                }
            }
        } else {
            int rondaInicio = (pestana * 20) + (pagina * 10) + 1;
            int cols = 2;
            int gapX = 12;
            int gapY = 7;
            int cW = (PANT_W - (2 * MARGEN) - gapX) / cols;
            int cH = 74;
            int gridX = PANT_X + MARGEN;
            int gridY = PANT_Y + 50;

            for (int index = 0; index < 10; index++) {
                int r = rondaInicio + index;
                if (r > 100) break;

                int col = index % cols;
                int row = index / cols;
                int cardX = gridX + (col * (cW + gapX));
                int cardItemY = gridY + (row * (cH + gapY));

                boolean puedeReclamar = (maxRonda >= r && r >= 4) && !reclamadas.contains(r);
                if (puedeReclamar) {
                    int bW = 90;
                    int bH = 34;
                    int bX = cardX + cW - bW - 12;
                    int bY = cardItemY + 32;

                    if (dentro(rx, ry, px(bX), py(bY), pl(bW), pl(bH))) {
                        sonar(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
                        ClientPlayNetworking.send(new Red.ReclamarRecompensaTorre(r));
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mx, my, boton);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (pestana < 5) {
            if (verticalAmount < 0 && pagina < 1) {
                pagina = 1;
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 0.9f);
                return true;
            } else if (verticalAmount > 0 && pagina > 0) {
                pagina = 0;
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 0.9f);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int contarDisponibles(Red.EstadoRecompensasTorre estado) {
        if (estado == null || estado.maxRonda() < 4) return 0;
        int count = 0;
        for (int r = 4; r <= estado.maxRonda(); r++) {
            if (!estado.reclamadas().contains(r)) {
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // UTILIDADES DE DIBUJADO DE ALTA DEFINICIÓN
    // =========================================================================

    /**
     * Dibuja un objeto 3D a píxeles exactos de pantalla usando escalado matricial limpio.
     */
    private void objeto(DrawContext ctx, ItemStack pila, int ax, int ay, int ladoArte) {
        if (pila == null || pila.isEmpty()) return;
        float escala = pl(ladoArte) / 16f;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(px(ax), py(ay), 0);
        m.scale(escala, escala, 1f);
        ctx.drawItem(pila, 0, 0);
        m.pop();
    }

    private void sonar(net.minecraft.sound.SoundEvent sonido, float tono) {
        if (client != null && client.player != null) {
            client.player.playSound(sonido, 0.7f, tono);
        }
    }

    private void separador(DrawContext ctx, int artY) {
        ctx.fill(px(PANEL_X + 24), py(artY), px(PANEL_X + PANEL_W - 24),
                py(artY) + Math.max(1, pl(2)), SEPARADOR);
    }

    private int anchoTexto(Text linea, int alto) {
        return Math.round(textRenderer.getWidth(linea) * alto / (float) textRenderer.fontHeight);
    }

    /**
     * Dibuja texto con sombra nativa de Minecraft para máxima nitidez y contraste.
     */
    private void texto(DrawContext ctx, Text linea, int cx, int arriba, int alto,
                       int color, boolean centrado, boolean sombra) {
        float escala = alto * k / textRenderer.fontHeight;
        if (escala <= 0) return;

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x0, y0, 0);
        m.scale(escala, escala, 1f);
        int ancho = textRenderer.getWidth(linea);
        int tx = Math.round(cx * k / escala) - (centrado ? ancho / 2 : 0);
        int ty = Math.round(arriba * k / escala);
        ctx.drawText(textRenderer, linea, tx, ty, color, sombra);
        m.pop();
    }

    private static boolean dentro(int rx, int ry, int x, int y, int w, int h) {
        return rx >= x && rx < x + w && ry >= y && ry < y + h;
    }

    /**
     * Dibuja un marco de 4 aristas limpias sin rellenar nunca el interior.
     */
    private static void marco(DrawContext ctx, int x, int y, int w, int h, int color, int g) {
        ctx.fill(x, y, x + w, y + g, color);                 // Arista superior
        ctx.fill(x, y + h - g, x + w, y + h, color);         // Arista inferior
        ctx.fill(x, y + g, x + g, y + h - g, color);         // Arista izquierda
        ctx.fill(x + w - g, y + g, x + w, y + h - g, color); // Arista derecha
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
