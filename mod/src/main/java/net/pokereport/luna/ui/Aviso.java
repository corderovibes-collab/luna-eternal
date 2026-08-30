package net.pokereport.luna.ui;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.net.Red;

/**
 * Cómo se le dice al jugador que ha conseguido algo.
 *
 * <h2>Por qué existe esto y no un {@code sendMessage} en cada sitio</h2>
 *
 * Lo pidió el usuario: <i>«cuando subes de nivel en cualquier trabajo que salga
 * una notificación y sonido de level up, así como cuando escaneas un Pokémon…
 * define bien eso»</i>. Lo que había era un {@code sendMessage} suelto por
 * sistema, cada uno con su formato, y <b>un logro en el chat se pierde entre lo
 * que digan los demás</b>.
 *
 * <h2>Las tres vías, y por qué hacen falta las tres</h2>
 *
 * <table border="1">
 *   <tr><th>Vía</th><th>Para qué</th></tr>
 *   <tr><td><b>Toast</b> (esquina)</td>
 *       <td>Se ve estés donde estés, y no lo tapa el chat de nadie</td></tr>
 *   <tr><td><b>Barra de acción</b></td>
 *       <td>Sale donde ya estás mirando cuando picas o pescas</td></tr>
 *   <tr><td><b>Chat</b></td>
 *       <td><b>Persiste.</b> Es lo único que puedes releer para saber cuánta
 *           Plata te dieron</td></tr>
 * </table>
 *
 * <h2>⚠ El toast solo llega a quien tiene el mod</h2>
 *
 * Es un paquete nuestro. El chat y la barra de acción son de vanilla y llegan
 * siempre, así que <b>quien no tenga el mod se entera igual</b> — con menos
 * fanfarria, pero se entera. Por eso no se manda solo el toast: sería un aviso
 * que desaparece según con qué cliente entres.
 *
 * <h2>⚠ Se llama SIEMPRE desde el hilo del servidor</h2>
 *
 * La red no es segura desde un hilo cualquiera, y estos avisos nacen casi siempre
 * en el executor de E/S. Cada método salta al hilo del servidor por su cuenta,
 * para que quien llama no tenga que acordarse.
 */
public final class Aviso {

    private Aviso() {
    }

    /**
     * Un logro: toast, barra de acción, chat y sonido.
     *
     * @param objeto identificador del objeto que sale de icono
     *               ({@code minecraft:iron_pickaxe}), o vacío para el de por
     *               defecto
     */
    public static void logro(ServerPlayerEntity jugador, String titulo,
                             String detalle, String objeto) {
        logro(jugador, titulo, detalle, objeto,
                SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f);
    }

    public static void logro(ServerPlayerEntity jugador, String titulo, String detalle,
                             String objeto, SoundEvent sonido, float tono) {
        if (jugador == null || jugador.isRemoved() || jugador.getServer() == null) {
            return;
        }
        jugador.getServer().execute(() -> {
            if (jugador.isRemoved()) {
                return;
            }
            try {
                // ⚠ EL TOAST EN try/catch APARTE. Si el cliente no tiene el mod,
                //   `send` puede quejarse; el chat y el sonido tienen que llegar
                //   igual. Un aviso a medias es mejor que ninguno.
                ServerPlayNetworking.send(jugador,
                        new Red.AvisoLogro(titulo, detalle, objeto == null ? "" : objeto));
            } catch (Throwable t) {
                LunaEternal.LOG.debug("Sin toast para {}: {}",
                        jugador.getName().getString(), t.toString());
            }
            // La barra de acción lleva el titular; el chat, el detalle. Repetir lo
            // mismo en los dos sitios es ruido.
            jugador.sendMessage(Text.literal("§6§l" + titulo), true);
            jugador.sendMessage(Text.literal("§6" + titulo + " §8· §f" + detalle), false);
            jugador.playSound(sonido, 0.8f, tono);
        });
    }

    /**
     * UN TÍTULO A PANTALLA COMPLETA, con su sonido.
     *
     * <h2>Por qué esto y no un toast</h2>
     *
     * Un toast es para lo que pasa <b>mientras juegas</b> y no debe
     * interrumpir: una captura, una subida de oficio. Ganar a un líder de
     * gimnasio es lo contrario — es el final de algo, y merece parar la pantalla
     * un segundo. Petición del usuario, y es la decisión correcta.
     *
     * <h2>⚠⚠ ESTO SÍ LLEGA A TODO EL MUNDO, TENGA O NO NUESTRO MOD</h2>
     *
     * El título es un paquete de <b>vainilla</b> ({@code TitleS2CPacket}), al
     * contrario que el toast, que solo lo dibuja quien tiene el mod. Para algo
     * que marca un hito eso importa: nadie se queda sin enterarse de que ha
     * ganado.
     *
     * <p>⚠ Y los tiempos van explícitos. Si no se mandan, se queda el último que
     * usara <b>otro mod o el propio servidor</b>, y un día el mensaje aparece y
     * desaparece en dos fotogramas sin que nadie sepa por qué.
     *
     * @param titulo  la línea grande
     * @param sub     la pequeña, debajo. Puede ser {@code null}
     */
    public static void titulo(ServerPlayerEntity jugador, Text titulo, Text sub,
                              SoundEvent sonido, float tono) {
        if (jugador == null || jugador.isRemoved()) {
            return;
        }
        var red = jugador.networkHandler;
        if (red == null) {
            return;
        }
        // fundido de entrada, tiempo visible, fundido de salida — en ticks
        red.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(
                10, 60, 20));
        if (sub != null) {
            red.sendPacket(
                    new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(sub));
        }
        red.sendPacket(
                new net.minecraft.network.packet.s2c.play.TitleS2CPacket(titulo));
        if (sonido != null) {
            jugador.playSoundToPlayer(sonido, SoundCategory.MASTER, 1.0f, tono);
        }
    }

    /**
     * Un aviso menor: solo barra de acción, sin toast ni chat.
     *
     * <p>Para lo que ocurre <b>muchas veces</b> —registrar una especie, cobrar
     * una venta—. Un toast por cada captura sería una cortina de avisos, y el
     * chat se llenaría hasta hacerse inútil justo cuando más se juega.
     */
    public static void breve(ServerPlayerEntity jugador, String texto) {
        if (jugador == null || jugador.isRemoved() || jugador.getServer() == null) {
            return;
        }
        jugador.getServer().execute(() -> {
            if (!jugador.isRemoved()) {
                jugador.sendMessage(Text.literal(texto), true);
            }
        });
    }
}
