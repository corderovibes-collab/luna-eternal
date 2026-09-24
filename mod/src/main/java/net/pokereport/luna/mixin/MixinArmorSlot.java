package net.pokereport.luna.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.pokereport.luna.item.ArmaduraRangoItem;

@Mixin(targets = "net.minecraft.screen.slot.ArmorSlot")
public abstract class MixinArmorSlot {

    @Shadow
    @Final
    private LivingEntity entity;

    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    private void luna$bloquearArmaduraSinRango(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && stack.getItem() instanceof ArmaduraRangoItem ar) {
            if (this.entity instanceof PlayerEntity player) {
                if (!ar.puedeEquipar(player)) {
                    if (!player.getWorld().isClient()) {
                        player.sendMessage(Text.literal("§cNo tienes el rango " + ar.rango().titulo + " §cpara equipar esta armadura."), true);
                    }
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
