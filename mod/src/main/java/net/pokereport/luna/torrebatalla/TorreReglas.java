package net.pokereport.luna.torrebatalla;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.pokereport.luna.world.LunaDimensions;

public class TorreReglas {

    public static final String TAG_TORRE = "luna_torre_batalla";

    public static void registrar() {
        // 1. Interceptar el inicio de TODA batalla en la Torre para igualar a Nivel 100
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL, evento -> {
            var battle = evento.getBattle();
            if (battle == null) return;
            
            // Verificamos si la batalla ocurre en la dimensión de la Torre
            boolean enLaTorre = battle.getPlayers().stream().anyMatch(player -> 
                LunaDimensions.TORRE.equals(player.getWorld().getRegistryKey())
            );
            
            if (!enLaTorre) return;

            // Opción 1: Format
            BattleFormat format = battle.getFormat();
            if (format != null) {
                format.setAdjustLevel(100);
            }

            // Opción 2: Asegurar forzando los niveles de los clones de batalla
            battle.getActors().forEach(actor -> {
                for (BattlePokemon bp : actor.getPokemonList()) {
                    Pokemon effected = bp.getEffectedPokemon();
                    if (effected != null) {
                        effected.setLevel(100);
                    }
                }
            });
        });

        // 2. Bloquear que los Pokémons de la Torre se registren en la Pokédex (Visual)
        CobblemonEvents.POKEMON_SEEN.subscribe(evento -> {
            if (evento.getPokemon().getPersistentData().getBoolean(TAG_TORRE)) {
                evento.cancel();
            }
        });

        // 3. Bloquear que los Pokémons de la Torre se guarden en la Pokédex (Escaneo o captura)
        CobblemonEvents.POKEDEX_DATA_CHANGED_PRE.subscribe(evento -> {
            var data = evento.getDataSource();
            if (data != null && data.getPokemon().getPersistentData().getBoolean(TAG_TORRE)) {
                evento.cancel();
            }
        });

        // 4. Bloquear la interacción directa (clic derecho con el Escáner)
        UseEntityCallback.EVENT.register((jugador, mundo, mano, entidad, hitResult) -> {
            if (mano != Hand.MAIN_HAND || mundo.isClient()) {
                return ActionResult.PASS;
            }
            if (entidad instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pe) {
                Pokemon pokemon = pe.getPokemon();
                if (pokemon.getPersistentData().getBoolean(TAG_TORRE)) {
                    var item = jugador.getStackInHand(mano).getItem().toString();
                    if (item.contains("pokedex") || item.contains("scanner")) {
                        jugador.sendMessage(net.minecraft.text.Text.literal("§cLa señal en la Torre es muy débil. ¡El escáner no funciona!"), true);
                        return ActionResult.SUCCESS; // Corta el escaneo
                    }
                }
            }
            return ActionResult.PASS;
        });
    }
}