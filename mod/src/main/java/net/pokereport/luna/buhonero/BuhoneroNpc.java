package net.pokereport.luna.buhonero;

import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.world.Decorativos;
import net.pokereport.luna.world.LunaDimensions;

public final class BuhoneroNpc {
    public static final String ID = "luna_buhonero";
    public static final Vec3d POS = new Vec3d(77.526,55,-66.7);
    private static int ticks;
    private BuhoneroNpc() {}
    public static boolean es(net.minecraft.entity.Entity e) {
        return e instanceof TrainerMob t && ID.equals(t.getTrainerId());
    }
    public static void clic() {
        UseEntityCallback.EVENT.register((p,w,hand,e,hit) -> {
            if (!es(e)) return ActionResult.PASS;
            if (hand == Hand.MAIN_HAND && p instanceof ServerPlayerEntity sp
                    && !net.pokereport.luna.ui.Toque.repetido(sp.getUuid(),ID)) {
                BuhoneroService.atender(sp,0,false,true);
            }
            return ActionResult.SUCCESS;
        });
    }
    public static boolean cerca(ServerPlayerEntity p) {
        return p.isAlive() && !p.isSpectator() && !net.pokereport.luna.puerta.Puerta.bloqueado(p)
                && p.getServerWorld().getRegistryKey().equals(LunaDimensions.CIUDADELA)
                && p.squaredDistanceTo(POS) <= 36
                && !p.getServerWorld().getOtherEntities(p,new Box(POS,POS).expand(1),BuhoneroNpc::es).isEmpty();
    }
    public static void servidor() {
        ServerEntityEvents.ENTITY_LOAD.register((e,w) -> { if(es(e)) preparar((TrainerMob)e); });
        // Esperar a que se carguen entidades del chunk evita duplicarlo al reiniciar.
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            ticks++;
            var w=s.getWorld(LunaDimensions.CIUDADELA);
            if(w==null) return;
            if(ticks==20) w.getChunk(BlockPos.ofFloored(POS));
            if(ticks==100 || ticks%1200==0) colocar(w);
        });
    }
    private static void preparar(TrainerMob t) {
        t.setAiDisabled(true); t.setInvulnerable(true); t.setSilent(true); t.setPersistent(true);
        t.setNoGravity(true);
        t.addCommandTag(Decorativos.MARCA); t.addCommandTag(ID);
        t.setCustomName(Text.literal("Buhonero - Mercado Negro")); t.setCustomNameVisible(true);
        t.refreshPositionAndAngles(POS.x,POS.y,POS.z,180f,0f); t.setHeadYaw(180f); t.setBodyYaw(180f);
    }
    private static void colocar(ServerWorld w) {
        if(!w.isChunkLoaded(BlockPos.ofFloored(POS))) return;
        if(!com.gitlab.srcmc.rctmod.api.RCTMod.getInstance().getTrainerManager().isValidId(ID)) {
            LunaEternal.LOG.error("Buhonero: falta el entrenador {} en los datos; no se coloca una entidad genérica",ID);
            return;
        }
        var encontrados=w.getOtherEntities(null,new Box(POS,POS).expand(8),BuhoneroNpc::es);
        if(!encontrados.isEmpty()) {
            preparar((TrainerMob)encontrados.getFirst());
            for(int i=1;i<encontrados.size();i++) encontrados.get(i).discard();
            return;
        }
        var e=TrainerMob.getEntityType().create(w);
        if(e instanceof TrainerMob t) {
            t.setTrainerId(ID); preparar(t);
            if(w.spawnEntity(t)) LunaEternal.LOG.info("Buhonero colocado en ciudadela: 77.526 55 -66.7, norte");
        }
    }
}
