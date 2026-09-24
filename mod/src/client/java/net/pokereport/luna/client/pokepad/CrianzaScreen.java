package net.pokereport.luna.client.pokepad;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.gui.pc.PCGUIConfiguration;
import com.cobblemon.mod.common.pokemon.Gender;
import com.mojang.blaze3d.systems.RenderSystem;
import kotlin.Unit;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;
import net.pokereport.luna.crianza.CrianzaService;

/**
 * Pantalla de CRIANZA en el PokéPad (Luna Eternal).
 *
 * <p>Diseñada sobre el chasis de cosméticos {@code pokepad_cosmeticos.png},
 * con 7 ranuras progresivas por rango y LunaCoins, selección de progenitores
 * (Hembra / Macho / Ditto) abriendo directamente la PC oficial de Cobblemon,
 * modelos de los progenitores y una guía de herencia lateral.
 *
 * <p>Cumple estrictamente las seis reglas de {@code docs/ui/dibujado.md}.
 */
public class CrianzaScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier MONEDA =
            Identifier.of("lunaeternal", "textures/gui/pokepad/lunacoin_oro.png");
    private static final Identifier ICONO_APP =
            Identifier.of("lunaeternal", "textures/gui/pokepad/crianza.png");
    private static final Identifier ICONO_HEMBRA =
            Identifier.of("lunaeternal", "textures/gui/pokepad/hembra_slot.png");
    private static final Identifier ICONO_MACHO =
            Identifier.of("lunaeternal", "textures/gui/pokepad/macho_slot.png");
    private static final Identifier ICONO_HUEVO =
            Identifier.of("lunaeternal", "textures/gui/pokepad/huevo_crianza.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int MARGEN = 14;

    private static final int ORO = 0xFFFFD65C;
    private static final int VERDE = 0xFF70E0AF;
    private static final int ROJO = 0xFFFF8A91;
    private static final int CYAN = 0xFF56C8D6;
    private static final int ROSA = 0xFFF56CA0;
    private static final int TEXTO_SUAVE = 0xFFA6B7CC;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;

    private final Screen anterior;
    private float k;
    private int ancho, alto, x0, y0;

    public static Screen pantallaVolver = null;
    private int ranuraSeleccionada = 0;

    private int refresco;
    private long ultimoEnvio;
    private int confirmarCompra = -1;


    public CrianzaScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.crianza"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        super.init();
        recalcular();
        // Solicitar datos frescos al servidor
        ClientPlayNetworking.send(new Red.PedirCrianza());
        ClientPlayNetworking.send(new Red.PedirSaldo());
    }

    private void recalcular() {
        float gui = (float) client.getWindow().getScaleFactor();
        float cabeW = (float) client.getWindow().getScaledWidth() * gui / (float) NAT_ANCHO;
        float cabeH = (float) client.getWindow().getScaledHeight() * gui / (float) NAT_ALTO;
        float cabe = Math.min(cabeW, cabeH);
        k = Math.min(1.0f, cabe) / gui;

        ancho = Math.round(NAT_ANCHO * k);
        alto = Math.round(NAT_ALTO * k);
        x0 = (width - ancho) / 2;
        y0 = (height - alto) / 2;
    }

    private int px(int x) { return x0 + Math.round(x * k); }
    private int py(int y) { return y0 + Math.round(y * k); }
    private int pl(int l) { return Math.max(1, Math.round(l * k)); }

    private boolean dentro(int rx, int ry, int x, int y, int w, int h) {
        return rx >= x && rx < x + w && ry >= y && ry < y + h;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        recalcular();

        // 1. Chasis del PokePad
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float guiScale = (float) client.getWindow().getScaleFactor();
        client.getTextureManager().getTexture(CHASIS).setFilter(Math.round(ancho * guiScale) != NAT_ANCHO || Math.round(alto * guiScale) != NAT_ALTO, false);
        ctx.drawTexture(CHASIS, x0, y0, ancho, alto, 0f, 0f, NAT_ANCHO, NAT_ALTO, NAT_ANCHO, NAT_ALTO);
        RenderSystem.disableBlend();
        ctx.fillGradient(px(PANT_X), py(PANT_Y), px(PANT_X + PANT_W), py(PANT_Y + PANT_H), 0xFF101E31, 0xFF080F1C);

        var estado = EstadoCliente.crianza();
        var ranuras = estado != null ? estado.ranuras() : null;
        var ranuraActual = (ranuras != null && ranuraSeleccionada < ranuras.size())
                ? ranuras.get(ranuraSeleccionada) : null;

        // 2. Panel izquierdo (decoración, guía de crianza, saldo)
        dibujarPanelIzquierdo(ctx, mouseX, mouseY, estado);

        // 3. Pantalla principal: Barra superior de 7 ranuras (R1 a R7)
        dibujarBarraRanuras(ctx, mouseX, mouseY, ranuras);

        // 4. Contenido principal debajo de las pestañas
        if (ranuraActual != null) {
            if (!ranuraActual.desbloqueada()) {
                dibujarRanuraBloqueada(ctx, mouseX, mouseY, ranuraActual);
            } else {
                dibujarMesaCrianza(ctx, mouseX, mouseY, ranuraActual);
            }
            // 5. Visor lateral derecho (modelo 3D e información de herencia)
            dibujarVisorDerecho(ctx, mouseX, mouseY, ranuraActual);
        } else {
            texto(ctx, Text.literal("Cargando datos de crianza..."), PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2,
                    16, TEXTO_SUAVE, true, false);
        }
    }

    // ------------------------------------------------------------- DIBUJADO DE SECCIONES

    private void dibujarPanelIzquierdo(DrawContext ctx, int mx, int my, Red.EstadoCrianza estado) {
        // Botón Atrás
        boolean encimaAtras = dentro(mx, my, px(PANEL_X + 18), py(PANEL_Y + 12), pl(60), pl(48));
        ctx.drawTexture(ATRAS, px(PANEL_X + 18), py(PANEL_Y + 12), pl(60), pl(48),
                0f, 0f, 60, 48, 60, 48);
        if (encimaAtras) {
            marco(ctx, px(PANEL_X + 18), py(PANEL_Y + 12), pl(60), pl(48), BORDE_ENCIMA, 2);
        }

        // Botón Cerrar
        boolean encimaCerrar = dentro(mx, my, px(PANEL_X + PANEL_W - 18 - 80), py(PANEL_Y + 12), pl(80), pl(64));
        ctx.drawTexture(CERRAR, px(PANEL_X + PANEL_W - 18 - 80), py(PANEL_Y + 12), pl(80), pl(64),
                0f, 0f, 80, 64, 80, 64);
        if (encimaCerrar) {
            marco(ctx, px(PANEL_X + PANEL_W - 18 - 80), py(PANEL_Y + 12), pl(80), pl(64), BORDE_ENCIMA, 2);
        }

        // Icono de Crianza
        int icX = PANEL_X + (PANEL_W - 48) / 2;
        int icY = PANEL_Y + 54;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(ICONO_APP, px(icX), py(icY), pl(48), pl(48), 0f, 0f, 100, 100, 100, 100);
        RenderSystem.disableBlend();

        // Título de la App
        texto(ctx, Text.literal("CRIANZA"), PANEL_X + PANEL_W / 2, icY + 52, 18, 0xFFFFFFFF, true, true);
        texto(ctx, Text.literal("Centro Genético PokéPad"), PANEL_X + PANEL_W / 2, icY + 70, 11, 0xFF8AA0C4, true, false);

        // Tarjeta 1: Resumen de ranuras
        int ranurasDesbloqueadas = 0;
        if (estado != null && estado.ranuras() != null) {
            for (var r : estado.ranuras()) {
                if (r.desbloqueada()) ranurasDesbloqueadas++;
            }
        }
        int cardY = icY + 90;
        int cardH = 46;
        ctx.fill(px(PANEL_X + 16), py(cardY), px(PANEL_X + PANEL_W - 16), py(cardY + cardH), 0x90101726);
        marco(ctx, px(PANEL_X + 16), py(cardY), pl(PANEL_W - 32), pl(cardH), 0xFF354466, 1);

        texto(ctx, Text.literal("RANURAS ACTIVAS"), PANEL_X + PANEL_W / 2, cardY + 8, 10, 0xFF8AA0C4, true, false);
        texto(ctx, Text.literal(ranurasDesbloqueadas + " de 7 Desbloqueadas"), PANEL_X + PANEL_W / 2, cardY + 24, 15, ORO, true, true);

        int gx = PANEL_X + 24;
        texto(ctx, Text.literal("TU CENTRO DE CRIANZA"), gx, 284, 13, ORO, false, false);
        String[][] pasos = {
            {"01  ELIGE LA PAREJA", "Selecciona Pokémon de tu PC o equipo."},
            {"02  COMPRUEBA AFINIDAD", "Comparte grupo huevo o usa Ditto."},
            {"03  RECOGE TU CRÍA", "La pareja inicia otro ciclo al recoger."}
        };
        for (int i = 0; i < pasos.length; i++) {
            int gy = 314 + i * 56;
            texto(ctx, Text.literal(pasos[i][0]), gx, gy, 12, 0xFFE0EAF7, false, false);
            textoAjustado(ctx, pasos[i][1], gx, gy + 20, 12, PANEL_W - 48, TEXTO_SUAVE, false);
        }
        ctx.fill(px(gx), py(491), px(PANEL_X + PANEL_W - 24), py(492), 0xFF354466);
        texto(ctx, Text.literal("AMPLÍA TU GUARDERÍA"), gx, 512, 13, ORO, false, false);
        String[] progreso = {"01–02   Gratis para todos", "03–05   150 LunaCoins cada una", "06         Rango Maestro", "07         Rango Leyenda"};
        for (int i = 0; i < progreso.length; i++)
            texto(ctx, Text.literal(progreso[i]), gx, 542 + i * 29, 12, 0xFFD4DFEF, false, false);
        long minutos = CrianzaService.duracionPorRango(estado == null ? 0 : estado.escalon()) / 60_000;
        texto(ctx, Text.literal("TU PRÓXIMO CICLO: " + minutos + " MIN"), gx, 664, 13, CYAN, false, false);

        // Tarjeta 3: Saldo de LunaCoins (abajo del panel izquierdo, sin solapamiento)
        long saldo = estado != null ? estado.saldoLuna() : (EstadoCliente.saldo() != null ? EstadoCliente.saldo().reportcoins() : 0L);
        int saldoY = PANEL_Y + PANEL_H - 64;
        int saldoH = 50;
        ctx.fill(px(PANEL_X + 16), py(saldoY), px(PANEL_X + PANEL_W - 16), py(saldoY + saldoH), 0x90101726);
        marco(ctx, px(PANEL_X + 16), py(saldoY), pl(PANEL_W - 32), pl(saldoH), 0xFF354466, 1);

        icono(ctx, MONEDA, PANEL_X + 26, saldoY + 9, 32);
        texto(ctx, Text.literal("SALDO DISPONIBLE"), PANEL_X + 68, saldoY + 9, 10, 0xFF8AA0C4, false, false);
        texto(ctx, Text.literal(String.format("%,d LC", saldo)), PANEL_X + 68, saldoY + 24, 16, ORO, false, true);
    }

    private void dibujarBarraRanuras(DrawContext ctx, int mx, int my, List<Red.FichaRanuraCrianza> ranuras) {
        int tabW = (PANT_W - 2 * MARGEN - 6 * 6) / 7;
        int y = PANT_Y + 12;
        for (int i = 0; i < 7; i++) {
            int x = PANT_X + MARGEN + i * (tabW + 6);
            var r = ranuras != null && i < ranuras.size() ? ranuras.get(i) : null;
            boolean abierta = r != null && r.desbloqueada();
            boolean sel = i == ranuraSeleccionada;
            boolean hover = dentro(mx, my, px(x), py(y), pl(tabW), pl(54));
            int color = r != null && r.huevoListo() ? VERDE : sel ? ORO : CYAN;
            tarjeta(ctx, x, y, tabW, 54, sel ? 0xFF263750 : 0xFF142237, sel || hover ? color : 0xFF31435D);
            texto(ctx, Text.literal("RANURA " + (i + 1)), x + tabW / 2, y + 10, 12, sel ? ORO : 0xFFE0EAF7, true, false);
            String estado = r == null ? "..." : !abierta ? (CrianzaService.esComprable(i) ? "150 LC" : i == 5 ? "Maestro" : "Leyenda")
                    : r.huevoListo() ? "Lista" : r.compatible() && r.inicioMs() > 0 ? "Criando" : "Disponible";
            texto(ctx, Text.literal(estado), x + tabW / 2, y + 30, 11, abierta ? color : TEXTO_SUAVE, true, false);
            if (r != null && abierta && r.compatible() && r.inicioMs() > 0) {
                float f = Math.min(1f, Math.max(0f, (float)(System.currentTimeMillis() - r.inicioMs()) / Math.max(1L, r.duracionMs())));
                ctx.fill(px(x + 2), py(y + 50), px(x + 2 + Math.round((tabW - 4) * f)), py(y + 53), color);
            }
        }
    }

    private void dibujarRanuraBloqueada(DrawContext ctx, int mx, int my, Red.FichaRanuraCrianza r) {
        int x = PANT_X + MARGEN, y = PANT_Y + 78, w = 465;
        tarjeta(ctx, x, y, w, 396, 0xFF142237, 0xFF31435D);
        icono(ctx, ICONO_HUEVO, x + w / 2 - 30, y + 28, 60);
        texto(ctx, Text.literal("DA ESPACIO A NUEVAS CRÍAS"), x + w / 2, y + 108, 19, 0xFFF0F5FF, true, false);
        texto(ctx, Text.literal("Ranura " + (r.indice() + 1) + " de tu guardería"), x + w / 2, y + 140, 14, TEXTO_SUAVE, true, false);
        if (CrianzaService.esComprable(r.indice())) {
            long saldo = EstadoCliente.crianza().saldoLuna();
            boolean alcanza = saldo >= CrianzaService.PRECIO_RANURA_LUNACOINS;
            texto(ctx, Text.literal("150 LunaCoins"), x + w / 2, y + 182, 25, ORO, true, false);
            boolean hover = dentro(mx, my, px(x + (w - 320) / 2), py(y + 230), pl(320), pl(46));
            tarjeta(ctx, x + (w - 320) / 2, y + 230, 320, 46, alcanza ? (hover ? 0xFF806328 : 0xFF5A4625) : 0xFF223044, alcanza ? ORO : TEXTO_SUAVE);
            String label = !alcanza ? "FALTAN " + (150 - saldo) + " LUNACOINS" : confirmarCompra == r.indice() ? "CONFIRMAR COMPRA · 150 LC" : "DESBLOQUEAR · 150 LC";
            texto(ctx, Text.literal(label), x + w / 2, y + 247, 14, alcanza ? ORO : TEXTO_SUAVE, true, false);
            texto(ctx, Text.literal("Un solo pago. Esta ranura será tuya."), x + w / 2, y + 306, 13, TEXTO_SUAVE, true, false);
        } else {
            String rango = r.indice() == 5 ? "MAESTRO" : "LEYENDA";
            texto(ctx, Text.literal("RANGO " + rango), x + w / 2, y + 192, 25, ORO, true, false);
            texto(ctx, Text.literal("Incluida mientras tengas el rango requerido."), x + w / 2, y + 248, 13, TEXTO_SUAVE, true, false);
            texto(ctx, Text.literal("Sin coste adicional de LunaCoins."), x + w / 2, y + 277, 13, CYAN, true, false);
        }
    }

    private void dibujarMesaCrianza(DrawContext ctx, int mx, int my, Red.FichaRanuraCrianza r) {
        int x = PANT_X + MARGEN, y = PANT_Y + 78;
        tarjeta(ctx, x, y, 465, 396, 0xFF111D2F, 0xFF31435D);
        progenitor(ctx, mx, my, x + 12, y + 14, true, r);
        progenitor(ctx, mx, my, x + 318, y + 14, false, r);
        tarjeta(ctx, x + 162, y + 14, 141, 230, 0xFF192D42, r.huevoListo() ? VERDE : 0xFF3C5975);
        texto(ctx, Text.literal("INCUBADORA"), x + 232, y + 30, 12, CYAN, true, false);
        icono(ctx, ICONO_HUEVO, x + 193, y + 61, 78);
        String fase = r.huevoListo() ? "¡Cría lista!" : r.compatible() && r.inicioMs() > 0 ? "En proceso" : "En espera";
        texto(ctx, Text.literal(fase), x + 232, y + 156, 14, r.huevoListo() ? VERDE : 0xFFE0EAF7, true, false);
        if (r.compatible() && r.inicioMs() > 0) {
            long restante = Math.max(0, r.duracionMs() - (System.currentTimeMillis() - r.inicioMs()));
            String tiempo = restante == 0 ? r.huevoListo() ? "Puedes recoger" : "Confirmando..." : String.format("%02d:%02d", (restante + 999) / 60000, ((restante + 999) / 1000) % 60);
            texto(ctx, Text.literal(tiempo), x + 232, y + 183, 15, CYAN, true, false);
            float f = 1f - Math.min(1f, (float)restante / Math.max(1L, r.duracionMs()));
            ctx.fill(px(x + 176), py(y + 220), px(x + 289), py(y + 225), 0xFF30455E);
            ctx.fill(px(x + 176), py(y + 220), px(x + 176 + Math.round(113 * f)), py(y + 225), CYAN);
        } else texto(ctx, Text.literal("Elige tu pareja"), x + 232, y + 187, 11, TEXTO_SUAVE, true, false);
        boolean hover = dentro(mx, my, px(x + 12), py(y + 258), pl(441), pl(46));
        tarjeta(ctx, x + 12, y + 258, 441, 46, r.huevoListo() ? hover ? 0xFF26745E : 0xFF1C5548 : 0xFF1C2D43, r.huevoListo() ? VERDE : 0xFF364B66);
        String accion = r.huevoListo() ? "RECOGER CRÍA" : "SELECCIONA DOS PROGENITORES";
        if (!r.huevoListo() && r.compatible() && r.inicioMs() > 0) {
            float progreso = Math.min(1f, Math.max(0f, (float)(System.currentTimeMillis() - r.inicioMs()) / Math.max(1L, r.duracionMs())));
            accion = "CRIANDO · " + (int)(progreso * 100) + "% · CICLO DE " + r.duracionMs() / 60_000 + " MIN";
            ctx.fill(px(x + 15), py(y + 297), px(x + 15 + Math.round(435 * progreso)), py(y + 302), CYAN);
        }
        texto(ctx, Text.literal(accion), x + 232, y + 275, 14, r.huevoListo() ? VERDE : TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(r.compatible() ? "PAREJA COMPATIBLE" : "PREPARA TU PAREJA"), x + 20, y + 324, 12, r.compatible() ? VERDE : ORO, false, false);
        textoAjustado(ctx, r.motivoIncompatible() == null ? "Elige Pokémon de tu PC o equipo." : r.motivoIncompatible(), x + 20, y + 345, 12, 425, TEXTO_SUAVE, false);
        texto(ctx, Text.literal("Cambiar o retirar reinicia el progreso de esta ranura."), x + 20, y + 371, 11, TEXTO_SUAVE, false, false);
    }

    private void progenitor(DrawContext ctx, int mx, int my, int x, int y, boolean hembra, Red.FichaRanuraCrianza r) {
        String especie = hembra ? r.madreEspecie() : r.padreEspecie();
        String uuid = hembra ? r.madreUuid() : r.padreUuid();
        boolean presente = uuid != null && !uuid.isBlank();
        boolean shiny = hembra ? r.madreShiny() : r.padreShiny();
        int color = hembra ? ROSA : CYAN;
        boolean hover = dentro(mx, my, px(x), py(y), pl(135), pl(230));
        tarjeta(ctx, x, y, 135, 230, hover ? 0xFF25364C : 0xFF19283D, hover ? color : 0xFF36485F);
        texto(ctx, Text.literal(hembra ? "HEMBRA" : "MACHO"), x + 67, y + 12, 13, color, true, false);
        texto(ctx, Text.literal("o Ditto / sin género"), x + 67, y + 32, 9, TEXTO_SUAVE, true, false);
        if (presente) {
            modelo(ctx, especie, "progenitor:" + r.indice() + ":" + hembra, shiny, x + 5, y + 53, 125, 90);
            textoAjustado(ctx, especie, x + 67, y + 151, 14, 123, 0xFFF0F5FF, true);
            texto(ctx, Text.literal("Nv. " + (hembra ? r.madreNivel() : r.padreNivel()) + (shiny ? " · Shiny" : "")), x + 67, y + 174, 11, shiny ? ORO : TEXTO_SUAVE, true, false);
            tarjeta(ctx, x + 12, y + 198, 111, 24, 0xFF352635, 0xFF754859);
            texto(ctx, Text.literal("Retirar"), x + 67, y + 205, 11, ROJO, true, false);
        } else {
            icono(ctx, hembra ? ICONO_HEMBRA : ICONO_MACHO, x + 37, y + 71, 60);
            texto(ctx, Text.literal("Elegir Pokémon"), x + 67, y + 160, 12, color, true, false);
            texto(ctx, Text.literal("Abrir PC + equipo"), x + 67, y + 187, 10, TEXTO_SUAVE, true, false);
        }
    }

    private void dibujarVisorDerecho(DrawContext ctx, int mx, int my, Red.FichaRanuraCrianza r) {
        int x = PANT_X + MARGEN + 479, y = PANT_Y + 78, w = 294;
        tarjeta(ctx, x, y, w, 396, 0xFF17263A, 0xFF31435D);
        texto(ctx, Text.literal("GUÍA DE HERENCIA"), x + 18, y + 17, 14, ORO, false, false);
        texto(ctx, Text.literal("Prepara los objetos antes de recoger"), x + 18, y + 43, 11, TEXTO_SUAVE, false, false);
        String[][] filas = {
            {"IVs", "3 estadísticas de los progenitores", "Con Lazo Destino: 5 estadísticas"},
            {"NATURALEZA", "Piedra Eterna: hereda su naturaleza", "Si ambos la llevan: 50% de cada uno"},
            {"POKÉ BALL", "Del progenitor principal", "Master y Gloria: Poké Ball normal"},
            {"ENTREGA", "Cría de nivel 1 al equipo o a la PC", "Si no hay espacio, libera una casilla"}
        };
        for (int i = 0; i < filas.length; i++) {
            int fy = y + 82 + i * 73;
            ctx.fill(px(x + 18), py(fy - 10), px(x + w - 18), py(fy - 9), 0xFF31435D);
            texto(ctx, Text.literal(filas[i][0]), x + 18, fy, 12, CYAN, false, false);
            textoAjustado(ctx, filas[i][1], x + 18, fy + 21, 12, w - 36, 0xFFE0EAF7, false);
            textoAjustado(ctx, filas[i][2], x + 18, fy + 39, 11, w - 36, TEXTO_SUAVE, false);
        }
    }

    private void modelo(DrawContext ctx, String especie, String key, boolean shiny, int x, int y, int w, int h) {
        if (especie == null || especie.isBlank()) return;
        var id = Identifier.tryParse("cobblemon:" + especie.toLowerCase(java.util.Locale.ROOT));
        if (id != null) Mascota3D.dibujarEspecie(ctx, id, "crianza:" + key, shiny ? "shiny" : "", px(x), py(y), pl(w), pl(h), 0.09f, 0f, true);
    }

    private void tarjeta(DrawContext ctx, int x, int y, int w, int h, int fondo, int borde) {
        ctx.fill(px(x), py(y), px(x + w), py(y + h), fondo);
        marco(ctx, px(x), py(y), pl(w), pl(h), borde, 1);
    }

    private void icono(DrawContext ctx, Identifier id, int x, int y, int tam) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(id, px(x), py(y), pl(tam), pl(tam), 0f, 0f, 100, 100, 100, 100);
        RenderSystem.disableBlend();
    }

    private void textoAjustado(DrawContext ctx, String valor, int x, int y, int tam, int limite, int color, boolean centrado) {
        Text t = Text.literal(valor == null ? "" : valor);
        int size = Math.min(tam, Math.max(8, (int)(limite * 9f / Math.max(1, textRenderer.getWidth(t)))));
        texto(ctx, t, x, y, size, color, centrado, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (++refresco >= 100) {
            refresco = 0;
            var estado = EstadoCliente.crianza();
            if (estado == null || estado.ranuras().stream().anyMatch(r ->
                    r.desbloqueada() && r.compatible() && !r.huevoListo() && r.inicioMs() > 0
                    && System.currentTimeMillis() - r.inicioMs() >= r.duracionMs())) {
                ClientPlayNetworking.send(new Red.PedirCrianza());
            }
        }
    }

    // ------------------------------------------------------------- SELECCIÓN MEDIANTE PC GUI

    /**
     * Abre la interfaz oficial de PC de Cobblemon configurada específicamente
     * para elegir un Pokémon como reproductor (Hembra o Macho).
     */
    private void abrirSelectorPC(boolean esHembra) {
        try {
            var storage = CobblemonClient.INSTANCE.getStorage();
            var player = client.player;
            if (player == null) return;
            var pc = storage.getPcStores().get(player.getUuid());
            var party = storage.getParty();
            if (pc == null) {
                // Solicitar sincronización de PC al servidor
                ClientPlayNetworking.send(new Red.PedirCrianza());
                player.sendMessage(Text.literal("§eSincronizando PC con el servidor... vuelve a hacer clic en un instante."), true);
                return;
            }

            pantallaVolver = this;

            PCGUIConfiguration config = new PCGUIConfiguration(
                    gui -> {
                        pantallaVolver = null;
                        gui.closeNormally(true);
                        client.setScreen(CrianzaScreen.this);
                        return Unit.INSTANCE;
                    },
                    (gui, pos, pokemon) -> {
                        if (pokemon != null) {
                            var grupos = pokemon.getForm().getEggGroups();
                            if (grupos.contains(EggGroup.UNDISCOVERED)) {
                                if (client.player != null) {
                                    client.player.sendMessage(
                                            Text.literal("§c" + pokemon.getSpecies().getName() + " no puede criar (Grupo Desconocido / Cría / Legendario)."), true);
                                    client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f, 0.6f);
                                }
                                return Unit.INSTANCE;
                            }
                            boolean esDitto = pokemon.getSpecies().getName().equalsIgnoreCase("ditto");
                            boolean esGenderless = pokemon.getGender() == Gender.GENDERLESS;
                            boolean compatible = esDitto || esGenderless || (esHembra
                                    ? pokemon.getGender() == Gender.FEMALE
                                    : pokemon.getGender() == Gender.MALE);
                            if (!compatible) {
                                if (client.player != null) {
                                    client.player.sendMessage(
                                            Text.literal("§cEste Pokémon no es compatible (requiere "
                                                    + (esHembra ? "Hembra, Ditto o Sin Género" : "Macho, Ditto o Sin Género") + ")."), true);
                                    client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f, 0.6f);
                                }
                                return Unit.INSTANCE;
                            }
                            pantallaVolver = null;

                            // Esperar el estado autoritativo; no inventar afinidad ni progreso.


                            ClientPlayNetworking.send(new Red.AccionCrianza(
                                    "asignar", ranuraSeleccionada, esHembra, pokemon.getUuid().toString()));
                            if (client.player != null) {
                                client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                            }
                            gui.closeNormally(true);
                            client.setScreen(CrianzaScreen.this);
                        }
                        return Unit.INSTANCE;
                    },
                    true,
                    pokemon -> true
            );

            int lastBox = CobblemonClient.INSTANCE.getLastPcBoxViewed();
            Set<Identifier> unseen = Collections.emptySet();
            PCGUI pcGui = new PCGUI(pc, party, config, lastBox, unseen);

            client.setScreen(pcGui);
        } catch (Throwable t) {
            pantallaVolver = null;
            LunaEternal.LOG.error("Error abriendo PCGUI para crianza", t);
        }
    }

    // ------------------------------------------------------------- INTERACCIÓN DEL RATÓN

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int rx = (int) mouseX, ry = (int) mouseY;

        // Botón Atrás
        if (dentro(rx, ry, px(PANEL_X + 18), py(PANEL_Y + 12), pl(60), pl(48))) {
            sonar();
            client.setScreen(anterior);
            return true;
        }

        // Botón Cerrar
        if (dentro(rx, ry, px(PANEL_X + PANEL_W - 18 - 80), py(PANEL_Y + 12), pl(80), pl(64))) {
            sonar();
            close();
            return true;
        }

        // Pestañas de Ranuras (0..6)
        int tabW = (PANT_W - 2 * MARGEN - 6 * 6) / 7;
        int tabH = 54;
        int startY = PANT_Y + 12;
        for (int i = 0; i < 7; i++) {
            int tabX = PANT_X + MARGEN + i * (tabW + 6);
            if (dentro(rx, ry, px(tabX), py(startY), pl(tabW), pl(tabH))) {
                sonar();
                ranuraSeleccionada = i;
                confirmarCompra = -1;
                return true;
            }
        }

        var estado = EstadoCliente.crianza();
        var ranuras = estado != null ? estado.ranuras() : null;
        var r = (ranuras != null && ranuraSeleccionada < ranuras.size()) ? ranuras.get(ranuraSeleccionada) : null;

        if (r != null) {
            int benchX = PANT_X + MARGEN;
            int benchY = PANT_Y + 78;
            int benchW = 465;

            if (!r.desbloqueada()) {
                // Expansiones permanentes: ranuras 3, 4 y 5.
                if (CrianzaService.esComprable(r.indice())) {
                    int btnW = 320, btnH = 46;
                    int btnX = benchX + (benchW - btnW) / 2, btnY = benchY + 230;
                    if (dentro(rx, ry, px(btnX), py(btnY), pl(btnW), pl(btnH))) {
                        sonar();
                        if (estado.saldoLuna() < CrianzaService.PRECIO_RANURA_LUNACOINS) return true;
                        if (confirmarCompra != r.indice()) { confirmarCompra = r.indice(); return true; }
                        confirmarCompra = -1;
                        enviarAccion("comprar_ranura", r.indice(), false, "");
                        return true;
                    }
                }
            } else {
                // Casilla Hembra
                int hx = benchX + 12, hy = benchY + 14, hw = 135, hh = 230;
                if (dentro(rx, ry, px(hx), py(hy), pl(hw), pl(hh))) {
                    if (r.madreUuid() != null && !r.madreUuid().isBlank()) {
                        int rtrY = hy + hh - 32;
                        int rtrW = hw - 24, rtrH = 24, rtrX = hx + 12;
                        if (dentro(rx, ry, px(rtrX), py(rtrY), pl(rtrW), pl(rtrH))) {
                            sonar();

                            enviarAccion("retirar", r.indice(), true, "");
                            return true;
                        }
                    }
                    sonar();
                    abrirSelectorPC(true);
                    return true;
                }

                // Casilla Macho
                int mxX = benchX + 318, myY = benchY + 14, mw = 135, mh = 230;
                if (dentro(rx, ry, px(mxX), py(myY), pl(mw), pl(mh))) {
                    if (r.padreUuid() != null && !r.padreUuid().isBlank()) {
                        int rtrY = myY + mh - 32;
                        int rtrW = mw - 24, rtrH = 24, rtrX = mxX + 12;
                        if (dentro(rx, ry, px(rtrX), py(rtrY), pl(rtrW), pl(rtrH))) {
                            sonar();

                            enviarAccion("retirar", r.indice(), false, "");
                            return true;
                        }
                    }
                    sonar();
                    abrirSelectorPC(false);
                    return true;
                }

                // Botón Reclamar Huevo
                if (r.huevoListo()) {
                    int btnW = 441, btnH = 46;
                    int btnX = benchX + 12, btnY = benchY + 258;
                    if (dentro(rx, ry, px(btnX), py(btnY), pl(btnW), pl(btnH))) {
                        client.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
                        enviarAccion("reclamar_huevo", r.indice(), false, "");
                        return true;
                    }
                }
            }

        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void enviarAccion(String accion, int indice, boolean hembra, String uuid) {
        long ahora = System.currentTimeMillis();
        if (ahora - ultimoEnvio < 600) return;
        ultimoEnvio = ahora;
        ClientPlayNetworking.send(new Red.AccionCrianza(accion, indice, hembra, uuid));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { client.setScreen(anterior); }

    // ------------------------------------------------------------- UTILIDADES DE DIBUJADO

    private void sonar() {
        if (client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.0f);
        }
    }

    private void marco(DrawContext ctx, int x, int y, int w, int h, int color, int grosor) {
        ctx.fill(x, y, x + w, y + grosor, color);
        ctx.fill(x, y + h - grosor, x + w, y + h, color);
        ctx.fill(x, y, x + grosor, y + h, color);
        ctx.fill(x + w - grosor, y, x + w, y + h, color);
    }

    private void texto(DrawContext ctx, Text t, int x, int y, int tam, int color, boolean centrado, boolean sombra) {
        float factor = (float) tam / 9f * k;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(px(x), py(y), 0);
        ms.scale(factor, factor, 1f);
        int tx = centrado ? -textRenderer.getWidth(t) / 2 : 0;
        ctx.drawText(textRenderer, t, tx, 0, color, sombra);
        ms.pop();
    }

    private void textoDerecha(DrawContext ctx, Text t, int xRight, int y, int tam, int color, boolean sombra) {
        float factor = (float) tam / 9f * k;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(px(xRight), py(y), 0);
        ms.scale(factor, factor, 1f);
        int tw = textRenderer.getWidth(t);
        ctx.drawText(textRenderer, t, -tw, 0, color, sombra);
        ms.pop();
    }
}
