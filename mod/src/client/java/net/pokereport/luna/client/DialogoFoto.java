package net.pokereport.luna.client;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.pokereport.luna.net.Red;

/**
 * El dialogo para elegir la foto del memorial, y su subida troceada.
 *
 * <h2>⚠⚠ AWT FileDialog, Y NO HAY OTRO CAMINO LIMPIO EN ESTE CLIENTE</h2>
 *
 * Minecraft no trae selector de archivos (LWJGL no incluye tinyfd en la
 * instancia, medido), asi que se usa el dialogo nativo de AWT en un hilo
 * aparte -- modal bloquearia el hilo de dibujado y la ventana se congelaria.
 * Funciona en Windows, que es lo que usa el launcher.
 *
 * <h2>⚠⚠ EL CLIENTE SOLO ENVIA: QUIEN DECIDE ES EL SERVIDOR</h2>
 *
 * El tamano se acota aqui por cortesia y en el servidor por regla (P6): la
 * foto se decodifica, se reescala y se recodifica alla, y queda PENDIENTE
 * hasta que un staff la apruebe. La pantalla se entera por {@code
 * ResultadoFoto} y vuelve a pedir sus fotos.
 */
public final class DialogoFoto {

    /** El trozo de subida: 16 KB, el mismo tamano que usa el servidor. */
    private static final int TROZO = 16 * 1024;

    private DialogoFoto() {}

    /** Abre el dialogo y, si se elige un fichero valido, lo sube troceado. */
    public static void abrir(net.minecraft.client.gui.screen.Screen pantalla) {
        var cliente = MinecraftClient.getInstance();
        new Thread(() -> {
            // ⚠ El dialogo es MODAL de su propio hilo: `setVisible` no vuelve
            //   hasta que el jugador elige o cancela. Por eso este hilo.
            var dialogo = new FileDialog((Frame) null,
                    "Elige la foto del memorial", FileDialog.LOAD);
            dialogo.setVisible(true);
            String archivo = dialogo.getFile();
            String carpeta = dialogo.getDirectory();
            dialogo.dispose();
            if (archivo == null || carpeta == null) {
                return;
            }
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(Path.of(carpeta, archivo));
            } catch (IOException e) {
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
}
