package net.pokereport.luna.lunita;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * La entidad viva de Lunita. No hereda de Pokémon, por lo que no es capturable,
 * no ocupa equipos ni puede entrar al motor de combates de Cobblemon.
 */
public final class LunitaEntity extends PathAwareEntity implements GeoEntity {
    public static final String MARCA = "luna_lunita";
    public static final int ANIMATION_TRANSITION_TICKS = 4;
    public static final int GREETING_TICKS = 25 + ANIMATION_TRANSITION_TICKS;
    private static final TrackedData<Integer> STATE = DataTracker.registerData(LunitaEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private int greetingTicks;
    private int returnTicks;
    private int stuckReturnTicks;
    private double returnProgressDistance = Double.POSITIVE_INFINITY;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    /** Punto certificado al crearla con el comando administrativo. */
    private BlockPos home;

    public LunitaEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
        setInvulnerable(true);
        addCommandTag(MARCA);
    }

    /** Atributos obligatorios para toda entidad viviente personalizada. */
    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.18)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 12.0);
    }

    @Override protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.55) {
            @Override public boolean canStart() {
                return state() != LunitaState.GREET && state() != LunitaState.RETURN_HOME && super.canStart();
            }
            @Override public boolean shouldContinue() {
                return state() != LunitaState.GREET && state() != LunitaState.RETURN_HOME && super.shouldContinue();
            }
        });
        goalSelector.add(6, new LookAroundGoal(this));
    }

    @Override protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(STATE, LunitaState.IDLE.ordinal());
    }

    /** Guardiana decorativa: ninguna fuente puede reducir su vida. */
    @Override public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (getWorld().isClient()) return ActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity serverPlayer) {
            LunitaManager.saludar(this, serverPlayer);
        }
        return ActionResult.SUCCESS;
    }

    @Override public void tick() {
        super.tick();
        if (!getWorld().isClient()) {
            if (greetingTicks > 0) {
                getNavigation().stop();
                if (--greetingTicks == 0) state(LunitaState.IDLE);
            } else if (home != null) {
                double distance = squaredDistanceTo(home.getX() + .5, home.getY(), home.getZ() + .5);
                if (distance > 144 || state() == LunitaState.RETURN_HOME) {
                    if (distance < 4) {
                        getNavigation().stop();
                        state(LunitaState.IDLE);
                        returnTicks = 0;
                    } else {
                        state(LunitaState.RETURN_HOME);
                        if (distance < returnProgressDistance - .25) {
                            returnProgressDistance = distance;
                            stuckReturnTicks = 0;
                        } else {
                            stuckReturnTicks++;
                        }
                        // Recalculate once per second, allowing the navigator to avoid obstacles.
                        if (returnTicks++ % 20 == 0) getNavigation().startMovingTo(
                                home.getX() + .5, home.getY(), home.getZ() + .5, .55);
                        if (stuckReturnTicks > 600) LunitaManager.recuperar(this, "ruta al hogar bloqueada");
                    }
                } else {
                    state(getNavigation().isIdle() ? LunitaState.IDLE : LunitaState.WANDER);
                    returnTicks = 0;
                }
            }
        }
        // La Ciudadela es una dimensión de vacío: una entidad especial no debe
        // desaparecer por una caída. La recuperación se ejecuta solo en servidor.
        if (!getWorld().isClient() && getY() < -32) {
            LunitaManager.recuperar(this, "caída al vacío");
        }
    }

    @Override public boolean cannotDespawn() { return true; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "lunita", ANIMATION_TRANSITION_TICKS, event -> {
            RawAnimation animation = switch (state()) {
                case GREET -> RawAnimation.begin().thenPlay("animation.lunita.greet");
                // El idle original es una pose estática; Lunita usa una
                // respiración viva y cambia a zancada al navegar.
                default -> event.isMoving()
                        ? RawAnimation.begin().thenLoop("animation.lunita.walk")
                        : RawAnimation.begin().thenLoop("animation.lunita.idle");
            };
            return event.setAndContinue(animation);
        }));
        controllers.add(new AnimationController<>(this, "blink", 0, event ->
                event.setAndContinue(RawAnimation.begin().thenLoop("animation.lunita.blink"))));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
    public LunitaState state() { return LunitaState.values()[dataTracker.get(STATE)]; }
    public void state(LunitaState next) {
        dataTracker.set(STATE, (next == null ? LunitaState.IDLE : next).ordinal());
        if (next == LunitaState.GREET) {
            greetingTicks = GREETING_TICKS;
            getNavigation().stop();
        }
        if (next == LunitaState.IDLE) {
            returnTicks = 0;
            stuckReturnTicks = 0;
            returnProgressDistance = Double.POSITIVE_INFINITY;
        }
    }
    public BlockPos home() { return home; }
    public void setHome(BlockPos next) { home = next == null ? null : next.toImmutable(); }

    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("luna_state", state().name());
        if (home != null) nbt.putLong("luna_home", home.asLong());
    }

    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        // Transient interactions must not resume after a chunk/server reload.
        state(LunitaState.IDLE);
        home = nbt.contains("luna_home") ? BlockPos.fromLong(nbt.getLong("luna_home")) : null;
        addCommandTag(MARCA);
        setPersistent();
        setInvulnerable(true);
    }
}
