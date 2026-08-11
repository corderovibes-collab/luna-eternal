package net.pokereport.luna;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.TypedActionResult;
import net.pokereport.luna.command.LunaCommand;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.EconomyService;
import net.pokereport.luna.player.PlayerService;
import net.pokereport.luna.ui.AlmanacItem;
import net.pokereport.luna.ui.MenuService;
import net.pokereport.luna.ui.Sidebar;
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
    private static ExecutorService io;

    @Override
    public void onInitializeServer() {
        LOG.info("Luna Eternal — iniciando");

        ServerLifecycleEvents.SERVER_STARTING.register(server -> boot());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> shutdown());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            // El Almanaque y la barra lateral se montan ya: son lo primero
            // que el jugador ve, y no dependen de la base de datos.
            AlmanacItem.ensure(player);
            Sidebar.install(player);
            MenuService.refresh(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.getPlayer();
            players.forget(player.getUuid());
            MenuService.forget(player);
            Sidebar.remove(player);
        });

        // Clic derecho con el Almanaque: se abre. Sin comandos (P9).
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient() && AlmanacItem.is(stack)
                    && player instanceof ServerPlayerEntity sp) {
                MenuService.openAlmanac(sp);
                return TypedActionResult.success(stack, false);
            }
            return TypedActionResult.pass(stack);
        });

        // Si alguien consigue tirarlo, el objeto tirado se desvanece: el
        // jugador lo recupera solo, así que dejarlo crearía duplicados.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity item) AlmanacItem.discardIfAlmanac(item);
        });

        // Refresco de la barra lateral. Lee de la caché en memoria, nunca de
        // la base de datos, y solo envía paquetes si el contenido cambió.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                var snap = MenuService.cached(p);
                if (snap == null) continue;
                snap.moonPhase = p.getWorld().getMoonPhase();
                snap.night = !p.getWorld().isDay();
                Sidebar.update(p, snap);
                AlmanacItem.ensure(p);
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
            database = new Database(cfg);
            database.migrate();

            players = new PlayerService(database);
            economy = new EconomyService(database);
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

    public static Database database() { return database; }
    public static PlayerService players() { return players; }
    public static EconomyService economy() { return economy; }
}
