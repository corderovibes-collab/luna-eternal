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
        ClientPlayNetworking.registerGlobalReceiver(Red.Ficha.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));
        // El catalogo de cosmeticos. Llega al abrir la tienda y despues de cada
        // compra: el servidor lo reenvia entero en vez de mandar cambios.
        ClientPlayNetworking.registerGlobalReceiver(Red.Cosmeticos.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));
        // Quien lleva que puesto. Llega para TODOS los jugadores, no solo para
        // uno mismo: es lo que hace que un cosmetico se vea.
        ClientPlayNetworking.registerGlobalReceiver(Red.CosmeticoPuesto.ID,
                (carga, ctx) -> net.pokereport.luna.client.pokepad.MascotasPuestas
                        .guardar(carga.jugador(), carga.especie(), carga.aspecto()));

        // ⚠ La capa se añade al renderizador del JUGADOR, sin mixin. Es la via
        //   que ofrece Fabric y la misma que usa Cobblemon para su Pokemon de
        //   hombro: la mascota hereda la matriz del jugador, asi que le sigue
        //   sin una linea de codigo de seguimiento.
        net.fabricmc.fabric.api.client.rendering.v1
                .LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (tipo, renderizador, ayuda, ctx) -> {
                    // ⚠ Se registra por el AYUDANTE, no llamando a `addFeature`
                    //   del renderizador: `addFeature` es del tipo concreto y
                    //   los genericos no cuadran desde el callback, que entrega
                    //   un `EntityRenderer<?, ?>`.
                    if (renderizador instanceof net.minecraft.client.render.entity
                            .PlayerEntityRenderer jugador) {
                        ayuda.register(
                                new net.pokereport.luna.client.pokepad.MascotaEnMundo(jugador));
                    }
                });

        // La voz de la Pokédex. Llega solo a quien ha escaneado; aquí solo se
        // reproduce, la decisión de a quién mandarla es del servidor.
        ClientPlayNetworking.registerGlobalReceiver(Red.VozPokedex.ID,
                (carga, ctx) -> VozPokedex.reproducir(carga.especie()));

        // El boton de voz dentro de la Pokedex de Cobblemon.
        BotonVoz.register();

        // Al salir del mundo se olvida: el saldo es de esa partida. Y se calla
        // la voz, que si no sigue sonando en la pantalla de servidores.
        ClientPlayConnectionEvents.DISCONNECT.register((manejador, cliente) -> {
            EstadoCliente.olvidar();
            // ⚠ Y las mascotas puestas. Los UUID coinciden entre servidores,
            //   asi que sin esto no saldrian mascotas ajenas al azar: saldrian
            //   LAS DE LAS MISMAS PERSONAS en otro servidor, que es mucho mas
            //   creible y por tanto mas dificil de reconocer como fallo.
            net.pokereport.luna.client.pokepad.MascotasPuestas.olvidar();
            VozPokedex.callar();
        });

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
