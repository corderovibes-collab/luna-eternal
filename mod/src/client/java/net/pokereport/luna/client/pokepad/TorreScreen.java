package net.pokereport.luna.client.pokepad;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;

public class TorreScreen extends Screen {

    private static final Identifier CERRAR = Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");

    private final Screen anterior;
    private float k = 1f;
    private int x0, y0;

    private static final int PANEL_W = 400;
    private static final int PANEL_H = 260;

    public TorreScreen(Screen anterior) {
        super(Text.literal("Torre de Batalla"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        super.init();
        this.k = this.height / 1080f; // Escala base
        this.x0 = (this.width - pl(PANEL_W)) / 2;
        this.y0 = (this.height - pl(PANEL_H)) / 2;
    }

    private int pl(int pixeles) {
        return Math.round(pixeles * k);
    }

    private int px(int x) {
        return x0 + Math.round(x * k);
    }

    private int py(int y) {
        return y0 + Math.round(y * k);
    }

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        // Fondo semi-transparente
        ctx.fillGradient(0, 0, width, height, 0x88000000, 0xDD000000);

        // Caja principal
        ctx.fill(px(0), py(0), px(PANEL_W), py(PANEL_H), 0xFF1E2430);
        ctx.drawBorder(px(0), py(0), pl(PANEL_W), pl(PANEL_H), 0xFF4A566E);

        // Cabecera
        ctx.fill(px(0), py(0), px(PANEL_W), py(40), 0xFF2D3545);
        ctx.drawBorder(px(0), py(0), pl(PANEL_W), pl(40), 0xFF4A566E);
        
        // Título
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Torre de Batalla", px(PANEL_W / 2), py(12), 0xFFFFB900);

        // Botones de Modos
        dibujarBotonModo(ctx, rx, ry, 30, 70, "Combate 1vs1", "Lucha uno contra uno. Escala la torre.", 0);
        dibujarBotonModo(ctx, rx, ry, 30, 130, "Combate 2vs2", "Lucha doble. Estrategia al máximo.", 1);
        dibujarBotonModo(ctx, rx, ry, 30, 190, "Aleatorio (Random)", "Equipos al azar nivel 100. ¡Prueba tu suerte!", 2);

        // Botón Cerrar (Esquina superior derecha)
        dibujarTextura(ctx, CERRAR, px(PANEL_W - 36), py(8), pl(24), pl(24), 24, 24);

        super.render(ctx, rx, ry, delta);
    }

    private void dibujarBotonModo(DrawContext ctx, int rx, int ry, int bx, int by, String titulo, String desc, int modoId) {
        int w = PANEL_W - 60;
        int h = 45;
        boolean hover = dentro(rx, ry, px(bx), py(by), pl(w), pl(h));

        ctx.fill(px(bx), py(by), px(bx + w), py(by + h), hover ? 0xFF3E4A61 : 0xFF2D3545);
        ctx.drawBorder(px(bx), py(by), pl(w), pl(h), hover ? 0xFFFFB900 : 0xFF4A566E);

        ctx.drawTextWithShadow(this.textRenderer, titulo, px(bx + 15), py(by + 10), hover ? 0xFFFFFFFF : 0xFFAAAAAA);
        ctx.drawTextWithShadow(this.textRenderer, desc, px(bx + 15), py(by + 25), 0xFF888888);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) return super.mouseClicked(mx, my, boton);
        int rx = (int) mx, ry = (int) my;

        if (dentro(rx, ry, px(PANEL_W - 36), py(8), pl(24), pl(24))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            close();
            return true;
        }

        // Modos
        if (dentro(rx, ry, px(30), py(70), pl(PANEL_W - 60), pl(45))) {
            seleccionarModo(0);
            return true;
        }
        if (dentro(rx, ry, px(30), py(130), pl(PANEL_W - 60), pl(45))) {
            seleccionarModo(1);
            return true;
        }
        if (dentro(rx, ry, px(30), py(190), pl(PANEL_W - 60), pl(45))) {
            seleccionarModo(2);
            return true;
        }

        return super.mouseClicked(mx, my, boton);
    }

    private void seleccionarModo(int modo) {
        sonar(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.2f);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.pokereport.luna.net.Red.EntrarTorreBatalla(modo));
        close();
    }

    private void sonar(net.minecraft.sound.SoundEvent sonido, float tono) {
        if (client != null && client.player != null) {
            client.player.playSound(sonido, 0.7f, tono);
        }
    }

    private boolean dentro(int rx, int ry, int x, int y, int w, int h) {
        return rx >= x && rx < x + w && ry >= y && ry < y + h;
    }

    private static void dibujarTextura(DrawContext ctx, Identifier tex, int x, int y, int w, int h, int natW, int natH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
    }
}
