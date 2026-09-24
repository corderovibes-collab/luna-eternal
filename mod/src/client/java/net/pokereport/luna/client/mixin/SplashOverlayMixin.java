package net.pokereport.luna.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {

    @Shadow
    @Final
    private Consumer<Optional<Throwable>> exceptionHandler;

    @Unique
    private static final Identifier TITLE_TEXTURE = Identifier.of("lunaeternal", "textures/gui/splash/luna_title.png");

    @Unique
    private static final int TITLE_TEXTURE_WIDTH = 2048;

    @Unique
    private static final int TITLE_TEXTURE_HEIGHT = 512;

    @Unique
    private static final float TITLE_SCALE = 0.5f;

    @Unique
    private static final float TITLE_RISE = 0.08f;

    @Unique
    private long overlayEndTime = -1L;

    @Unique
    private final long delayDuration = 1000L;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void pokereport$onRenderOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        if (client.getResourceManager().getResource(TITLE_TEXTURE).isEmpty()) {
            ci.cancel();
            return;
        }

        context.fill(0, 0, width, height, 0xFF0B0E14);

        float scale = Math.min((float) width / TITLE_TEXTURE_WIDTH, (float) height / TITLE_TEXTURE_HEIGHT) * TITLE_SCALE;
        int drawW = Math.round(TITLE_TEXTURE_WIDTH * scale);
        int drawH = Math.round(TITLE_TEXTURE_HEIGHT * scale);
        int x = (width - drawW) / 2;
        int y = (height - drawH) / 2 - Math.round(height * TITLE_RISE);

        RenderSystem.enableBlend();
        context.drawTexture(TITLE_TEXTURE, x, y, drawW, drawH, 0.0F, 0.0F, TITLE_TEXTURE_WIDTH, TITLE_TEXTURE_HEIGHT, TITLE_TEXTURE_WIDTH, TITLE_TEXTURE_HEIGHT);

        SplashOverlayAccessor accessor = (SplashOverlayAccessor) (Object) this;
        ResourceReload reload = accessor.getReload();
        float currentProgress = accessor.getProgress();
        float actualProgress = reload.getProgress();
        float smoothProgress = MathHelper.clamp(currentProgress * 0.95F + actualProgress * 0.05F, 0.0F, 1.0F);
        accessor.setProgress(smoothProgress);

        int barHalfWidth = (int) (Math.min((double) width * 0.75, (double) height) * 0.5);
        int barCenterY = (int) ((double) height * 0.8325);
        int barMinX = width / 2 - barHalfWidth;
        int barMaxX = width / 2 + barHalfWidth;
        int barMinY = barCenterY - 5;
        int barMaxY = barCenterY + 5;

        ((SplashOverlayInvoker) this).invokeDrawProgressBar(context, barMinX, barMinY, barMaxX, barMaxY, 1.0F);

        if (reload.isComplete()) {
            try {
                reload.throwException();
                this.exceptionHandler.accept(Optional.empty());
            } catch (Throwable t) {
                this.exceptionHandler.accept(Optional.of(t));
            }
            if (this.overlayEndTime == -1L) {
                this.overlayEndTime = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - this.overlayEndTime > this.delayDuration) {
                client.setOverlay(null);
            }
        }
        ci.cancel();
    }
}
