package net.pokereport.luna.client.heal;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class HologramaEnfermera {

    public static void registrar() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient cliente = MinecraftClient.getInstance();
            if (cliente.world == null || cliente.player == null) return;

            MatrixStack matrices = context.matrixStack();
            Vec3d camara = context.camera().getPos();
            TextRenderer tr = cliente.textRenderer;

            for (Entity e : cliente.world.getEntities()) {
                if (e.getCommandTags().contains("luna_enfermera")) {
                    double dist = e.squaredDistanceTo(cliente.player);
                    if (dist > 100.0) continue; // Solo si esta cerca

                    double x = e.getX() - camara.x;
                    double y = e.getY() + e.getHeight() + 0.5 - camara.y; // Arriba de la cabeza
                    double z = e.getZ() - camara.z;

                    matrices.push();
                    matrices.translate(x, y, z);
                    
                    // Rotar hacia la camara
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(context.camera().getPitch()));
                    
                    // Escalar
                    float escala = 0.025f;
                    matrices.scale(-escala, -escala, escala);

                    Matrix4f posMatrix = matrices.peek().getPositionMatrix();

                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);

                    // Texto con icono improvisado
                    String texto = "[\u27A4] Clic Derecho para Interactuar";
                    float textWidth = tr.getWidth(texto);
                    
                    // Fondo semi-transparente
                    int alpha = (int) (Math.max(0, 1 - Math.sqrt(dist) / 10.0) * 255);
                    if (alpha > 200) alpha = 200;
                    if (alpha < 20) {
                        matrices.pop();
                        continue;
                    }
                    
                    int bg = (alpha << 24) | 0x000000;
                    
                    tr.draw(texto, -textWidth / 2f, 0, 0xFFFFFF | (alpha << 24), false, posMatrix, context.consumers(), 
                            TextRenderer.TextLayerType.NORMAL, bg, 0xF000F0);

                    matrices.pop();
                }
            }
        });
    }
}
