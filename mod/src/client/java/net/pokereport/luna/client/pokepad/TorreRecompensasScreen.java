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

import java.util.ArrayList;
import java.util.List;

/**
 * PANTALLA DE RECOMPENSAS DE TEMPORADA DE LA TORRE DE BATALLA.
 *
 * <p>Integrada en el chasis estándar de cosméticos del PokePad (pokepad_cosmeticos.png):
 * - Panel izquierdo: Récord de temporada, estadísticas de reclamo, botón "Reclamar Todo" y reglas.
 * - Pantalla derecha: Pestañas por rangos de rondas (1-20, 21-40, 41-60, 61-80, 81-100, 101+),
 *   tarjetas individuales de recompensas con iconos de ítems, divisas y botones de reclamo interactivos.
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

    // Paleta estándar PokePad
    private static final int BORDE_ENCIMA = 0xFFF35C0C; // Naranja acento
    private static final int BORDE_BASE = 0xFF7C89B4;
    private static final int FONDO_TARJETA = 0xFF1D2536;
    private static final int FONDO_TARJETA_DISPONIBLE = 0xFF24324A;
    private static final int FONDO_TARJETA_RECLAMADA = 0xFF171E2B;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF8FA0C8;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int ORO = 0xFFFFD65C;
    private static final int CIAN = 0xFF55FFFF;
    private static final int VERDE_BOTON = 0xFF2E9E56;
    private static final int VERDE_BOTON_ENCIMA = 0xFF4FD07A;
    private static final int GRIS_INACTIVO = 0xFF2B3344;

    private final Screen anterior;
    private float k;
    private int ancho, alto, x0, y0;

    private int pestana = 0; // 0: 1-20, 1: 21-40, 2: 41-60, 3: 61-80, 4: 81-100, 5: 101+
    private int pagina = 0;  // 0 o 1 dentro de la pestaña

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

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanelIzquierdo(ctx, rx, ry);
        dibujarPantallaDerecha(ctx, rx, ry);
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;

        // Botón Atrás
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 120, 96);
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2, pl(60) + 4, pl(48) + 4,
                    BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.literal("TORRE"), PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false, false);

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
        dibujarTextura(ctx, ICONO, px(cx - 40), py(PANEL_Y + NAV_ALTO + 10),
                pl(80), pl(80), 100, 100);

        // Título de Recompensas
        texto(ctx, Text.literal("RECOMPENSAS"), cx, PANEL_Y + NAV_ALTO + 98, 24, ORO, true, false);
        texto(ctx, Text.literal("TEMPORADA #" + temp), cx, PANEL_Y + NAV_ALTO + 126, 14, CIAN, true, false);

        separador(ctx, PANEL_Y + NAV_ALTO + 148);

        // Tarjeta Récord de Temporada
        int cardY = PANEL_Y + NAV_ALTO + 160;
        int cardW = PANEL_W - 40;
        int cardH = 76;
        int cardX = PANEL_X + 20;

        ctx.fill(px(cardX), py(cardY), px(cardX + cardW), py(cardY + cardH), 0xFF182030);
        marco(ctx, px(cardX), py(cardY), pl(cardW), pl(cardH), 0xFF354460, 1);

        texto(ctx, Text.literal("RÉCORD DE TEMPORADA"), cx, cardY + 8, 12, TEXTO_SUAVE, true, false);
        String txtRecord = maxRonda > 0 ? "Ronda " + maxRonda + " ★" : "Sin récord";
        texto(ctx, Text.literal(txtRecord), cx, cardY + 24, 22, maxRonda > 0 ? ORO : 0xFFA0AEC0, true, false);

        String breakdown = "1v1: " + r1 + "  ·  2v2: " + r2 + "  ·  Draft: " + ra;
        texto(ctx, Text.literal(breakdown), cx, cardY + 54, 11, 0xFF8FA0C8, true, false);

        // Botón "Reclamar Todo"
        int btnW = PANEL_W - 40;
        int btnH = 48;
        int btnX = PANEL_X + 20;
        int btnY = cardY + cardH + 16;

        boolean puedeReclamarTodo = pendientes > 0;
        boolean hoverBtn = dentro(rx, ry, px(btnX), py(btnY), pl(btnW), pl(btnH));

        int colorBtn = puedeReclamarTodo
                ? (hoverBtn ? VERDE_BOTON_ENCIMA : VERDE_BOTON)
                : GRIS_INACTIVO;
        int bordeBtn = puedeReclamarTodo ? (hoverBtn ? 0xFFFFFFFF : 0xFF144D25) : 0xFF3A4456;

        ctx.fill(px(btnX), py(btnY), px(btnX + btnW), py(btnY + btnH), colorBtn);
        marco(ctx, px(btnX), py(btnY), pl(btnW), pl(btnH), bordeBtn, Math.max(1, pl(hoverBtn ? 2 : 1)));

        String txtBoton = puedeReclamarTodo
                ? "RECLAMAR TODO (" + pendientes + ")"
                : "TODO RECLAMADO (0)";
        texto(ctx, Text.literal(txtBoton), cx, btnY + 16, 17,
                puedeReclamarTodo ? 0xFFFFFFFF : 0xFF7A869E, true, false);

        separador(ctx, btnY + btnH + 16);

        // Resumen de Reglas de la Temporada
        int regY = btnY + btnH + 30;
        texto(ctx, Text.literal("REGLAS DE TEMPORADA"), cx, regY, 15, 0xFFFFFFFF, true, false);
        regY += 22;

        String[] reglas = {
                "• 1 reclamo por temporada",
                "• Desbloqueadas por tu récord",
                "• Cada 5 rondas: +1,000 Plata",
                "• Cada 30 rondas: +50 LunaCoins",
                "• Ronda 101+: 1 Master Ball fija"
        };
        for (String r : reglas) {
            texto(ctx, Text.literal(r), PANEL_X + 28, regY, 12, 0xFFD2D9E8, false, false);
            regY += 17;
        }
    }

    private void dibujarPantallaDerecha(DrawContext ctx, int rx, int ry) {
        dibujarPestanas(ctx, rx, ry);
        if (pestana == 5) {
            dibujarSeccionInfinita(ctx, rx, ry);
        } else {
            dibujarParrillaRondas(ctx, rx, ry);
        }
    }

    private void dibujarPestanas(DrawContext ctx, int rx, int ry) {
        String[] titulosPestanas = { "1 - 20", "21 - 40", "41 - 60", "61 - 80", "81 - 100", "101+ ★" };
        int tabY = PANT_Y + 10;
        int tabH = 34;
        int totalW = PANT_W - (2 * MARGEN);
        int gap = 6;
        int tabW = (totalW - ((titulosPestanas.length - 1) * gap)) / titulosPestanas.length;

        for (int i = 0; i < titulosPestanas.length; i++) {
            int tabX = PANT_X + MARGEN + (i * (tabW + gap));
            boolean activa = (i == pestana);
            boolean hover = dentro(rx, ry, px(tabX), py(tabY), pl(tabW), pl(tabH));

            int fondo = activa ? 0xFF2A364F : (hover ? 0xFF222B3D : 0xFF171F2D);
            int borde = activa ? BORDE_ENCIMA : (hover ? BORDE_BASE : 0xFF2D3950);

            ctx.fill(px(tabX), py(tabY), px(tabX + tabW), py(tabY + tabH), fondo);
            marco(ctx, px(tabX), py(tabY), pl(tabW), pl(tabH), borde, Math.max(1, pl(activa ? 2 : 1)));

            int colorTexto = activa ? ORO : (hover ? 0xFFFFFFFF : 0xFF8FA0C8);
            texto(ctx, Text.literal(titulosPestanas[i]), tabX + tabW / 2, tabY + 10, 15,
                    colorTexto, true, false);
        }
    }

    private void dibujarParrillaRondas(DrawContext ctx, int rx, int ry) {
        var estado = EstadoCliente.recompensasTorre();
        int maxRonda = estado != null ? estado.maxRonda() : 0;
        List<Integer> reclamadas = estado != null ? estado.reclamadas() : List.of();

        int rondaInicio = (pestana * 20) + (pagina * 10) + 1;
        int cols = 2;
        int rows = 5;
        int gridX = PANT_X + MARGEN;
        int gridY = PANT_Y + 54;
        int gapX = 10;
        int gapY = 8;
        int cardW = (PANT_W - (2 * MARGEN) - gapX) / cols;
        int cardH = 68;

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

            // Fondo y borde según estado
            int fondoCard = puedeReclamar
                    ? (cardHover ? 0xFF283852 : FONDO_TARJETA_DISPONIBLE)
                    : (esReclamada ? FONDO_TARJETA_RECLAMADA : FONDO_TARJETA);
            int bordeCard = puedeReclamar
                    ? (cardHover ? BORDE_ENCIMA : ORO)
                    : (esReclamada ? 0xFF2B3A4C : 0xFF2A3448);

            ctx.fill(px(cx), py(cy), px(cx + cardW), py(cy + cardH), fondoCard);
            marco(ctx, px(cx), py(cy), pl(cardW), pl(cardH), bordeCard, Math.max(1, pl(puedeReclamar ? 2 : 1)));

            // Columna 1: Ronda y Badge
            texto(ctx, Text.literal("RONDA " + r), cx + 12, cy + 10, 16,
                    puedeReclamar ? ORO : (esReclamada ? 0xFF9FAEC4 : 0xFF718096), false, false);

            if (info.tituloHito() != null) {
                int colorHito = r == 100 ? ORO : (r == 50 || r == 70 || r == 90 ? CIAN : 0xFFFFA07A);
                texto(ctx, Text.literal(info.tituloHito()), cx + 12, cy + 28, 10, colorHito, false, false);
            }

            // Bonificaciones de moneda escritas
            if (info.plata() > 0 || info.lunacoins() > 0) {
                String bonoStr = "";
                if (info.plata() > 0) bonoStr += "+1k Plata ";
                if (info.lunacoins() > 0) bonoStr += "+50 LC";
                texto(ctx, Text.literal(bonoStr), cx + 12, cy + 46, 11, 0xFF68D391, false, false);
            }

            // Columna 2: Ítems dibujados
            ctx.draw();
            int itemStartX = cx + 150;
            int itemY = cy + 18;
            for (int itIdx = 0; itIdx < Math.min(4, info.items().size()); itIdx++) {
                var it = info.items().get(itIdx);
                ItemStack st = it.crearStack();
                objeto(ctx, st, itemStartX + (itIdx * 34), itemY, 28);
                // Si cantidad > 1, dibujar texto de cantidad
                if (st.getCount() > 1) {
                    texto(ctx, Text.literal("x" + st.getCount()),
                            itemStartX + (itIdx * 34) + 18, itemY + 20, 11, 0xFFFFFFFF, false, true);
                }
            }

            // Columna 3: Estado o Botón de Reclamar
            int btnW = 90;
            int btnH = 34;
            int btnX = cx + cardW - btnW - 12;
            int btnY = cy + (cardH - btnH) / 2;

            if (r < 4) {
                texto(ctx, Text.literal("Sin premio"), btnX + btnW / 2, cy + cardH / 2 - 6, 13,
                        0xFF606A7C, true, false);
            } else if (esReclamada) {
                texto(ctx, Text.literal("✓ Reclamado"), btnX + btnW / 2, cy + cardH / 2 - 6, 13,
                        0xFF4FD07A, true, false);
            } else if (puedeReclamar) {
                boolean btnHover = dentro(rx, ry, px(btnX), py(btnY), pl(btnW), pl(btnH));
                ctx.fill(px(btnX), py(btnY), px(btnX + btnW), py(btnY + btnH),
                        btnHover ? VERDE_BOTON_ENCIMA : VERDE_BOTON);
                marco(ctx, px(btnX), py(btnY), pl(btnW), pl(btnH),
                        btnHover ? 0xFFFFFFFF : 0xFF144D25, 1);
                texto(ctx, Text.literal("¡RECLAMAR!"), btnX + btnW / 2, btnY + 10, 13,
                        0xFFFFFFFF, true, false);
            } else {
                texto(ctx, Text.literal("🔒 Bloqueado"), btnX + btnW / 2, cy + cardH / 2 - 6, 13,
                        0xFF718096, true, false);
            }
        }

        // Paginador al pie de la cuadrícula
        int pagY = PANT_Y + PANT_H - 42;
        int btnPagW = 100;
        int btnPagH = 26;

        // Botón Página Anterior
        boolean puedeAnt = pagina > 0;
        boolean hoverAnt = puedeAnt && dentro(rx, ry, px(PANT_X + MARGEN), py(pagY), pl(btnPagW), pl(btnPagH));
        ctx.fill(px(PANT_X + MARGEN), py(pagY), px(PANT_X + MARGEN + btnPagW), py(pagY + btnPagH),
                hoverAnt ? 0xFF2A364F : 0xFF1A2230);
        marco(ctx, px(PANT_X + MARGEN), py(pagY), pl(btnPagW), pl(btnPagH),
                hoverAnt ? BORDE_ENCIMA : 0xFF354460, 1);
        texto(ctx, Text.literal("◄ ANTERIOR"), PANT_X + MARGEN + btnPagW / 2, pagY + 7, 13,
                puedeAnt ? (hoverAnt ? 0xFFFFFFFF : 0xFFC0CADC) : 0xFF505A6E, true, false);

        // Indicador de página
        int centroX = PANT_X + PANT_W / 2;
        int rInicio = (pestana * 20) + (pagina * 10) + 1;
        int rFin = Math.min(100, rInicio + 9);
        texto(ctx, Text.literal("Rondas " + rInicio + " - " + rFin + " (Pág. " + (pagina + 1) + " de 2)"),
                centroX, pagY + 7, 14, ORO, true, false);

        // Botón Página Siguiente
        int nextX = PANT_X + PANT_W - MARGEN - btnPagW;
        boolean puedeSig = pagina < 1;
        boolean hoverSig = puedeSig && dentro(rx, ry, px(nextX), py(pagY), pl(btnPagW), pl(btnPagH));
        ctx.fill(px(nextX), py(pagY), px(nextX + btnPagW), py(pagY + btnPagH),
                hoverSig ? 0xFF2A364F : 0xFF1A2230);
        marco(ctx, px(nextX), py(pagY), pl(btnPagW), pl(btnPagH),
                hoverSig ? BORDE_ENCIMA : 0xFF354460, 1);
        texto(ctx, Text.literal("SIGUIENTE ►"), nextX + btnPagW / 2, pagY + 7, 13,
                puedeSig ? (hoverSig ? 0xFFFFFFFF : 0xFFC0CADC) : 0xFF505A6E, true, false);
    }

    private void dibujarSeccionInfinita(DrawContext ctx, int rx, int ry) {
        var estado = EstadoCliente.recompensasTorre();
        int maxRonda = estado != null ? estado.maxRonda() : 0;
        List<Integer> reclamadas = estado != null ? estado.reclamadas() : List.of();

        // Tarjeta Hero: Explicación de las Rondas 101+
        int heroX = PANT_X + MARGEN;
        int heroY = PANT_Y + 54;
        int heroW = PANT_W - (2 * MARGEN);
        int heroH = 120;

        ctx.fill(px(heroX), py(heroY), px(heroX + heroW), py(heroY + heroH), 0xFF1D2638);
        marco(ctx, px(heroX), py(heroY), pl(heroW), pl(heroH), ORO, 2);

        texto(ctx, Text.literal("🏆 MAESTRÍA INFINITA (RONDA 101 EN ADELANTE)"),
                heroX + 20, heroY + 14, 18, ORO, false, false);
        texto(ctx, Text.literal("Por cada ronda consecutiva que conquistes más allá del piso 100, recibirás:"),
                heroX + 20, heroY + 38, 13, 0xFFE2E8F0, false, false);

        ctx.draw();
        ItemStack masterBall = new ItemStack(Registries.ITEM.get(Identifier.of("cobblemon", "master_ball")));
        objeto(ctx, masterBall, heroX + 24, heroY + 60, 44);

        texto(ctx, Text.literal("• 1 Master Ball fija por cada victoria"),
                heroX + 80, heroY + 68, 14, 0xFFFFFFFF, false, false);
        texto(ctx, Text.literal("• +1,000 Plata (cada 5 rondas)  y  +50 LunaCoins (cada 30 rondas)"),
                heroX + 80, heroY + 88, 13, 0xFF68D391, false, false);

        // Lista de rondas 101 a 110 (o hasta maxRonda)
        int listY = heroY + heroH + 16;
        int cardW = (heroW - 10) / 2;
        int cardH = 58;

        for (int i = 0; i < 4; i++) {
            int r = 101 + i;
            int col = i % 2;
            int row = i / 2;
            int cx = heroX + (col * (cardW + 10));
            int cy = listY + (row * (cardH + 8));

            boolean esReclamada = reclamadas.contains(r);
            boolean esDesbloqueada = (maxRonda >= r);
            boolean puedeReclamar = esDesbloqueada && !esReclamada;
            boolean cardHover = dentro(rx, ry, px(cx), py(cy), pl(cardW), pl(cardH));

            int fondoCard = puedeReclamar
                    ? (cardHover ? 0xFF283852 : FONDO_TARJETA_DISPONIBLE)
                    : (esReclamada ? FONDO_TARJETA_RECLAMADA : FONDO_TARJETA);
            int bordeCard = puedeReclamar ? (cardHover ? BORDE_ENCIMA : ORO) : 0xFF2A3448;

            ctx.fill(px(cx), py(cy), px(cx + cardW), py(cy + cardH), fondoCard);
            marco(ctx, px(cx), py(cy), pl(cardW), pl(cardH), bordeCard, Math.max(1, pl(puedeReclamar ? 2 : 1)));

            texto(ctx, Text.literal("RONDA " + r), cx + 12, cy + 10, 16,
                    puedeReclamar ? ORO : (esReclamada ? 0xFF9FAEC4 : 0xFF718096), false, false);

            TorreRecompensas.InfoRecompensa info = TorreRecompensas.obtenerInfo(r);
            if (info.plata() > 0 || info.lunacoins() > 0) {
                String bonoStr = "";
                if (info.plata() > 0) bonoStr += "+1k Plata ";
                if (info.lunacoins() > 0) bonoStr += "+50 LC";
                texto(ctx, Text.literal(bonoStr), cx + 12, cy + 32, 11, 0xFF68D391, false, false);
            }

            ctx.draw();
            objeto(ctx, masterBall, cx + 140, cy + 12, 34);

            int btnW = 90;
            int btnH = 32;
            int btnX = cx + cardW - btnW - 12;
            int btnY = cy + (cardH - btnH) / 2;

            if (esReclamada) {
                texto(ctx, Text.literal("✓ Reclamado"), btnX + btnW / 2, cy + cardH / 2 - 6, 13,
                        0xFF4FD07A, true, false);
            } else if (puedeReclamar) {
                boolean btnHover = dentro(rx, ry, px(btnX), py(btnY), pl(btnW), pl(btnH));
                ctx.fill(px(btnX), py(btnY), px(btnX + btnW), py(btnY + btnH),
                        btnHover ? VERDE_BOTON_ENCIMA : VERDE_BOTON);
                marco(ctx, px(btnX), py(btnY), pl(btnW), pl(btnH),
                        btnHover ? 0xFFFFFFFF : 0xFF144D25, 1);
                texto(ctx, Text.literal("¡RECLAMAR!"), btnX + btnW / 2, btnY + 9, 13,
                        0xFFFFFFFF, true, false);
            } else {
                texto(ctx, Text.literal("🔒 Ronda " + r), btnX + btnW / 2, cy + cardH / 2 - 6, 13,
                        0xFF718096, true, false);
            }
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

        // Botón "Reclamar Todo"
        var estado = EstadoCliente.recompensasTorre();
        int pendientes = contarDisponibles(estado);
        int cardY = PANEL_Y + NAV_ALTO + 160;
        int cardH = 76;
        int btnW = PANEL_W - 40;
        int btnH = 48;
        int btnX = PANEL_X + 20;
        int btnY = cardY + cardH + 16;
        if (pendientes > 0 && dentro(rx, ry, px(btnX), py(btnY), pl(btnW), pl(btnH))) {
            sonar(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.2f);
            ClientPlayNetworking.send(new Red.ReclamarRecompensaTorre(-1));
            return true;
        }

        // Clics en Pestañas
        int tabY = PANT_Y + 10;
        int tabH = 34;
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

        // Clics en la Parrilla de Rondas (Pestañas 0 a 4)
        if (pestana < 5) {
            int rondaInicio = (pestana * 20) + (pagina * 10) + 1;
            int cols = 2;
            int gridX = PANT_X + MARGEN;
            int gridY = PANT_Y + 54;
            int gapX = 10;
            int gapY = 8;
            int cW = (PANT_W - (2 * MARGEN) - gapX) / cols;
            int cH = 68;

            int maxRonda = estado != null ? estado.maxRonda() : 0;
            List<Integer> reclamadas = estado != null ? estado.reclamadas() : List.of();

            for (int index = 0; index < 10; index++) {
                int r = rondaInicio + index;
                if (r > 100) break;

                int col = index % cols;
                int row = index / cols;
                int cardX = gridX + (col * (cW + gapX));
                int cardItemY = gridY + (row * (cH + gapY));

                boolean puedeReclamar = (maxRonda >= r && r >= 4 && !reclamadas.contains(r));
                if (puedeReclamar) {
                    int bW = 90;
                    int bH = 34;
                    int bX = cardX + cW - bW - 12;
                    int bY = cardItemY + (cH - bH) / 2;

                    if (dentro(rx, ry, px(bX), py(bY), pl(bW), pl(bH))) {
                        sonar(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
                        ClientPlayNetworking.send(new Red.ReclamarRecompensaTorre(r));
                        return true;
                    }
                }
            }

            // Paginador
            int pagY = PANT_Y + PANT_H - 42;
            int btnPagW = 100;
            int btnPagH = 26;
            // Anterior
            if (pagina > 0 && dentro(rx, ry, px(PANT_X + MARGEN), py(pagY), pl(btnPagW), pl(btnPagH))) {
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                pagina--;
                return true;
            }
            // Siguiente
            int nextX = PANT_X + PANT_W - MARGEN - btnPagW;
            if (pagina < 1 && dentro(rx, ry, px(nextX), py(pagY), pl(btnPagW), pl(btnPagH))) {
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                pagina++;
                return true;
            }
        } else {
            // Clics en Pestaña 5 (101+ Infinito)
            int heroX = PANT_X + MARGEN;
            int heroY = PANT_Y + 54;
            int heroW = PANT_W - (2 * MARGEN);
            int heroH = 120;
            int listY = heroY + heroH + 16;
            int cW = (heroW - 10) / 2;
            int cH = 58;

            int maxRonda = estado != null ? estado.maxRonda() : 0;
            List<Integer> reclamadas = estado != null ? estado.reclamadas() : List.of();

            for (int i = 0; i < 4; i++) {
                int r = 101 + i;
                int col = i % 2;
                int row = i / 2;
                int cardX = heroX + (col * (cW + 10));
                int cardItemY = listY + (row * (cH + 8));

                boolean puedeReclamar = (maxRonda >= r && !reclamadas.contains(r));
                if (puedeReclamar) {
                    int bW = 90;
                    int bH = 32;
                    int bX = cardX + cW - bW - 12;
                    int bY = cardItemY + (cH - bH) / 2;

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

    private void objeto(DrawContext ctx, ItemStack pila, int ax, int ay, int altoArte) {
        if (pila.isEmpty()) return;
        float escala = altoArte * k / 16f;
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
        ctx.drawText(textRenderer, linea, tx, ty, color, false);
        m.pop();
    }

    private static boolean dentro(int rx, int ry, int x, int y, int w, int h) {
        return rx >= x && rx < x + w && ry >= y && ry < y + h;
    }

    private static void marco(DrawContext ctx, int x, int y, int w, int h, int color, int g) {
        ctx.fill(x, y, x + w, y + g, color);
        ctx.fill(x, y + h - g, x + w, y + h, color);
        ctx.fill(x, y + g, x + w, y + h, color);
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
