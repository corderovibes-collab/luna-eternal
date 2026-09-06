package net.pokereport.luna.client.pokepad;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TorreScreen extends Screen {

    private static final Identifier CERRAR = Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier TEX_1VS1 = Identifier.of("lunaeternal", "textures/gui/pokepad/torre_modo_1vs1.png");
    private static final Identifier TEX_2VS2 = Identifier.of("lunaeternal", "textures/gui/pokepad/torre_modo_2vs2.png");
    private static final Identifier TEX_RANDOM = Identifier.of("lunaeternal", "textures/gui/pokepad/torre_modo_aleatorio.png");

    private final Screen anterior;
    private float k = 1f;
    private int x0, y0;

    private static final int PANEL_W = 1200;
    private static final int PANEL_H = 700;

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
        MatrixStack mt = ctx.getMatrices();
        mt.push();
        mt.translate(px(PANEL_W / 2), py(12), 0);
        mt.scale(1.5f, 1.5f, 1.0f);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Torre de Batalla", 0, 0, 0xFFFFB900);
        mt.pop();

        // Botones de Modos (Tarjetas)
        int gap = 40;
        int cardW = (PANEL_W - (gap * 4)) / 3; // 3 tarjetas con gaps
        int bx = gap;
        int by = 80;
        
        dibujarTarjeta(ctx, rx, ry, bx, by, cardW, TEX_1VS1, "Combate 1vs1", "Lucha uno contra uno. Escala la torre enfrentando entrenadores cada vez mas fuertes.", 0);
        bx += cardW + gap;
        dibujarTarjeta(ctx, rx, ry, bx, by, cardW, TEX_2VS2, "Combate 2vs2", "Lucha doble 2vs2. Estrategia al maximo.", 1);
        bx += cardW + gap;
        dibujarTarjeta(ctx, rx, ry, bx, by, cardW, TEX_RANDOM, "Aleatorio (Random)", "Equipos al azar nivel 100. Pon a prueba tu suerte y adaptabilidad.", 2);

        // Botón Cerrar
        dibujarTextura(ctx, CERRAR, px(PANEL_W - 36), py(8), pl(24), pl(24), 24, 24);

        super.render(ctx, rx, ry, delta);
    }

    private void dibujarTarjeta(DrawContext ctx, int rx, int ry, int bx, int by, int w, Identifier tex, String titulo, String desc, int modoId) {
        int h = 550; // alto de tarjeta
        boolean hover = dentro(rx, ry, px(bx), py(by), pl(w), pl(h));

        // Fondo tarjeta
        ctx.fill(px(bx), py(by), px(bx + w), py(by + h), hover ? 0xFF3E4A61 : 0xFF2D3545);
        ctx.drawBorder(px(bx), py(by), pl(w), pl(h), hover ? 0xFFFFB900 : 0xFF4A566E);

        // Imagen (16:9 aprox, ancho completo - padding)
        int pad = 10;
        int imgW = w - (pad * 2);
        int imgH = (int)(imgW * (192.0 / 256.0));
        
        // Renderizar la textura
        dibujarTextura(ctx, tex, px(bx + pad), py(by + pad), pl(imgW), pl(imgH), 256, 256);
        
        if (hover) {
            ctx.drawBorder(px(bx + pad - 1), py(by + pad - 1), pl(imgW + 2), pl(imgH + 2), 0xFFFFB900);
        } else {
            ctx.drawBorder(px(bx + pad - 1), py(by + pad - 1), pl(imgW + 2), pl(imgH + 2), 0xFF111111);
        }

        int ty = by + pad + imgH + 30;
        
        // Titulo a escala doble (2.0)
        MatrixStack matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(px(bx + w / 2), py(ty), 0);
        matrices.scale(2.0f, 2.0f, 1.0f);
        ctx.drawCenteredTextWithShadow(this.textRenderer, titulo, 0, 0, hover ? 0xFFFFFFFF : 0xFFFFB900);
        matrices.pop();
        
        int descY = ty + 40;
        // Descripcion envuelta (multilinea)
        ctx.drawTextWrapped(this.textRenderer, net.minecraft.text.StringVisitable.plain(desc), px(bx + pad + 10), py(descY), pl(w - (pad * 2) - 20), 0xFFAAAAAA);
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

        int gap = 40;
        int cardW = (PANEL_W - (gap * 4)) / 3;
        int h = 550;
        
        int bx = gap;
        if (dentro(rx, ry, px(bx), py(80), pl(cardW), pl(h))) { seleccionarModo(0); return true; }
        bx += cardW + gap;
        if (dentro(rx, ry, px(bx), py(80), pl(cardW), pl(h))) { seleccionarModo(1); return true; }
        bx += cardW + gap;
        if (dentro(rx, ry, px(bx), py(80), pl(cardW), pl(h))) { seleccionarModo(2); return true; }

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