package net.pokereport.luna.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.text.Text;
import net.pokereport.luna.client.menu.MenuBackground;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {
    protected ConnectScreenMixin(Text title) {
        super(title);
    }

    // ConnectScreen inherits this method: an injector cannot target inherited bytecode.
    // Adding the override preserves the background without requiring a missing method.
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        MenuBackground.render(context, this.width, this.height);
    }
}
