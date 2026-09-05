package net.pokereport.luna.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.client.pokepad.MemorialScreen;
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
 * holograma brilla igual de noche, y con su tinte azulado y semitransparente,
 * da el aspecto de holograma que un memorial necesita.
 *
 * <h2>⚠ EL CLIC DERECHO SOBRE EL HOLOGRAMA, SIN ENTIDAD DEBAJO</h2>
 *
 * Como no hay entidad, el clic se calcula aqui: cada tick se mira si el jugador
 * pulso usar con la mano vacia y si su rayo cruza el mismo quad que se dibuja
 * (misma posicion, mismo tamaño, mismos ejes). Si cruza, se abre el memorial
 * con la foto y la descripcion. Con algo en la mano el clic es del objeto
 * (una Pokeball, un bloque) y no se roba.
 */
public final class HologramaSantuario {

    private HologramaSantuario() {}

    /** True si el boton de usar estaba pulsado en el tick anterior: el flanco
     *  se detecta con {@code isPressed} para no depender de quien consuma
     *  antes la pulsacion de vainilla. */
    private static boolean usoAntes = false;

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
        ClientTickEvents.END_CLIENT_TICK.register(HologramaSantuario::clicSiToca);
    }

    /**
     * El clic derecho con la mano vacia sobre un holograma abre su memorial.
     *
     * <p>⚠ Sin entidad, el clic no lo puede cortar nadie: aqui se repite la
     * geometria del dibujado --mismo centro, mismo tamaño, mismos ejes de
     * camara-- y si el rayo del jugador cruza el quad, se abre la pantalla
     * con la foto y la descripcion. Y si el crosshair apunta a un bloque mas
     * cerca, el holograma queda detras y no cuenta.
     */
    private static void clicSiToca(MinecraftClient cliente) {
        boolean ahora = cliente.options.useKey.isPressed();
        if (!ahora || usoAntes) {
            usoAntes = ahora;
            return;
        }
        usoAntes = true;
        if (cliente.currentScreen != null || cliente.player == null || cliente.world == null) {
            return;
        }
        // ⚠ Con algo en la mano, el clic derecho es del objeto (una Pokeball,
        //   un bloque, comida): abrir el memorial encima seria robarselo.
        if (!cliente.player.getMainHandStack().isEmpty()) {
            return;
        }
        var estado = EstadoCliente.santuario();
        if (estado == null) {
            return;
        }
        var camara = cliente.gameRenderer.getCamera();
        Vec3d origen = camara.getPos();
        Vec3d direccion = Vec3d.fromPolar(cliente.player.getPitch(),
                cliente.player.getYaw());
        double tope = 8.0;
        if (cliente.crosshairTarget instanceof BlockHitResult bloque) {
            tope = Math.min(tope, bloque.getPos().subtract(origen).length());
        }
        for (var nicho : estado.nichos()) {
            if (nicho.estado().dueno().isEmpty() || nicho.memorial().foto().isEmpty()) {
                continue;
            }
            var foto = TexturasFoto.lista(nicho.memorial().foto());
            if (foto == null) {
                // Sin textura no hay holograma dibujado al que apuntar.
                continue;
            }
            var p = nicho.pos();
            Vec3d centro = new Vec3d(p.x() + 0.5, p.y() + 1.55, p.z() + 0.5);
            Vec3d hacia = centro.subtract(origen);
            double t = hacia.dotProduct(direccion);
            if (t < 0.4 || t > tope) {
                continue;
            }
            // El quad mira a la camara: sus ejes son la derecha y el arriba
            // de la vista, no los del mundo.
            Vec3d derecha = direccion.crossProduct(new Vec3d(0, 1, 0));
            if (derecha.lengthSquared() < 1.0E-6) {
                derecha = new Vec3d(1, 0, 0);
            }
            derecha = derecha.normalize();
            Vec3d arriba = derecha.crossProduct(direccion);
            Vec3d tocado = origen.add(direccion.multiply(t)).subtract(centro);
            float ancho = 1.6f * foto.ancho() / Math.max(1, foto.alto());
            if (Math.abs(tocado.dotProduct(derecha)) <= ancho / 2
                    && Math.abs(tocado.dotProduct(arriba)) <= 0.8) {
                cliente.setScreen(new MemorialScreen(null, nicho));
                return;
            }
        }
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
        // ⚠ TONO HOLOGRAFICO: ligeramente azulado y semitransparente para que
        //   no parezca una pantalla LED. El 0.92 de alfa deja ver el mundo
        //   detras, y el tinte frio (0.82, 0.84, 0.90) da el aspecto de
        //   holograma sin perder legibilidad.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(0.82f, 0.84f, 0.90f, 0.92f);
        RenderSystem.setShaderTexture(0, foto.textura());

        // ⚠⚠ LA V IBA AL REVES, y la foto salia boca abajo. El quad usa el
        //    mismo marco que los carteles de nombre (giro por -yaw y pitch) y
        //    en el la v pequeña es el borde SUPERIOR de la imagen -- los
        //    glifos de vainilla pintan arriba con su v de arriba. Aqui la base
        //    del quad llevaba v=0. La u ya estaba bien: -X del quad cae a la
        //    izquierda de quien mira y lleva el borde izquierdo de la imagen.
        buffer.vertex(entrada, -m, n, 0f).texture(0f, 0f);
        buffer.vertex(entrada, m, n, 0f).texture(1f, 0f);
        buffer.vertex(entrada, m, -n, 0f).texture(1f, 1f);
        buffer.vertex(entrada, -m, -n, 0f).texture(0f, 1f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        matrices.pop();
    }
}
