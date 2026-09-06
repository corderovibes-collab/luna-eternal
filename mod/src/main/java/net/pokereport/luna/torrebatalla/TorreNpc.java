package net.pokereport.luna.torrebatalla;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.pokereport.luna.net.Red;

public class TorreNpc {

    public static void registrarClic() {
        UseEntityCallback.EVENT.register((jugador, mundo, mano, entidad, hitResult) -> {
            if (mundo.isClient()) {
                return ActionResult.PASS;
            }
            if (!esNpcTorre(entidad)) {
                return ActionResult.PASS;
            }

            // Administradores en creativo + sneak pueden editar el NPC
            if (jugador.isCreative() && jugador.isSneaking()) {
                return ActionResult.PASS;
            }

            if (jugador instanceof ServerPlayerEntity sp) {
                Red.enviarAbrirTorreBatalla(sp);
            }
            return ActionResult.SUCCESS; // Corta la interacción por defecto
        });
    }

    private static boolean esNpcTorre(Entity entidad) {
        return entidad.getCommandTags().contains("luna_torre_batalla");
    }

    public static void colocarNpc(ServerPlayerEntity jugador) {
        var src = jugador.getCommandSource().withSilent();
        
        // Spawnear el NPC de la torre (un veterano o algo)
        jugador.getServer().getCommandManager().executeWithPrefix(src, "spawnnpc cobblemon:veteran_male");
        
        // Buscarlo
        net.minecraft.util.math.Box caja = jugador.getBoundingBox().expand(2.0);
        var entidades = jugador.getServerWorld().getOtherEntities(jugador, caja, 
            e -> e.getType().getUntranslatedName().contains("npc") || e.getType().getUntranslatedName().contains("cobblemon"));
        
        for (Entity e : entidades) {
            if (!e.getCommandTags().contains("luna_torre_batalla") && !e.getCommandTags().contains("luna_santuario") && !e.getCommandTags().contains("luna_enfermera")) {
                e.addCommandTag("luna_torre_batalla");
                
                if (e instanceof net.minecraft.entity.mob.MobEntity me) {
                    me.setInvulnerable(true);
                    me.setPersistent();
                }
                
                e.setYaw(jugador.getYaw());
                e.setPitch(0);
                e.setHeadYaw(jugador.getHeadYaw());
                break;
            }
        }
    }
}