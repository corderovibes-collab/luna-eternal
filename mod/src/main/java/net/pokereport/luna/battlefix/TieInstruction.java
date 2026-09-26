package net.pokereport.luna.battlefix;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import com.cobblemon.mod.common.battles.dispatch.DispatchResult;
import com.cobblemon.mod.common.battles.dispatch.DispatchResultKt;
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction;
import com.cobblemon.mod.common.battles.dispatch.WaitDispatch;
import kotlin.Unit;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Instrucción formal para procesar el mensaje "|tie" emitido por Showdown
 * cuando ambos combatientes pierden todos sus Pokémon en el mismo turno.
 * Cobblemon omite este parser de fábrica, lo que causaba que el combate se congelara.
 */
public class TieInstruction implements InterpreterInstruction {
    private final BattleMessage message;

    public TieInstruction(BattleMessage message) {
        this.message = message;
    }

    public BattleMessage getMessage() {
        return this.message;
    }

    @Override
    public void invoke(PokemonBattle battle) {
        List<BattleActor> allActors = new ArrayList<>();
        for (BattleActor actor : battle.getActors()) {
            allActors.add(actor);
        }

        battle.dispatch(() -> {
            Text drawText = Text.literal("§e¡El combate ha terminado en empate!");
            for (BattleActor actor : allActors) {
                try {
                    actor.lose(Collections.emptyList(), Collections.emptyList());
                } catch (Throwable ignored) {}
            }
            battle.broadcastChatMessage(drawText);
            return new WaitDispatch(2.0f);
        });

        battle.dispatchGo(() -> {
            battle.setWinners(Collections.emptyList());
            battle.setLosers(allActors);
            battle.end();
            try {
                CobblemonEvents.BATTLE_VICTORY.emit(new BattleVictoryEvent[]{
                    new BattleVictoryEvent(battle, Collections.emptyList(), allActors, false)
                });
            } catch (Throwable ignored) {}
            ShowdownInterpreter.INSTANCE.getLastCauser().remove(battle.getBattleId());
            return Unit.INSTANCE;
        });
    }
}
