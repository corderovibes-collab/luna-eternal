package net.pokereport.luna.client.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class IconLinkButton extends PressableWidget {
    private static final ButtonTextures SPRITES = new ButtonTextures(
            Identifier.ofVanilla("widget/button"),
            Identifier.ofVanilla("widget/button_disabled"),
            Identifier.ofVanilla("widget/button_highlighted")
    );
    private static final int ICON_PADDING = 4;
    private final Identifier icon;
    private final int textureSize;
    private final Runnable onClick;

    public IconLinkButton(int x, int y, int size, Identifier icon, int textureSize, Text message, Runnable onClick) {
        super(x, y, size, size, message);
        this.icon = icon;
        this.textureSize = textureSize;
        this.onClick = onClick;
    }

    @Override
    public void onPress() {
        if (this.onClick != null) {
            this.onClick.run();
        }
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawGuiTexture(SPRITES.get(this.active, this.isSelected()), this.getX(), this.getY(), this.getWidth(), this.getHeight());
        RenderSystem.enableBlend();
        int iconX = this.getX() + ICON_PADDING;
        int iconY = this.getY() + ICON_PADDING;
        int iconW = this.getWidth() - (ICON_PADDING * 2);
        int iconH = this.getHeight() - (ICON_PADDING * 2);
        context.drawTexture(this.icon, iconX, iconY, iconW, iconH, 0.0F, 0.0F, this.textureSize, this.textureSize, this.textureSize, this.textureSize);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
