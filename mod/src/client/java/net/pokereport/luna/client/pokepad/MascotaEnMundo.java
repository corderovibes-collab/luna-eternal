package net.pokereport.luna.client.pokepad;

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Dibuja la mascota de cada jugador <b>al lado suyo, en el mundo</b>.
 *
 * <p>Es lo que convierte la tienda en un producto. Hasta ahora un cosmético
 * comprado solo lo veía su dueño dentro del PokePad, y {@code monetization.md}
 * lo dice con esas palabras: «un cosmético sin nadie que lo vea no vale nada».
 *
 * <h2>Solo cliente, sin entidad</h2>
 *
 * Decisión del usuario, y con motivo. Soltar un {@code PokemonEntity} de verdad
 * daría animaciones y sonidos gratis, pero cuesta tick de servidor por jugador y
 * obliga a resolver a mano qué pasa en combate, al morir, al cambiar de
 * dimensión, con el despawn, y si alguien intenta capturarla o pelear con ella.
 *
 * <p>Dibujada solo en el cliente <b>no existe</b>: no puede romper nada de la
 * jugabilidad porque no está ahí. El precio es este fichero.
 *
 * <h2>Por qué es un FeatureRenderer y no un evento de mundo</h2>
 *
 * Fabric permite añadir capas al renderizador del jugador sin mixins
 * ({@code LivingEntityFeatureRendererRegistrationCallback}). Es lo mismo que usa
 * Cobblemon para su Pokémon de hombro, y trae dos cosas gratis: se dibuja con la
 * matriz del jugador —así que <b>sigue al jugador sin código de seguimiento</b>—
 * y respeta su interpolación, así que no da tirones al moverse.
 */
public class MascotaEnMundo
        extends FeatureRenderer<AbstractClientPlayerEntity,
                                PlayerEntityModel<AbstractClientPlayerEntity>> {

    private static final Logger LOG = LoggerFactory.getLogger("LunaEternal");
    private static final Set<String> FALLIDOS = new HashSet<>();

    /**
     * Contexto de dibujado en MUNDO, no en interfaz.
     *
     * <p>⚠ {@code RenderState.WORLD} no es un detalle: con {@code PROFILE} el
     * modelo sale con la pose y las transformaciones del retrato, que están
     * pensadas para una ficha y no para andar por el suelo.
     */
    private final RenderContext contexto = new RenderContext();

    public MascotaEnMundo(FeatureRendererContext<AbstractClientPlayerEntity,
            PlayerEntityModel<AbstractClientPlayerEntity>> padre) {
        super(padre);
        contexto.put(RenderContext.Companion.getRENDER_STATE(),
                RenderContext.RenderState.WORLD);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider buffer, int luz,
                       AbstractClientPlayerEntity jugador,
                       float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {
        // ⚠ En 1.21.1 el FeatureRenderer recibe LA ENTIDAD. En 1.21.2 pasa a
        //   recibir un "render state" sin UUID, y entonces habria que buscar al
        //   jugador por nombre. Si algun dia se actualiza, este es el sitio.

        MascotasPuestas.Puesta puesta = MascotasPuestas.de(jugador.getUuid());
        if (puesta == null || puesta.vacia()) {
            return;
        }
        Identifier especie = Identifier.tryParse(puesta.especie());
        if (especie == null) {
            return;
        }

        FloatingState fs = MascotasPuestas.estado(jugador.getUuid());
        fs.setCurrentAspects(puesta.aspecto().isEmpty()
                ? Set.of() : Set.of(puesta.aspecto()));

        matrices.push();
        try {
            // A la izquierda del jugador y a sus pies. En coordenadas del modelo
            // del jugador el eje Y va HACIA ABAJO, de ahi el 1.5 positivo: es la
            // altura de un jugador, o sea el suelo.
            matrices.translate(-0.75, 1.5, 0.0);
            // Los modelos de Cobblemon vienen del revés respecto al del jugador.
            matrices.multiply(new org.joml.Quaternionf().rotationZ((float) Math.PI));

            // Pequeña: es una mascota. A tamaño real un Snorlax taparia a su
            // dueño, que es justo lo contrario de lo que se compra.
            matrices.scale(0.35f, 0.35f, 0.35f);

            PosableModel modelo = VaryingModelRepository.INSTANCE.getPoser(especie, fs);
            Identifier textura = VaryingModelRepository.INSTANCE.getTexture(especie, fs);

            modelo.setContext(contexto);
            contexto.put(RenderContext.Companion.getSPECIES(), especie);
            contexto.put(RenderContext.Companion.getASPECTS(), fs.getCurrentAspects());
            contexto.put(RenderContext.Companion.getPOSABLE_STATE(), fs);
            fs.setCurrentModel(modelo);

            modelo.applyAnimations(jugador, fs, limbAngle, limbDistance, 0f, 0f, 0f);

            VertexConsumer vc = buffer.getBuffer(RenderLayer.getEntityCutout(textura));
            modelo.withLayerContext(buffer, fs,
                    VaryingModelRepository.INSTANCE.getLayers(especie, fs),
                    () -> {
                        modelo.render(contexto, matrices, vc, luz,
                                OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
                        return kotlin.Unit.INSTANCE;
                    });
            modelo.setDefault();
        } catch (RuntimeException e) {
            // ⚠ NO SE PROPAGA. Esto corre dentro del renderizado del JUGADOR:
            // una excepción aquí no rompe una mascota, tumba el dibujado de esa
            // persona — y con suerte el del mundo entero. Un aspecto mal escrito
            // en el catálogo no puede costar eso.
            if (FALLIDOS.add(puesta.especie() + ":" + puesta.aspecto())) {
                LOG.warn("No se pudo dibujar la mascota {} de {}",
                        puesta.especie(), jugador.getGameProfile().getName(), e);
            }
        } finally {
            matrices.pop();
        }
    }
}
