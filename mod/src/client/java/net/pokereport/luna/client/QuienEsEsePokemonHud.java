package net.pokereport.luna.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.net.Red;
import net.pokereport.luna.quienesepokemon.QuienEsEsePokemonNet;

/** HUD no modal: deja el chat libre, que es donde se participa. */
public final class QuienEsEsePokemonHud {
    private static final Identifier INICIO = Identifier.of("lunaeternal", "quien_es_ese_pokemon.inicio");
    private static long rondaId;
    private static int dex;
    private static long terminaEn;
    private static String ganador = "";
    private static long premio;
    private static boolean revelado;
    private static boolean registrado;

    private QuienEsEsePokemonHud() {}

    public static void registrar() {
        if (registrado) return;
        registrado = true;
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> dibujar(ctx));
    }

    public static void ronda(QuienEsEsePokemonNet.Ronda paquete) {
        rondaId = paquete.id(); dex = paquete.dex(); terminaEn = paquete.terminaEn(); revelado = false; ganador = "";
        var cliente = MinecraftClient.getInstance();
        if (cliente.getSoundManager() != null) cliente.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvent.of(INICIO), 0.82f));
    }

    public static void revelacion(QuienEsEsePokemonNet.Revelacion paquete) {
        if (paquete.id() != rondaId) return;
        dex = paquete.dex(); ganador = paquete.ganador(); premio = paquete.premio(); terminaEn = paquete.ocultarEn(); revelado = true;
    }

    private static void dibujar(DrawContext ctx) {
        if (dex < 1 || System.currentTimeMillis() >= terminaEn) return;
        MinecraftClient cliente = MinecraftClient.getInstance();
        if (cliente.options.hudHidden) return;
        int ancho = ctx.getScaledWindowWidth();
        int panelW = Math.min(276, ancho - 24), panelH = 225;
        int x = (ancho - panelW) / 2, y = 18;
        ctx.fill(x - 2, y - 2, x + panelW + 2, y + panelH + 2, 0xD8120D22);
        ctx.fill(x, y, x + panelW, y + panelH, 0xED21183D);
        ctx.fill(x + 1, y + 1, x + panelW - 1, y + 30, 0xF036175B);
        String titulo = revelado ? "¡ES " + ganador.toUpperCase(java.util.Locale.ROOT) + "!" : "¿QUIÉN ES ESTE POKÉMON?";
        int tw = cliente.textRenderer.getWidth(titulo);
        ctx.drawTextWithShadow(cliente.textRenderer, Text.literal(titulo), x + (panelW - tw) / 2, y + 10, revelado ? 0xFFF6D56A : 0xFFFFFFFF);
        String ruta = String.format(java.util.Locale.ROOT,
                "textures/quien_es_ese_pokemon/%s/%03d.png", revelado ? "revelados" : "siluetas", dex);
        Identifier imagen = Identifier.of("lunaeternal", ruta);
        int tam = 158, ix = x + (panelW - tam) / 2, iy = y + 37;
        ctx.fill(ix - 4, iy - 4, ix + tam + 4, iy + tam + 4, 0xB8000000);
        ctx.drawTexture(imagen, ix, iy, tam, tam, 0f, 0f, 475, 475, 475, 475);
        String pie;
        if (revelado) pie = premio > 0 ? "§a" + ganador + " ganó §6$" + premio : "§7Nadie acertó esta vez";
        else pie = "§fEscribe el nombre en el chat  §8•  §e" + Math.max(0, (terminaEn - System.currentTimeMillis() + 999) / 1000) + "s";
        int pw = cliente.textRenderer.getWidth(Text.literal(pie));
        ctx.drawTextWithShadow(cliente.textRenderer, Text.literal(pie), x + (panelW - pw) / 2, y + 202, 0xFFFFFFFF);
    }
}
