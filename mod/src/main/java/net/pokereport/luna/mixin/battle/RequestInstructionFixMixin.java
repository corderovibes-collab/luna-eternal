package net.pokereport.luna.mixin.battle;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.interpreter.instructions.RequestInstruction;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Corrige la inversión de orden de despacho entre PostUpdateInstruction y RequestInstruction.
 *
 * Cuando ocurre un Double KO, Showdown emite sideupdate con |request|{"forceSwitch":[true],...}.
 * En el despachador de Cobblemon, PostUpdateInstruction se ejecutaba ANTES de que el nuevo request
 * estuviera asignado, por lo que postUpdate() no detectaba la necesidad de cambio, no activaba mustChoose
 * y no enviaba BattleMakeChoicePacket ni al jugador ni a la IA.
 *
 * Al inyectar en invoke$lambda$0 justo después de setRequest(), ejecutamos postUpdate()
 * con el request ya presente, activando mustChoose y enviando BattleMakeChoicePacket a ambos combatientes.
 */
@Mixin(value = RequestInstruction.class, remap = false)
public abstract class RequestInstructionFixMixin {

    @Inject(method = "invoke$lambda$0", at = @At("TAIL"), remap = false)
    private static void pokeReport$onAfterRequestSet(RequestInstruction this$0, ShowdownActionRequest request, CallbackInfoReturnable<Unit> cir) {
        if (this$0 == null || request == null) return;
        List<Boolean> forceSwitch = request.getForceSwitch();
        if (forceSwitch != null && forceSwitch.contains(true)) {
            BattleActor actor = this$0.getBattleActor();
            if (actor != null) {
                actor.postUpdate();
            }
        }
    }
}
