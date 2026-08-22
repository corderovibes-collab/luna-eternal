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
     * Un estado por cosmético. No se limpia: son unas decenas de objetos
     * pequeños y vaciarlo al cambiar de pestaña costaría reiniciar la animación
     * cada vez que el jugador va y vuelve.
     */
    private static final Map<String, FloatingState> ESTADOS = new HashMap<>();

    /** Giro fijo: de perfil y ligeramente hacia el jugador, como el PC de Cobblemon. */
    private static final Quaternionf GIRO =
            new Quaternionf().rotationXYZ(0.35f, -0.55f, 0f);

    /**
     * Pinta el cosmético centrado en la caja dada, en coordenadas ya escaladas.
     *
     * @param escala tamaño del modelo. No es el de la caja: un Wailord y un
     *               Joltik con el mismo número salen igual de altos, que es lo
     *               que se quiere en una rejilla — si cada uno saliera a su
     *               tamaño real, media tienda serían motas
     */
    public static void dibujar(DrawContext ctx, Cosmetico c,
                               int x, int y, int ancho, int alto,
                               float escala, float delta) {
        if (!c.esMascota()) {
            return;
        }
        Identifier especie = Identifier.tryParse(c.especie());
        if (especie == null) {
            return;
        }

        FloatingState estado = ESTADOS.computeIfAbsent(c.id(), k -> new FloatingState());
        // El aspecto es lo que convierte un Charizard en `charizard_knight`.
        // Se reasigna en cada fotograma a propósito: es barato, y así un cambio
        // de aspecto se ve sin tener que invalidar el estado.
        estado.setCurrentAspects(c.aspecto().isEmpty()
                ? Set.of()
                : Set.of(c.aspecto()));

        // ⚠⚠ VACIAR EL BUFER DE LA INTERFAZ ANTES DEL 3D. ESTO ES LO QUE QUITA
        //    EL PARPADEO.
        //
        // `DrawContext` no dibuja al momento: acumula los rectangulos y texturas
        // en un bufer y los vuelca al final. `drawProfilePokemon`, en cambio,
        // dibuja YA, tocando la matriz y el estado de OpenGL por su cuenta.
        //
        // Mezclados, el orden de lo 2D y lo 3D cambia de un fotograma a otro
        // segun cuando le toque volcar al bufer: los modelos aparecian delante
        // y detras de las celdas alternativamente, y eso es lo que se ve como
        // titileo. No es el modelo: es quien pinta primero.
        //
        // Vaciando antes y despues, cada cosa cae en su sitio y el orden deja
        // de depender del azar.
        ctx.draw();

        // ⚠ RECORTE. Sin él, un modelo alto se sale de su celda y se dibuja
        // encima de la de al lado — y como el 3D no respeta el orden de dibujado
        // de la interfaz, tapa también los precios.
        ctx.enableScissor(x, y, x + ancho, y + alto);

        MatrixStack m = ctx.getMatrices();
        m.push();
        // Al centro de la caja, y un poco abajo: un modelo cuelga de su origen,
        // así que centrarlo de verdad lo deja flotando en la mitad de arriba.
        m.translate(x + ancho / 2.0, y + alto * 0.62, 100.0);

        try {
            PokemonGuiUtilsKt.drawProfilePokemon(
                    especie,            // species
                    m,                  // matrixStack
                    GIRO,               // rotation
                    PoseType.PROFILE,   // poseType
                    estado,             // state
                    delta,              // partialTicks
                    escala,             // scale
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
            // Y se vacia otra vez: lo que el modelo haya dejado en vuelo tiene
            // que salir ANTES de que la interfaz siga dibujando encima.
            ctx.draw();
            ctx.disableScissor();
        }
    }
}
