package net.pokereport.luna.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.pokereport.luna.cosmetics.Catalogo;

/**
 * Dibuja los sombreros sobre la cabeza de cada jugador.
 *
 * <h2>⚠⚠ NO HAY OBJETO, Y ESE ES TODO EL PUNTO</h2>
 *
 * Los dos packs de los que sale el arte lo aplican con un <b>objeto</b>:
 * CobbleHats con una calabaza tallada con {@code CustomModelData}, Cobblemon
 * Accessories con CIT Resewn sobre un casco. Las dos formas tienen las mismas
 * tres consecuencias, y ninguna es aceptable aquí:
 *
 * <ul>
 *   <li>ocupa la ranura del casco: o llevas sombrero o llevas protección;</li>
 *   <li>se cae al morir, se comercia, se pierde;</li>
 *   <li>y sobre todo <b>se puede regalar</b>, con lo que el cosmético dejaría de
 *       venir solo de LunaCoins o de eventos — que es lo que dice D-039.</li>
 * </ul>
 *
 * <p>Aquí no existe ningún objeto. El servidor dice quién lleva cuál, el cliente
 * lo dibuja, y no hay nada que se pueda quitar, tirar ni intercambiar. Es
 * exactamente el mismo razonamiento por el que el datapack de
 * CobblemonMoreCosmetics <b>no se instala</b>: no se vigila la puerta, se quita.
 *
 * <h2>Cómo se dibuja</h2>
 *
 * El modelo se hornea como cualquier modelo de objeto —se registra en
 * {@link #registrarModelos} para que el juego lo cargue— y se pinta con el
 * {@code ItemRenderer} sobre el hueso de la cabeza. Así hereda la rotación y el
 * balanceo de la cabeza sin código de animación propio: cuando el jugador mira
 * hacia arriba, el sombrero mira hacia arriba.
 */
public final class Sombreros {

    private Sombreros() {
    }

    /**
     * Quién lleva qué. <b>Concurrente</b> porque se escribe desde el hilo de red y
     * se lee desde el de render.
     */
    private static final Map<UUID, String> LLEVA = new ConcurrentHashMap<>();

    public static void recibir(UUID jugador, String sombrero) {
        if (sombrero == null || sombrero.isEmpty()) {
            LLEVA.remove(jugador);
        } else {
            LLEVA.put(jugador, sombrero);
        }
    }

    public static void olvidarTodo() {
        LLEVA.clear();
        previsualizado = null;
    }

    /**
     * El sombrero que se está PROBANDO en una pantalla, en vez del que se lleva.
     *
     * <p>⚠ Una variable estática y no un parámetro, porque el dibujado del
     * jugador pasa por {@code InventoryScreen.drawEntity} — código de vanilla que
     * no admite pasarle nada nuestro. El dibujado es <b>síncrono y en el hilo de
     * render</b>, así que poner, dibujar y limpiar no se solapa con nada.
     *
     * <p>Y por eso {@link #probar} se llama SIEMPRE en par con {@link #dejarDeProbar}:
     * si se quedara puesta, el jugador vería en el mundo el último sombrero que
     * miró en la tienda sin haberlo comprado.
     */
    private static volatile String previsualizado;

    public static void probar(String cosmeticId) {
        previsualizado = cosmeticId;
    }

    public static void dejarDeProbar() {
        previsualizado = null;
    }

    /** El identificador del modelo de un sombrero, a partir del de la pieza. */
    public static Identifier modeloDe(String cosmeticId) {
        // `sombrero_ash_journey` -> `lunaeternal:sombreros/ash_journey`
        String corto = cosmeticId.startsWith("sombrero_")
                ? cosmeticId.substring("sombrero_".length())
                : cosmeticId;
        return Identifier.of("lunaeternal", "sombreros/" + corto);
    }

    /**
     * Le dice al juego que hornee nuestros modelos.
     *
     * <p>⚠ <b>Sin esto no falla: sale un cubo morado y negro.</b> Un modelo que no
     * se ha registrado no se hornea, y {@code getModel} devuelve el modelo de
     * «falta esto» en vez de {@code null}. O sea que el síntoma no es una
     * excepción sino un sombrero que parece un error de textura, y a eso se le
     * busca en el sitio equivocado.
     *
     * <p>La lista sale del CATÁLOGO y no de recorrer los ficheros: así un
     * sombrero que esté en la tienda y no en el resource pack se nota al
     * arrancar, no cuando alguien lo compra.
     */
    public static void registrarModelos() {
        List<Identifier> ids = new ArrayList<>();
        for (var pieza : Catalogo.todas()) {
            if (Catalogo.SOMBREROS.equals(pieza.categoria())) {
                ids.add(modeloDe(pieza.id()));
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        ModelLoadingPlugin.register(ctx -> ctx.addModels(ids));
    }

    /** Engancha el dibujado a los dos modelos de jugador (normal y de brazo fino). */
    public static void registrarDibujado() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (tipo, renderizador, ayuda, ctx) -> {
                    if (renderizador instanceof
                            net.minecraft.client.render.entity.PlayerEntityRenderer pr) {
                        pr.addFeature(new Capa(pr));
                    }
                });
    }

    private static final class Capa
            extends FeatureRenderer<AbstractClientPlayerEntity,
                                    PlayerEntityModel<AbstractClientPlayerEntity>> {

        Capa(FeatureRendererContext<AbstractClientPlayerEntity,
                PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack m, VertexConsumerProvider vc, int luz,
                           AbstractClientPlayerEntity jugador,
                           float pasoAnterior, float paso, float delta,
                           float edad, float guiñada, float cabeceo) {
            // Lo que se está probando gana sobre lo que se lleva: en la tienda,
            // la celda enseña ESE sombrero aunque lleves otro puesto.
            String id = previsualizado != null ? previsualizado : LLEVA.get(jugador.getUuid());
            if (id == null || jugador.isInvisible() || jugador.isSpectator()) {
                return;
            }
            var cliente = MinecraftClient.getInstance();
            var horneado = cliente.getBakedModelManager()
                    .getModel(ModelIdentifier.ofInventoryVariant(modeloDe(id)));
            if (horneado == null) {
                return;
            }

            m.push();
            // ⚠ SE ENGANCHA AL HUESO DE LA CABEZA, no a la posición del jugador.
            //   Así el sombrero hereda la rotación y el balanceo sin una línea de
            //   animación: si la cabeza mira arriba, el sombrero mira arriba. Y
            //   funciona igual agachado, nadando y durmiendo, que son tres casos
            //   que a mano habría que tratar uno por uno.
            getContextModel().head.rotate(m);

            // Los modelos vienen pensados para dibujarse como un objeto en la
            // cabeza, así que el centro está en (0.5, 0.5, 0.5) de su cubo.
            m.translate(0.0, -0.25, 0.0);
            m.scale(0.625f, -0.625f, -0.625f);
            m.translate(-0.5, -0.5, -0.5);

            cliente.getItemRenderer().renderItem(
                    ItemStack.EMPTY, ModelTransformationMode.HEAD, false,
                    m, vc, luz, OverlayTexture.DEFAULT_UV, horneado);
            m.pop();
        }
    }

}
