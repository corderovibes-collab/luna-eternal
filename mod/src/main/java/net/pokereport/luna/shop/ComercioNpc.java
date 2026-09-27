package net.pokereport.luna.shop;

import java.util.List;

import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.net.Red;
import net.pokereport.luna.ui.Toque;
import net.pokereport.luna.world.LunaDimensions;

/** Vendedores estáticos de la Torre Comercial. El catálogo y los pagos siguen en servidor. */
public final class ComercioNpc {
    private ComercioNpc() {}

    private record Puesto(String id, String nombre, String destino, Vec3d pos, float yaw) {}

    // Minecraft: sur=0, oeste=90, norte=180, este=-90.
    private static final List<Puesto> PUESTOS = List.of(
            new Puesto("protecciones", "Protecciones del Hogar", "tienda:protecciones",
                    new Vec3d(4.444, 70, 108.851), 180f),
            new Puesto("cuidado", "Cuidado Esencial", "tienda:cuidado",
                    new Vec3d(-7.683, 70, 112.508), -90f),
            new Puesto("exclusivos", "Kits Exclusivos", "kits:exclusivos",
                    new Vec3d(4.398, 70, 121.935), 180f),
            new Puesto("muebles", "Muebles y Decoración", "tienda:muebles",
                    new Vec3d(16.577, 70, 113.556), 90f),
            new Puesto("rango", "Kits de Rango", "kits:rango",
                    new Vec3d(4.575, 92, 111.343), 0f));

    public static void registrar() {
        UseEntityCallback.EVENT.register((jugador, mundo, mano, entidad, golpe) -> {
            Puesto puesto = puesto(entidad);
            if (puesto == null) return ActionResult.PASS;
            if (mano != Hand.MAIN_HAND) return ActionResult.SUCCESS;
            if (!mundo.isClient() && jugador instanceof ServerPlayerEntity sp
                    && !Toque.repetido(sp.getUuid(), "comercio:" + puesto.id())) {
                Red.enviarAbrirComercio(sp, puesto.destino());
            }
            return ActionResult.SUCCESS;
        });
    }

    public static int colocarTodos(MinecraftServer servidor) {
        ServerWorld mundo = servidor.getWorld(LunaDimensions.CIUDADELA);
        if (mundo == null) return 0;
        int puestos = 0;
        for (Puesto p : PUESTOS) {
            for (Entity e : mundo.getOtherEntities(null, Box.of(p.pos(), 4, 4, 4), ComercioNpc::es)) e.discard();
            TrainerMob mob = TrainerMob.getEntityType().create(mundo);
            if (mob == null) continue;
            mob.refreshPositionAndAngles(p.pos().x, p.pos().y, p.pos().z, p.yaw(), 0f);
            mob.setHeadYaw(p.yaw()); mob.setBodyYaw(p.yaw());
            mob.setCustomName(Text.literal(p.nombre())); mob.setCustomNameVisible(true);
            mob.setAiDisabled(true); mob.setInvulnerable(true); mob.setSilent(true); mob.setPersistent(true);
            mob.addCommandTag("luna_comercio"); mob.addCommandTag("luna_comercio_" + p.id());
            if (mundo.spawnEntity(mob)) puestos++;
        }
        return puestos;
    }

    private static boolean es(Entity e) { return e.getCommandTags().contains("luna_comercio"); }
    private static Puesto puesto(Entity e) {
        for (Puesto p : PUESTOS) if (e.getCommandTags().contains("luna_comercio_" + p.id())) return p;
        return null;
    }
}
