package net.pokereport.luna.client.pokepad;

import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Dibuja un Pokémon de Cobblemon en 3D dentro de una caja de la interfaz.
 *
 * <p><b>Todo el trato con Cobblemon vive aquí.</b> Si mañana cambia su API, se
 * rompe este fichero y no la pantalla entera — que es la diferencia entre un
 * rato y una tarde.
 *
 * <h2>Por qué se llama directamente y no por reflexión</h2>
 *
 * {@code Apps.abrirPokedex()} usa reflexión, y tiene motivo: allí hay que llegar
 * a un {@code object} de Kotlin y a un {@code companion} con parámetros por
 * defecto, que desde Java son un campo {@code INSTANCE} y un método sintético.
 *
 * Aquí no hace falta. {@code drawProfilePokemon} es una <b>función de nivel
 * superior</b>, que Kotlin compila a un método estático de
 * {@code PokemonGuiUtilsKt}, y Cobblemon entra como {@code modCompileOnly}, así
 * que está en el classpath de compilación. Loom lo remapea a los mapeos del
 * proyecto, de forma que aquí se ve con tipos Yarn ({@code MatrixStack},
 * {@code Identifier}) aunque su fuente esté en Mojmap.
 *
 * <p>⚠ <b>Hay que pasar los 16 parámetros.</b> En Kotlin casi todos tienen valor
 * por defecto; desde Java esos valores no existen y omitir uno no compila. Van
 * escritos con su nombre en un comentario porque son seis floats seguidos y
 * equivocarse de orden entre {@code r,g,b,a} y {@code headYaw,headPitch} no da
 * ningún error: sale un Pokémon torcido o de color raro.
 *
 * <h2>⚠⚠ {@code vendor/cobblemon} ES HEAD, NO LA VERSIÓN QUE USAMOS</h2>
 *
 * Esta llamada se escribió leyendo el fuente clonado y <b>no compiló</b>: allí
 * la función toma un {@code ProfileTransformType} y un {@code blockLight} que en
 * <b>1.7.3 no existen</b> — ahí son tres booleanos seguidos y catorce
 * parámetros menos uno. El clon se hizo con {@code --depth 1}, así que es la
 * punta del desarrollo, no la versión contra la que compila el mod.
 *
 * <p>D-021 dice que el fuente está clonado para «tener la verdad sobre IDs,
 * spawns y API sin adivinar», y sigue siendo cierto — pero la verdad es de OTRA
 * versión. Para una firma concreta, la fuente fiable es el jar instalado:
 *
 * <pre>
 * javap -classpath Cobblemon-fabric-1.7.3+1.21.1.jar
 *       com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt
 * </pre>
 *
 * o el fuente en la etiqueta exacta:
 * {@code gitlab.com/cable-mc/cobblemon/-/raw/1.7.3/...}
 *
 * <h2>El estado se guarda, no se crea en cada fotograma</h2>
 *
 * {@code FloatingState} lleva la animación, la pose y los aspectos actuales.
 * Creándolo dentro de {@code render} el modelo se reiniciaría 60 veces por
 * segundo: se quedaría clavado en el primer fotograma de su animación y ninguna
 * transición llegaría a verse. Se guarda uno por celda, con la clave del
 * cosmético.
 */
public final class Mascota3D {

    private Mascota3D() {
    }

    private static final Logger LOG = LoggerFactory.getLogger("LunaEternal");

    /** Cosmeticos que ya han fallado. Ver el `catch` de `dibujar`. */
    private static final Set<String> FALLIDOS = new HashSet<>();

    /**
     * Un estado por RANURA Y cosmético, no solo por cosmético.
     *
     * <p>⚠⚠ AQUI ESTABA EL TITILEO DEL PREVISUALIZADOR, Y ES SUTIL.
     *
     * Con la clave siendo solo el identificador, el cosmetico que estas
     * previsualizando comparte estado con SU PROPIA CELDA de la rejilla —
     * porque el que miras sigue estando ahi. El mismo {@code FloatingState} se
     * dibujaba DOS VECES en el mismo fotograma, con escalas, anclajes y
     * animacion distintos, y cada pasada pisaba lo que la otra acababa de
     * escribir: {@code currentModel}, la pose y el reloj de animacion.
     *
     * <p>Encaja exacto con el sintoma: la rejilla sola no titilaba, y empezaba
     * justo al pulsar un cosmetico y verlo en el panel. No era el dibujado —
     * era un estado compartido entre dos sitios que lo usan distinto.
     *
     * <p>No se limpia: son unas decenas de objetos pequeños, y vaciarlo al
     * cambiar de pestaña costaría reiniciar la animación cada vez que el
     * jugador va y vuelve.
     */
    private static final Map<String, FloatingState> ESTADOS = new HashMap<>();

    /** Giro fijo: de perfil y ligeramente hacia el jugador, como el PC de Cobblemon. */
    private static final Quaternionf GIRO =
            new Quaternionf().rotationXYZ(0.35f, -0.55f, 0f);

    /**
     * Pinta el cosmético centrado en la caja dada, en coordenadas ya escaladas.
     *
     * @param animar solo el que esta bajo el raton se anima. Es lo que hace el
     *               PC de Cobblemon, y no es solo por rendimiento: ocho modelos
     *               animandose a la vez en una rejilla es ruido visual, y con la
     *               animacion parada se aprecia mejor el disfraz, que es lo que
     *               se esta vendiendo
     * @param origenY donde cae el ORIGEN del modelo dentro de la caja, de 0
     *                (arriba) a 1 (abajo). El modelo cuelga hacia abajo desde
     *                ahi, asi que un valor alto lo saca por el pie
     * @param animar solo el que esta bajo el raton se anima. Es lo que hace el un Wailord y un
     *               Joltik con el mismo número salen igual de altos, que es lo
     *               que se quiere en una rejilla — si cada uno saliera a su
     *               tamaño real, media tienda serían motas
     */
    public static void dibujar(DrawContext ctx, Cosmetico c, String ranura,
                               int x, int y, int ancho, int alto,
                               float origenY, float delta, boolean animar) {
        if (!c.esMascota()) {
            return;
        }
        Identifier especie = Identifier.tryParse(c.especie());
        if (especie == null) {
            return;
        }
        if (c.esDeMinecraft()) {
            dibujarCriatura(ctx, c, ranura, especie, x, y, ancho, alto);
            return;
        }

        FloatingState estado = ESTADOS.computeIfAbsent(ranura + ":" + c.id(),
                k -> new FloatingState());
        // El aspecto es lo que convierte un Charizard en `charizard_knight`.
        // Se reasigna en cada fotograma a propósito: es barato, y así un cambio
        // de aspecto se ve sin tener que invalidar el estado.
        estado.setCurrentAspects(c.aspecto().isEmpty()
                ? Set.of()
                : Set.of(c.aspecto()));

        // ⚠ RECORTE. Sin él, un modelo alto se sale de su celda y se dibuja
        // encima de la de al lado — y como el 3D no respeta el orden de dibujado
        // de la interfaz, tapa también los precios.
        ctx.enableScissor(x, y, x + ancho, y + alto);

        MatrixStack m = ctx.getMatrices();
        m.push();
        // ⚠⚠ EL MODELO CUELGA HACIA ABAJO DESDE SU ORIGEN, Y ESO CAMBIA DONDE
        //    HAY QUE PONERLO.
        //
        // El PC de Cobblemon traslada a `posY + 1.0` sobre una celda de 25: el
        // origen va casi ARRIBA (4 %), no en el centro. Yo lo ponia al 62 %, y
        // por eso los Pokemon salian bajos y cortados por el pie de la celda --
        // no era la escala, era el punto de anclaje.
        //
        // La escala tambien sale de ellos: 2.5 de matriz por 4.5 de parametro
        // sobre 25 px, o sea 0,45 por pixel de caja.
        m.translate(x + ancho / 2.0, y + alto * origenY, 0.0);

        try {
            PokemonGuiUtilsKt.drawProfilePokemon(
                    especie,            // species
                    m,                  // matrixStack
                    GIRO,               // rotation
                    PoseType.PROFILE,   // poseType
                    estado,             // state
                    animar ? delta : 0f, // partialTicks: ver `animar`
                    Math.min(ancho, alto) * 0.45f,  // scale: la proporcion de Cobblemon
                    true,               // applyProfileTransform
                    false,              // applyBaseScale: ver el javadoc de `escala`
                    false,              // doQuirks: los tics de idle distraen en una rejilla
                    1f, 1f, 1f, 1f,     // r, g, b, a
                    0f,                 // headYaw
                    0f);                // headPitch
        } catch (RuntimeException e) {
            // ⚠ NO SE DEJA PROPAGAR. Esto corre dentro de `render`, así que una
            // excepción aquí no rompe una celda: tumba la pantalla entera y saca
            // al jugador al menú con un volcado. Una especie mal escrita en el
            // catálogo del servidor no puede costar eso.
            //
            // Y se avisa UNA VEZ por cosmético, no en cada fotograma: a 60 fps
            // un fallo llenaría el log con miles de líneas iguales y taparía
            // cualquier otra cosa que hubiera pasado.
            if (FALLIDOS.add(c.id())) {
                LOG.warn("PokePad: no se pudo dibujar el cosmético {}", c.id(), e);
            }
        } finally {
            m.pop();
            ctx.disableScissor();
        }
    }

    /**
     * Una criatura de Minecraft, con el renderizador de entidades de vanilla.
     *
     * <p>Peticion del usuario: "mascotas de verdad", bichos pequeños que no
     * compiten con el Pokemon que llevas.
     *
     * <p>⚠ LA ENTIDAD SE GUARDA, NO SE CREA EN CADA FOTOGRAMA. Crear una
     * entidad reserva memoria, registra sus datos rastreados y corre su
     * constructor: hacerlo 60 veces por segundo y por celda es basura para el
     * recolector a cambio de nada. Y ademas se perderia la pose, asi que la
     * abeja no batiria las alas.
     *
     * <p>⚠ Se crean con el mundo del CLIENTE y no se añaden a el. Son una
     * maqueta para dibujar: si entraran en el mundo, tendrian fisica, colisiones
     * y saldrian en las estadisticas.
     */
    private static void dibujarCriatura(DrawContext ctx, Cosmetico c, String ranura,
                                        Identifier id,
                                        int x, int y, int ancho, int alto) {
        var cliente = net.minecraft.client.MinecraftClient.getInstance();
        if (cliente.world == null) {
            return;
        }
        String clave = ranura + ":" + c.id();
        net.minecraft.entity.LivingEntity ent = CRIATURAS.get(clave);
        if (ent == null) {
            var tipo = net.minecraft.registry.Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);
            if (tipo == null) {
                if (FALLIDOS.add(c.id())) {
                    LOG.warn("PokePad: la criatura {} no existe", c.especie());
                }
                return;
            }
            var creada = tipo.create(cliente.world);
            if (!(creada instanceof net.minecraft.entity.LivingEntity viva)) {
                if (FALLIDOS.add(c.id())) {
                    LOG.warn("PokePad: {} no es una criatura viva", c.especie());
                }
                return;
            }
            ent = viva;
            CRIATURAS.put(clave, ent);
        }

        // `drawEntity` recorta por si mismo con el rectangulo que se le pasa, y
        // mira al raton -- pasandole el centro, la criatura mira de frente en vez
        // de seguir el cursor por toda la pantalla.
        int cx = x + ancho / 2, cy = y + alto / 2;
        net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(
                ctx, x, y, x + ancho, y + alto,
                Math.round(Math.min(ancho, alto) * 0.30f), 0.0f, cx, cy, ent);
    }

    /** Una criatura por cosmetico. Ver `dibujarCriatura`. */
    private static final Map<String, net.minecraft.entity.LivingEntity> CRIATURAS =
            new HashMap<>();
}
