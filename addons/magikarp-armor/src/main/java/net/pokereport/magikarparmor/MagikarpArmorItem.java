package net.pokereport.magikarparmor;

import java.util.function.Consumer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class MagikarpArmorItem extends ArmorItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin()
            .thenLoop("animation.magikarp_armor.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String piece;

    public MagikarpArmorItem(RegistryEntry<ArmorMaterial> material, Type type,
                             Settings settings, String piece) {
        super(material, type, settings);
        this.piece = piece;
    }
    public String piece() { return piece; }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<MagikarpArmorItem> armorRenderer;
            private GeoItemRenderer<MagikarpArmorItem> itemRenderer;
            @Override
            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
                    @Nullable T entity, ItemStack stack, @Nullable EquipmentSlot slot,
                    @Nullable BipedEntityModel<T> original) {
                if (armorRenderer == null) armorRenderer = new MagikarpArmorRenderer();
                return armorRenderer;
            }
            @Override
            public net.minecraft.client.render.item.BuiltinModelItemRenderer getGeoItemRenderer() {
                if (itemRenderer == null) itemRenderer = new GeoItemRenderer<>(new MagikarpArmorModel());
                return itemRenderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, 6,
                state -> state.setAndContinue(IDLE)));
    }
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
