package net.pokereport.luna.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.pokereport.luna.net.Red;

/**
 * El dialogo para elegir la foto del memorial, y su subida troceada.
 *
 * <h2>⚠⚠⚠ tinyfd, NO AWT -- el AWT se abria INVISIBLE y se comia los clics</h2>
 *
 * La primera version usaba {@code FileDialog} de AWT en un hilo aparte. Paso el
 * 2026-09-04, y el sintoma lo describio el usuario: «no se abre nada, se
 * buguea». El dialogo SI se abria --<b>detras de la ventana GLFW del juego</b>,
 * sin barra de tareas-- y como era modal, se tragaba los clics: la partida
 * parecia congelada con un boton que no hace nada. Es un fallo conocido de
 * mezclar AWT con GLFW.
 *
 * <p>La cura es {@code TinyFileDialogs} de LWJGL, <b>que Mojang ya incluye en
 * la distribucion de 1.21.1</b> (comprobado en el JSON de la version, no
 * supuesto: {@code org.lwjgl:lwjgl-tinyfd:3.3.3} con natives de Windows). Es
 * el dialogo NATIVO del sistema, se muestra encima de la ventana del juego y
 * no necesita hilo aparte ni EDT.
 *
 * <h2>⚠⚠ EL CLIENTE SOLO ENVIA: QUIEN DECIDE ES EL SERVIDOR</h2>
 *
 * El tamano se acota aqui por cortesia y en el servidor por regla (P6): la
 * foto se decodifica, se reescala y se recodifica alla, y queda PENDIENTE
 * hasta que un staff la apruebe.
 */
public final class DialogoFoto {

    /** El trozo de subida: 16 KB, el mismo tamano que usa el servidor. */
    private static final int TROZO = 16 * 1024;

    private DialogoFoto() {}

    /** Abre el dialogo nativo y, si se elige un fichero valido, lo sube troceado. */
    public static void abrir(net.minecraft.client.gui.screen.Screen pantalla) {
        var cliente = MinecraftClient.getInstance();
        new Thread(() -> {
            String elegida = elegirConTinyfd();
            if (elegida == null || elegida.isBlank()) {
                return; // el jugador cancelo el dialogo
            }
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(Path.of(elegida));
            } catch (IOException | RuntimeException e) {
                return;
            }
            // ⚠ El tope se comprueba aqui SOLO para no gastar red en algo que
            //   el servidor va a rechazar igual: la regla la aplica el.
            if (bytes.length == 0
                    || bytes.length > net.pokereport.luna.santuario.SantuarioService.FOTO_MAX_BYTES) {
                cliente.execute(() -> {
                    if (cliente.player != null) {
                        cliente.player.sendMessage(net.minecraft.text.Text.translatable(
                                "pokepad.lunaeternal.santuario.error.foto_grande"), false);
                    }
                });
                return;
            }
            String idem = UUID.randomUUID().toString();
            int total = bytes.length;
            int trozos = (total + TROZO - 1) / TROZO;
            for (int i = 0; i < trozos; i++) {
                int desde = i * TROZO;
                int hasta = Math.min(total, desde + TROZO);
                byte[] parte = java.util.Arrays.copyOfRange(bytes, desde, hasta);
                final int indice = i;
                // ⚠ SE ENCOLA EN EL HILO DEL CLIENTE: los paquetes no se mandan
                //   desde un hilo de AWT.
                cliente.execute(() -> ClientPlayNetworking.send(new Red.SubirFoto(
                        idem, total, indice, parte)));
            }
        }, "luna-dialogo-foto").start();
    }

    /**
     * El dialogo nativo de LWJGL.
     *
     * <p>⚠ Los filtros van en un {@code PointerBuffer} con cadenas nativas,
     * dentro de un {@code MemoryStack}: la firma de 3.3.3 es asi (la variante
     * con {@code Map} llego despues). Y todo vive en un try-with-resources para
     * que la pila nativa se libere pase lo que pase.
     */
    private static String elegirConTinyfd() {
        try (MemoryStack pila = MemoryStack.stackPush()) {
            var filtros = pila.mallocPointer(3);
            filtros.put(pila.UTF8("*.png"));
            filtros.put(pila.UTF8("*.jpg"));
            filtros.put(pila.UTF8("*.jpeg"));
            filtros.flip();
            return TinyFileDialogs.tinyfd_openFileDialog(
                    "Elige la foto del memorial",
                    "",
                    filtros,
                    "Imagen (PNG o JPG)",
                    false);
        } catch (Throwable t) {
            // ⚠ Si tinyfd no esta (no deberia: lo trae Minecraft), se avisa en
            //   vez de morir en silencio -- un boton mudo es el fallo del que
            //   venimos.
            var cliente = MinecraftClient.getInstance();
            cliente.execute(() -> {
                if (cliente.player != null) {
                    cliente.player.sendMessage(net.minecraft.text.Text.translatable(
                            "pokepad.lunaeternal.santuario.error.sin_dialogo"), false);
                }
            });
            return null;
        }
    }
}
