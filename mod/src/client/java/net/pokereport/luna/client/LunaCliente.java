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
    private static KeyBinding abrirMochila;

    /**
     * Abre la eleccion de inicial cuando el jugador puede verla.
     *
     * <p>⚠ Se comprueba {@code currentScreen == null} y no «esta en el mundo»:
     * lo que hay que respetar es que no se le arranque de golpe otra pantalla que
     * ya tenia abierta --su inventario, un menu de Cobblemon--. Si la tiene, se
     * abrira en el siguiente tick en el que la cierre.
     */
    private static void abrirInicialSiToca(net.minecraft.client.MinecraftClient cliente) {
        if (cliente.player == null || cliente.currentScreen != null) {
            return;
        }
        var datos = EstadoCliente.iniciales();
        if (datos != null && !datos.yaEligio() && !datos.opciones().isEmpty()) {
            cliente.setScreen(new net.pokereport.luna.client.pokepad.InicialScreen());
        }
    }

    @Override
    public void onInitializeClient() {
        abrirPad = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lunaeternal.pokepad",
                InputUtil.Type.KEYSYM,
                // B de "bolsillo". No la usa vanilla, y queda cerca de las
                // teclas de movimiento sin pisar el inventario ni el chat.
                GLFW.GLFW_KEY_B,
                "key.categories.lunaeternal"));

        abrirMochila = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lunaeternal.mochila",
                InputUtil.Type.KEYSYM,
                // ⚠ N de "mochila", y SIN asignar por defecto seria peor: una
                //   tecla que hay que ir a buscar a los ajustes no la usa nadie.
                //   N no la usa vanilla y esta al lado de B, que ya es nuestra.
                GLFW.GLFW_KEY_N,
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

        // ⚠⚠ LOS INICIALES, Y LA PANTALLA SE ABRE SOLA. Es lo que arregla el
        //   bloqueo circular que llevaba meses abierto: un jugador nuevo no tenia
        //   ningun Pokemon, y sin Pokemon no servia nada de lo construido.
        //
        //   Un icono mas en el PokePad NO habria bastado: quien acaba de entrar
        //   no sabe que el PokePad existe.
        //
        //   ⚠ Solo se abre si NO hay ya una pantalla abierta. Si el jugador esta
        //     en su inventario o en un menu de Cobblemon, arrancarselo de golpe
        //     es peor que esperar: se le abrira en el siguiente paquete.
        ClientPlayNetworking.registerGlobalReceiver(Red.Iniciales.ID, (carga, ctx) -> {
            EstadoCliente.guardar(carga);
            // No se abre AQUI. Ver `abrirInicialSiToca`: al llegar este paquete
            // el jugador suele estar todavia en la pantalla de carga, y ese es
            // justo el momento en el que no se puede abrir nada.
        });

        // El arbol de misiones. Llega al abrir la pantalla y despues de cada
        // cobro: el servidor lo reenvia ENTERO en vez de mandar cambios.
        ClientPlayNetworking.registerGlobalReceiver(Red.Misiones.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));

        // Las cinco Vias. Llegan al abrir Trabajos, no antes: nadie las mira
        // el resto del tiempo.
        // ⚠ La pantalla se asocia AQUI, en el cliente. El TIPO se registra en
        //   el entrypoint `main` porque su registro se sincroniza; asociarle una
        //   pantalla es cosa solo del cliente, y hacerlo en `main` reventaria en
        //   el servidor dedicado, que no tiene clases de pantalla.
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(
                net.pokereport.luna.backpack.Registro.TIPO,
                net.pokereport.luna.client.pokepad.MochilaScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(Red.EstadoGts.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));

        ClientPlayNetworking.registerGlobalReceiver(Red.EstadoMercado.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));
        ClientPlayNetworking.registerGlobalReceiver(Red.EstadoCazas.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));
        ClientPlayNetworking.registerGlobalReceiver(Red.EstadoTesoros.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));
        // ⚠⚠ ESTE ABRE LA RULETA. Es lo que convierte «he pulsado abrir» en la
        //    animacion: el servidor ya sorteo, gasto la llave y lo anoto, asi
        //    que lo que llega aqui es un HECHO. La ruleta solo lo enseña.
        //    ⚠ Y por eso la animacion NO decide nada: si decidiera ella, el
        //      premio de la pantalla y el de la base podrian no coincidir.
        ClientPlayNetworking.registerGlobalReceiver(Red.ResultadoCofre.ID,
                (carga, ctx) -> {
                    EstadoCliente.guardar(carga);
                    ctx.client().execute(() -> {
                        var pantalla = ctx.client().currentScreen;
                        if (pantalla instanceof net.pokereport.luna.client.pokepad
                                .TesorosScreen tes) {
                            tes.alLlegarResultado(carga);
                        }
                    });
                });
        // ⚠⚠ ESTE NO SOLO GUARDA: PUEDE ABRIR LA PANTALLA. Es lo que hace que
        //    el clic derecho en un Miraidon lleve a Viajes -- el servidor manda
        //    el estado con la bandera puesta y el cliente abre.
        //    ⚠ Y se abre SOLO si no hay otra pantalla delante: si no, tocar sin
        //      querer un Miraidon con el inventario abierto lo cerraria de golpe.
        ClientPlayNetworking.registerGlobalReceiver(Red.EstadoViajes.ID,
                (carga, ctx) -> {
                    EstadoCliente.guardar(carga);
                    if (carga.abrir()) {
                        var cliente = ctx.client();
                        if (cliente.currentScreen == null) {
                            cliente.setScreen(new net.pokereport.luna.client.pokepad
                                    .ViajesScreen(null));
                        }
                    }
                });

        // ⚠⚠ ESTE TAMPOCO SOLO GUARDA: ABRE EL DIALOGO DEL GIMNASIO. Es lo
        //    que convierte el clic derecho en el lider en una pantalla, y por
        //    eso NO hay un `PedirGimnasio`: al dialogo solo se llega tocandole.
        //    ⚠ Y solo si no hay otra pantalla delante --salvo que ya sea esta,
        //      que es el caso de «te he dicho que no puedes»: ahi hay que
        //      refrescar la que ya esta abierta, no abrir otra.
        ClientPlayNetworking.registerGlobalReceiver(Red.EstadoGimnasio.ID,
                (carga, ctx) -> {
                    EstadoCliente.guardar(carga);
                    var cliente = ctx.client();
                    if (cliente.currentScreen == null) {
                        cliente.setScreen(new net.pokereport.luna.client.pokepad
                                .GimnasioScreen());
                    }
                });

        ClientPlayNetworking.registerGlobalReceiver(Red.EstadoExplorar.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));

        ClientPlayNetworking.registerGlobalReceiver(Red.EstadoCura.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));

        ClientPlayNetworking.registerGlobalReceiver(Red.EstadoProtecciones.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));

        ClientPlayNetworking.registerGlobalReceiver(Red.DetalleParcela.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));

        ClientPlayNetworking.registerGlobalReceiver(Red.EstadoCartas.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));

        ClientPlayNetworking.registerGlobalReceiver(Red.Tienda.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));

        ClientPlayNetworking.registerGlobalReceiver(Red.EstadoClan.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));

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
        ClientPlayNetworking.registerGlobalReceiver(Red.EstadoTrajes.ID,
                (carga, ctx) -> EstadoCliente.guardar(carga));

        // ⚠ Quien lleva que. Es un paquete DISTINTO del estado de la pantalla:
        //   aquel es tuyo y este es de todos.
        ClientPlayNetworking.registerGlobalReceiver(Red.TrajeDe.ID,
                (carga, ctx) -> Trajes.guardar(carga.jugador(), carga.traje()));

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
        Trajes.registrarDibujado();

        // Al salir del mundo se olvida: el saldo es de esa partida. Y se calla
        // la voz, que si no sigue sonando en la pantalla de servidores.
        // ⚠ SE PIDE AL ENTRAR, y esto arregla "no veo mis cosmeticos al
        //   reconectar". El servidor difundia al recibir su JOIN, que es SU idea
        //   de "ya esta dentro"; entre eso y que el cliente tenga mundo y
        //   renderizadores hay una ventana, y el paquete que cae ahi se pierde
        //   sin error. Los demas si lo veian porque ELLOS ya llevaban rato
        //   conectados.
        //
        //   Preguntar en vez de esperar quita la ventana entera: el unico que
        //   sabe cuando esta listo es el cliente.
        ClientPlayConnectionEvents.JOIN.register((manejador, remitente, cliente) -> {
            ClientPlayNetworking.send(new Red.PedirLlevados());
            // Y si no ha elegido inicial, el servidor lo dira y la pantalla
            // se abrira sola. Ver el receptor de `Iniciales`.
            ClientPlayNetworking.send(new Red.PedirInicial());
        });

        ClientPlayConnectionEvents.DISCONNECT.register((manejador, cliente) -> {
            EstadoCliente.olvidar();
            VozPokedex.callar();
            // Sin esto, entrar en otro mundo arrastra los cosmeticos del anterior.
            Auras.olvidarTodo();
            Sombreros.olvidarTodo();
        });

        ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
            // ⚠⚠ EL INICIAL SE ABRE DESDE EL TICK, NO AL RECIBIR EL PAQUETE.
            //
            //   Al llegar `Iniciales` el jugador esta casi siempre en la pantalla
            //   de carga del terreno, asi que `currentScreen == null` es falso y
            //   la apertura se perdia -- el mismo tipo de carrera que dejaba los
            //   cosmeticos sin verse al reconectar, y con el mismo sintoma:
            //   nada, sin error.
            //
            //   Comprobarlo cada tick no cuesta nada --dos comparaciones-- y se
            //   abre en cuanto hay hueco de verdad. Deja de comprobarse solo:
            //   una vez abierta, `currentScreen` ya no es null.
            abrirInicialSiToca(cliente);

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

            // ⚠⚠ AQUI NO SE ABRE NADA: SE PIDE. Un contenedor lo crea el
            //    SERVIDOR y le asigna un identificador de sincronizacion; una
            //    pantalla abierta por el cliente no estaria conectada a nada y
            //    lo que moviera dentro no existiria.
            while (abrirMochila.wasPressed()) {
                if (cliente.currentScreen == null) {
                    ClientPlayNetworking.send(new Red.AbrirMochila());
                }
            }
        });
    }
}
