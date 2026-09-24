package net.pokereport.luna.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.rank.RankService;
import net.pokereport.luna.ui.Tablist.Rank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cancela la generación de orbes de experiencia al morir para jugadores con rango Leyenda,
 * evitando que la experiencia caiga al suelo o se duplique entre compañeros.
 */
@Mixin(PlayerEntity.class)
public abstract class MixinLeyendaDeath {

    @Inject(method = "getXpToDrop", at = @At("HEAD"), cancellable = true)
    private void luna$cancelLeyendaXpDrop(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            Rank r = RankService.enCache(player.getUuid());
            if (r == Rank.LEYENDA || player.hasPermissionLevel(2) || r.equipo) {
                cir.setReturnValue(0);
            }
        }
    }
}
