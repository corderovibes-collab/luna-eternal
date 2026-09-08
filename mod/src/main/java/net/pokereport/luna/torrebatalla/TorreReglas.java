package net.pokereport.luna.torrebatalla;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.pokereport.luna.world.LunaDimensions;

import java.util.UUID;

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

        // 2. Bloquear clic derecho con la Pokédex en el aire dentro de la Torre
        UseItemCallback.EVENT.register((jugador, mundo, mano) -> {
            if (LunaDimensions.TORRE.equals(mundo.getRegistryKey())) {
                ItemStack stack = jugador.getStackInHand(mano);
                if (esPokedex(stack.getItem())) {
                    if (!mundo.isClient()) {
                        jugador.sendMessage(Text.literal("§c[Torre de Batalla] ¡No puedes usar la Pokédex en la Torre de Batalla!"), true);
                    }
                    return TypedActionResult.fail(stack);
                }
            }
            return TypedActionResult.pass(jugador.getStackInHand(mano));
        });

        // 3. Bloquear interacción directa (clic derecho a entidades con Pokédex) en la Torre
        UseEntityCallback.EVENT.register((jugador, mundo, mano, entidad, hitResult) -> {
            if (LunaDimensions.TORRE.equals(mundo.getRegistryKey())) {
                ItemStack stack = jugador.getStackInHand(mano);
                if (esPokedex(stack.getItem())) {
                    if (!mundo.isClient()) {
                        jugador.sendMessage(Text.literal("§c[Torre de Batalla] ¡No puedes usar la Pokédex en la Torre de Batalla!"), true);
                    }
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        // 4. Bloquear que los Pokémons de la Torre se registren en la Pokédex (Visual)
        CobblemonEvents.POKEMON_SEEN.subscribe(Priority.HIGHEST, evento -> {
            Pokemon pokemon = evento.getPokemon();
            if (pokemon != null && pokemon.getPersistentData().getBoolean(TAG_TORRE)) {
                evento.cancel();
                return;
            }
            var entity = pokemon != null ? pokemon.getEntity() : null;
            if (entity != null && entity.getWorld() != null && LunaDimensions.TORRE.equals(entity.getWorld().getRegistryKey())) {
                evento.cancel();
            }
        });

        // 5. Bloquear que los Pokémons de la Torre se guarden en la Pokédex (Escaneo o captura)
        CobblemonEvents.POKEDEX_DATA_CHANGED_PRE.subscribe(Priority.HIGHEST, evento -> {
            var data = evento.getDataSource();
            if (data != null && data.getPokemon().getPersistentData().getBoolean(TAG_TORRE)) {
                evento.cancel();
                return;
            }
            UUID playerUuid = evento.getPlayerUUID();
            if (playerUuid != null) {
                var sp = com.cobblemon.mod.common.util.PlayerExtensionsKt.getPlayer(playerUuid);
                if (sp != null && LunaDimensions.TORRE.equals(sp.getWorld().getRegistryKey())) {
                    evento.cancel();
                }
            }
        });
    }

    public static boolean esPokedex(Item item) {
        if (item == null) return false;
        if (item instanceof com.cobblemon.mod.common.item.PokedexItem) return true;
        String path = Registries.ITEM.getId(item).getPath();
        return path.contains("pokedex") || path.contains("scanner");
    }
}