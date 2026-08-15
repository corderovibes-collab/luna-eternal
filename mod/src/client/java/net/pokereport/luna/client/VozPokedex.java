package net.pokereport.luna.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.pokereport.luna.pokedex.Cooldown;
import net.pokereport.luna.pokedex.VozService;

/**
 * Reproduce la descripción hablada de una especie, para este jugador y ya.
 *
 * <p><b>Suena como sonido «master», no en el mundo.</b> Un sonido colocado en el
 * espacio se atenúa con la distancia y se va a un lado según hacia dónde mires,
 * y eso en una narración es justo lo que no se quiere: escaneas a diez bloques,
 * te giras, y dejas de entender lo que dice. Un master suena igual pase lo que
 * pase, como la música o los sonidos de menú. Es la misma vía que usa Cobblemon
 * para su interfaz ({@code SimpleSoundInstance.forUI}).
 *
 * <p>Hay <b>dos</b> guardas aquí, y no sobra ninguna:
 *
 * <ul>
 *   <li>si ya está sonando <b>esa misma especie</b>, no se reinicia. Es lo que
 *       convierte una ráfaga de peticiones en una sola narración;
 *   <li>y un freno breve entre reproducciones, para que insistir con el botón
 *       de la Pokédex no la trocee.
 * </ul>
 *
 * <p>El servidor ya frena lo suyo, pero el botón de la Pokédex <b>no pasa por el
 * servidor</b> —el cliente sabe qué especie mira y si hay voz—, así que sin esto
 * quedaría sin freno.
 */
public final class VozPokedex {

    /** Entre dos reproducciones, aunque sean de especies distintas. */
    private static final Cooldown FRENO = new Cooldown(1_000);

    private static SoundInstance sonando;
    private static String especieSonando;

    private VozPokedex() {}

    /**
     * Suelta la voz de una especie. Devuelve si llegó a sonar.
     *
     * <p>Una especie distinta <b>corta</b> a la anterior: escanear otra cosa es
     * una orden clara, y dos narraciones a la vez no se entienden. La misma
     * especie mientras suena se ignora.
     */
    public static boolean reproducir(String especie) {
        MinecraftClient cliente = MinecraftClient.getInstance();
        String id = VozService.normalizar(especie);
        if (cliente == null || id.isBlank() || !VozService.tieneVoz(id)) {
            return false;
        }
        if (id.equals(especieSonando) && sigueSonando(cliente)) {
            return false;         // ya la está oyendo: no se reinicia
        }
        if (!FRENO.toca("voz")) {
            return false;
        }
        // `SoundEvent.of` NO necesita que el sonido esté en el registro: el
        // gestor lo resuelve por su identificador contra sounds.json, que es
        // donde de verdad vive la lista. Registrarlo además obligaría a tenerlo
        // también en el servidor, que no reproduce nada.
        SoundInstance nueva = PositionedSoundInstance.master(
                SoundEvent.of(Identifier.of("lunaeternal", "pokedex." + id)),
                1.0f, 1.0f);
        cliente.execute(() -> {
            callar();
            sonando = nueva;
            especieSonando = id;
            cliente.getSoundManager().play(nueva);
        });
        return true;
    }

    /** ¿Puede sonar ahora mismo? Para pintar el botón apagado mientras no. */
    public static boolean disponible(String especie) {
        String id = VozService.normalizar(especie);
        return VozService.tieneVoz(id) && !id.equals(especieSonando);
    }

    /** Corta lo que hubiera. Al salir del mundo, y antes de cada nueva. */
    public static void callar() {
        MinecraftClient cliente = MinecraftClient.getInstance();
        if (cliente != null && sonando != null) {
            cliente.getSoundManager().stop(sonando);
        }
        sonando = null;
        especieSonando = null;
    }

    private static boolean sigueSonando(MinecraftClient cliente) {
        return sonando != null && cliente.getSoundManager().isPlaying(sonando);
    }
}
