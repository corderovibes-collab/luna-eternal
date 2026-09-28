package net.pokereport.luna.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.quienesepokemon.QuienEsEsePokemonNet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** HUD no modal: deja el chat libre, que es donde se participa. */
public final class QuienEsEsePokemonHud {
    private static final Identifier INICIO = Identifier.of("lunaeternal", "quien_es_ese_pokemon.inicio");
    private static long rondaId;
    private static int dex;
    private static long terminaEn;
    private static String ganador = "";
    private static long premio;
    private static boolean revelado;
    private static boolean oculto;
    private static boolean registrado;
    private static long mostradoDesde;
    private static final Path CONFIG = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().resolve("lunaeternal-eventos.properties");

    private QuienEsEsePokemonHud() {}

    public static void registrar() {
        if (registrado) return;
        registrado = true;
        cargarPreferencia();
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> dibujar(ctx));
    }

    public static void ronda(QuienEsEsePokemonNet.Ronda paquete) {
        rondaId = paquete.id(); dex = paquete.dex(); terminaEn = paquete.terminaEn(); revelado = false; ganador = "";
        mostradoDesde = System.currentTimeMillis();
        var cliente = MinecraftClient.getInstance();
        if (cliente.getSoundManager() != null) cliente.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvent.of(INICIO), 0.82f));
        if (cliente.player != null) {
            cliente.player.sendMessage(Text.literal(oculto
                    ? "§dEvento activo. §fF8 §7muestra la interfaz; también puedes responder por chat."
                    : "§7Pulsa §fF8 §7para ocultar o volver a mostrar este evento."), true);
        }
    }

    public static void revelacion(QuienEsEsePokemonNet.Revelacion paquete) {
        if (paquete.id() != rondaId) return;
        dex = paquete.dex(); ganador = paquete.ganador(); premio = paquete.premio(); terminaEn = paquete.ocultarEn(); revelado = true;
        mostradoDesde = System.currentTimeMillis();
    }
    public static boolean alternar() {
        oculto = !oculto;
        guardarPreferencia();
        return !oculto;
    }

    /** El estado de una ronda pertenece a una conexión, nunca a la siguiente. */
    public static void olvidarRonda() {
        rondaId = 0L;
        dex = 0;
        terminaEn = 0L;
        ganador = "";
        premio = 0L;
        revelado = false;
        mostradoDesde = 0L;
    }

    private static void cargarPreferencia() {
        try {
            if (!Files.isRegularFile(CONFIG)) return;
            Properties p = new Properties();
            try (var in = Files.newInputStream(CONFIG)) { p.load(in); }
            oculto = !Boolean.parseBoolean(p.getProperty("quienEsEsePokemon.visible", "true"));
        } catch (Exception ignored) { oculto = false; }
    }

    private static void guardarPreferencia() {
        try {
            Files.createDirectories(CONFIG.getParent());
            Properties p = new Properties();
            p.setProperty("quienEsEsePokemon.visible", Boolean.toString(!oculto));
            try (var out = Files.newOutputStream(CONFIG)) { p.store(out, "Preferencias visuales Luna Eternal"); }
        } catch (Exception ignored) { }
    }

    private static void dibujar(DrawContext ctx) {
        if (oculto || dex < 1 || System.currentTimeMillis() >= terminaEn) return;
        MinecraftClient cliente = MinecraftClient.getInstance();
        if (cliente.options.hudHidden) return;
        long ahora = System.currentTimeMillis();
        int ancho = ctx.getScaledWindowWidth(), alto = ctx.getScaledWindowHeight();
        int panelW = Math.min(310, ancho - 20), panelH = Math.min(244, alto - 32);
        int x = (ancho - panelW) / 2;
        double entrada = Math.min(1.0, (ahora - mostradoDesde) / 420.0);
        entrada = 1.0 - Math.pow(1.0 - entrada, 3.0);
        int yFinal = Math.max(10, Math.min(22, (alto - panelH) / 5));
        int y = yFinal - (int) ((1.0 - entrada) * (panelH + 30));
        int pulso = 2 + (int) (2 * (1 + Math.sin(ahora / 180.0)));
        int acento = revelado ? 0xFFFFCF45 : 0xFF35D9FF;

        // Halo, sombra y cristal oscuro: legible sin tapar por completo el mundo.
        ctx.fill(x - pulso - 2, y - pulso - 2, x + panelW + pulso + 2, y + panelH + pulso + 2,
                revelado ? 0x33FFD33D : 0x332BDFFF);
        ctx.fill(x + 5, y + 7, x + panelW + 6, y + panelH + 8, 0x88000000);
        ctx.fill(x - 2, y - 2, x + panelW + 2, y + panelH + 2, acento);
        ctx.fill(x, y, x + panelW, y + panelH, 0xEF090D1E);
        ctx.fill(x + 2, y + 2, x + panelW - 2, y + 34, revelado ? 0xE75A3D08 : 0xE71A2458);
        ctx.fill(x + 2, y + 34, x + panelW - 2, y + 36, acento);

        // Esquinas tecnológicas al estilo del PokePad.
        for (int lado : new int[]{0, 1}) {
            int cx = lado == 0 ? x + 7 : x + panelW - 23;
            ctx.fill(cx, y + 7, cx + 16, y + 9, 0xFFFFFFFF);
            ctx.fill(cx, y + 7, cx + 2, y + 18, 0xFFFFFFFF);
            ctx.fill(cx, y + panelH - 9, cx + 16, y + panelH - 7, acento);
        }

        String titulo = revelado ? "¡POKÉMON DESCUBIERTO!" : "¿QUIÉN ES ESTE POKÉMON?";
        int tw = cliente.textRenderer.getWidth(titulo);
        ctx.drawTextWithShadow(cliente.textRenderer, Text.literal(titulo), x + (panelW - tw) / 2, y + 12,
                revelado ? 0xFFFFE07A : 0xFFFFFFFF);
        String ruta = String.format(java.util.Locale.ROOT,
                "textures/quien_es_ese_pokemon/%s/%03d.png", revelado ? "revelados" : "siluetas", dex);
        Identifier imagen = Identifier.of("lunaeternal", ruta);
        int tam = Math.min(170, panelH - 72), ix = x + (panelW - tam) / 2, iy = y + 43;
        int brillo = 18 + (int) (10 * (1 + Math.sin(ahora / 140.0)));
        ctx.fill(ix - 7, iy - 7, ix + tam + 7, iy + tam + 7,
                revelado ? 0xAA5C4200 : 0xAA071027);
        ctx.fill(ix - 3, iy - 3, ix + tam + 3, iy + tam + 3, acento);
        ctx.fill(ix, iy, ix + tam, iy + tam, 0xF0030610);
        ctx.drawTexture(imagen, ix, iy, tam, tam, 0f, 0f, 475, 475, 475, 475);
        // Chispas ligeras, deterministas y sin crear objetos por fotograma.
        for (int i = 0; i < 10; i++) {
            double fase = ahora / 520.0 + i * 2.31;
            int px = x + panelW / 2 + (int) (Math.cos(fase) * (tam / 2 + 13));
            int py = iy + tam / 2 + (int) (Math.sin(fase * 1.17) * (tam / 2 + 9));
            int s = 1 + ((i + brillo) & 1);
            ctx.fill(px, py, px + s, py + s, acento);
        }
        String pie;
        if (revelado) pie = premio > 0 ? "§a★ §f" + ganador + " §aganó §6$" + premio : "§7Nadie acertó esta vez";
        else pie = "§fResponde en el chat  §8•  §e" + Math.max(0, (terminaEn - ahora + 999) / 1000) + "s  §8•  §7F8 ocultar";
        int pw = cliente.textRenderer.getWidth(Text.literal(pie));
        int pieY = y + panelH - 19;
        ctx.drawTextWithShadow(cliente.textRenderer, Text.literal(pie), x + (panelW - pw) / 2, pieY, 0xFFFFFFFF);
        if (!revelado) {
            int total = panelW - 24;
            int restante = (int) Math.max(0, Math.min(total, (terminaEn - ahora) * total / 45_000L));
            ctx.fill(x + 12, y + panelH - 7, x + panelW - 12, y + panelH - 4, 0xFF161B31);
            ctx.fill(x + 12, y + panelH - 7, x + 12 + restante, y + panelH - 4, acento);
        }
    }
}
