package net.pokereport.luna.lunita;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
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
    private LunitaState state = LunitaState.IDLE;
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
        // Prioridades bajas: la interacción y LunitaBrain pueden pausar la ruta
        // sin competir con un pathfinding calculado cada tick.
        goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.55));
        goalSelector.add(6, new LookAroundGoal(this));
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
        // La Ciudadela es una dimensión de vacío: una entidad especial no debe
        // desaparecer por una caída. La recuperación se ejecuta solo en servidor.
        if (!getWorld().isClient() && getY() < -32) {
            LunitaManager.recuperar(this, "caída al vacío");
        }
    }

    @Override public boolean cannotDespawn() { return true; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "lunita", 0, event -> {
            RawAnimation animation = switch (state) {
                case GREET -> RawAnimation.begin().thenPlay("animation.lunita.greet");
                case RETURN_HOME, TAKE_OFF, FLY, GLIDE -> RawAnimation.begin().thenLoop("animation.lunita.return_home");
                // Native Blockbench idle retained from the Lunita/Eevee model.
                default -> RawAnimation.begin().thenLoop("animation.eevee.ground_idle");
            };
            return event.setAndContinue(animation);
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
    public LunitaState state() { return state; }
    public void state(LunitaState next) { state = next == null ? LunitaState.IDLE : next; }
    public BlockPos home() { return home; }
    public void setHome(BlockPos next) { home = next == null ? null : next.toImmutable(); }

    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("luna_state", state.name());
        if (home != null) nbt.putLong("luna_home", home.asLong());
    }

    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        try { state(LunitaState.valueOf(nbt.getString("luna_state"))); }
        catch (IllegalArgumentException ignored) { state(LunitaState.IDLE); }
        home = nbt.contains("luna_home") ? BlockPos.fromLong(nbt.getLong("luna_home")) : null;
        addCommandTag(MARCA);
        setPersistent();
        setInvulnerable(true);
    }
}
