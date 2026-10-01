package net.pokereport.luna.rotom;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.HashMap;
import java.util.UUID;

/** Stationary device, with no movement goals and no connection to battle actors. */
public final class RotomEntity extends MobEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private boolean noticed;
    private int gestureTicks;
    private static final net.minecraft.entity.data.TrackedData<Integer> FACE =
            net.minecraft.entity.data.DataTracker.registerData(RotomEntity.class,
                    net.minecraft.entity.data.TrackedDataHandlerRegistry.INTEGER);
    @Override protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(FACE, 0);
    }
    public int expression() {
        // Different UUID offsets avoid identical blink cycles for future devices.
        int phase = Math.floorMod(age + getUuid().hashCode(), 97);
        return dataTracker.get(FACE) == 0 && phase < 3 ? 6 : dataTracker.get(FACE);
    }
    public RotomEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        setPersistent(); setNoGravity(true); setInvulnerable(true);
    }
    @Override protected void initGoals() {}
    @Override public boolean damage(DamageSource source, float amount) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean cannotDespawn() { return true; }
    @Override public boolean isFireImmune() { return true; }
    @Override public boolean isPushedByFluids() { return false; }
    @Override public boolean canUsePortals(boolean vehicles) { return false; }
    @Override public void pushAwayFrom(net.minecraft.entity.Entity other) {}
    @Override protected boolean canAddPassenger(net.minecraft.entity.Entity passenger) { return false; }
    @Override public boolean handleFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.SUCCESS;
        if (getWorld().isClient()) return ActionResult.SUCCESS;
        var device = RotomTutorial.device(this);
        if (!(player instanceof ServerPlayerEntity p) || device == null
                || p.squaredDistanceTo(this) > 36 || net.pokereport.luna.puerta.Puerta.bloqueado(p))
            return ActionResult.FAIL;
        long now = getWorld().getTime();
        if (cooldowns.getOrDefault(p.getUuid(), -100L) > now) return ActionResult.SUCCESS;
        if (!ServerPlayNetworking.canSend(p, RotomTutorial.Open.ID)) return ActionResult.FAIL;
        cooldowns.put(p.getUuid(), now + 60);
        gestureTicks = 48;
        triggerAnim("gesture", "interact");
        p.playSoundToPlayer(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, .3f, 1.7f);
        ((ServerWorld)getWorld()).spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                getX(), getY() + .6, getZ(), 8, .25, .25, .25, .02);
        ServerPlayNetworking.send(p, new RotomTutorial.Open(device.id()));
        return ActionResult.SUCCESS;
    }
    @Override public void tick() {
        setNoGravity(true); setVelocity(0, 0, 0);
        super.tick();
        var device = RotomTutorial.device(this);
        if (!getWorld().isClient() && device != null) {
            if (gestureTicks > 0) {
                gestureTicks--;
                dataTracker.set(FACE, gestureTicks > 36 ? 2 : gestureTicks > 16 ? 4 : 1);
            } else dataTracker.set(FACE, noticed ? 3 : 0);
            var home = device.position();
            if (squaredDistanceTo(home) > .0001) setPosition(home);
            if (age % 20 == 0) {
                cooldowns.values().removeIf(until -> until <= getWorld().getTime());
                var player = getWorld().getClosestPlayer(this, 5);
                if (player != null && !noticed) triggerAnim("gesture", "notice");
                noticed = player != null;
                float facing = device.yaw();
                if (player != null) {
                    float desired = (float)(Math.toDegrees(Math.atan2(player.getZ()-getZ(), player.getX()-getX())) - 90);
                    facing += net.minecraft.util.math.MathHelper.clamp(
                            net.minecraft.util.math.MathHelper.wrapDegrees(desired - device.yaw()), -25, 25);
                }
                setYaw(facing); setHeadYaw(facing); setBodyYaw(facing);
            }
            if (noticed && age % 40 == 0)
                ((ServerWorld)getWorld()).spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        getX(), getY()+.15, getZ(), 1, .08, .05, .08, .005);
        }
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 5,
                s -> s.setAndContinue(RawAnimation.begin().thenLoop("animation.rotom.idle"))));
        controllers.add(new AnimationController<>(this, "gesture", 3, s -> PlayState.STOP)
                .triggerableAnim("notice", RawAnimation.begin().thenPlay("animation.rotom.notice"))
                .triggerableAnim("interact", RawAnimation.begin().thenPlay("animation.rotom.interact")
                        .thenPlay("animation.rotom.scan").thenPlay("animation.rotom.happy")));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
