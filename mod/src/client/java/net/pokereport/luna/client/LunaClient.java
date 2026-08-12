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

        // Pokédex: se cierra el Pad y se abre la de Cobblemon. Es un atajo
        // al objeto, no una copia — su pantalla ya existe y es mejor.
        ClientPlayNetworking.registerGlobalReceiver(
            PadPayloads.AbrirPokedex.ID, (payload, context) ->
                context.client().execute(LunaClient::abrirPokedex));

        LOG.info("Luna Eternal — interfaz de cliente lista");
    }

    private static void abrirPokedex() {
        try {
            com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI.Companion.open(
                com.cobblemon.mod.common.client.CobblemonClient.INSTANCE
                    .getClientPokedexData(),
                // PokedexType es un enum, no un objeto con companion.
                com.cobblemon.mod.common.client.pokedex.PokedexType.RED,
                null, null);
        } catch (Throwable t) {
            LOG.error("No se pudo abrir la Pokédex de Cobblemon", t);
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.player != null) {
                c.player.sendMessage(net.minecraft.text.Text.literal(
                    "§cNo se pudo abrir la Pokédex."), false);
            }
        }
    }

    private static void abrir(PadPayloads.Abrir datos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        client.setScreen(new PadScreen(datos));
    }
}
