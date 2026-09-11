package net.pokereport.eeveelutionarmor;

import java.util.function.Consumer;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class EeveelutionArmorItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String piece;

    public EeveelutionArmorItem(RegistryEntry<ArmorMaterial> material, Type type,
                                Settings settings, String piece) {
        super(material, type, settings);
        this.piece = piece;
    }

    public String piece() {
        return piece;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<EeveelutionArmorItem> armorRenderer;
            private GeoItemRenderer<EeveelutionArmorItem> itemRenderer;

            @Override
            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
                    @Nullable T entity, ItemStack stack, @Nullable EquipmentSlot slot,
                    @Nullable BipedEntityModel<T> original) {
                if (armorRenderer == null) {
                    armorRenderer = new EeveelutionArmorRenderer();
                }
                return armorRenderer;
            }

            @Override
            public net.minecraft.client.render.item.BuiltinModelItemRenderer getGeoItemRenderer() {
                if (itemRenderer == null) {
                    itemRenderer = new GeoItemRenderer<>(new EeveelutionArmorModel());
                }
                return itemRenderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // El movimiento es un flipbook de textura; no hay animación de huesos.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
