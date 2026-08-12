package net.pokereport.luna.client;

import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.util.math.QuaternionUtilsKt;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Dibuja un Pokémon en 3D dentro del Pad.
 *
 * <p>Esto no es un sprite: es el <b>modelo real</b> de Cobblemon, con su
 * animación de reposo, girado en tres cuartos. Es lo que hace que la pantalla
 * de Cazas o la de Tesoros se vea como en un juego y no como una lista.
 *
 * <h2>Cómo se averiguó la llamada</h2>
 *
 * La firma se saco del <b>jar de 1.7.3</b>, no del repositorio: el {@code main}
 * de Cobblemon va por 1.8.0 y ahí este método tiene un parámetro distinto
 * ({@code ProfileTransformType} en vez de un booleano). Compilar contra el
 * fuente equivocado habría dado un {@code NoSuchMethodError} <b>en ejecución</b>,
 * que es el peor momento para enterarse.
 *
 * <p>Los valores de rotación y escala salen de un llamador real de Cobblemon
 * ({@code StorageSlot}), leído del bytecode. No son inventados.
 *
 * <p>Se llama al método completo, con los 16 parámetros. Kotlin genera un
 * {@code $default} para los opcionales, pero es sintético y Java no puede
 * invocarlo.
 */
public final class PokemonRender {

    /**
     * Un estado de animación por especie.
     *
     * <p>Hace falta porque el estado guarda en qué punto de la animación va.
     * Si se creara uno nuevo en cada fotograma, el Pokémon se quedaría
     * congelado en el primer instante — parecería una imagen fija.
     */
    private static final Map<String, PosableState> ESTADOS = new HashMap<>();

    /** Ángulos de tres cuartos, los mismos que usa Cobblemon. */
    private static final Vector3f ANGULOS = new Vector3f(13F, 35F, 0F);

    /**
     * Una rotación NUEVA en cada llamada. Esto no es derroche: es la causa
     * del parpadeo.
     *
     * <p>{@code drawProfilePokemon} hace {@code rotation.conjugate()}, que
     * <b>modifica el objeto que recibe</b>. Con una constante compartida, la
     * rotación se invertía en cada llamada: tres modelos a sesenta fotogramas
     * por segundo la dejaban oscilando sin parar. Cobblemon crea una nueva en
     * cada fotograma por exactamente este motivo.
     */
    private static Quaternionf rotacion() {
        return QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), ANGULOS);
    }

    private PokemonRender() {}

    /**
     * @param especie nombre corto: «pikachu», «snorlax»
     * @param x       centro horizontal, en píxeles de interfaz
     * @param y       base del modelo
     * @param lado    lado de la celda: de ahí sale la escala
     */
    public static void dibujar(DrawContext ctx, String especie,
                               int x, int y, int lado, float delta) {
        PosableState estado = ESTADOS.computeIfAbsent(especie,
                                                      k -> new FloatingState());
        var matrices = ctx.getMatrices();

        // Recorte a la celda. Ademas de evitar que un Snorlax invada la de al
        // lado, es lo que quita el parpadeo: sin el, el modelo se dibuja fuera
        // y se pelea con lo que ya habia pintado ahi.
        // enableScissor ya vuelca el lote pendiente por dentro, asi que no
        // hace falta un ctx.draw() extra. El orden lo garantiza PadScreen,
        // que dibuja en tres pasadas: fondos, modelos y texto.
        ctx.enableScissor(x, y, x + lado, y + lado);
        matrices.push();

        // Cobblemon aplica DOS escalas: la de la matriz y la del propio
        // metodo. Yo solo pasaba la segunda, y por eso salia diminuto.
        // Referencia: StorageSlot usa SIZE=25, scale(2,5) y scale=4,5F.
        matrices.translate(x + lado / 2.0, y + lado * 0.06, 0);
        float k = lado / 25F * 2.5F;
        matrices.scale(k, k, 1F);
        try {
            PokemonGuiUtilsKt.drawProfilePokemon(
                Identifier.of("cobblemon", especie),
                matrices,
                rotacion(),
                PoseType.PROFILE,
                estado,
                delta,
                4.5F,
                true,    // applyProfileTransform
                false,   // applyBaseScale
                false,   // doQuirks
                1F, 1F, 1F, 1F,   // r g b a — el default es 1, no 0
                0F, 0F);          // headYaw, headPitch
        } catch (Throwable t) {
            // Una especie mal escrita o un modelo que falta no puede tumbar
            // la pantalla entera: se deja el hueco y se sigue.
            LunaClient.LOG.warn("No se pudo dibujar '{}': {}", especie, t.toString());
        } finally {
            matrices.pop();
            ctx.disableScissor();
            // La iluminacion NO se toca aqui: drawProfilePokemon termina con
            // Lighting.setupFor3DItems() por su cuenta. Restaurarla ademas
            // desde fuera era pelearse con el.
        }
    }

    /** Al cerrar el Pad: si no, los estados se acumulan sin límite. */
    public static void olvidar() {
        ESTADOS.clear();
    }
}
