package net.pokereport.luna.client.pokepad;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;

/**
 * SANTUARIO: los nichos de Monumentos.
 *
 * <h2>Diseño visual inspirado en Explorar, Viajes y Tesoros</h2>
 *
 * <pre>
 *   MENU          dos tarjetas destacadas con arte a sangre y degradados velo
 *   COMPRA        alquiler 24h (Plata) vs permanente (LunaCoins) con badges e iconos
 *   COMPRA_LISTA  selector de nichos libres para reclamar
 *   NICHOS        lista de memoriales de jugadores con miniatura, honores y TP
 *   MI_NICHO      editor de titulo, descripcion y foto del propio memorial
 *   MODERACION    panel de staff con foto grande de 200x180 px y botones directos
 * </pre>
 *
 * <h2>⚠⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 */
public class SantuarioScreen extends Screen {

    // ---- vistas y navegacion -----------------------------------------------

    private enum Vista {
        MENU,           // tarjetas destacadas: COMPRA + NICHOS (+MODERAR si staff)
        COMPRA,         // tarjetas: alquiler 24h / permanente
        COMPRA_LISTA,   // selector de nichos libres para reclamar
        NICHOS,         // lista de nichos de jugadores con TP
        MI_NICHO,       // editor de memorial
        MODERACION;     // staff: fotos pendientes con preview grande
    }

    private Vista volver(Vista v) {
        return switch (v) {
            case MENU -> null;
            case COMPRA, NICHOS, MODERACION -> Vista.MENU;
            case COMPRA_LISTA -> Vista.COMPRA;
            case MI_NICHO -> Vista.NICHOS;
        };
    }

    // ---- texturas y recursos -----------------------------------------------

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier ICONO =
            Identifier.of("lunaeternal", "textures/gui/pokepad/santuario.png");

    private static final Identifier ARTE_COMPRA =
            Identifier.of("lunaeternal", "textures/gui/pokepad/santuario_compra.png");
    private static final Identifier ARTE_NICHOS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/santuario_nichos.png");
    private static final Identifier ARTE_ALQUILER =
            Identifier.of("lunaeternal", "textures/gui/pokepad/santuario_alquiler.png");
    private static final Identifier ARTE_PERMANENTE =
            Identifier.of("lunaeternal", "textures/gui/pokepad/santuario_permanente.png");

    private static final Identifier ICONO_PLATA =
            Identifier.of("lunaeternal", "textures/gui/pokepad/plata.png");
    private static final Identifier ICONO_LUNA =
            Identifier.of("lunaeternal", "textures/gui/pokepad/lunacoin_oro.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    private static final int MARGEN = 14;
    private static final int FILA_ALTO = 94, FILA_AIRE = 10;
    private static final int PAG_Y = 698 + (745 - 698 - 40) / 2;
    private static final int PAG_SEP = 215;

    // Paleta oficial oscura y pulida (Luna Eternal nocturnal style)
    private static final int CARD_FONDO = 0xFF141D2E;
    private static final int CARD_FONDO_HOVER = 0xFF1C283E;
    private static final int CARD_BORDE = 0xFF283854;
    private static final int CARD_BORDE_ENCIMA = 0xFFF35C0C;
    private static final int CARD_SUBFONDO = 0xFF0E1522;

    private static final int TEXTO_BLANCO = 0xFFFFFFFF;
    private static final int TEXTO_CLARO = 0xFFE0E8F8;
    private static final int TEXTO_SUAVE = 0xFF8E9BB8;
    private static final int TEXTO_MUTED = 0xFF5A6684;
    private static final int CONTORNO_OSCURO = 0xFF080B12;

    private static final int ORO = 0xFFFFD65C;
    private static final int ORO_OSCURO = 0xFFD49E2A;
    private static final int PLATA_COLOR = 0xFFD8E4F8;
    private static final int VERDE_ESMERALDA = 0xFF2E9E56;
    private static final int AZUL_ZAFIRO = 0xFF3D6CB8;
    private static final int AMATISTA_TP = 0xFF7A4FB8;
    private static final int ROJO_CORAZON = 0xFFFF5A78;
    private static final int ROJO_RECHAZAR = 0xFFA63242;

    private final Screen anterior;
    private final Map<Identifier, Boolean> cacheArte = new HashMap<>();

    private float k;
    private int ancho, alto, x0, y0;
    private int pagina;

    private Vista vista = Vista.MENU;
    private String abierta = "";
    private boolean compraPermanente = false;
    private TextFieldWidget campoTitulo;
    private TextFieldWidget campoHistoria;
    private String rellenado = "";
    private String vistoSubida = "";

    public SantuarioScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.santuario"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        campoTitulo = campo(32, textoDe(campoTitulo));
        campoHistoria = campo(320, textoDe(campoHistoria));
        addSelectableChild(campoTitulo);
        addSelectableChild(campoHistoria);
        ClientPlayNetworking.send(new Red.PedirSantuario());
        ClientPlayNetworking.send(new Red.PedirFotos());
        ClientPlayNetworking.send(new Red.PedirPendientes());
    }

    private static String textoDe(TextFieldWidget c) {
        return c == null ? "" : c.getText();
    }

    private TextFieldWidget campo(int max, String valor) {
        var c = new TextFieldWidget(textRenderer, 0, 0, 10, 10, Text.literal(""));
        c.setMaxLength(max);
        c.setText(valor);
        return c;
    }

    private void colocar(TextFieldWidget c, int ax, int ay, int aw, int ah) {
        c.setX(px(ax));
        c.setY(py(ay));
        c.setWidth(pl(aw));
        c.setHeight(Math.max(12, pl(ah)));
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

    private boolean hayArte(Identifier id) {
        return cacheArte.computeIfAbsent(id, kId ->
                client != null && client.getResourceManager().getResource(kId).isPresent());
    }

    private void irA(Vista v) {
        vista = v;
        pagina = 0;
        if (v != Vista.MI_NICHO) {
            abierta = "";
            rellenado = "";
            setFocused(null);
        }
    }

    // ---- renderizado -------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        super.render(ctx, rx, ry, delta);
        var subida = EstadoCliente.fotoSubida();
        if (subida != null && !subida.idem().equals(vistoSubida)) {
            vistoSubida = subida.idem();
            ClientPlayNetworking.send(new Red.PedirFotos());
            ClientPlayNetworking.send(new Red.PedirPendientes());
        }
        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNav(ctx, rx, ry);
        dibujarPanel(ctx, rx, ry);
        switch (vista) {
            case MENU -> dibujarMenu(ctx, rx, ry);
            case COMPRA -> dibujarCompra(ctx, rx, ry);
            case COMPRA_LISTA -> { dibujarCompraLista(ctx, rx, ry); dibujarPie(ctx, rx, ry); }
            case NICHOS -> { dibujarNichos(ctx, rx, ry); dibujarPie(ctx, rx, ry); }
            case MI_NICHO -> dibujarMio(ctx, rx, ry);
            case MODERACION -> dibujarModeracion(ctx, rx, ry);
        }
    }

    private void dibujarNav(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        boolean sobreAtras = dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48));
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 80, 64);
        if (sobreAtras) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2, pl(60) + 4, pl(48) + 4,
                    CARD_BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.translatable(vista == Vista.MENU
                        ? "pokepad.lunaeternal.inicio"
                        : "pokepad.lunaeternal.protecciones.volver"),
                PANEL_X + 92, cy - 14, 28, TEXTO_BLANCO, false, 0);

        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4, CARD_BORDE_ENCIMA, 2);
        }
    }

    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        int cx = PANEL_X + PANEL_W / 2;
        dibujarTextura(ctx, ICONO, px(cx - 62), py(PANEL_Y + NAV_ALTO + 18),
                pl(124), pl(124), 100, 100);
        texto(ctx, Text.translatable("pokepad.lunaeternal.app.santuario"),
                cx, PANEL_Y + NAV_ALTO + 158, 30, TEXTO_BLANCO, true, CONTORNO_OSCURO);

        int y = PANEL_Y + NAV_ALTO + 202;
        String clave = switch (vista) {
            case MENU -> "pokepad.lunaeternal.santuario.explica";
            case COMPRA, COMPRA_LISTA -> "pokepad.lunaeternal.santuario.explica_compra";
            case NICHOS -> "pokepad.lunaeternal.santuario.explica_nichos";
            case MI_NICHO -> "pokepad.lunaeternal.santuario.explica_mi_nicho";
            case MODERACION -> "pokepad.lunaeternal.santuario.explica_modera";
        };
        for (String linea : partir(Text.translatable(clave).getString(), PANEL_W - 56, 16)) {
            texto(ctx, Text.literal(linea), cx, y, 16, TEXTO_SUAVE, true, 0);
            y += 20;
        }

        separador(ctx, y + 16);
        y += 34;

        var e = EstadoCliente.santuario();
        if (e == null) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                    cx, y + 20, 18, TEXTO_MUTED, true, 0);
            return;
        }

        // Stat Badge Container (estilo Tesoros / Explorar)
        int cardW = PANEL_W - 50;
        int cardH = 74;
        int bx = cx - cardW / 2;
        ctx.fill(px(bx), py(y), px(bx + cardW), py(y + cardH), CARD_SUBFONDO);
        marco(ctx, px(bx), py(y), pl(cardW), pl(cardH), CARD_BORDE, Math.max(1, pl(2)));

        int ocupados = 0;
        for (var n : e.nichos()) {
            if (!n.estado().dueno().isEmpty()) ocupados++;
        }

        texto(ctx, Text.translatable("pokepad.lunaeternal.santuario.ocupados"),
                cx, y + 10, 14, TEXTO_MUTED, true, 0);
        texto(ctx, Text.literal(ocupados + " / " + e.nichos().size()),
                cx, y + 32, 32, ORO, true, CONTORNO_OSCURO);

        // Si es staff, botón de acceso rápido a moderación en panel lateral
        if (e.modera() && vista != Vista.MODERACION) {
            int my = y + cardH + 16;
            int pendN = 0;
            var pend = EstadoCliente.pendientes();
            if (pend != null) pendN = pend.fotos().size();
            String rotulo = Text.translatable("pokepad.lunaeternal.santuario.moderar").getString();
            if (pendN > 0) rotulo += " (" + pendN + ")";
            boton(ctx, rx, ry, bx, my, cardW, 42, Text.literal(rotulo), true, AMATISTA_TP);
        }
    }

    // ---- MENU PRINCIPAL (2 tarjetas estilizadas tipo Explorar) -------------

    private void dibujarMenu(DrawContext ctx, int rx, int ry) {
        var e = EstadoCliente.santuario();
        if (e == null) {
            centrado(ctx, "pokepad.lunaeternal.cargando", 24, TEXTO_SUAVE, 0);
            return;
        }

        int ax = PANT_X + MARGEN;
        int aw = PANT_W - 2 * MARGEN;
        int y = PANT_Y + MARGEN;
        int cardH = e.modera() ? 180 : 210;
        int aire = 16;

        // Tarjeta 1: COMPRA TU ESPACIO
        tarjetaDestacada(ctx, rx, ry, ax, y, aw, cardH, ARTE_COMPRA, AZUL_ZAFIRO,
                Text.translatable("pokepad.lunaeternal.santuario.menu_compra"),
                Text.translatable("pokepad.lunaeternal.santuario.menu_compra_desc"),
                "MEMORIALES ETERNOS", Text.translatable("pokepad.lunaeternal.santuario.elegir_nicho"));
        y += cardH + aire;

        // Tarjeta 2: NICHOS DE JUGADORES
        tarjetaDestacada(ctx, rx, ry, ax, y, aw, cardH, ARTE_NICHOS, VERDE_ESMERALDA,
                Text.translatable("pokepad.lunaeternal.santuario.menu_nichos"),
                Text.translatable("pokepad.lunaeternal.santuario.menu_nichos_desc"),
                "COMUNIDAD · MONUMENTOS", Text.literal("EXPLORAR NICHOS"));
        y += cardH + aire;

        // Si es staff: Tarjeta 3 estilizada para moderación
        if (e.modera()) {
            int pendN = 0;
            var pend = EstadoCliente.pendientes();
            if (pend != null) pendN = pend.fotos().size();
            boolean encMod = dentro(rx, ry, px(ax), py(y), pl(aw), pl(58));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + 58), encMod ? CARD_FONDO_HOVER : CARD_FONDO);
            marco(ctx, px(ax), py(y), pl(aw), pl(58), encMod ? CARD_BORDE_ENCIMA : AMATISTA_TP, Math.max(1, pl(2)));
            texto(ctx, Text.translatable("pokepad.lunaeternal.santuario.moderar"),
                    ax + 24, y + 16, 22, TEXTO_BLANCO, false, CONTORNO_OSCURO);
            String descPend = pendN > 0
                    ? Text.translatable("pokepad.lunaeternal.santuario.pendientes_n", pendN).getString()
                    : Text.translatable("pokepad.lunaeternal.santuario.pendientes_vacio").getString();
            texto(ctx, Text.literal(descPend), ax + aw - 24, y + 18, 16, pendN > 0 ? ORO : TEXTO_MUTED, false, 0);
        }
    }

    /**
     * Dibuja una tarjeta con la misma técnica de {@link ExplorarScreen}:
     * Si la ilustración existe, la dibuja a sangre con velo de gradiente arriba y abajo.
     * Si no existe, dibuja un contenedor oscuro con tema y acento vibrante.
     */
    private void tarjetaDestacada(DrawContext ctx, int rx, int ry,
                                  int ax, int ay, int aw, int ah,
                                  Identifier arteId, int colorAcento,
                                  Text titulo, Text desc, String badgePill, Text botonTexto) {
        boolean enc = dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));

        if (hayArte(arteId)) {
            dibujarTextura(ctx, arteId, px(ax), py(ay), pl(aw), pl(ah), 1024, 680);
            velo(ctx, ax, ay, aw, 64, true);
            velo(ctx, ax, ay + ah - 68, aw, 68, false);
            if (enc) {
                ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah), 0x22FFFFFF);
            }
        } else {
            // Fondo oscuro con diseño temático
            ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah), enc ? CARD_FONDO_HOVER : CARD_FONDO);
            ctx.fill(px(ax), py(ay), px(ax + 8), py(ay + ah), colorAcento);
            // Barra de acento superior tenue
            ctx.fill(px(ax + 8), py(ay), px(ax + aw), py(ay + 4), colorAcento & 0x66FFFFFF);
        }

        marco(ctx, px(ax), py(ay), pl(aw), pl(ah),
                enc ? CARD_BORDE_ENCIMA : CARD_BORDE, Math.max(2, pl(enc ? 3 : 2)));

        // Pill badge decorativa arriba
        if (badgePill != null && !badgePill.isEmpty()) {
            pill(ctx, badgePill, ax + 24, ay + 14, colorAcento);
        }

        // Título principal con contorno oscuro
        texto(ctx, titulo, ax + 24, ay + 38, 28, TEXTO_BLANCO, false, CONTORNO_OSCURO);

        // Subtítulo descriptivo
        for (String linea : partir(desc.getString(), aw - 240, 16)) {
            texto(ctx, Text.literal(linea), ax + 24, ay + 72, 16, TEXTO_CLARO, false, CONTORNO_OSCURO);
            break;
        }

        // Botón a la derecha integrado en la tarjeta
        int btnW = 180;
        int btnH = 44;
        int btnX = ax + aw - btnW - 24;
        int btnY = ay + ah / 2 - btnH / 2;
        boton(ctx, rx, ry, btnX, btnY, btnW, btnH, botonTexto, true, colorAcento);
    }

    // ---- COMPRA TU ESPACIO (2 tarjetas estilizadas) ------------------------

    private void dibujarCompra(DrawContext ctx, int rx, int ry) {
        var e = EstadoCliente.santuario();
        if (e == null) {
            centrado(ctx, "pokepad.lunaeternal.cargando", 24, TEXTO_SUAVE, 0);
            return;
        }

        int ax = PANT_X + MARGEN;
        int aw = PANT_W - 2 * MARGEN;
        int y = PANT_Y + MARGEN;
        int cardH = 205;
        int aire = 18;

        // Tarjeta ALQUILER 24 HORAS
        tarjetaCompraOpcion(ctx, rx, ry, ax, y, aw, cardH, ARTE_ALQUILER, AZUL_ZAFIRO,
                Text.translatable("pokepad.lunaeternal.santuario.alquiler_titulo"),
                Text.translatable("pokepad.lunaeternal.santuario.alquiler_desc"),
                "⏱ 24 HORAS", ICONO_PLATA,
                String.format("%,d", e.precioPlata()) + " Plata", PLATA_COLOR,
                Text.translatable("pokepad.lunaeternal.santuario.elegir_nicho"));
        y += cardH + aire;

        // Tarjeta COMPRA PERMANENTE
        tarjetaCompraOpcion(ctx, rx, ry, ax, y, aw, cardH, ARTE_PERMANENTE, ORO_OSCURO,
                Text.translatable("pokepad.lunaeternal.santuario.permanente_titulo"),
                Text.translatable("pokepad.lunaeternal.santuario.permanente_desc"),
                "⭐ PERMANENTE", ICONO_LUNA,
                String.format("%,d", e.precioLuna()) + " LunaCoins", ORO,
                Text.translatable("pokepad.lunaeternal.santuario.elegir_nicho"));
    }

    private void tarjetaCompraOpcion(DrawContext ctx, int rx, int ry,
                                     int ax, int ay, int aw, int ah,
                                     Identifier arteId, int colorAcento,
                                     Text titulo, Text desc, String badgePill,
                                     Identifier iconoMoneda, String precioTexto, int colorPrecio,
                                     Text botonTexto) {
        boolean enc = dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));

        if (hayArte(arteId)) {
            dibujarTextura(ctx, arteId, px(ax), py(ay), pl(aw), pl(ah), 1024, 680);
            velo(ctx, ax, ay, aw, 64, true);
            velo(ctx, ax, ay + ah - 68, aw, 68, false);
            if (enc) ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah), 0x22FFFFFF);
        } else {
            ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah), enc ? CARD_FONDO_HOVER : CARD_FONDO);
            ctx.fill(px(ax), py(ay), px(ax + 8), py(ay + ah), colorAcento);
            ctx.fill(px(ax + 8), py(ay), px(ax + aw), py(ay + 4), colorAcento & 0x66FFFFFF);
        }

        marco(ctx, px(ax), py(ay), pl(aw), pl(ah),
                enc ? CARD_BORDE_ENCIMA : CARD_BORDE, Math.max(2, pl(enc ? 3 : 2)));

        // Pill superior
        pill(ctx, badgePill, ax + 24, ay + 16, colorAcento);

        // Título principal
        texto(ctx, titulo, ax + 24, ay + 42, 28, TEXTO_BLANCO, false, CONTORNO_OSCURO);

        // Descripción
        for (String linea : partir(desc.getString(), aw - 300, 16)) {
            texto(ctx, Text.literal(linea), ax + 24, ay + 78, 16, TEXTO_CLARO, false, CONTORNO_OSCURO);
            break;
        }

        // Badge con icono de moneda y precio a la derecha
        int badgeW = 240, badgeH = 46;
        int badgeX = ax + aw - badgeW - 24;
        int badgeY = ay + 32;
        ctx.fill(px(badgeX), py(badgeY), px(badgeX + badgeW), py(badgeY + badgeH), 0xDD0C1320);
        marco(ctx, px(badgeX), py(badgeY), pl(badgeW), pl(badgeH), colorAcento, Math.max(1, pl(2)));

        dibujarTextura(ctx, iconoMoneda, px(badgeX + 12), py(badgeY + 8), pl(30), pl(30), 48, 48);
        texto(ctx, Text.literal(precioTexto), badgeX + 50, badgeY + 12, 22, colorPrecio, false, CONTORNO_OSCURO);

        // Botón de acción debajo del badge
        int btnW = 240, btnH = 44;
        int btnY = ay + ah - btnH - 24;
        boton(ctx, rx, ry, badgeX, btnY, btnW, btnH, botonTexto, true, colorAcento);
    }

    // ---- COMPRA_LISTA (elegir nicho libre) ----------------------------------

    private void dibujarCompraLista(DrawContext ctx, int rx, int ry) {
        var e = EstadoCliente.santuario();
        if (e == null) {
            centrado(ctx, "pokepad.lunaeternal.cargando", 24, TEXTO_SUAVE, 0);
            return;
        }
        var libres = e.nichos().stream()
                .filter(n -> n.estado().dueno().isEmpty())
                .toList();

        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;

        texto(ctx, Text.translatable(compraPermanente
                        ? "pokepad.lunaeternal.santuario.elige_permanente"
                        : "pokepad.lunaeternal.santuario.elige_alquiler"),
                ax + 8, PANT_Y + MARGEN - 4, 24, TEXTO_BLANCO, false, CONTORNO_OSCURO);

        if (libres.isEmpty()) {
            centrado(ctx, "pokepad.lunaeternal.santuario.sin_libres", 24, TEXTO_BLANCO, -26);
            centrado(ctx, "pokepad.lunaeternal.santuario.sin_libres2", 18, TEXTO_SUAVE, 14);
            return;
        }

        int desde = pagina * filasCaben();
        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= libres.size()) break;
            var nicho = libres.get(i);
            int y = filaY(n);
            boolean enc = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA_ALTO));

            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA_ALTO), enc ? CARD_FONDO_HOVER : CARD_FONDO);
            ctx.fill(px(ax), py(y), px(ax + 6), py(y + FILA_ALTO), compraPermanente ? ORO_OSCURO : AZUL_ZAFIRO);
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA_ALTO), enc ? CARD_BORDE_ENCIMA : CARD_BORDE, Math.max(1, pl(2)));

            // Nombre y coordenadas
            texto(ctx, Text.literal(nicho.nombre()), ax + 20, y + 14, 24, TEXTO_BLANCO, false, CONTORNO_OSCURO);
            texto(ctx, Text.literal("Monumentos · Pos: " + nicho.pos().x() + ", " + nicho.pos().y() + ", " + nicho.pos().z()),
                    ax + 20, y + 42, 16, TEXTO_MUTED, false, 0);

            pill(ctx, "DISPONIBLE", ax + 20, y + 64, VERDE_ESMERALDA);

            // Botón reclamar a la derecha
            int btnW = 200, btnH = 44;
            boton(ctx, rx, ry, ax + aw - btnW - 20, y + 25, btnW, btnH,
                    Text.translatable(compraPermanente
                            ? "pokepad.lunaeternal.santuario.comprar"
                            : "pokepad.lunaeternal.santuario.alquilar"),
                    true, compraPermanente ? ORO_OSCURO : AZUL_ZAFIRO);
        }
    }

    // ---- NICHOS DE JUGADORES (reclamados + TP) -----------------------------

    private void dibujarNichos(DrawContext ctx, int rx, int ry) {
        var e = EstadoCliente.santuario();
        if (e == null) {
            centrado(ctx, "pokepad.lunaeternal.cargando", 24, TEXTO_SUAVE, 0);
            return;
        }
        var lista = e.nichos();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;

        texto(ctx, Text.translatable("pokepad.lunaeternal.santuario.menu_nichos"),
                ax + 8, PANT_Y + MARGEN - 4, 24, TEXTO_BLANCO, false, CONTORNO_OSCURO);

        int desde = pagina * filasCaben();
        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= lista.size()) break;
            var nicho = lista.get(i);
            var estado = nicho.estado();
            int y = filaY(n);
            boolean enc = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA_ALTO));

            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA_ALTO), enc ? CARD_FONDO_HOVER : CARD_FONDO);
            ctx.fill(px(ax), py(y), px(ax + 6), py(y + FILA_ALTO),
                    estado.dueno().isEmpty() ? VERDE_ESMERALDA : (estado.mio() ? ORO_OSCURO : AZUL_ZAFIRO));
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA_ALTO), enc ? CARD_BORDE_ENCIMA : CARD_BORDE, Math.max(1, pl(2)));

            // Miniatura de foto o recuadro decorativo a la izquierda
            int thumbSize = 64;
            int thumbX = ax + 18;
            int thumbY = y + (FILA_ALTO - thumbSize) / 2;
            var foto = TexturasFotoActual(nicho.memorial().foto());
            if (foto != null) {
                dibujarFoto(ctx, foto, thumbX, thumbY, thumbSize, thumbSize);
                marco(ctx, px(thumbX), py(thumbY), pl(thumbSize), pl(thumbSize), ORO_OSCURO, Math.max(1, pl(1)));
            } else {
                ctx.fill(px(thumbX), py(thumbY), px(thumbX + thumbSize), py(thumbY + thumbSize), CARD_SUBFONDO);
                marco(ctx, px(thumbX), py(thumbY), pl(thumbSize), pl(thumbSize), CARD_BORDE, Math.max(1, pl(1)));
                texto(ctx, Text.literal("✦"), thumbX + thumbSize / 2, thumbY + 18, 26, TEXTO_MUTED, true, 0);
            }

            int infoX = thumbX + thumbSize + 16;
            texto(ctx, Text.literal(nicho.nombre()), infoX, y + 14, 22, TEXTO_BLANCO, false, CONTORNO_OSCURO);

            if (estado.dueno().isEmpty()) {
                texto(ctx, Text.translatable("pokepad.lunaeternal.santuario.libre"), infoX, y + 42, 16, VERDE_ESMERALDA, false, 0);
            } else {
                String duenoStr = "de " + estado.dueno();
                if (estado.mio()) duenoStr = "TU NICHO";
                texto(ctx, Text.literal(duenoStr), infoX, y + 40, 16, estado.mio() ? ORO : TEXTO_CLARO, false, 0);

                String subtitulo = (nicho.memorial().titulo().isEmpty() ? "" : "\"" + nicho.memorial().titulo() + "\"  ·  ")
                        + "♥ " + nicho.memorial().honores() + " honores";
                texto(ctx, Text.literal(subtitulo), infoX, y + 64, 15, ROJO_CORAZON, false, 0);
            }

            // Botones de acción a la derecha
            int bx = ax + aw - 16;
            if (estado.mio()) {
                boton(ctx, rx, ry, bx - 390, y + 26, 110, 42,
                        Text.translatable("pokepad.lunaeternal.santuario.ver"), true, AZUL_ZAFIRO);
                boton(ctx, rx, ry, bx - 265, y + 26, 125, 42,
                        Text.translatable("pokepad.lunaeternal.santuario.mi_nicho"), true, VERDE_ESMERALDA);
                boton(ctx, rx, ry, bx - 125, y + 26, 125, 42,
                        Text.translatable("pokepad.lunaeternal.santuario.tp"), true, AMATISTA_TP);
            } else if (!estado.dueno().isEmpty()) {
                boton(ctx, rx, ry, bx - 260, y + 26, 120, 42,
                        Text.translatable("pokepad.lunaeternal.santuario.ver"), true, AZUL_ZAFIRO);
                boton(ctx, rx, ry, bx - 125, y + 26, 125, 42,
                        Text.translatable("pokepad.lunaeternal.santuario.tp"), true, AMATISTA_TP);
            } else {
                boton(ctx, rx, ry, bx - 125, y + 26, 125, 42,
                        Text.translatable("pokepad.lunaeternal.santuario.tp"), true, AMATISTA_TP);
            }
        }
    }

    private void dibujarPie(DrawContext ctx, int rx, int ry) {
        if (paginas() <= 1) return;
        int cx = PANT_X + PANT_W / 2;
        flecha(ctx, rx, ry, cx - PAG_SEP, PAG_Y, false, pagina > 0);
        flecha(ctx, rx, ry, cx + PAG_SEP - 40, PAG_Y, true, pagina < paginas() - 1);
        texto(ctx, Text.literal((pagina + 1) + " / " + paginas()), cx, PAG_Y + 10, 22,
                TEXTO_BLANCO, true, CONTORNO_OSCURO);
    }

    private void flecha(DrawContext ctx, int rx, int ry, int ax, int ay,
                        boolean derecha, boolean activa) {
        boolean encima = activa && dentro(rx, ry, px(ax), py(ay), pl(40), pl(40));
        int color = !activa ? 0xFF4A566E : (encima ? ORO : TEXTO_BLANCO);
        texto(ctx, Text.literal(derecha ? ">" : "<"), ax + 20, ay + 8, 28, color, true, CONTORNO_OSCURO);
    }

    // ---- MI NICHO ----------------------------------------------------------

    private Red.NichoSantuario elAbierto() {
        var e = EstadoCliente.santuario();
        if (e == null) return null;
        for (var n : e.nichos()) {
            if (n.id().equals(abierta)) return n;
        }
        return null;
    }

    private void dibujarMio(DrawContext ctx, int rx, int ry) {
        var nicho = elAbierto();
        if (nicho == null) {
            centrado(ctx, "pokepad.lunaeternal.cargando", 24, TEXTO_SUAVE, 0);
            return;
        }
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        texto(ctx, Text.literal("EDITANDO: " + nicho.nombre()), ax + 8, PANT_Y + MARGEN - 4, 26,
                TEXTO_BLANCO, false, CONTORNO_OSCURO);

        if (!abierta.equals(rellenado)) {
            rellenado = abierta;
            campoTitulo.setText(nicho.memorial().titulo());
            campoHistoria.setText(nicho.memorial().descripcion());
        }

        int y = PANT_Y + MARGEN + 30;
        rotulo(ctx, "pokepad.lunaeternal.santuario.titulo", ax, y);
        colocar(campoTitulo, ax, y + 26, 380, 32);
        campoTitulo.render(ctx, rx, ry, 0);

        rotulo(ctx, "pokepad.lunaeternal.santuario.historia", ax, y + 76);
        colocar(campoHistoria, ax, y + 102, aw - 210, 120);
        campoHistoria.render(ctx, rx, ry, 0);

        boton(ctx, rx, ry, ax + 30, y + 242, 170, 42,
                Text.translatable("pokepad.lunaeternal.santuario.guardar"),
                !campoTitulo.getText().trim().isEmpty(), VERDE_ESMERALDA);

        // ---- la foto
        int fx = ax + aw - 208;
        rotulo(ctx, "pokepad.lunaeternal.santuario.foto", fx, y);
        var actual = TexturasFotoActual(nicho.memorial().foto());
        if (actual != null) {
            dibujarFoto(ctx, actual, fx, y + 26, 180, 150);
            marco(ctx, px(fx), py(y + 26), pl(180), pl(150), ORO_OSCURO, Math.max(1, pl(2)));
        } else {
            ctx.fill(px(fx), py(y + 26), px(fx + 180), py(y + 176), CARD_SUBFONDO);
            marco(ctx, px(fx), py(y + 26), pl(180), pl(150), CARD_BORDE, Math.max(1, pl(2)));
            texto(ctx, Text.translatable("pokepad.lunaeternal.santuario.sin_foto"),
                    fx + 90, y + 86, 16, TEXTO_MUTED, true, 0);
        }
        boton(ctx, rx, ry, fx, y + 186, 180, 38,
                Text.translatable("pokepad.lunaeternal.santuario.subir"),
                true, AZUL_ZAFIRO);
        if (!nicho.memorial().foto().isEmpty()) {
            boton(ctx, rx, ry, fx, y + 232, 180, 38,
                    Text.translatable("pokepad.lunaeternal.santuario.quitar_foto"),
                    true, ROJO_RECHAZAR);
        }

        // ---- mis fotos disponibles
        rotulo(ctx, "pokepad.lunaeternal.santuario.mis_fotos", ax, y + 292);
        var fotos = EstadoCliente.misFotos();
        if (fotos == null || fotos.fotos().isEmpty()) {
            texto(ctx, Text.translatable(fotos == null
                    ? "pokepad.lunaeternal.cargando"
                    : "pokepad.lunaeternal.santuario.sin_fotos"),
                    ax, y + 320, 16, TEXTO_MUTED, false, 0);
        } else {
            int n = 0;
            for (var f : fotos.fotos()) {
                int fy = y + 320 + n * 34;
                if (fy > PANT_Y + PANT_H - 50) break;
                String etiqueta = Text.translatable(
                        "pokepad.lunaeternal.santuario.estado." + f.estado().toLowerCase())
                        .getString();
                texto(ctx, Text.literal("#" + f.fotoId() + " · " + etiqueta),
                        ax, fy + 6, 16, TEXTO_CLARO, false, 0);
                if ("APROBADA".equals(f.estado())) {
                    boton(ctx, rx, ry, ax + 330, fy, 150, 30,
                            Text.translatable("pokepad.lunaeternal.santuario.poner"),
                            true, VERDE_ESMERALDA);
                }
                n++;
            }
        }
    }

    private TexturasFotoActual TexturasFotoActual(String sha1) {
        if (sha1 == null || sha1.isEmpty()) return null;
        var f = net.pokereport.luna.client.TexturasFoto.lista(sha1);
        if (f == null) {
            net.pokereport.luna.client.TexturasFoto.pedir(sha1);
            return null;
        }
        return new TexturasFotoActual(f, sha1);
    }

    private record TexturasFotoActual(
            net.pokereport.luna.client.TexturasFoto.Foto foto, String sha1) {}

    private void dibujarFoto(DrawContext ctx, TexturasFotoActual actual,
                             int ax, int ay, int aw, int ah) {
        var foto = actual.foto();
        float kFoto = Math.min(aw / (float) foto.ancho(), ah / (float) foto.alto());
        int dw = Math.round(foto.ancho() * kFoto);
        int dh = Math.round(foto.alto() * kFoto);
        int dx = px(ax + (aw - dw) / 2);
        int dy = py(ay + (ah - dh) / 2);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(foto.textura(), dx, dy, pl(dw), pl(dh), 0f, 0f,
                foto.ancho(), foto.alto(), foto.ancho(), foto.alto());
        RenderSystem.disableBlend();
    }

    // ---- MODERACION (solo staff) --- fotos grandes 200x180 -----------------

    private void dibujarModeracion(DrawContext ctx, int rx, int ry) {
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        texto(ctx, Text.translatable("pokepad.lunaeternal.santuario.moderacion"),
                ax + 8, PANT_Y + MARGEN - 4, 26, TEXTO_BLANCO, false, CONTORNO_OSCURO);

        var pend = EstadoCliente.pendientes();
        if (pend == null) {
            centrado(ctx, "pokepad.lunaeternal.cargando", 24, TEXTO_SUAVE, 0);
            return;
        }
        if (pend.fotos().isEmpty()) {
            centrado(ctx, "pokepad.lunaeternal.santuario.pendientes_vacio", 22,
                    TEXTO_SUAVE, 0);
            return;
        }

        int tarjetaH = 220;
        int y = PANT_Y + MARGEN + 36;
        for (var f : pend.fotos()) {
            if (y + tarjetaH > PANT_Y + PANT_H - 10) break;

            ctx.fill(px(ax), py(y), px(ax + aw), py(y + tarjetaH), CARD_FONDO);
            marco(ctx, px(ax), py(y), pl(aw), pl(tarjetaH), CARD_BORDE, Math.max(1, pl(2)));

            // Foto grande a la izquierda (200 x 180 px)
            int fotoW = 200, fotoH = 180;
            var foto = net.pokereport.luna.client.TexturasFoto.lista(f.sha1());
            if (foto == null) {
                net.pokereport.luna.client.TexturasFoto.pedir(f.sha1());
                ctx.fill(px(ax + 16), py(y + 20), px(ax + 16 + fotoW), py(y + 20 + fotoH), CARD_SUBFONDO);
                texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                        ax + 16 + fotoW / 2, y + 20 + fotoH / 2 - 10, 16, TEXTO_MUTED, true, 0);
            } else {
                dibujarFoto(ctx, new TexturasFotoActual(foto, f.sha1()), ax + 16, y + 20, fotoW, fotoH);
                marco(ctx, px(ax + 16), py(y + 20), pl(fotoW), pl(fotoH), ORO_OSCURO, Math.max(1, pl(2)));
            }

            // Info a la derecha
            int infoX = ax + fotoW + 42;
            texto(ctx, Text.literal("FOTO #" + f.fotoId()), infoX, y + 28, 24, TEXTO_BLANCO, false, CONTORNO_OSCURO);
            texto(ctx, Text.literal("Jugador: " + f.dueno()), infoX, y + 60, 20, ORO, false, 0);

            // Botones APROBAR / RECHAZAR
            boton(ctx, rx, ry, infoX, y + 104, 210, 44,
                    Text.translatable("pokepad.lunaeternal.santuario.aprobar"),
                    true, VERDE_ESMERALDA);
            boton(ctx, rx, ry, infoX, y + 156, 210, 44,
                    Text.translatable("pokepad.lunaeternal.santuario.rechazar"),
                    true, ROJO_RECHAZAR);

            y += tarjetaH + 12;
        }
    }

    private boolean clicModeracion(int rx, int ry) {
        var pend = EstadoCliente.pendientes();
        if (pend == null) return false;
        int ax = PANT_X + MARGEN;
        int tarjetaH = 220;
        int fotoW = 200;
        int infoX = ax + fotoW + 42;
        int y = PANT_Y + MARGEN + 36;
        for (var f : pend.fotos()) {
            if (y + tarjetaH > PANT_Y + PANT_H - 10) break;
            if (dentro(rx, ry, px(infoX), py(y + 104), pl(210), pl(44))) {
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
                ClientPlayNetworking.send(new Red.ModerarFoto(f.fotoId(), true));
                return true;
            }
            if (dentro(rx, ry, px(infoX), py(y + 156), pl(210), pl(44))) {
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 0.9f);
                ClientPlayNetworking.send(new Red.ModerarFoto(f.fotoId(), false));
                return true;
            }
            y += tarjetaH + 12;
        }
        return false;
    }

    // ---- interaccion -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) return super.mouseClicked(mx, my, boton);
        int rx = (int) mx, ry = (int) my;

        // ATRAS
        int cy = py(PANEL_Y + NAV_ALTO / 2);
        if (dentro(rx, ry, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            Vista destino = volver(vista);
            if (destino == null) {
                if (client != null) client.setScreen(anterior);
            } else {
                irA(destino);
            }
            return true;
        }

        // CERRAR
        int cxC = PANEL_X + PANEL_W - 18 - 80;
        if (dentro(rx, ry, px(cxC), cy - pl(32), pl(80), pl(64))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            close();
            return true;
        }

        // Acceso rápido a moderar en panel lateral si es staff
        var e = EstadoCliente.santuario();
        if (e != null && e.modera() && vista != Vista.MODERACION) {
            int cx = PANEL_X + PANEL_W / 2;
            int cardW = PANEL_W - 50;
            int bx = cx - cardW / 2;
            int myY = py(PANEL_Y + NAV_ALTO + 202) + pl(34 + 74 + 16);
            if (dentro(rx, ry, px(bx), myY, pl(cardW), pl(42))) {
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                irA(Vista.MODERACION);
                ClientPlayNetworking.send(new Red.PedirPendientes());
                return true;
            }
        }

        return switch (vista) {
            case MENU -> clicMenu(rx, ry);
            case COMPRA -> clicCompra(rx, ry);
            case COMPRA_LISTA -> clicCompraLista(rx, ry) || super.mouseClicked(mx, my, boton);
            case NICHOS -> clicNichos(rx, ry) || super.mouseClicked(mx, my, boton);
            case MI_NICHO -> clicMio(rx, ry, mx, my, boton) || super.mouseClicked(mx, my, boton);
            case MODERACION -> clicModeracion(rx, ry) || super.mouseClicked(mx, my, boton);
        };
    }

    private boolean clicMenu(int rx, int ry) {
        var e = EstadoCliente.santuario();
        if (e == null) return false;
        int ax = PANT_X + MARGEN;
        int aw = PANT_W - 2 * MARGEN;
        int y = PANT_Y + MARGEN;
        int cardH = e.modera() ? 180 : 210;
        int aire = 16;

        // Tarjeta COMPRA
        if (dentro(rx, ry, px(ax), py(y), pl(aw), pl(cardH))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            irA(Vista.COMPRA);
            return true;
        }
        y += cardH + aire;

        // Tarjeta NICHOS
        if (dentro(rx, ry, px(ax), py(y), pl(aw), pl(cardH))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            irA(Vista.NICHOS);
            return true;
        }
        y += cardH + aire;

        // Tarjeta MODERAR (solo staff)
        if (e.modera() && dentro(rx, ry, px(ax), py(y), pl(aw), pl(58))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            irA(Vista.MODERACION);
            ClientPlayNetworking.send(new Red.PedirPendientes());
            return true;
        }
        return false;
    }

    private boolean clicCompra(int rx, int ry) {
        int ax = PANT_X + MARGEN;
        int aw = PANT_W - 2 * MARGEN;
        int y = PANT_Y + MARGEN;
        int cardH = 205;
        int aire = 18;

        // Tarjeta Alquiler
        if (dentro(rx, ry, px(ax), py(y), pl(aw), pl(cardH))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            compraPermanente = false;
            irA(Vista.COMPRA_LISTA);
            return true;
        }
        y += cardH + aire;

        // Tarjeta Permanente
        if (dentro(rx, ry, px(ax), py(y), pl(aw), pl(cardH))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f);
            compraPermanente = true;
            irA(Vista.COMPRA_LISTA);
            return true;
        }
        return false;
    }

    private boolean clicCompraLista(int rx, int ry) {
        if (clicPaginacion(rx, ry)) return true;
        var e = EstadoCliente.santuario();
        if (e == null) return false;
        var libres = e.nichos().stream()
                .filter(n -> n.estado().dueno().isEmpty())
                .toList();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        int desde = pagina * filasCaben();
        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= libres.size()) break;
            var nicho = libres.get(i);
            int y = filaY(n);
            int btnW = 200, btnH = 44;
            if (dentro(rx, ry, px(ax + aw - btnW - 20), py(y + 25), pl(btnW), pl(btnH))) {
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
                if (compraPermanente) {
                    ClientPlayNetworking.send(new Red.ComprarNicho(
                            nicho.id(), UUID.randomUUID().toString()));
                } else {
                    ClientPlayNetworking.send(new Red.AlquilarNicho(
                            nicho.id(), UUID.randomUUID().toString()));
                }
                return true;
            }
        }
        return false;
    }

    private boolean clicNichos(int rx, int ry) {
        if (clicPaginacion(rx, ry)) return true;
        var e = EstadoCliente.santuario();
        if (e == null || e.nichos().isEmpty()) return false;
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        int bx = ax + aw - 16;
        int desde = pagina * filasCaben();
        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= e.nichos().size()) break;
            var nicho = e.nichos().get(i);
            var estado = nicho.estado();
            int y = filaY(n);

            if (estado.mio()) {
                if (dentro(rx, ry, px(bx - 390), py(y + 26), pl(110), pl(42))) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                    client.setScreen(new MemorialScreen(this, nicho));
                    return true;
                }
                if (dentro(rx, ry, px(bx - 265), py(y + 26), pl(125), pl(42))) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                    abierta = nicho.id();
                    rellenado = "";
                    vista = Vista.MI_NICHO;
                    return true;
                }
                if (dentro(rx, ry, px(bx - 125), py(y + 26), pl(125), pl(42))) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                    ClientPlayNetworking.send(new Red.TpNicho(nicho.id()));
                    close();
                    return true;
                }
            } else if (!estado.dueno().isEmpty()) {
                if (dentro(rx, ry, px(bx - 260), py(y + 26), pl(120), pl(42))) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                    client.setScreen(new MemorialScreen(this, nicho));
                    return true;
                }
                if (dentro(rx, ry, px(bx - 125), py(y + 26), pl(125), pl(42))) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                    ClientPlayNetworking.send(new Red.TpNicho(nicho.id()));
                    close();
                    return true;
                }
            } else {
                if (dentro(rx, ry, px(bx - 125), py(y + 26), pl(125), pl(42))) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                    ClientPlayNetworking.send(new Red.TpNicho(nicho.id()));
                    close();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean clicPaginacion(int rx, int ry) {
        if (paginas() <= 1) return false;
        int cx = PANT_X + PANT_W / 2;
        if (pagina > 0 && dentro(rx, ry, px(cx - PAG_SEP), py(PAG_Y), pl(40), pl(40))) {
            pagina--;
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            return true;
        }
        if (pagina < paginas() - 1
                && dentro(rx, ry, px(cx + PAG_SEP - 40), py(PAG_Y), pl(40), pl(40))) {
            pagina++;
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            return true;
        }
        return false;
    }

    private boolean clicMio(int rx, int ry, double mx, double my, int boton) {
        var nicho = elAbierto();
        if (nicho == null) return false;
        for (var c : new TextFieldWidget[] {campoTitulo, campoHistoria}) {
            if (c.mouseClicked(mx, my, boton)) {
                setFocused(c);
                return true;
            }
        }
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        int y = PANT_Y + MARGEN + 30;
        if (!campoTitulo.getText().trim().isEmpty()
                && dentro(rx, ry, px(ax + 30), py(y + 242), pl(170), pl(42))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
            ClientPlayNetworking.send(new Red.TextosNicho(abierta,
                    campoTitulo.getText().trim(), campoHistoria.getText().trim()));
            return true;
        }
        int fx = ax + aw - 208;
        if (dentro(rx, ry, px(fx), py(y + 186), pl(180), pl(38))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            net.pokereport.luna.client.DialogoFoto.abrir(this);
            return true;
        }
        if (!nicho.memorial().foto().isEmpty()
                && dentro(rx, ry, px(fx), py(y + 232), pl(180), pl(38))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 0.9f);
            ClientPlayNetworking.send(new Red.QuitarFoto(abierta));
            return true;
        }
        var fotos = EstadoCliente.misFotos();
        if (fotos != null) {
            int n = 0;
            for (var f : fotos.fotos()) {
                int fy = y + 320 + n * 34;
                if ("APROBADA".equals(f.estado())
                        && dentro(rx, ry, px(ax + 330), py(fy), pl(150), pl(30))) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
                    ClientPlayNetworking.send(new Red.PonerFoto(abierta, f.fotoId()));
                    return true;
                }
                n++;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        if (tecla == 256 && getFocused() != null) {
            setFocused(null);
            return true;
        }
        for (var c : new TextFieldWidget[] {campoTitulo, campoHistoria}) {
            if (getFocused() == c && c.keyPressed(tecla, escaneo, mods)) {
                return true;
            }
        }
        return super.keyPressed(tecla, escaneo, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        for (var f : new TextFieldWidget[] {campoTitulo, campoHistoria}) {
            if (getFocused() == f) {
                return f.charTyped(c, mods);
            }
        }
        return super.charTyped(c, mods);
    }

    private void sonar(net.minecraft.sound.SoundEvent sonido, float tono) {
        if (client != null && client.player != null) {
            client.player.playSound(sonido, 0.7f, tono);
        }
    }

    // ---- utilidades de dibujado --------------------------------------------

    /** Velo de gradiente (como en {@link ExplorarScreen}). */
    private void velo(DrawContext ctx, int ax, int ay, int aw, int ah, boolean arriba) {
        int pasos = Math.max(4, pl(ah) / 2);
        int altoPx = pl(ah);
        for (int i = 0; i < pasos; i++) {
            float f = i / (float) pasos;
            int alfa = (int) ((arriba ? (1 - f) : f) * 210);
            int y1 = py(ay) + i * altoPx / pasos;
            int y2 = py(ay) + (i + 1) * altoPx / pasos;
            ctx.fill(px(ax), y1, px(ax + aw), y2, (alfa << 24));
        }
    }

    /** Pill badge decorativa pequeña con color de acento. */
    private void pill(DrawContext ctx, String texto, int ax, int ay, int colorAcento) {
        int tw = anchoArte(texto, 13);
        int pw = tw + 16;
        int ph = 20;
        ctx.fill(px(ax), py(ay), px(ax + pw), py(ay + ph), 0xCC0A101A);
        marco(ctx, px(ax), py(ay), pl(pw), pl(ph), colorAcento, Math.max(1, pl(1)));
        texto(ctx, Text.literal(texto), ax + pw / 2, ay + 3, 13, colorAcento, true, 0);
    }

    private void rotulo(DrawContext ctx, String clave, int ax, int ay) {
        texto(ctx, Text.translatable(clave), ax, ay, 20, TEXTO_BLANCO, false, CONTORNO_OSCURO);
    }

    private void centrado(DrawContext ctx, String clave, int alto, int color, int dy) {
        texto(ctx, Text.translatable(clave), PANT_X + PANT_W / 2,
                PANT_Y + PANT_H / 2 + dy, alto, color, true, CONTORNO_OSCURO);
    }

    private void boton(DrawContext ctx, int rx, int ry, int ax, int ay, int aw, int ah,
                       Text etiqueta, boolean activo, int color) {
        boolean encima = activo && dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        int bg = !activo ? 0xFF2A364C : (encima ? aclarar(color) : color);
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah), bg);
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), encima ? CARD_BORDE_ENCIMA : 0xFF0A0E18, Math.max(1, pl(2)));

        int t = 18;
        while (t > 12 && anchoArte(etiqueta.getString(), t) > aw - 16) {
            t--;
        }
        texto(ctx, etiqueta, ax + aw / 2, ay + ah / 2 - t / 2 - 1, t,
                activo ? TEXTO_BLANCO : TEXTO_MUTED, true, CONTORNO_OSCURO);
    }

    private static int aclarar(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 36);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 36);
        int b = Math.min(255, (color & 0xFF) + 36);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private List<String> partir(String texto, int anchoArte, int altoArte) {
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
                py(artY) + Math.max(1, pl(2)), 0xFF24344E);
    }

    private void texto(DrawContext ctx, Text linea, int cx, int arriba, int alto,
                       int color, boolean centrado, int contornoColor) {
        float escala = alto * k / textRenderer.fontHeight;
        if (escala <= 0) return;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x0, y0, 0);
        m.scale(escala, escala, 1f);
        int anchoTexto = textRenderer.getWidth(linea);
        int tx = Math.round(cx * k / escala) - (centrado ? anchoTexto / 2 : 0);
        int ty = Math.round(arriba * k / escala);
        if (contornoColor != 0) {
            ctx.drawText(textRenderer, linea, tx - 1, ty, contornoColor, false);
            ctx.drawText(textRenderer, linea, tx + 1, ty, contornoColor, false);
            ctx.drawText(textRenderer, linea, tx, ty - 1, contornoColor, false);
            ctx.drawText(textRenderer, linea, tx, ty + 1, contornoColor, false);
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
        ctx.fill(x, y + g, x + g, y + h - g, color);
        ctx.fill(x + w - g, y + g, x + w, y + h - g, color);
    }

    private static void dibujarTextura(DrawContext ctx, Identifier tex,
                                       int x, int y, int w, int h, int natW, int natH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
    }

    private int filaY(int n) {
        return PANT_Y + MARGEN + 26 + n * (FILA_ALTO + FILA_AIRE);
    }

    private int filasCaben() {
        return 4;
    }

    private int paginas() {
        var e = EstadoCliente.santuario();
        if (e == null) return 1;
        int total = switch (vista) {
            case COMPRA_LISTA -> (int) e.nichos().stream()
                    .filter(n -> n.estado().dueno().isEmpty()).count();
            case NICHOS -> e.nichos().size();
            default -> 0;
        };
        return Math.max(1, (total + filasCaben() - 1) / filasCaben());
    }
}
