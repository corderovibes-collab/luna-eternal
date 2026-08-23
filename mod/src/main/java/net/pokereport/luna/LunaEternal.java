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

    private static Database database;
    private static PlayerService players;
    private static EconomyService economy;
    private static net.pokereport.luna.progression.ProgressionService progression;
    private static net.pokereport.luna.shop.ShopCatalog shop;
    private static net.pokereport.luna.gts.GtsService gts;
    private static net.pokereport.luna.pokedex.PokedexService pokedex;
    private static net.pokereport.luna.kit.KitCatalog kits;
    private static net.pokereport.luna.kit.KitService kitService;
    private static net.pokereport.luna.quest.QuestService quests;
    private static net.pokereport.luna.economy.EconomyStats stats;
    private static net.pokereport.luna.hunt.HuntService hunts;
    private static net.pokereport.luna.cosmetics.CosmeticsService cosmetics;
    private static ExecutorService io;
    /** Clave de alta de constructor. Vacía = las altas están cerradas. */
    private static String builderKey = "";

    @Override
    public void onInitializeServer() {
        LOG.info("Luna Eternal — iniciando");

        ServerLifecycleEvents.SERVER_STARTING.register(server -> boot());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> shutdown());

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Tablist.setup(server);
            // Se suscribe cuando Cobblemon ya esta cargado del todo.
            net.pokereport.luna.pokedex.CaptureListener.register();
            // Los OFICIOS: mineria, pesca, cultivo y cria.
            net.pokereport.luna.progression.OficiosListener.register();
            net.pokereport.luna.pokedex.ScanListener.register();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            Tablist.onJoin(server, player);
            PlayerCache.refresh(player);

            // Entregas pendientes del GTS: compras sin entregar, listados
            // retirados y, sobre todo, listados CADUCADOS — que antes dejaban
            // el objeto perdido para siempre.
            var profile = player.getGameProfile();
            submit(() -> {
                try {
                    long id = players.resolve(profile.getId(), profile.getName());
                    net.pokereport.luna.gts.GtsDelivery.claimAll(player, id);

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

            if (server.getTicks() % 20 != 0) return;
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
            quests = new net.pokereport.luna.quest.QuestService(database);
            stats = new net.pokereport.luna.economy.EconomyStats(database);
            hunts = new net.pokereport.luna.hunt.HuntService(database);
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
    public static net.pokereport.luna.hunt.HuntService hunts() { return hunts; }
    public static net.pokereport.luna.cosmetics.CosmeticsService cosmetics() { return cosmetics; }
    public static net.pokereport.luna.gts.GtsService gts() { return gts; }
    public static net.pokereport.luna.pokedex.PokedexService pokedex() { return pokedex; }
    public static net.pokereport.luna.kit.KitCatalog kits() { return kits; }
    public static net.pokereport.luna.kit.KitService kitService() { return kitService; }
    public static net.pokereport.luna.quest.QuestService quests() { return quests; }
    public static net.pokereport.luna.economy.EconomyStats stats() { return stats; }
}
