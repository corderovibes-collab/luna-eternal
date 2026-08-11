package net.pokereport.luna.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.pokereport.luna.net.PadPayloads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lado cliente del mod (D-025).
 *
 * <p>Hace una sola cosa: cuando llega un paquete «abre esta pantalla», la
 * abre. No guarda estado de juego, no calcula precios, no decide qué está
 * desbloqueado. Todo eso vive en el servidor y llega ya resuelto.
 *
 * <p>Es deliberado: el mod de cliente se distribuye, así que cualquiera puede
 * abrirlo y leerlo. Si aquí hubiera lógica de negocio, sería lógica pública y
 * manipulable.
 */
public final class LunaClient implements ClientModInitializer {

    public static final Logger LOG = LoggerFactory.getLogger("lunaeternal-client");

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
            PadPayloads.Abrir.ID, (payload, context) ->
                context.client().execute(() -> abrir(payload)));

        LOG.info("Luna Eternal — interfaz de cliente lista");
    }

    private static void abrir(PadPayloads.Abrir datos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        client.setScreen(new PadScreen(datos));
    }
}
