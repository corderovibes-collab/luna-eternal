package net.pokereport.neon;

import java.lang.reflect.Field;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Los retoques que solo tienen sentido en la pantalla del jugador.
 *
 * <p>Hoy solo uno: <b>el haz de la Poké Ball deja de ser rojo</b>.
 */
public class LunaNeonCliente implements ClientModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger("LunaNeon");

    /** Dónde vive el color, dentro de Cobblemon. */
    private static final String CLASE =
            "com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer";
    private static final String CAMPO = "recallBeamColour";

    /**
     * Azul luna, el mismo de la pantalla de la Pokédex (#8B93D8).
     *
     * <p>El original es {@code (1, 0.1, 0.1)} — rojo saturado. Este es más
     * apagado a propósito: contra el cielo de noche fija de la ciudadela un
     * haz a tope de saturación se come todo lo demás.
     */
    private static final float R = 0.545F;
    private static final float G = 0.576F;
    private static final float B = 0.847F;

    @Override
    public void onInitializeClient() {
        // Se espera a que el cliente esté arrancado del todo. Tocarlo durante la
        // inicialización obligaría a cargar la clase del renderizador antes de
        // tiempo, y esa clase toca texturas y estilos que aún no están listos.
        // El haz no se dibuja hasta que alguien saca un Pokémon, así que llegamos
        // de sobra.
        ClientLifecycleEvents.CLIENT_STARTED.register(cliente -> tenirHaz());
    }

    /**
     * Cambia el color del haz de la Poké Ball, el de sacar y el de guardar.
     *
     * <p><b>Por qué por reflexión y no compilando contra Cobblemon:</b> esto es
     * un campo interno de su renderizador, no una API que ellos mantengan. Con
     * una dependencia de compilación, el día que lo renombren nuestro mod
     * reventaría en la pantalla del jugador. Así, como mucho, el haz se queda
     * rojo y queda una línea en el log.
     *
     * <p><b>Y por qué basta con esto, sin mixin:</b> el campo es un
     * {@link Vector4f}, que es <i>mutable</i>. En Kotlin {@code val} fija la
     * referencia, no el contenido: se le puede cambiar el valor por dentro.
     *
     * <p>No se puede hacer con un resource pack, y merece la pena saber por qué:
     * la textura del haz ({@code phase_beam.png}) es <b>blanca</b>, y el rojo
     * sale de multiplicarla por ese vector. Repintar la textura de cian daría
     * un haz casi negro, porque el verde y el azul quedan al 10 %.
     */
    private static void tenirHaz() {
        try {
            Field campo = Class.forName(CLASE).getDeclaredField(CAMPO);
            campo.setAccessible(true);
            Object valor = campo.get(null);
            if (valor instanceof Vector4f color) {
                color.set(R, G, B, 1F);
                LOG.info("Haz de la Poke Ball: azul luna");
            } else {
                LOG.warn("El haz de la Poke Ball no es un Vector4f ({}), se deja rojo",
                        valor == null ? "null" : valor.getClass().getName());
            }
        } catch (Throwable e) {
            // Cobblemon lo ha movido, renombrado o no está. No es motivo para
            // estropearle la partida a nadie.
            LOG.warn("No se pudo tenir el haz de la Poke Ball ({}), se deja rojo: {}",
                    CLASE + "#" + CAMPO, e.toString());
        }
    }
}
