package net.pokereport.luna.client.mixin;

import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SplashOverlay.class)
public interface SplashOverlayAccessor {
    @Accessor("progress")
    float getProgress();

    @Accessor("progress")
    void setProgress(float progress);

    @Accessor("reload")
    ResourceReload getReload();
}
