package net.pokereport.luna.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.pokereport.luna.net.Red;

/**
 * El holograma de cada memorial: la foto flotando sobre su proyector.
 *
 * <h2>⚠⚠ EL CLIENTE SOLO DIBUJA LO QUE EL SERVIDOR DIJO</h2>
 *
 * De aqui no sale ninguna verdad: el estado viaja en {@code EstadoSantuario} y
 * la foto solo se pinta si ese estado dice que el nicho tiene dueno y una foto
 * aprobada (P6). Si el servidor no ha dicho nada, no se pinta nada -- un
 * holograma que el cliente se inventara seria justo la clase de mentira que
 * este proyecto evita.
 *
 * <h2>⚠ EL QUAD SE DIBUJA A MANO, y es la unica forma sin entidades</h2>
 *
 * No hay ninguna entidad nueva en el mundo (un registro que se sincroniza es
 * una forma mas de echar a alguien). El quad sale del {@code Tessellator} en
 * {@code AFTER_ENTITIES}, orientado hacia la camara con las rotaciones de la
 * propia camara. La textura {@code POSITION_TEXTURE} no recibe luz: por eso el
 * holograma brilla igual de noche, que es justo lo que un memorial necesita.
 */
public final class HologramaSantuario {

    private HologramaSantuario() {}

    /** Se registra una vez, con los demas eventos de cliente. */
    public static void registrar() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            var estado = EstadoCliente.santuario();
            if (estado == null) {
                return;
            }
            var cliente = MinecraftClient.getInstance();
            var mundo = cliente.world;
            if (mundo == null) {
                return;
            }
            var camara = cliente.gameRenderer.getCamera();
            var posCam = camara.getPos();
            // ⚠ El vaiven se mide con el reloj de pared: es pura animacion, y
            //   que dos clientes no vayan acompasados no se nota en un
            //   holograma -- y evita depender del API de ticks del contexto.
            float tiempo = System.currentTimeMillis() / 1000f;

            for (Red.NichoSantuario nicho : estado.nichos()) {
                // ⚠ Solo lo reclamado con foto aprobada: el estado es la regla.
                if (nicho.estado().dueno().isEmpty()
                        || nicho.memorial().foto().isEmpty()) {
                    continue;
                }
                var pos = nicho.pos();
                double dx = pos.x() + 0.5 - posCam.x;
                double dz = pos.z() + 0.5 - posCam.z;
                if (dx * dx + dz * dz > 64 * 64) {
                    continue;
                }
                String sha1 = nicho.memorial().foto();
                var foto = TexturasFoto.lista(sha1);
                if (foto == null) {
                    // ⚠ Se pide UNA vez (TexturasFoto corta los repetidos) y
                    //   mientras no llega, aqui no hay nada que pintar.
                    TexturasFoto.pedir(sha1);
                    continue;
                }
                dibujar(context.matrixStack(), camara, pos, foto, tiempo);
            }
        });
    }

    /** Un quad flotando, mirando a la camara, con un vaiven lento. */
    private static void dibujar(MatrixStack matrices,
                                net.minecraft.client.render.Camera camara,
                                Red.PosNicho pos, TexturasFoto.Foto foto,
                                float tiempo) {
        var posCam = camara.getPos();

        // ⚠ El vaiven es del DIBUJADO, no del dato: el servidor manda la
        //   posicion y el cliente solo la anima. Dos clientes con distinto
        //   desfase ven la foto en el mismo sitio, a distinta altura de ola.
        float bob = MathHelper.sin(tiempo * 1.1f) * 0.045f;

        matrices.push();
        matrices.translate(pos.x() + 0.5 - posCam.x,
                pos.y() + 1.55 + bob - posCam.y,
                pos.z() + 0.5 - posCam.z);
        // ⚠ Orientar hacia la camara = girar el quad con SUS angulos. Es la
        //   tecnica del cartel de un nombre flotante, sin entidades de por
        //   medio.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camara.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camara.getPitch()));

        float alto = 1.6f;
        float ancho = alto * foto.ancho() / Math.max(1, foto.alto());
        float m = ancho / 2f;
        float n = alto / 2f;

        var tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE);
        var entrada = matrices.peek();
        // ⚠ El alfa vive aqui y no en la textura: la foto es una fotografia y
        //   su alfa no se toca -- que parezca un holograma es asunto de este
        //   dibujado, no del PNG.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.94f);
        RenderSystem.setShaderTexture(0, foto.textura());

        buffer.vertex(entrada, -m, n, 0f).texture(0f, 1f);
        buffer.vertex(entrada, m, n, 0f).texture(1f, 1f);
        buffer.vertex(entrada, m, -n, 0f).texture(1f, 0f);
        buffer.vertex(entrada, -m, -n, 0f).texture(0f, 0f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        matrices.pop();
    }
}
