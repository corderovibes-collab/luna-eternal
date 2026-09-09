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

    /**
     * CUANTO MIDE DE ALTO LA FOTO, en bloques.
     *
     * <h2>&#9888;&#9888; ERA 1,6 Y SE VEIA PEQUEÑA (peticion del usuario)</h2>
     *
     * Un nicho construido es un hueco de 3 de ancho: una foto de 1,6 dejaba
     * medio hueco vacio alrededor y desde el pasillo no se distinguia de que
     * era. A 2,4 llena el nicho sin tocar sus paredes -- una apaisada 16:9 mide
     * 4,27 de ancho, asi que <b>se sale del 3x3 a proposito</b>: sobresalir por
     * delante es lo que la hace visible desde lejos, y por detras no hay nada
     * que tapar porque la pared queda a su espalda.
     */
    private static final float ALTO = 2.4f;

    /**
     * A que altura sobre el proyector flota su CENTRO.
     *
     * <p>&#9888; Sube de 1,55 a 1,75 para que, al crecer la foto, <b>el borde de
     * abajo se quede donde estaba</b> (0,75 sobre el pedestal) y todo lo que
     * gana lo gane hacia arriba. Bajar el centro con una foto mas alta la
     * habria metido dentro del pedestal.
     */
    private static final float ALTURA = 1.75f;

    /**
     * CUANTO SE SEPARA DE LA PARED DEL FONDO.
     *
     * <h2>&#9888;&#9888;&#9888; ESTO ES «LOS HOLOGRAMAS ESTAN MAL POSICIONADOS»</h2>
     *
     * El quad se dibujaba <b>centrado en el bloque del proyector</b>, y el
     * proyector de un nicho esta pegado a su pared del fondo. Como la foto
     * <b>gira para mirar a la camara</b>, en cuanto uno se pone de lado la mitad
     * del quad barre hacia dentro del muro y <b>desaparece dentro de la pared</b>:
     * se ve un trozo de foto recortado, y parece un fallo de dibujado.
     *
     * <p>La foto se adelanta 0,8 hacia donde el nicho se abre, asi que gira en
     * el aire del hueco y no dentro de la piedra. <b>La direccion la manda el
     * servidor</b> ({@code PosNicho.frente}), medida en bloques: aqui no se
     * deduce nada, porque el teletransporte usa esa misma direccion y las dos
     * tienen que estar de acuerdo.
     */
    private static final double SALIENTE = 0.8;

    private HologramaSantuario() {}

    /**
     * El centro exacto de la foto de un nicho.
     *
     * <p>&#9888;&#9888; <b>LO USAN EL DIBUJADO Y EL CLIC, y por eso esta aqui.</b>
     * La geometria estaba escrita dos veces --una en {@code dibujar} y otra en
     * {@code clicSiToca}-- con los numeros a mano en las dos. Nada obligaba a
     * que coincidieran: al mover la foto, el sitio donde hay que pulsar se
     * habria quedado donde estaba, <b>sin dar ningun error</b>. Es la leccion de
     * la rejilla del PokePad, donde el dibujado y el clic calculan la ranura
     * igual.
     */
    private static Vec3d centroDe(Red.PosNicho pos) {
        var frente = net.pokereport.luna.santuario.Orientacion.porIndice(pos.frente());
        return new Vec3d(
                pos.x() + 0.5 + frente.getOffsetX() * SALIENTE,
                pos.y() + ALTURA,
                pos.z() + 0.5 + frente.getOffsetZ() * SALIENTE);
    }

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
                if (dx * dx + dz * dz > 96 * 96) {
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
            Vec3d centro = centroDe(nicho.pos());
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
            float ancho = ALTO * foto.ancho() / Math.max(1, foto.alto());
            if (Math.abs(tocado.dotProduct(derecha)) <= ancho / 2
                    && Math.abs(tocado.dotProduct(arriba)) <= ALTO / 2) {
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

        Vec3d centro = centroDe(pos);
        matrices.push();
        matrices.translate(centro.x - posCam.x,
                centro.y + bob - posCam.y,
                centro.z - posCam.z);
        // ⚠ Orientar hacia la camara = girar el quad con SUS angulos. Es la
        //   tecnica del cartel de un nombre flotante, sin entidades de por
        //   medio.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camara.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camara.getPitch()));

        float alto = ALTO;
        float ancho = alto * foto.ancho() / Math.max(1, foto.alto());
        float m = ancho / 2f;
        float n = alto / 2f;

        var tess = Tessellator.getInstance();
        var entrada = matrices.peek();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        // ⚠ AURA BLANCA: marco luminoso suave alrededor de la foto.
        float g1 = 0.035f; // grosor del halo
        BufferBuilder auraBuffer = tess.begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        // Top aura
        auraBuffer.vertex(entrada, -m - g1, n + g1, -0.002f).color(1f, 1f, 1f, 0.45f);
        auraBuffer.vertex(entrada, m + g1, n + g1, -0.002f).color(1f, 1f, 1f, 0.45f);
        auraBuffer.vertex(entrada, m, n, -0.002f).color(1f, 1f, 1f, 0.85f);
        auraBuffer.vertex(entrada, -m, n, -0.002f).color(1f, 1f, 1f, 0.85f);
        // Bottom aura
        auraBuffer.vertex(entrada, -m, -n, -0.002f).color(1f, 1f, 1f, 0.85f);
        auraBuffer.vertex(entrada, m, -n, -0.002f).color(1f, 1f, 1f, 0.85f);
        auraBuffer.vertex(entrada, m + g1, -n - g1, -0.002f).color(1f, 1f, 1f, 0.45f);
        auraBuffer.vertex(entrada, -m - g1, -n - g1, -0.002f).color(1f, 1f, 1f, 0.45f);
        // Left aura
        auraBuffer.vertex(entrada, -m - g1, n + g1, -0.002f).color(1f, 1f, 1f, 0.45f);
        auraBuffer.vertex(entrada, -m, n, -0.002f).color(1f, 1f, 1f, 0.85f);
        auraBuffer.vertex(entrada, -m, -n, -0.002f).color(1f, 1f, 1f, 0.85f);
        auraBuffer.vertex(entrada, -m - g1, -n - g1, -0.002f).color(1f, 1f, 1f, 0.45f);
        // Right aura
        auraBuffer.vertex(entrada, m, n, -0.002f).color(1f, 1f, 1f, 0.85f);
        auraBuffer.vertex(entrada, m + g1, n + g1, -0.002f).color(1f, 1f, 1f, 0.45f);
        auraBuffer.vertex(entrada, m + g1, -n - g1, -0.002f).color(1f, 1f, 1f, 0.45f);
        auraBuffer.vertex(entrada, m, -n, -0.002f).color(1f, 1f, 1f, 0.85f);

        // ⚠ BRILLITOS ESTELARES ✨: pequeños destellos tipo estrella que titilan
        //   alrededor del marco del memorial.
        float[][] estrellas = {
            {-m - 0.02f, n + 0.02f, 0.0f, 0.045f},
            {m + 0.03f, n + 0.01f, 1.3f, 0.040f},
            {-m - 0.03f, -n - 0.01f, 2.7f, 0.042f},
            {m + 0.02f, -n - 0.03f, 4.1f, 0.048f},
            {m * 0.25f, n + 0.04f, 1.9f, 0.038f},
            {-m * 0.35f, -n - 0.04f, 3.4f, 0.035f}
        };
        for (float[] e : estrellas) {
            float ex = e[0], ey = e[1], fDesfase = e[2], baseR = e[3];
            float ciclo = (MathHelper.sin(tiempo * 2.8f + fDesfase) + 1f) * 0.5f;
            if (ciclo > 0.15f) {
                float alfaEstrella = Math.min(1f, ciclo * 1.3f);
                float rEstrella = baseR * (0.6f + 0.5f * ciclo);
                // Diamante brillante (rombo centrado en ex, ey)
                auraBuffer.vertex(entrada, ex, ey + rEstrella, 0.001f).color(1f, 1f, 1f, alfaEstrella);
                auraBuffer.vertex(entrada, ex + rEstrella * 0.5f, ey, 0.001f).color(1f, 1f, 1f, alfaEstrella);
                auraBuffer.vertex(entrada, ex, ey - rEstrella, 0.001f).color(1f, 1f, 1f, alfaEstrella);
                auraBuffer.vertex(entrada, ex - rEstrella * 0.5f, ey, 0.001f).color(1f, 1f, 1f, alfaEstrella);
            }
        }
        BufferRenderer.drawWithGlobalProgram(auraBuffer.end());

        // ⚠ LA FOTO TAL CUAL: 100% opaca, nítida, sin saturación ni niebla.
        BufferBuilder buffer = tess.begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, foto.textura());

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
