package net.pokereport.luna.backpack;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.LunaEternal;

/**
 * El tipo de contenedor de la mochila.
 *
 * <h2>⚠⚠⚠ SE REGISTRA EN EL ENTRYPOINT `main`, NO EN EL DE CLIENTE</h2>
 *
 * Un {@code ScreenHandlerType} vive en un registro <b>que se sincroniza</b>: el
 * servidor abre el contenedor mandando un número, y el cliente lo busca en su
 * tabla. Registrarlo solo en un lado descuadra las dos tablas — es exactamente
 * el fallo de los bloques que ya está documentado en CLAUDE.md, y allí costó
 * entender por qué se dibujaba otra cosa.
 *
 * <p>Aquí no se dibujaría otra cosa: <b>echaría al jugador</b> con un error de
 * decodificación que no nombra la mochila.
 */
public final class Registro {

    private Registro() {}

    public static final Identifier ID =
            Identifier.of(LunaEternal.MOD_ID, "mochila");

    /**
     * ⚠ ExtendedScreenHandlerType porque el cliente NECESITA saber cuántas
     * filas están abiertas <b>antes</b> de dibujar. Un tipo normal no puede
     * mandar nada al abrir, y entonces el cliente tendría que preguntarlo con
     * otro paquete — y dibujaría la primera imagen con datos equivocados.
     */
    public static final ScreenHandlerType<MochilaHandler> TIPO =
            new ExtendedScreenHandlerType<>(MochilaHandler::new,
                    MochilaHandler.Apertura.CODEC);

    public static void registrar() {
        Registry.register(Registries.SCREEN_HANDLER, ID, TIPO);
    }

    /**
     * Abre la mochila de un jugador.
     *
     * <h2>⚠⚠ SE CARGA DE LA BASE ANTES DE ABRIR, y por eso hay dos saltos</h2>
     *
     * Leer la mochila es una consulta, y consultar desde el hilo del servidor
     * está prohibido. Así que: se pide al executor de E/S, y cuando contesta se
     * vuelve al hilo del servidor para abrir la pantalla.
     *
     * <p>⚠ Si la carga falla <b>no se abre nada</b> y se avisa. Abrir una
     * mochila vacía cuando en realidad hay cosas dentro invita a llenarla — y
     * entonces al guardar se machaca lo que había.
     */
    public static void abrir(ServerPlayerEntity jugador) {
        var svc = LunaEternal.backpacks();
        var servidor = jugador.getServer();
        if (svc == null || servidor == null) {
            return;
        }
        int filas = Mochila.filasDe(
                net.pokereport.luna.ui.Tablist.escalonDe(jugador));
        var registros = jugador.getRegistryManager();

        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                SimpleInventory inv = svc.cargar(id, registros);
                servidor.execute(() -> {
                    if (jugador.isRemoved()) {
                        return;
                    }
                    // ⚠⚠⚠ TIENE QUE SER UNA `ExtendedScreenHandlerFactory`, y con
                    //     una `NamedScreenHandlerFactory` a secas Fabric LANZA:
                    //       «Extended screen handler ... must be opened with an
                    //        ExtendedScreenHandlerFactory!»
                    //
                    //     Y tiene sentido: el tipo es `Extended` porque manda
                    //     datos al abrir --cuantas filas-- y una factoria normal
                    //     no tiene de donde sacarlos. `getScreenOpeningData` es
                    //     el metodo que los produce.
                    //
                    //     ⚠ El fallo NO SE VE AL COMPILAR: `openHandledScreen`
                    //       acepta cualquier `NamedScreenHandlerFactory`, y la
                    //       comprobacion la hace Fabric al abrir.
                    jugador.openHandledScreen(
                        new net.fabricmc.fabric.api.screenhandler.v1
                                .ExtendedScreenHandlerFactory<MochilaHandler.Apertura>() {
                            @Override
                            public Text getDisplayName() {
                                return Text.translatable(
                                    "pokepad.lunaeternal.mochila.titulo");
                            }

                            @Override
                            public MochilaHandler.Apertura getScreenOpeningData(
                                    ServerPlayerEntity p) {
                                return new MochilaHandler.Apertura(filas);
                            }

                            @Override
                            public ScreenHandler createMenu(int sincId,
                                    net.minecraft.entity.player.PlayerInventory pi,
                                    net.minecraft.entity.player.PlayerEntity p) {
                                return new MochilaHandler(sincId, pi, inv, filas);
                            }
                        });
                    Abiertas.recordar(jugador, inv);
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo abrir la mochila de {}",
                        jugador.getName().getString(), e);
                servidor.execute(() -> jugador.sendMessage(Text.literal(
                        "§cNo se pudo abrir tu mochila. Inténtalo otra vez."),
                        true));
            }
        });
    }
}
