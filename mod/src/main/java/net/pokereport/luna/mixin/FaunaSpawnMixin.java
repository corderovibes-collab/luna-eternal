package net.pokereport.luna.mixin;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.pokereport.luna.world.FaunaControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerWorld.class)
public abstract class FaunaSpawnMixin {
    @Inject(method="spawnEntity",at=@At("HEAD"),cancellable=true)
    private void luna$filtrarFauna(Entity e, CallbackInfoReturnable<Boolean> cir) {
        if(FaunaControl.bloquear(e)) cir.setReturnValue(false);
    }
}
