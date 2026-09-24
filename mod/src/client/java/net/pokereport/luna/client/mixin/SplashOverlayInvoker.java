package net.pokereport.luna.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SplashOverlay.class)
public interface SplashOverlayInvoker {
    @Invoker("renderProgressBar")
    void invokeDrawProgressBar(DrawContext context, int minX, int minY, int maxX, int maxY, float opacity);
}
