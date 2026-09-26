package net.pokereport.luna.mixin.battle;

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Corrige el conflicto de sincronización entre Cobblemon y Radical Cobblemon Trainers (RCT).
 *
 * RCT inyecta en postUpdate() de AIBattleActor con prioridad 7777 un dispatchGo que programa
 * forzosamente setMustChoose(true) sin invocar onChoiceRequested(). Si la IA ya había seleccionado
 * sus respuestas durante sendUpdate(BattleMakeChoicePacket), ese mustChoose = true diferido
 * rompe la condición de entrada en PokemonBattle.checkForInputDispatch:
 * readyToInput = allMatch(!a.getMustChoose() && (!responses.isEmpty() || ...))
 * causando que el combate espere eternamente.
 *
 * Con prioridad 8888, este Mixin intercepta setMustChoose:
 * 1. Si el actor ya tiene respuestas preparadas (!responses.isEmpty()), cancela setMustChoose(true).
 * 2. Si el actor es AIBattleActor y no tiene respuestas, dispara onChoiceRequested() para que elija.
 */
@Mixin(value = BattleActor.class, priority = 8888)
public abstract class BattleActorChoiceFixMixin {

    @Shadow(remap = false)
    public abstract List<ShowdownActionResponse> getResponses();

    @Shadow(remap = false)
    public abstract ShowdownActionRequest getRequest();

    @Inject(method = "setMustChoose", at = @At("HEAD"), cancellable = true, remap = false)
    private void pokeReport$onSetMustChoose(boolean value, CallbackInfo ci) {
        if (value) {
            // Caso 1: Si ya existen respuestas preparadas, jamás debe quedar en mustChoose=true.
            // Esto anula el efecto bloqueante de la lambda diferida de RCT.
            if (!this.getResponses().isEmpty()) {
                ci.cancel();
                return;
            }

            // Caso 2: Si es una IA y se le pide elegir pero aún no tiene respuestas,
            // aseguramos que genere su respuesta inmediatamente si el request no es de espera.
            BattleActor self = (BattleActor) (Object) this;
            if (self instanceof AIBattleActor ai) {
                ShowdownActionRequest req = this.getRequest();
                if (req != null && !req.getWait()) {
                    try {
                        ai.onChoiceRequested();
                    } catch (Throwable ignored) {}
                    if (!this.getResponses().isEmpty()) {
                        ci.cancel();
                    }
                }
            }
        }
    }
}
