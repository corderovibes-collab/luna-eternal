package net.pokereport.luna.client.heal;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.pokereport.luna.net.Red;
import net.minecraft.client.sound.PositionedSoundInstance;

public class CentroPokemonScreen extends Screen {

    private final Screen padre;
    private long tickInicio;
    private static final String TEXTO = "¡Hola! Bienvenido al Centro Pokémon. ¿Te gustaría que curase a tus Pokémon? Podría llevarme un momento, pero te los dejaré como nuevos.";
    private int ultimoChar;

    public CentroPokemonScreen(Screen padre) {
        super(Text.literal("Centro Pokémon"));
        this.padre = padre;
    }

    @Override
    protected void init() {
        this.tickInicio = System.currentTimeMillis();
        this.ultimoChar = 0;
    }

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        super.render(ctx, rx, ry, delta);

        int w = this.width;
        int h = this.height;

        int pW = Math.min(600, (int) (w * 0.8));
        int pH = 120;
        int pX = (w - pW) / 2;
        int pY = h - pH - 40;

        // Fondo oscuro
        ctx.fill(0, 0, w, h, 0x99000000);

        // Caja de diálogo
        ctx.fill(pX, pY, pX + pW, pY + pH, 0xEE1A1A1A);
        ctx.fill(pX + 2, pY + 2, pX + pW - 2, pY + pH - 2, 0xEE2A2A2A);

        long ahora = System.currentTimeMillis();
        int chars = (int) ((ahora - tickInicio) / 30);
        
        if (chars > TEXTO.length()) chars = TEXTO.length();

        if (chars > ultimoChar && chars < TEXTO.length()) {
            if (chars % 2 == 0 && this.client != null) {
                this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 2.0f, 0.3f));
            }
            ultimoChar = chars;
        }

        String t = TEXTO.substring(0, chars);
        
        // Título Enfermera Joy
        ctx.drawTextWithShadow(this.textRenderer, "Enfermera Joy", pX + 20, pY + 15, 0xFFFFAA);
        
        ctx.drawTextWrapped(this.textRenderer, net.minecraft.text.StringVisitable.plain(t), 
                pX + 20, pY + 40, pW - 40, 0xFFFFFF);

        if (chars >= TEXTO.length()) {
            boolean sobreSi = rx >= pX + pW - 220 && rx <= pX + pW - 120 && ry >= pY + pH - 40 && ry <= pY + pH - 15;
            boolean sobreNo = rx >= pX + pW - 110 && rx <= pX + pW - 10 && ry >= pY + pH - 40 && ry <= pY + pH - 15;

            ctx.fill(pX + pW - 220, pY + pH - 40, pX + pW - 120, pY + pH - 15, sobreSi ? 0xFFFF5555 : 0xFF555555);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "Sí, por favor", pX + pW - 170, pY + pH - 32, 0xFFFFFF);

            ctx.fill(pX + pW - 110, pY + pH - 40, pX + pW - 10, pY + pH - 15, sobreNo ? 0xFFFF5555 : 0xFF555555);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "No, gracias", pX + pW - 60, pY + pH - 32, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        long ahora = System.currentTimeMillis();
        int chars = (int) ((ahora - tickInicio) / 30);

        if (chars < TEXTO.length()) {
            tickInicio = ahora - (TEXTO.length() * 30L);
            if (this.client != null) {
                this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 2.0f, 0.3f));
            }
            return true;
        }

        int w = this.width;
        int h = this.height;
        int pW = Math.min(600, (int) (w * 0.8));
        int pH = 120;
        int pX = (w - pW) / 2;
        int pY = h - pH - 40;

        boolean sobreSi = mx >= pX + pW - 220 && mx <= pX + pW - 120 && my >= pY + pH - 40 && my <= pY + pH - 15;
        boolean sobreNo = mx >= pX + pW - 110 && mx <= pX + pW - 10 && my >= pY + pH - 40 && my <= pY + pH - 15;

        if (sobreSi) {
            ClientPlayNetworking.send(new Red.ConfirmarCuraCentro());
            if (this.client != null) this.client.setScreen(padre);
            return true;
        }

        if (sobreNo) {
            if (this.client != null) this.client.setScreen(padre);
            return true;
        }

        return super.mouseClicked(mx, my, boton);
    }
}
