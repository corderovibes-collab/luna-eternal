package net.pokereport.luna.client;


import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.pokereport.luna.client.pokepad.PokePadScreen;
import net.pokereport.luna.net.Red;
import org.lwjgl.glfw.GLFW;

/**
 * El lado de cliente de Luna Eternal: hoy, abrir el PokePad.
 *
 * <p>Este conjunto de fuentes <b>no existe en el servidor</b>. Loom lo compila
 * aparte ({@code splitEnvironmentSourceSets}), así que aquí se puede tocar
 * todo lo de dibujar sin miedo a que el servidor intente cargarlo.
 *
 * <p><b>Una tecla, no un comando</b> (P9). Y por ahora la tecla es el único
 * camino: cuando exista el objeto que lo abre —el Almanaque— será un clic
 * derecho, y esto se quedará como atajo.
 */
public class LunaCliente implements ClientModInitializer {

    private static KeyBinding abrirPad;

    @Override
    public void onInitializeClient() {
        abrirPad = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lunaeternal.pokepad",
                InputUtil.Type.KEYSYM,
                // B de "bolsillo". No la usa vanilla, y queda cerca de las
                // teclas de movimiento sin pisar el inventario ni el chat.
                GLFW.GLFW_KEY_B,
                "key.categories.lunaeternal"));

        // La respuesta del servidor con el saldo. Solo se guarda para dibujarla.
        ClientPlayNetworking.registerGlobalReceiver(Red.Saldo.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));

        // Al salir del mundo se olvida: el saldo es de esa partida.
        ClientPlayConnectionEvents.DISCONNECT.register(
                (manejador, cliente) -> EstadoCliente.olvidar());

        ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
            // `wasPressed` vacía la cola de pulsaciones: con un `if` simple, una
            // pulsación larga abriría y cerraría la pantalla en bucle.
            while (abrirPad.wasPressed()) {
                if (cliente.currentScreen == null) {
                    cliente.setScreen(new PokePadScreen());
                }
            }
        });
    }
}
