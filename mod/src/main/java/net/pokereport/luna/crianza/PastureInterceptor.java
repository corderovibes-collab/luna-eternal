package net.pokereport.luna.crianza;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.pokereport.luna.net.Red;

/**
 * Intercepta el clic derecho sobre el bloque de Pasture (pastizal / máquina de crianza)
 * de Cobblemon para abrir directamente la interfaz de Crianza del PokéPad de Luna Eternal,
 * evitando que se abra la interfaz por defecto de Cobblemon.
 */
public final class PastureInterceptor {

    private static final Identifier PASTURE_ID = Identifier.of("cobblemon", "pasture");

    private PastureInterceptor() {}

    public static void registrarServidor() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            var pos = hitResult.getBlockPos();
            var state = world.getBlockState(pos);
            var blockId = Registries.BLOCK.getId(state.getBlock());
            if (!PASTURE_ID.equals(blockId)) {
                return ActionResult.PASS;
            }

            // Consumir SIEMPRE la interacción sobre pasturas para que ni MAIN_HAND ni OFF_HAND
            // puedan abrir la interfaz nativa de Cobblemon ni activar Cobbreeding en el mundo.
            if (hand == Hand.MAIN_HAND) {
                // Neutralizar cualquier activación previa de Cobbreeding en el bloque si existiera
                for (var prop : state.getProperties()) {
                    if ("breeding_activated".equalsIgnoreCase(prop.getName())
                            && prop instanceof net.minecraft.state.property.BooleanProperty boolProp
                            && Boolean.TRUE.equals(state.get(boolProp))) {
                        world.setBlockState(pos, state.with(boolProp, false));
                        break;
                    }
                }

                if (!world.isClient() && player instanceof ServerPlayerEntity sp) {
                    Red.enviarAbrirCrianza(sp);
                }
            }
            return ActionResult.SUCCESS;
        });
    }
}
