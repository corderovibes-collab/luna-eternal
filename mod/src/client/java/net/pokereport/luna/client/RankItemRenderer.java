package net.pokereport.luna.client;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.pokereport.luna.item.ArmaduraRangoItem;

/**
 * Renderizador de items para piezas de armadura de rango (Élite, Campeón, Maestro, Leyenda).
 * Dibuja el modelo 3D real de Blockbench en GUI, mano y mundo usando BuiltinItemRendererRegistry.
 */
public final class RankItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (!(stack.getItem() instanceof ArmaduraRangoItem ar)) return;
        String suitId = ar.suitId();
        // Los nombres del registro de items no son los huesos del traje.
        String piezaTipo = switch (ar.pieza()) {
            case "helmet" -> "head";
            case "chestplate" -> "body";
            case "leggings" -> "legs";
            case "boots" -> "boots";
            default -> throw new IllegalArgumentException("Pieza de rango desconocida: " + ar.pieza());
        };

        // DrawContext gestiona la iluminación hasta vaciar los buffers del item.
        // Cambiarla aquí altera también los vértices que aún están pendientes.
        Trajes.renderizarItemModel(matrices, vertexConsumers, light, suitId, piezaTipo, mode, stack.hasGlint());
    }
}
