package net.pokereport.luna.battlefix;

import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import net.pokereport.luna.LunaEternal;

public final class BattleFixService {
    private static boolean registrado = false;

    public static synchronized void registrar() {
        if (registrado) return;
        registrado = true;
        try {
            ShowdownInterpreter.registerUpdateInstructionParser("tie",
                    (battle, instructionSet, message, iterator) -> new TieInstruction(message));
            LunaEternal.LOG.info("PokeReport BattleFix: Parser oficial para '|tie' registrado en ShowdownInterpreter.");
        } catch (Throwable t) {
            LunaEternal.LOG.error("PokeReport BattleFix: No se pudo registrar parser de tie en ShowdownInterpreter", t);
        }
    }
}
