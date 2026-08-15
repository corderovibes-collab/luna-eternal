package net.pokereport.luna.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Reproduce la descripción hablada de una especie, para este jugador y ya.
 *
 * <p><b>Suena como sonido «master», no en el mundo.</b> Un sonido colocado en el
 * espacio se atenúa con la distancia y se va a un lado según hacia dónde mires,
 * y eso en una narración es justo lo que no se quiere: escaneas a diez bloques,
 * te giras, y dejas de entender lo que dice. Un master suena igual pase lo que
 * pase, como la música o los sonidos de menú.
 *
 * <p>Es la misma vía que usa Cobblemon para sus sonidos de interfaz
 * ({@code SimpleSoundInstance.forUI}, que en nuestros mapeos es
 * {@code PositionedSoundInstance.master}).
 */
public final class VozPokedex {

    /** La que está sonando, para poder cortarla. */
    private static SoundInstance sonando;

    private VozPokedex() {}

    /**
     * Suelta la voz de una especie.
     *
     * <p>Si ya había una sonando <b>se corta</b>. Escanear es rápido y las
     * descripciones duran varios segundos, así que sin esto dos escaneos
     * seguidos se solapan y no se entiende ninguno de los dos. Cortar la
     * anterior es lo que hace cualquier audioguía.
     */
    public static void reproducir(String especie) {
        MinecraftClient cliente = MinecraftClient.getInstance();
        if (cliente == null || especie == null || especie.isBlank()) {
            return;
        }
        Identifier id = Identifier.of("lunaeternal", "pokedex." + especie);
        // `SoundEvent.of` NO necesita que el sonido esté en el registro: el
        // gestor de sonido lo resuelve por su identificador contra sounds.json,
        // que es donde de verdad vive la lista. Registrarlo ademas obligaria a
        // tenerlo en el servidor, que no reproduce nada.
        SoundInstance nueva = PositionedSoundInstance.master(
                SoundEvent.of(id), 1.0f, 1.0f);
        cliente.execute(() -> {
            if (sonando != null) {
                cliente.getSoundManager().stop(sonando);
            }
            sonando = nueva;
            cliente.getSoundManager().play(nueva);
        });
    }

    /** Al salir del mundo se calla. */
    public static void callar() {
        MinecraftClient cliente = MinecraftClient.getInstance();
        if (cliente != null && sonando != null) {
            cliente.getSoundManager().stop(sonando);
            sonando = null;
        }
    }

}
