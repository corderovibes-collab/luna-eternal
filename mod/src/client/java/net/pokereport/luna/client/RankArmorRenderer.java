package net.pokereport.luna.client;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.pokereport.luna.item.ArmaduraRangoItem;

/**
 * Renderizador de armadura para piezas de armadura de rango (Élite, Campeón, Maestro, Leyenda).
 * Reemplaza el modelo vanilla por los cubos y texturas exactos de los trajes 3D.
 */
public final class RankArmorRenderer implements ArmorRenderer {

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       ItemStack stack, LivingEntity entity, EquipmentSlot slot,
                       int light, BipedEntityModel<LivingEntity> contextModel) {
        if (stack.getItem() instanceof ArmaduraRangoItem ar) {
            Trajes.renderizarPieza(matrices, vertexConsumers, light, ar.suitId(), slot, contextModel, stack.hasGlint());
        }
    }
}
