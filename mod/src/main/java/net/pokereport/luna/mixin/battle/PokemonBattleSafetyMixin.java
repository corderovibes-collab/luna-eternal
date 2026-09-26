package net.pokereport.luna.mixin.battle;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.dispatch.BattleDispatch;
import com.cobblemon.mod.common.battles.dispatch.DispatchResult;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMakeChoicePacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleQueueRequestPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Red de seguridad activa en PokemonBattle.tick().
 *
 * Supervisa los combates en curso para evitar bloqueos por desincronización o paquetes perdidos:
 * 1. Si hay cambios forzados pendientes y la cola de dispatches está ociosa, garantiza que la IA
 *    genere su elección (con fallback al primer Pokémon vivo de banquillo) y que el jugador reciba
 *    el diálogo de selección.
 * 2. Si todos los combatientes con cambio forzado tienen sus respuestas listas, dispara checkForInputDispatch().
 * 3. Si todos los Pokémon de ambos bandos están debilitados y Showdown no ha finalizado, resuelve empate.
 * 4. Si un bando tiene 0 Pokémon en todo su equipo y el otro tiene vivos, declara ganador al bando con vida.
 */
@Mixin(value = PokemonBattle.class, remap = false)
public abstract class PokemonBattleSafetyMixin {

    @Shadow(remap = false)
    public abstract boolean getStarted();

    @Shadow(remap = false)
    public abstract boolean getEnded();

    @Shadow(remap = false)
    public abstract ConcurrentLinkedDeque<BattleDispatch> getDispatches();

    @Shadow(remap = false)
    public abstract DispatchResult getDispatchResult();

    @Shadow(remap = false)
    public abstract Iterable<BattleActor> getActors();

    @Shadow(remap = false)
    public abstract void checkForInputDispatch();

    @Shadow(remap = false)
    public abstract void setWinners(List<? extends BattleActor> winners);

    @Shadow(remap = false)
    public abstract void setLosers(List<? extends BattleActor> losers);

    @Shadow(remap = false)
    public abstract void end();

    @Shadow(remap = false)
    public abstract UUID getBattleId();

    @Shadow(remap = false)
    public abstract void broadcastChatMessage(Text text);

    @Unique
    private int pokeReport$safetyTicks = 0;

    @Unique
    private int pokeReport$deadStallTicks = 0;

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void pokeReport$onTickSafety(CallbackInfo ci) {
        if (!this.getStarted() || this.getEnded()) {
            return;
        }

        DispatchResult dr = this.getDispatchResult();
        if (dr != null && !dr.canProceed()) {
            return;
        }

        if (!this.getDispatches().isEmpty()) {
            this.pokeReport$safetyTicks = 0;
            this.pokeReport$deadStallTicks = 0;
            return;
        }

        this.pokeReport$safetyTicks++;

        // Ejecutar revisión cada 10 ticks (0.5 s) en estado ocioso
        if (this.pokeReport$safetyTicks >= 10) {
            this.pokeReport$safetyTicks = 0;
            this.pokeReport$revisarCombate();
        }
    }

    @Unique
    private void pokeReport$revisarCombate() {
        boolean algunCambioForzado = false;

        for (BattleActor actor : this.getActors()) {
            ShowdownActionRequest req = actor.getRequest();
            if (req != null && req.getForceSwitch() != null && req.getForceSwitch().contains(true)) {
                algunCambioForzado = true;

                // 1. Si es IA y no ha respondido:
                if (actor instanceof AIBattleActor ai && actor.getResponses().isEmpty()) {
                    try {
                        ai.onChoiceRequested();
                    } catch (Throwable ignored) {}

                    // Si aún no tiene respuestas, rescatar con el primer Pokémon vivo de banquillo
                    if (actor.getResponses().isEmpty()) {
                        BattlePokemon vivo = null;
                        for (BattlePokemon bp : actor.getPokemonList()) {
                            if (bp != null && bp.getHealth() > 0 && !bp.isSentOut() && !bp.getWillBeSwitchedIn()) {
                                vivo = bp;
                                break;
                            }
                        }
                        if (vivo != null) {
                            vivo.setWillBeSwitchedIn(true);
                            actor.getResponses().add(new SwitchActionResponse(vivo.getUuid()));
                            actor.setMustChoose(false);
                        } else {
                            actor.getResponses().add(PassActionResponse.INSTANCE);
                            actor.setMustChoose(false);
                        }
                    }
                }

                // 2. Si es Jugador y el flag mustChoose quedó apagado pero necesita elegir:
                if (actor instanceof PlayerBattleActor playerActor && !actor.getMustChoose() && actor.getResponses().isEmpty()) {
                    actor.setMustChoose(true);
                    actor.sendUpdate(new BattleMakeChoicePacket());
                    actor.sendUpdate(new BattleQueueRequestPacket(req));
                }
            }
        }

        // Si hay cambio forzado en curso y los actores requeridos ya tienen respuestas preparadas:
        if (algunCambioForzado) {
            boolean listosParaEnviar = true;
            for (BattleActor a : this.getActors()) {
                ShowdownActionRequest req = a.getRequest();
                if (req != null && req.getForceSwitch() != null && req.getForceSwitch().contains(true)) {
                    if (a.getResponses().isEmpty()) {
                        listosParaEnviar = false;
                        break;
                    }
                }
            }
            if (listosParaEnviar) {
                this.checkForInputDispatch();
            }
        }

        // Detección de fin de combate si Showdown quedó estancado
        int actoresConVida = 0;
        int totalActores = 0;
        BattleActor ultimoConVida = null;

        for (BattleActor actor : this.getActors()) {
            totalActores++;
            boolean tieneVida = false;
            for (BattlePokemon bp : actor.getPokemonList()) {
                if (bp != null && bp.getHealth() > 0) {
                    tieneVida = true;
                    break;
                }
            }
            if (tieneVida) {
                actoresConVida++;
                ultimoConVida = actor;
            }
        }

        // Caso A: Ambos bandos perdieron todos sus Pokémon
        if (actoresConVida == 0 && totalActores > 0) {
            this.pokeReport$deadStallTicks++;
            if (this.pokeReport$deadStallTicks >= 4) { // ~2 segundos en estancamiento total
                this.pokeReport$resolverEmpate();
            }
        } else if (actoresConVida == 1 && totalActores > 1 && ultimoConVida != null) {
            // Caso B: Exactamente un bando tiene vida y el resto no tiene Pokémon
            this.pokeReport$deadStallTicks++;
            if (this.pokeReport$deadStallTicks >= 6) { // ~3 segundos en espera
                this.pokeReport$resolverVictoria(ultimoConVida);
            }
        } else {
            this.pokeReport$deadStallTicks = 0;
        }
    }

    @Unique
    private void pokeReport$resolverEmpate() {
        if (this.getEnded()) return;
        List<BattleActor> allActors = new ArrayList<>();
        for (BattleActor a : this.getActors()) {
            allActors.add(a);
        }

        this.broadcastChatMessage(Text.literal("§e¡El combate ha terminado en empate!"));
        this.setWinners(Collections.emptyList());
        this.setLosers(allActors);
        this.end();
        try {
            CobblemonEvents.BATTLE_VICTORY.emit(new BattleVictoryEvent[]{
                new BattleVictoryEvent((PokemonBattle)(Object)this, Collections.emptyList(), allActors, false)
            });
        } catch (Throwable ignored) {}
        ShowdownInterpreter.INSTANCE.getLastCauser().remove(this.getBattleId());
    }

    @Unique
    private void pokeReport$resolverVictoria(BattleActor ganador) {
        if (this.getEnded()) return;
        List<BattleActor> winners = List.of(ganador);
        List<BattleActor> losers = new ArrayList<>();
        for (BattleActor a : this.getActors()) {
            if (!a.equals(ganador)) losers.add(a);
        }

        this.broadcastChatMessage(Text.literal("§6¡" + ganador.getName().getString() + " ha ganado el combate!"));
        this.setWinners(winners);
        this.setLosers(losers);
        this.end();
        try {
            CobblemonEvents.BATTLE_VICTORY.emit(new BattleVictoryEvent[]{
                new BattleVictoryEvent((PokemonBattle)(Object)this, winners, losers, false)
            });
        } catch (Throwable ignored) {}
        ShowdownInterpreter.INSTANCE.getLastCauser().remove(this.getBattleId());
    }
}
