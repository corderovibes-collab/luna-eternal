package net.pokereport.luna.torrebatalla;

import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
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

    public static final String ID_TRAINER = "luna_cynthia";
    public static final String TAG_TORRE = "luna_torre_batalla";

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

            // Un clic manda DOS paquetes (INTERACT_AT e INTERACT) y en los
            // dos la mano es la PRINCIPAL. Ver `Toque`.
            if (jugador instanceof ServerPlayerEntity sp
                    && !net.pokereport.luna.ui.Toque.repetido(
                            sp.getUuid(), "torre")) {
                Red.enviarAbrirTorreBatalla(sp);
            }
            return ActionResult.SUCCESS; // Corta la interacción por defecto
        });

        // Actualizar automáticamente cualquier NPC existente en el mundo cuando cargue el chunk
        ServerEntityEvents.ENTITY_LOAD.register((entidad, mundo) -> {
            if (entidad instanceof TrainerMob mob && esNpcTorre(entidad)) {
                preparar(mob);
            }
        });
    }

    public static boolean esNpcTorre(Entity entidad) {
        return entidad.getCommandTags().contains(TAG_TORRE)
                || (entidad instanceof TrainerMob t && ID_TRAINER.equals(t.getTrainerId()));
    }

    public static void preparar(TrainerMob mob) {
        mob.setTrainerId(ID_TRAINER);
        mob.setCustomName(Text.literal("Cynthia"));
        mob.setCustomNameVisible(true);
        mob.setAiDisabled(true);
        mob.setInvulnerable(true);
        mob.setSilent(true);
        mob.setPersistent(true);
        mob.addCommandTag(TAG_TORRE);
        mob.addCommandTag(Decorativos.MARCA);
    }

    public static void colocarNpc(ServerPlayerEntity jugador) {
        ServerWorld mundo = jugador.getServerWorld();
        Vec3d pos = jugador.getPos();
        float yaw = jugador.getYaw();

        // Limpiar cualquier NPC previo de la torre en un radio cercano
        Box caja = Box.of(pos, 8, 8, 8);
        for (Entity prev : mundo.getEntitiesByClass(Entity.class, caja, TorreNpc::esNpcTorre)) {
            prev.discard();
        }

        // Crear el NPC recepcionista directamente como TrainerMob
        TrainerMob mob = TrainerMob.getEntityType().create(mundo);
        if (mob != null) {
            mob.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
            mob.setHeadYaw(yaw);
            mob.setBodyYaw(yaw);
            preparar(mob);
            mundo.spawnEntity(mob);
        }

        jugador.sendMessage(Text.literal("§a[Torre de Batalla] Cynthia colocada con éxito."), false);
    }

    public static int quitarCercano(ServerPlayerEntity jugador) {
        ServerWorld mundo = jugador.getServerWorld();
        Vec3d pos = jugador.getPos();
        Box caja = Box.of(pos, 8, 8, 8);
        int eliminados = 0;
        for (Entity e : mundo.getEntitiesByClass(Entity.class, caja, TorreNpc::esNpcTorre)) {
            e.discard();
            eliminados++;
        }
        return eliminados;
    }
}