package net.pokereport.luna.client.pokepad;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TorreScreen extends Screen {

    private static final Identifier CERRAR = Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier TEX_1VS1 = Identifier.of("lunaeternal", "textures/gui/pokepad/torre_modo_1vs1.png");
    private static final Identifier TEX_2VS2 = Identifier.of("lunaeternal", "textures/gui/pokepad/torre_modo_2vs2.png");
    private static final Identifier TEX_RANDOM = Identifier.of("lunaeternal", "textures/gui/pokepad/torre_modo_aleatorio.png");

    private final Screen anterior;
    private int x0, y0;
    private int panelW, panelH;

    public TorreScreen(Screen anterior) {
        super(Text.literal("Torre de Batalla"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        super.init();
        // Dynamic sizing for Minecraft's GUI scale
        this.panelW = Math.min(this.width - 20, 600); // 600 is wide enough for 3 cards
        this.panelH = Math.min(this.height - 20, 280);
        this.x0 = (this.width - this.panelW) / 2;
        this.y0 = (this.height - this.panelH) / 2;
    }

    private int px(int x) { return x0 + x; }
    private int py(int y) { return y0 + y; }

    @Override
    public void renderInGameBackground(DrawContext context) {
        // OVERRIDE: Do not draw the dark gradient! 
        // By doing nothing here, we keep the blur from renderBackground but avoid the darkening.
    }

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        this.renderBackground(ctx, rx, ry, delta);
        
        ctx.fill(px(0), py(0), px(panelW), py(panelH), 0xFF1E2430);
        ctx.drawBorder(px(0), py(0), panelW, panelH, 0xFF4A566E);

        // Header
        ctx.fill(px(0), py(0), px(panelW), py(30), 0xFF2D3545);
        ctx.drawBorder(px(0), py(0), panelW, 30, 0xFF4A566E);
        
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Torre de Batalla", px(panelW / 2), py(10), 0xFFFFB900);
        dibujarTextura(ctx, CERRAR, px(panelW - 24), py(3), 24, 24, 24, 24);

        int gap = 15;
        int cardW = (panelW - (gap * 4)) / 3;
        int cardH = panelH - 30 - (gap * 2);
        
        int bx = gap;
        int by = 30 + gap;
        
        dibujarTarjeta(ctx, rx, ry, bx, by, cardW, cardH, TEX_1VS1, "Combate 1vs1", "Lucha uno contra uno.", 0);
        bx += cardW + gap;
        dibujarTarjeta(ctx, rx, ry, bx, by, cardW, cardH, TEX_2VS2, "Combate 2vs2", "Lucha doble 2vs2.", 1);
        bx += cardW + gap;
        dibujarTarjeta(ctx, rx, ry, bx, by, cardW, cardH, TEX_RANDOM, "Aleatorio", "Equipos al azar nivel 100.", 2);
    }

    private void dibujarTarjeta(DrawContext ctx, int rx, int ry, int bx, int by, int w, int h, Identifier tex, String titulo, String desc, int modoId) {
        boolean hover = dentro(rx, ry, px(bx), py(by), w, h);

        ctx.fill(px(bx), py(by), px(bx + w), py(by + h), hover ? 0xFF3E4A61 : 0xFF2D3545);
        ctx.drawBorder(px(bx), py(by), w, h, hover ? 0xFFFFB900 : 0xFF4A566E);

        int pad = 5;
        int imgW = w - (pad * 2);
        int imgH = (int)(imgW * (192.0 / 256.0));
        
        dibujarTextura(ctx, tex, px(bx + pad), py(by + pad), imgW, imgH, 256, 256);
        
        ctx.drawBorder(px(bx + pad - 1), py(by + pad - 1), imgW + 2, imgH + 2, hover ? 0xFFFFB900 : 0xFF111111);

        int ty = by + pad + imgH + 10;
        ctx.drawCenteredTextWithShadow(this.textRenderer, titulo, px(bx + w / 2), py(ty), hover ? 0xFFFFFFFF : 0xFFFFB900);
        
        int descY = ty + 12;
        ctx.drawTextWrapped(this.textRenderer, net.minecraft.text.StringVisitable.plain(desc), px(bx + pad + 2), py(descY), w - (pad * 2) - 4, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) return super.mouseClicked(mx, my, boton);
        int rx = (int) mx, ry = (int) my;

        if (dentro(rx, ry, px(panelW - 24), py(3), 24, 24)) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            close();
            return true;
        }

        int gap = 15;
        int cardW = (panelW - (gap * 4)) / 3;
        int h = panelH - 30 - (gap * 2);
        
        int bx = gap;
        if (dentro(rx, ry, px(bx), py(30 + gap), cardW, h)) { seleccionarModo(0); return true; }
        bx += cardW + gap;
        if (dentro(rx, ry, px(bx), py(30 + gap), cardW, h)) { seleccionarModo(1); return true; }
        bx += cardW + gap;
        if (dentro(rx, ry, px(bx), py(30 + gap), cardW, h)) { seleccionarModo(2); return true; }

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
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
    }
}