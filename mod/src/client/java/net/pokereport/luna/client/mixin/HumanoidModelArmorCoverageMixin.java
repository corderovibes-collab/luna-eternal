package net.pokereport.luna.client.mixin;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Oculta solo las partes vanilla que quedan debajo de nuestras piezas
 * GeckoLib. Es el mismo contrato que usa Diosesmon para que el cuerpo del
 * jugador no se dibuje dentro del peto o de las piernas personalizadas.
 */
@Mixin(BipedEntityModel.class)
public abstract class HumanoidModelArmorCoverageMixin<T extends LivingEntity> {
    @Inject(method = "setAngles", at = @At("TAIL"))
    private void luna$hideVanillaBody(T entity, float limbSwing, float limbSwingAmount,
                                      float age, float headYaw, float headPitch,
                                      CallbackInfo callbackInfo) {
        if (!(entity instanceof PlayerEntity)) {
            return;
        }

        BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
        ItemStack headStack = entity.getEquippedStack(EquipmentSlot.HEAD);
        boolean customHelmet = isOurArmor(headStack);
        boolean pikachuHelmet = isPikachuHelmet(headStack);
        boolean customChest = isOurArmor(entity.getEquippedStack(EquipmentSlot.CHEST));
        boolean customLeggings = isOurArmor(entity.getEquippedStack(EquipmentSlot.LEGS));
        boolean customBoots = isOurArmor(entity.getEquippedStack(EquipmentSlot.FEET));
        boolean customLowerBody = customLeggings && customBoots;

        // La cabeza vanilla (model.head) permanece siempre visible (incluyendo Pikachu, Magikarp y Eevee).
        // El gorro/capa exterior de la skin vanilla (model.hat) se oculta bajo cualquier casco custom para evitar Z-fighting.
        // El cuerpo y brazos vanilla se ocultan si lleva peto custom para evitar que atraviesen el modelo 3D.
        // Las piernas se ocultan si lleva armadura inferior completa (pantalones + botas).
        model.head.visible = true;
        model.hat.visible = !customHelmet;
        model.body.visible = !customChest;
        model.leftArm.visible = !customChest;
        model.rightArm.visible = !customChest;
        model.leftLeg.visible = !customLowerBody;
        model.rightLeg.visible = !customLowerBody;
    }

    private static boolean isPikachuHelmet(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id != null && id.getNamespace().equals("pikachuarmor") && id.getPath().contains("helmet");
    }

    private static boolean isOurArmor(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (id == null) {
            return false;
        }
        return id.getNamespace().equals("magikarparmor")
                || id.getNamespace().equals("pikachuarmor")
                || id.getNamespace().equals("eeveelution");
    }
}
