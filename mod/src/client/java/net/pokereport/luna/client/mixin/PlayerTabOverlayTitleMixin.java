package net.pokereport.luna.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renderiza el logo del servidor en la cabecera del Tablist.
 *
 * <p>Replica la arquitectura de Diosesmon ({@code PlayerTabOverlayTitleMixin}):
 * en lugar de usar glifos de fuente en la cabecera (que fallan con cajas vacías [],
 * se pixelan con la escala GUI o deforman las líneas), se extiende el panel oscuro
 * 38 píxeles hacia arriba y se dibuja la textura nativa con {@link DrawContext#drawTexture}.
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerTabOverlayTitleMixin {

    @Unique
    private static final Identifier LOGO_TEXTURE = Identifier.of("lunaeternal", "textures/gui/tablist_logo.png");
    @Unique
    private static final int LOGO_RAW_WIDTH = 1180;
    @Unique
    private static final int LOGO_RAW_HEIGHT = 364;
    @Unique
    private static final int TITLE_RENDER_HEIGHT = 28;
    @Unique
    private static final int STRIP_HEIGHT = 38;

    @Unique
    private static boolean luna$panelExtended = false;

    @ModifyConstant(method = "render", constant = @Constant(intValue = 10, ordinal = 0))
    private int luna$pushPanelTopY(int y) {
        return y + STRIP_HEIGHT;
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V",
                    ordinal = 0
            )
    )
    private void luna$extendFirstFillUpward(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        luna$panelExtended = true;
        original.call(context, x1, y1 - STRIP_HEIGHT, x2, y2, color);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void luna$drawTitleInsidePanel(DrawContext context, int width, Scoreboard scoreboard, @Nullable ScoreboardObjective objective, CallbackInfo ci) {
        if (!luna$panelExtended) {
            return;
        }
        luna$panelExtended = false;

        int renderWidth = Math.round((float) TITLE_RENDER_HEIGHT * (float) LOGO_RAW_WIDTH / (float) LOGO_RAW_HEIGHT);
        int x = width / 2 - renderWidth / 2;
        int y = 16;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        context.drawTexture(LOGO_TEXTURE, x, y, renderWidth, TITLE_RENDER_HEIGHT, 0.0F, 0.0F, LOGO_RAW_WIDTH, LOGO_RAW_HEIGHT, LOGO_RAW_WIDTH, LOGO_RAW_HEIGHT);
    }
}
