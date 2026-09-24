package net.pokereport.luna.client.mixin;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import net.minecraft.client.gui.DrawContext;
import net.pokereport.luna.client.battle.LunaBattleTooltipHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin que intercepta la fase final del renderizado de cada casilla de movimiento
 * (BattleMoveSelection.MoveTile) para asegurar que nunca se pierda el tooltip ni la
 * información de efectividad y rango de daño.
 */
@Mixin(value = BattleMoveSelection.MoveTile.class, remap = false)
public abstract class BattleMoveTileTooltipAuditMixin {
    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void luna$auditAndEnsureTooltip(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            LunaBattleTooltipHelper.auditAndFixTooltip((BattleMoveSelection.MoveTile) (Object) this, mouseX, mouseY);
        } catch (Throwable ignored) {
            // Protección total: nunca permitir que un error en el tooltip rompa el dibujado de la pantalla
        }
    }
}
