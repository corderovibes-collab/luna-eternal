package net.pokereport.luna.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.pokereport.luna.item.ArmaduraRangoItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** También bloquea el equipamiento automático mediante dispensadores. */
@Mixin(PlayerEntity.class)
public abstract class MixinRankEquipment {
    @Inject(method = "canEquip", at = @At("HEAD"), cancellable = true)
    private void luna$comprobarRango(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof ArmaduraRangoItem armadura
                && !armadura.puedeEquipar((PlayerEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
