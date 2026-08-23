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

        // Los LOGROS: suben a la esquina como un toast. Van aqui y no en una
        // pantalla porque tienen que verse ESTES DONDE ESTES -- picando, pescando
        // o mirando el mundo.
        ClientPlayNetworking.registerGlobalReceiver(Red.AvisoLogro.ID, (carga, ctx) -> {
            var cliente = ctx.client();
            cliente.getToastManager().add(new ToastLuna(cliente,
                    net.minecraft.text.Text.literal(carga.titulo()),
                    net.minecraft.text.Text.literal(carga.detalle()),
                    carga.objeto()));
        });

        // El arbol de misiones. Llega al abrir la pantalla y despues de cada
        // cobro: el servidor lo reenvia ENTERO en vez de mandar cambios.
        ClientPlayNetworking.registerGlobalReceiver(Red.Misiones.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));

        // Las cinco Vias. Llegan al abrir Trabajos, no antes: nadie las mira
        // el resto del tiempo.
        ClientPlayNetworking.registerGlobalReceiver(Red.Trabajos.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));

        // Quien lleva que. Llega al entrar --lo de todos-- y cada vez que
        // alguien se pone o se quita algo. Es lo que hace que un cosmetico lo
        // vean los DEMAS y no solo su dueño en la tienda.
        //
        // ⚠ UN SOLO PAQUETE PARA TODAS LAS CATEGORIAS. Empezo siendo `AuraDe` y
        //   al llegar los sombreros habrian sido dos paquetes, dos receptores y
        //   dos difusiones que mantener sincronizadas. La categoria viaja dentro,
        //   asi que añadir capas es una linea en este switch.
        ClientPlayNetworking.registerGlobalReceiver(Red.LlevaPuesto.ID, (carga, ctx) -> {
            switch (carga.categoria()) {
                case net.pokereport.luna.cosmetics.Catalogo.AURAS ->
                        Auras.recibir(carga.jugador(), carga.cosmetico());
                case net.pokereport.luna.cosmetics.Catalogo.SOMBREROS ->
                        Sombreros.recibir(carga.jugador(), carga.cosmetico());
                default -> { }
            }
        });

        // La voz de la Pokédex. Llega solo a quien ha escaneado; aquí solo se
        // reproduce, la decisión de a quién mandarla es del servidor.
        ClientPlayNetworking.registerGlobalReceiver(Red.VozPokedex.ID,
                (carga, ctx) -> VozPokedex.reproducir(carga.especie()));

        // El boton de voz dentro de la Pokedex de Cobblemon.
        BotonVoz.register();

        // Los sombreros: hornear sus modelos y engancharlos al jugador.
        // ⚠ EL ORDEN IMPORTA. `registrarModelos` tiene que correr ANTES de que el
        //   juego cargue los recursos por primera vez, o sea en la inicializacion
        //   y no perezosamente: un modelo que no se registro a tiempo no se
        //   hornea, y sale el cubo morado y negro en vez de un error.
        Sombreros.registrarModelos();
        Sombreros.registrarDibujado();

        // Al salir del mundo se olvida: el saldo es de esa partida. Y se calla
        // la voz, que si no sigue sonando en la pantalla de servidores.
        ClientPlayConnectionEvents.DISCONNECT.register((manejador, cliente) -> {
            EstadoCliente.olvidar();
            VozPokedex.callar();
            // Sin esto, entrar en otro mundo arrastra los cosmeticos del anterior.
            Auras.olvidarTodo();
            Sombreros.olvidarTodo();
        });

        ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
            // Las partículas de las auras. Va lo PRIMERO del tick y fuera del
            // bucle de la tecla: si se colara dentro, solo se dibujarían mientras
            // hay pulsaciones en la cola, o sea casi nunca.
            Auras.tick(cliente);

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
