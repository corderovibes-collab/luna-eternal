package net.pokereport.luna;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.command.LunaCommand;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.EconomyService;
import net.pokereport.luna.player.PlayerService;
import net.pokereport.luna.ui.PlayerCache;
import net.pokereport.luna.ui.Tablist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Punto de entrada. Solo servidor.
 *
 * <p>Arranque: configuración → base de datos → migraciones → servicios.
 * Si la base de datos no responde y {@code db.failFast} está activo, el
 * servidor <b>no arranca</b>. Es deliberado: arrancar sin persistencia
 * significa perder progreso de los jugadores en silencio, y eso es peor que
 * no arrancar (data-model.md §7).
 */
public final class LunaEternal implements DedicatedServerModInitializer {

    public static final String MOD_ID = "lunaeternal";
    public static final Logger LOG = LoggerFactory.getLogger("LunaEternal");

    /**
     * El nombre del servidor tal y como lo lee el jugador (MARCA-001).
     *
     * <p>⚠ NO ES {@link #MOD_ID}, Y NO PUEDE SERLO. {@code MOD_ID} es
     * identidad: lo usan el registro de Fabric, los espacios de nombres de
     * datapacks y resource packs, y la ruta de los assets. Cambiarlo rompe el
     * mundo guardado. Este es solo texto, y cambia cuando cambie la marca.
     *
     * <p>⚠⚠ Y VA AQUI, EN UN SOLO SITIO, porque escrito a mano se queda a
     * medias: el 2026-08-23 el nombre estaba repetido en el prefijo del chat,
     * en un comando y en dos ficheros de idioma, y renombrar el servidor
     * significaba encontrarlos todos. Uno que se escape no da ningun error --
     * simplemente hay una pantalla que sigue diciendo el nombre viejo.
     */
    public static final String NOMBRE = "PokeReport Network";

    /** El prefijo de chat del servidor, con sus colores ya puestos. */
    public static final String PREFIJO = "§8[§6" + NOMBRE + "§8] §f";

    private static Database database;
    private static PlayerService players;
    private static EconomyService economy;
    private static net.pokereport.luna.progression.ProgressionService progression;
    private static net.pokereport.luna.shop.ShopCatalog shop;
    private static net.pokereport.luna.gts.GtsService gts;
    private static net.pokereport.luna.pokedex.PokedexService pokedex;
    private static net.pokereport.luna.kit.KitCatalog kits;
    private static net.pokereport.luna.kit.KitService kitService;
    private static net.pokereport.luna.clan.ClanService clans;
    private static net.pokereport.luna.market.MarketService market;
    private static net.pokereport.luna.market.Tasador tasador;
    private static net.pokereport.luna.quest.QuestService quests;
    private static net.pokereport.luna.economy.EconomyStats stats;
    private static net.pokereport.luna.hunt.HuntService hunts;
    private static net.pokereport.luna.crate.CrateService crates;
    private static net.pokereport.luna.rank.RankService ranks;
    private static net.pokereport.luna.traje.TrajeService trajes;
    private static net.pokereport.luna.backpack.BackpackService backpacks;
    private static net.pokereport.luna.world.Regreso regresos;
    private static net.pokereport.luna.gym.MedallaService medallas;
    private static net.pokereport.luna.cosmetics.CosmeticsService cosmetics;
    private static ExecutorService io;
    /** Clave de alta de constructor. Vacía = las altas están cerradas. */
    private static String builderKey = "";

    @Override
    public void onInitializeServer() {
        LOG.info("Luna Eternal — iniciando");

        // ⚠ AQUI Y NO EN SERVER_STARTED: `SERVER_STARTED` corre en cada
        //   arranque del servidor, y registrar un evento dos veces lo llama dos
        //   veces. En un servidor dedicado solo pasa una, pero no hay motivo
        //   para depender de eso -- y un dia se prueba en un mundo local.
        net.pokereport.luna.world.Decorativos.protegerlos();
        net.pokereport.luna.world.Decorativos.fueraDeLaPokedex();
        net.pokereport.luna.world.Decorativos.abrirViajesAlTocar();
        // ⚠⚠⚠ TODO LO DE GIMNASIOS VA DETRAS DE ESTA GUARDA, Y NO ES PARANOIA.
        //    El paquete `gym` toca clases de rctmod --TrainerMob, RCTMod-- que
        //    son `modCompileOnly`: existen al compilar y puede que no al
        //    arrancar. Sin la guarda, un servidor sin rctmod se cae con
        //    NoClassDefFoundError, que es un error que NO NOMBRA al mod que
        //    falta y manda a buscar el fallo a nuestro codigo.
        //    ⚠ Y basta con mirarlo una vez: un mod no se carga a media partida.
        if (hayEntrenadores()) {
            // El clic derecho en un lider: dialogo en la ciudadela, combate en
            // la arena. Va aqui --y no en SERVER_STARTED-- porque
            // `UseEntityCallback` se registra una vez, como los de arriba.
            net.pokereport.luna.gym.Combate.registrarClic();
        } else {
            LOG.warn("rctmod no esta instalado: los gimnasios quedan apagados");
        }

        ServerLifecycleEvents.SERVER_STARTING.register(server -> boot());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> shutdown());

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // ⚠ El borde del salvaje SE APLICA EN CADA ARRANQUE. Se guarda en el
            //   nivel, asi que bastaria con ponerlo una vez -- hasta que alguien
            //   lo cambie con un comando o restauremos un respaldo viejo.
            net.pokereport.luna.world.Salvaje.ponerBorde(server);
            Tablist.setup(server);
            // Se suscribe cuando Cobblemon ya esta cargado del todo.
            net.pokereport.luna.pokedex.CaptureListener.register();
            // Los OFICIOS: mineria, pesca, cultivo y cria.
            net.pokereport.luna.progression.OficiosListener.register();
            net.pokereport.luna.pokedex.ScanListener.register();
            // ⚠ Aqui y no antes: se suscribe a un evento de Cobblemon, y
            //   hasta SERVER_STARTED no esta cargado del todo.
            if (hayEntrenadores()) {
                net.pokereport.luna.gym.Combate.escuchar();
            }

            // ⚠ Las ordenes vencidas se cierran AL ARRANCAR y se devuelve lo
            //   retenido. Y las consultas del libro filtran ademas por
            //   expires_at, para que una vencida no se pueda cruzar aunque el
            //   barrido no haya pasado: una tarea periodica es otra cosa que
            //   puede no estar corriendo.
            submit(() -> {
                try {
                    int n = market.caducar();
                    if (n > 0) {
                        LOG.info("Mercado: {} ordenes caducadas, lo retenido devuelto", n);
                    }
                } catch (Exception e) {
                    LOG.error("No se pudieron caducar las ordenes del mercado", e);
                }
            });
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            Tablist.onJoin(server, player);
            // ⚠⚠ EL RANGO SE CARGA Y LUEGO SE REPINTA LA ETIQUETA. `onJoin` ya
            //    la puso, pero con el rango que hubiera en cache -- y al entrar
            //    no hay ninguno. Sin este repintado, quien entra se ve ENTRENADOR
            //    hasta la siguiente vez que algo toque su prefijo, que puede
            //    ser nunca. Es la leccion del 23-ago: si el servidor cambia un
            //    estado que el cliente dibuja, el servidor lo reenvia.
            if (ranks != null) {
                var perfil = player.getGameProfile();
                ranks.cargar(perfil.getId(), perfil.getName(),
                        () -> server.execute(() -> {
                            if (!player.isRemoved()) {
                                Tablist.refrescarClan(server, player);
                                cargarTraje(player);
                            }
                        }));
            }
            // ⚠ El tiempo de juego de hoy: se lee al entrar y se lleva en
            //   memoria. La pantalla de Tesoros ensena cuanto falta para la
            //   llave diaria, y eso se pregunta EN EL HILO DEL SERVIDOR.
            submit(() -> {
                try {
                    var perfilA = player.getGameProfile();
                    long id = players.resolve(perfilA.getId(), perfilA.getName());
                    server.execute(() -> {
                        if (!player.isRemoved()) {
                            net.pokereport.luna.crate.Actividad.alEntrar(player, id);
                        }
                    });
                } catch (Exception e) {
                    LOG.warn("No se pudo iniciar la actividad de {}",
                            player.getGameProfile().getName(), e);
                }
            });
            // ⚠⚠ LAS MEDALLAS SE CARGAN AL ENTRAR, y por el mismo motivo que
            //    el rango: las pregunta el dialogo del gimnasio EN EL MOMENTO
            //    DEL CLIC, que corre en el hilo del servidor. La cache no es una
            //    optimizacion, es lo que permite contestar sin ir a la base.
            if (medallas != null) {
                var perfilM = player.getGameProfile();
                medallas.cargar(perfilM.getId(), perfilM.getName(),
                        () -> server.execute(() -> {
                            if (!player.isRemoved()) {
                                // ⚠ Y se reenvia la ficha: al entrar ya se mando
                                //   una con cero medallas --porque aun no habian
                                //   llegado-- y sin este reenvio el PokePad las
                                //   ensenaria todas apagadas hasta reabrirlo.
                                net.pokereport.luna.net.Red.enviarSaldo(player);
                            }
                        }));
            }
            // ⚠⚠ SI VUELVE DENTRO DE UNA ARENA, FUERA. Pasa de verdad: alguien
            //    se desconecta a mitad de combate, o el servidor se reinicia con
            //    gente dentro. Al volver aparece en una copia que ya no tiene
            //    reservada, sin lider y SIN SALIDA -- de la dimension de
            //    gimnasios no se sale andando.
            if (hayEntrenadores()) {
                net.pokereport.luna.gym.Combate.alEntrar(player);
            }

            // La etiqueta del clan, que va en el mismo equipo que el rango.
            Tablist.refrescarClan(server, player);
            PlayerCache.refresh(player);

            // Entregas pendientes del GTS: compras sin entregar, listados
            // retirados y, sobre todo, listados CADUCADOS — que antes dejaban
            // el objeto perdido para siempre.
            var profile = player.getGameProfile();
            submit(() -> {
                try {
                    long id = players.resolve(profile.getId(), profile.getName());
                    net.pokereport.luna.gts.GtsDelivery.claimAll(player, id);

                    // Y lo que le deba el MERCADO: lo comprado mientras estaba
                    // desconectado, y lo devuelto de ordenes canceladas o
                    // caducadas. Ver MarketDelivery.
                    net.pokereport.luna.market.MarketDelivery.entregarTodo(player, id);

                    // Los cosmeticos que ven los demas --auras, sombreros,
                    // capas-- EN LAS DOS DIRECCIONES. Ver `Red.difundirTodo`.
                    net.pokereport.luna.net.Red.difundirTodo(player);
                } catch (Exception e) {
                    LOG.error("No se pudieron comprobar las entregas pendientes", e);
                }
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.getPlayer();
            players.forget(player.getUuid());
            PlayerCache.forget(player);
            net.pokereport.luna.heal.HealService.olvidar(player);
            net.pokereport.luna.pokedex.ScanListener.olvidar(player);
            // ⚠⚠ ANTES de `players.forget`: guardar necesita resolver el id,
            //    y si ya se ha olvidado hay que volver a la base a buscarlo.
            // ⚠ ANTES de olvidar al jugador: apuntar necesita resolver su id.
            net.pokereport.luna.world.Regreso.apuntar(player);
            net.pokereport.luna.backpack.Abiertas.guardarYOlvidar(player);
            net.pokereport.luna.rank.RankService.olvidar(player.getUuid());
            // ⚠⚠ ANTES de `players.forget`: el volcado necesita resolver el
            //    id, y si ya se olvido tendria que volver a la base.
            net.pokereport.luna.crate.Actividad.alSalir(player);
            net.pokereport.luna.traje.TrajeService.olvidar(player.getUuid());
            // ⚠⚠ SIN ESTO LA RANURA DEL GIMNASIO QUEDA PILLADA PARA SIEMPRE.
            //    Quien cierra el juego a mitad de combate deja su copia
            //    reservada a un jugador que ya no esta, y al octavo nadie mas
            //    puede retar al lider. No da ningun error: deja de funcionar.
            if (hayEntrenadores()) {
                net.pokereport.luna.gym.Combate.alSalir(player);
            } else {
                net.pokereport.luna.gym.Ranuras.alSalir(player);
            }
            net.pokereport.luna.gym.MedallaService.olvidar(player.getUuid());
            // ⚠ Y su cuenta atras, que ademas SUELTA lo que tuviera reservado:
            //   irse a mitad de la cuenta del gimnasio dejaba la ranura pillada.
            net.pokereport.luna.world.Espera.olvidar(player.getUuid());
            Tablist.onLeave(server, player);
        });

        // AQUI IBA LA INTERFAZ.
        //
        // El clic derecho con El Almanaque abria el menu de cofre. Se retiro
        // entero con los menus (D-026): la interfaz se rehace en el cliente,
        // con arte real, y el disparador lo pondra ella.
        //
        // Lo que hay debajo —economia, progresion, tienda, GTS, Pokedex, kits,
        // misiones, cazas, viaje entre mundos— sigue intacto y con sus
        // invariantes en /luna autotest. Lo que falta es la pantalla.

        // LA BARRA LATERAL TAMBIEN SE FUE (D-026).
        //
        // Era un marcador de vanilla, y se notaba: la columna de numeros rojos
        // que Minecraft dibuja a la derecha no se puede quitar, y las lineas en
        // gris apagado eran las de un objetivo que no cabia. Estorbaba mientras
        // se construye y ademas era justo el tipo de interfaz que este proyecto
        // ha decidido no tener.
        //
        // Lo que ENSEÑABA sigue siendo la especificacion del HUD del cliente
        // —fase lunar, tres saldos, via dominante, clan, oficio, medallas—, y
        // todo eso lo sigue calculando PlayerCache. Lo que se tira es el
        // marcador, no el diseño.

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Va ANTES del corte de 20 ticks: lleva su propio ritmo, y
            // encadenarlo al de aqui lo ataria a un numero que no es suyo.
            net.pokereport.luna.world.ConstructorBuffs.tick(server);
            // Lo mismo: lleva su propio ritmo (ticks exactos), asi que va
            // ANTES del corte de 20.
            net.pokereport.luna.gym.Programador.tick(server);

            if (server.getTicks() % 20 != 0) return;
            // ⚠ La cuenta atras de los viajes va AQUI, en el corte de 20 ticks:
            //   lo que ensena es un numero de SEGUNDOS, y mirar la posicion
            //   veinte veces por segundo no la haria mas justa, solo mas
            //   quisquillosa -- un tiron de red moveria al jugador medio bloque
            //   y volveria, cancelandole el viaje sin que el tocara nada.
            net.pokereport.luna.world.Espera.tick(server);
            // ⚠ El tiempo de juego ACTIVO va aqui, en el corte de 20 ticks: lo
            //   que cuenta son SEGUNDOS, y mirar la posicion veinte veces por
            //   segundo no lo haria mas justo -- solo veinte veces mas caro.
            net.pokereport.luna.crate.Actividad.tick(server);
            // El contador de conectados cambia con cada entrada y salida;
            // recalcularlo aquí evita tener que engancharlo a cada evento.
            Tablist.updateHeaderFooter(server);

            // Informe economico al log cada hora. Sin historial no se puede
            // ver una tendencia, y una tendencia es lo unico que permite
            // corregir antes de que el problema sea visible.
            if (server.getTicks() % 72_000 == 0) {
                net.pokereport.luna.command.EconomyReport.logDaily();
            }
        });

        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registry, env) -> LunaCommand.register(dispatcher));
    }

    private void boot() {
        try {
            LunaConfig cfg = LunaConfig.load();
            net.pokereport.luna.economy.Currency.applyDisplayNames(
                cfg.nameePokedollar, cfg.nameMark, cfg.namePremium);
            builderKey = cfg.builderKey;
            LOG.info("Altas de constructor: {}",
                     builderKey.isBlank() ? "CERRADAS" : "abiertas con clave");
            database = new Database(cfg);
            database.migrate();

            players = new PlayerService(database);
            economy = new EconomyService(database);
            progression = new net.pokereport.luna.progression.ProgressionService(database);
            // Valida el invariante anti-arbitraje. Si el catálogo permite
            // ganar dinero comprando y revendiendo, el servidor NO arranca.
            shop = net.pokereport.luna.shop.ShopCatalog.load();
            gts = new net.pokereport.luna.gts.GtsService(database);
            pokedex = new net.pokereport.luna.pokedex.PokedexService(database);
            // Valida el tope diario. Si un kit inyecta de mas, NO arranca.
            kits = net.pokereport.luna.kit.KitCatalog.load();
            kitService = new net.pokereport.luna.kit.KitService(database);
            clans = new net.pokereport.luna.clan.ClanService(database);
            market = new net.pokereport.luna.market.MarketService(database);
            tasador = new net.pokereport.luna.market.Tasador(database);
            quests = new net.pokereport.luna.quest.QuestService(database);
            stats = new net.pokereport.luna.economy.EconomyStats(database);
            hunts = new net.pokereport.luna.hunt.HuntService(database);
            ranks = new net.pokereport.luna.rank.RankService(database);
            trajes = new net.pokereport.luna.traje.TrajeService(database);
            backpacks = new net.pokereport.luna.backpack.BackpackService(database);
            regresos = new net.pokereport.luna.world.Regreso(database);
            medallas = new net.pokereport.luna.gym.MedallaService(database);
            crates = new net.pokereport.luna.crate.CrateService(database);
            net.pokereport.luna.crate.Actividad.arrancar(database);
            cosmetics = new net.pokereport.luna.cosmetics.CosmeticsService(database);
            io = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "luna-io");
                t.setDaemon(true);
                return t;
            });

            LOG.info("Luna Eternal — base de datos lista");
        } catch (Exception e) {
            LOG.error("FALLO AL ARRANCAR: {}", e.getMessage(), e);
            throw new RuntimeException(
                "Luna Eternal no pudo iniciarse. Revisa config/lunaeternal.properties", e);
        }
    }

    private void shutdown() {
        LOG.info("Luna Eternal — cerrando");
        if (io != null) {
            io.shutdown();
            try {
                if (!io.awaitTermination(10, TimeUnit.SECONDS)) io.shutdownNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (database != null) database.close();
    }

    /**
     * Ejecuta trabajo de base de datos fuera del hilo del servidor.
     * Nunca se consulta la base en el bucle de tick (data-model.md §4).
     */
    public static void submit(Runnable task) {
        if (io == null) return;
        io.submit(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                LOG.error("Error en tarea de fondo", t);
            }
        });
    }

    public static String builderKey() { return builderKey; }
    public static Database database() { return database; }
    public static PlayerService players() { return players; }
    public static EconomyService economy() { return economy; }
    public static net.pokereport.luna.progression.ProgressionService progression() {
        return progression;
    }
    public static net.pokereport.luna.shop.ShopCatalog shop() { return shop; }
    public static net.pokereport.luna.market.MarketService market() { return market; }
    public static net.pokereport.luna.market.Tasador tasador() { return tasador; }
    public static net.pokereport.luna.hunt.HuntService hunts() { return hunts; }
    public static net.pokereport.luna.crate.CrateService crates() { return crates; }

    public static net.pokereport.luna.rank.RankService ranks() { return ranks; }
    public static net.pokereport.luna.gym.MedallaService medallas() {
        return medallas;
    }

    /**
     * ¿Esta rctmod instalado?
     *
     * <p>⚠ De el salen los lideres de gimnasio y sus combates. Es
     * {@code modCompileOnly}: existe al compilar y puede no existir al arrancar,
     * asi que todo lo que lo toca se pregunta esto primero.
     */
    public static boolean hayEntrenadores() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .isModLoaded("rctmod");
    }
    public static net.pokereport.luna.traje.TrajeService trajes() { return trajes; }

    public static net.pokereport.luna.backpack.BackpackService backpacks() {
        return backpacks;
    }

    public static net.pokereport.luna.world.Regreso regresos() {
        return regresos;
    }
    public static net.pokereport.luna.cosmetics.CosmeticsService cosmetics() { return cosmetics; }
    public static net.pokereport.luna.gts.GtsService gts() { return gts; }
    public static net.pokereport.luna.pokedex.PokedexService pokedex() { return pokedex; }
    public static net.pokereport.luna.kit.KitCatalog kits() { return kits; }
    public static net.pokereport.luna.kit.KitService kitService() { return kitService; }
    public static net.pokereport.luna.clan.ClanService clans() { return clans; }
    public static net.pokereport.luna.quest.QuestService quests() { return quests; }
    public static net.pokereport.luna.economy.EconomyStats stats() { return stats; }

    /**
     * Carga el traje de quien entra y lo reparte.
     *
     * <h2>⚠⚠ VA DENTRO DEL CALLBACK DEL RANGO, Y NO AL LADO</h2>
     *
     * `revisar()` necesita saber el escalon, y el escalon lo carga `ranks`
     * <b>de forma asincrona</b>. Llamado en paralelo leeria el rango ANTERIOR
     * --o ninguno-- y le quitaria el traje a alguien que si puede llevarlo. Es
     * exactamente la trampa de `conceder()` en la pantalla del inicial: un
     * metodo que encola y vuelve PARECE sincrono.
     *
     * <h2>⚠⚠ Y HACE LAS DOS MITADES DEL REPARTO</h2>
     *
     * <ul>
     *   <li>{@code repartirTraje} — decirle a los demas lo que lleva este;</li>
     *   <li>{@code ponerAlDia} — decirle a este lo que llevan los demas.</li>
     * </ul>
     *
     * La segunda es la que se olvida siempre, y sin ella quien entra ve a todo
     * el mundo sin traje hasta que alguien se cambie de ropa.
     */
    private static void cargarTraje(net.minecraft.server.network.ServerPlayerEntity player) {
        if (trajes == null) {
            return;
        }
        submit(() -> {
            final long id;
            try {
                id = players.resolve(player.getUuid(),
                        player.getGameProfile().getName());
            } catch (java.sql.SQLException e) {
                LOG.error("No se pudo resolver el jugador para su traje", e);
                return;
            }
            trajes.cargar(id, player.getUuid());
            player.getServer().execute(() -> {
                if (player.isRemoved()) {
                    return;
                }
                // ⚠ El rango se puede BAJAR, y el permiso solo se mira al
                //   ponerselo. Sin esta revision, quien baje de MAESTRO seguiria
                //   con el traje de MAESTRO para siempre.
                submit(() -> {
                    trajes.revisar(player, id);
                    player.getServer().execute(() -> {
                        if (player.isRemoved()) {
                            return;
                        }
                        net.pokereport.luna.net.Red.repartirTraje(player);
                        net.pokereport.luna.net.Red.ponerAlDia(player);
                    });
                });
            });
        });
    }
}
