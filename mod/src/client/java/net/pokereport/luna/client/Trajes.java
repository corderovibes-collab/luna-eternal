package net.pokereport.luna.client;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.pokereport.luna.LunaEternal;

/**
 * DIBUJA EL TRAJE DE RANGO SOBRE CADA JUGADOR.
 *
 * <h2>⚠⚠⚠ NO HAY OBJETO, IGUAL QUE CON LOS SOMBREROS</h2>
 *
 * La forma «normal» de hacer esto es una armadura de verdad, que es lo que hace
 * Diosesmon. Tiene tres consecuencias y ninguna vale aquí:
 *
 * <ul>
 *   <li>ocupa las cuatro ranuras de armadura: o llevas traje o llevas protección;</li>
 *   <li>se cae al morir, se comercia, se pierde;</li>
 *   <li>y sobre todo <b>se puede regalar</b> — con lo que el traje dejaría de
 *       venir del rango, que es lo único que lo sostiene.</li>
 * </ul>
 *
 * <p>Y hay una cuarta que es de infraestructura: <b>un objeto nuevo es una
 * entrada más en un registro que se sincroniza</b>, o sea que echa del servidor
 * a quien no se haya actualizado. Los veinte objetos de cinco trajes serían
 * veinte razones para dejar a alguien fuera. Aquí no se registra nada.
 *
 * <h2>⚠⚠ NO SE USA GECKOLIB, AUNQUE ESTÉ INSTALADO</h2>
 *
 * GeckoLib hace falta para modelos <b>animados</b>, y estos no lo están: son
 * cubos rígidos pegados a los huesos del jugador. Se leen del mismo
 * {@code .geo.json} que genera {@code tools/gen_trajes.py} y se convierten a
 * {@link ModelPart} de vainilla, que es exactamente lo que hace el dibujado de
 * armadura del propio Minecraft. Menos dependencias, y el arte no cambia.
 *
 * <h2>⚠⚠⚠ LA CONVERSIÓN DE COORDENADAS ES DONDE SE ROMPE TODO</h2>
 *
 * Bedrock (que es el formato del {@code .geo.json}) y Java miden al revés:
 *
 * <pre>
 *   Bedrock   Y hacia ARRIBA, origen en los pies (la cabeza está en y=24..32)
 *   Java      Y hacia ABAJO,  origen en el pivote del hueso
 * </pre>
 *
 * De ahí sale {@code y = 24 - oy - sy} y {@code z = -oz - sz}. Si se copia mal,
 * <b>no hay ningún error</b>: el traje sale del revés, o metido dentro del
 * cuerpo, o flotando debajo de los pies.
 */
public final class Trajes {

    private Trajes() {
    }

    /** uuid -> identificador del traje. Lo dice el servidor, siempre. */
    private static final Map<UUID, String> LLEVA = new ConcurrentHashMap<>();

    /** Lo que se está probando en la pantalla, que gana sobre lo que llevas. */
    private static volatile String previsualizado;

    /** id de traje -> modelo horneado, una vez. */
    private static final Map<String, Modelo> CACHE = new HashMap<>();

    private static final String[] PIEZAS = {"head", "body", "legs", "boots"};

    /**
     * Los huesos que existen, y a qué parte del jugador se pegan.
     *
     * <p>⚠ El orden no importa, pero los <b>nombres sí</b>: son los que escribe
     * {@code tools/trajes/modelo.py}. Si dejaran de coincidir, ese hueso
     * sencillamente no se dibujaría — sin error y sin traza.
     */
    private static final String[][] HUESOS = {
        {"armorHead", "head"},
        {"armorBody", "body"},
        {"armorRightArm", "rightArm"},
        {"armorLeftArm", "leftArm"},
        {"armorRightLeg", "rightLeg"},
        {"armorLeftLeg", "leftLeg"},
        {"armorRightBoot", "rightLeg"},
        {"armorLeftBoot", "leftLeg"},
    };

    /** Los pivotes de vainilla, ya convertidos a coordenadas Java. */
    private static final Map<String, float[]> PIVOTE = Map.of(
            "head", new float[] {0, 0, 0},
            "body", new float[] {0, 0, 0},
            "rightArm", new float[] {-5, 2, 0},
            "leftArm", new float[] {5, 2, 0},
            "rightLeg", new float[] {-1.9f, 12, 0},
            "leftLeg", new float[] {1.9f, 12, 0});

    private record Pieza(String hueso, String ancla, ModelPart parte,
                         Identifier textura) {}

    private record Modelo(List<Pieza> piezas) {}

    // ---- lo que dice el servidor -------------------------------------------

    public static void guardar(UUID jugador, String traje) {
        if (traje == null || traje.isEmpty()) {
            LLEVA.remove(jugador);
        } else {
            LLEVA.put(jugador, traje);
        }
    }

    public static String de(UUID jugador) {
        return LLEVA.get(jugador);
    }

    public static void previsualizar(String traje) {
        previsualizado = traje;
    }

    // ---- horneado -----------------------------------------------------------

    private static Modelo modelo(String id) {
        return CACHE.computeIfAbsent(id, Trajes::hornear);
    }

    private static Modelo hornear(String id) {
        var piezas = new ArrayList<Pieza>();
        var recursos = MinecraftClient.getInstance().getResourceManager();
        for (String pieza : PIEZAS) {
            // ⚠⚠⚠ `trajes/` Y NO `geo/`. GeckoLib valida TODO lo que hay en
            //    `assets/*/geo/` aunque no lo use nadie, y un modelo que no le
            //    guste NO da un aviso: tumba la recarga de recursos entera y el
            //    cliente se queda colgado en la pantalla de carga. Pasó de
            //    verdad el 2026-08-28.
            var ruta = Identifier.of(LunaEternal.MOD_ID,
                    "trajes/" + id + "/" + id + "_" + pieza + ".geo.json");
            var recurso = recursos.getResource(ruta);
            if (recurso.isEmpty()) {
                continue;   // esa pieza no existe en este traje: es legítimo
            }
            var textura = Identifier.of(LunaEternal.MOD_ID,
                    "textures/armor/" + id + "/" + id + "_" + pieza + ".png");
            try (var lector = new InputStreamReader(recurso.get().getInputStream())) {
                var raiz = JsonParser.parseReader(lector).getAsJsonObject();
                var geo = raiz.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
                int lado = geo.getAsJsonObject("description")
                        .get("texture_width").getAsInt();
                piezas.addAll(desdeGeo(geo.getAsJsonArray("bones"), lado, textura));
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo leer el traje {} ({})", id, pieza, e);
            }
        }
        // ⚠⚠ UN TRAJE SIN UN SOLO HUESO NO ES UN AVISO, ES UN FALLO. El
        //    servidor lo declaro `listo` y el jugador se lo va a poner: si el
        //    arte no esta, se equipa, se sincroniza, no da ningun error Y NO SE
        //    VE NADA. Es el fallo de los 62 cosmeticos que no existian, y por eso
        //    se dice a nivel de ERROR y no de info.
        if (piezas.isEmpty()) {
            LunaEternal.LOG.error("El traje {} NO TIENE ARTE: se declaro listo en el "
                    + "servidor pero no hay trajes/{}/ en el cliente", id, id);
        } else {
            LunaEternal.LOG.info("Traje {}: {} huesos horneados", id, piezas.size());
        }
        return new Modelo(piezas);
    }

    private static List<Pieza> desdeGeo(JsonArray huesos, int lado, Identifier textura) {
        var salida = new ArrayList<Pieza>();
        for (var elem : huesos) {
            var hueso = elem.getAsJsonObject();
            String nombre = hueso.get("name").getAsString();
            String ancla = null;
            for (String[] par : HUESOS) {
                if (par[0].equals(nombre)) {
                    ancla = par[1];
                }
            }
            if (ancla == null || !hueso.has("cubes")) {
                continue;   // las anclas `biped*` van vacías a propósito
            }
            float[] pv = PIVOTE.get(ancla);
            var datos = new ModelData();
            var constructor = ModelPartBuilder.create();
            for (var c : hueso.getAsJsonArray("cubes")) {
                var cubo = c.getAsJsonObject();
                float[] o = tres(cubo.getAsJsonArray("origin"));
                float[] s = tres(cubo.getAsJsonArray("size"));
                float[] uv = dos(cubo.getAsJsonArray("uv"));
                float inflate = cubo.has("inflate")
                        ? cubo.get("inflate").getAsFloat() : 0f;
                // ⚠⚠⚠ EL ESPEJO. Sin esto, un traje IMPORTADO de una skin de
                //    64x32 sale con los detalles del lado cambiado: en esas
                //    skins el brazo y la pierna izquierdos NO tienen dibujo
                //    propio --son el derecho reflejado-- y comparten las mismas
                //    UV con una instruccion de reflejo dentro.
                //    ⚠⚠ Y leerlo mal NO DA NINGUN ERROR: el traje se dibuja
                //       entero, con su silueta correcta, y solo las costuras
                //       caen al lado que no es. Se lee como «algo no cuadra»
                //       sin poder decir el que.
                //    ⚠ `mirrored` es de ida y vuelta: se apaga despues de cada
                //      cubo o se lo lleva TODO lo que venga detras en el mismo
                //      hueso.
                boolean espejo = cubo.has("mirror")
                        && cubo.get("mirror").getAsBoolean();
                if (espejo) {
                    constructor.mirrored();
                }
                // ⚠⚠ AQUI ESTA LA CONVERSION, Y ES LA LINEA QUE MAS DUELE SI SE
                //    EQUIVOCA: Bedrock mide Y hacia arriba desde los pies y Java
                //    hacia abajo desde el pivote del hueso.
                constructor.uv((int) uv[0], (int) uv[1]).cuboid(
                        o[0] - pv[0],
                        (24f - o[1] - s[1]) - pv[1],
                        (-o[2] - s[2]) - pv[2],
                        s[0], s[1], s[2], new Dilation(inflate));
                if (espejo) {
                    constructor.mirrored(false);
                }
            }
            datos.getRoot().addChild(nombre, constructor,
                    ModelTransform.pivot(pv[0], pv[1], pv[2]));
            var parte = TexturedModelData.of(datos, lado, lado)
                    .createModel().getChild(nombre);
            salida.add(new Pieza(nombre, ancla, parte, textura));
        }
        return salida;
    }

    private static float[] tres(JsonArray a) {
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(),
                            a.get(2).getAsFloat()};
    }

    private static float[] dos(JsonArray a) {
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat()};
    }

    // ---- dibujado -----------------------------------------------------------

    public static void registrarDibujado() {
        net.fabricmc.fabric.api.client.rendering.v1
                .LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (tipo, renderizador, ayuda, ctx) -> {
                    if (renderizador instanceof
                            net.minecraft.client.render.entity.PlayerEntityRenderer pr) {
                        ayuda.register(new Capa(pr));
                    }
                });
        LunaEternal.LOG.info("Trajes: capa de dibujado registrada");
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
            // Lo que se prueba gana sobre lo que se lleva, pero SOLO en ti
            // mismo: si no, probarte uno se lo pondría a todo el mundo.
            boolean soyYo = jugador == MinecraftClient.getInstance().player;
            String id = soyYo && previsualizado != null
                    ? previsualizado : LLEVA.get(jugador.getUuid());
            if (id == null || id.isEmpty()
                    || jugador.isInvisible() || jugador.isSpectator()) {
                return;
            }
            var modelo = modelo(id);
            if (modelo.piezas().isEmpty()) {
                return;
            }
            var jm = getContextModel();
            for (Pieza p : modelo.piezas()) {
                // ⚠⚠ `copyTransform` ES LO QUE HACE QUE EL TRAJE SE MUEVA CON EL
                //    JUGADOR. Sin esto los cubos se quedan en pose de descanso
                //    mientras el cuerpo anda -- y eso NO da error, solo se ve
                //    ridículo. Es la misma vía que usa la armadura de vainilla.
                switch (p.ancla()) {
                    case "head" -> p.parte().copyTransform(jm.head);
                    case "body" -> p.parte().copyTransform(jm.body);
                    case "rightArm" -> p.parte().copyTransform(jm.rightArm);
                    case "leftArm" -> p.parte().copyTransform(jm.leftArm);
                    case "rightLeg" -> p.parte().copyTransform(jm.rightLeg);
                    case "leftLeg" -> p.parte().copyTransform(jm.leftLeg);
                    default -> { }
                }
                // ⚠ `getEntityCutoutNoCull` y no `getEntitySolid`: la textura
                //   tiene zonas transparentes (lo que no ocupa ningún cubo), y
                //   con la capa sólida saldrían como cuadros negros.
                var vertices = vc.getBuffer(RenderLayer.getEntityCutoutNoCull(p.textura()));
                p.parte().render(m, vertices, luz, OverlayTexture.DEFAULT_UV);
            }
        }
    }
}
