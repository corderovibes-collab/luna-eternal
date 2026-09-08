package net.pokereport.luna.torrebatalla;

import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.net.Red;
import net.pokereport.luna.world.Decorativos;

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
        ServerWorld mundo = jugador.getServerWorld();
        Vec3d pos = jugador.getPos();
        float yaw = jugador.getYaw();

        // Limpiar cualquier NPC previo u holograma de la torre en un radio cercano
        Box caja = Box.of(pos, 8, 8, 8);
        for (Entity prev : mundo.getEntitiesByClass(Entity.class, caja, e -> e.getCommandTags().contains("luna_torre_batalla"))) {
            prev.discard();
        }
        TorreRanking.quitar(mundo, pos);

        // Crear el NPC recepcionista directamente como TrainerMob
        TrainerMob mob = TrainerMob.getEntityType().create(mundo);
        if (mob != null) {
            mob.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
            mob.setHeadYaw(yaw);
            mob.setBodyYaw(yaw);
            mob.setTrainerId("hoenn_champion_rocco");
            mob.setCustomName(Text.literal("§6§lRecepcionista de la Torre"));
            mob.setCustomNameVisible(true);
            mob.setAiDisabled(true);
            mob.setInvulnerable(true);
            mob.setSilent(true);
            mob.setPersistent(true);
            mob.addCommandTag("luna_torre_batalla");
            mob.addCommandTag(Decorativos.MARCA);
            mundo.spawnEntity(mob);
        }

        // Colocar el holograma del TOP 10 2 bloques a la derecha de donde mira el jugador
        double angulo = Math.toRadians(yaw - 90);
        double offsetX = Math.cos(angulo) * 2.0;
        double offsetZ = Math.sin(angulo) * 2.0;
        TorreRanking.colocarHolograma(mundo, pos.add(offsetX, 0, offsetZ));

        jugador.sendMessage(Text.literal("§a[Torre de Batalla] Recepcionista y Holograma de Ranking colocados con éxito."), false);
    }
}