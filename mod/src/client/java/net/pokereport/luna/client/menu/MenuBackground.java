package net.pokereport.luna.client.menu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class MenuBackground {
    public static final Identifier TEXTURE = Identifier.of("lunaeternal", "textures/gui/title/background.png");
    private static final int TEX_W = 3840;
    private static final int TEX_H = 2160;

    private MenuBackground() {}

    public static void render(DrawContext context, int width, int height) {
        float scale = Math.max((float) width / (float) TEX_W, (float) height / (float) TEX_H);
        int drawW = (int) Math.ceil((float) TEX_W * scale);
        int drawH = (int) Math.ceil((float) TEX_H * scale);
        int x = (width - drawW) / 2;
        int y = (height - drawH) / 2;
        context.drawTexture(TEXTURE, x, y, drawW, drawH, 0.0F, 0.0F, TEX_W, TEX_H, TEX_W, TEX_H);
    }

    public static void render(DrawContext context) {
        render(context, context.getScaledWindowWidth(), context.getScaledWindowHeight());
    }
}
