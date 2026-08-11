package net.pokereport.luna.ui;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;

/**
 * Abre interfaces respetando la separación de hilos.
 *
 * <p>El orden importa y no es negociable:
 * <ol>
 *   <li>los datos de la base se leen en el hilo de E/S,</li>
 *   <li>el estado del mundo se lee en el hilo del servidor,</li>
 *   <li>el menú se abre en el hilo del servidor.</li>
 * </ol>
 *
 * <p>Hacerlo al revés —consultar la base mientras se dibuja— congela el
 * servidor entero cada vez que alguien abre un menú, y con cien jugadores eso
 * no es un detalle: es el servidor caído
 * ({@code docs/technical/data-model.md} §4).
 */
public final class MenuService {

    /**
     * Ficha en memoria de cada jugador conectado.
     *
     * <p>Es lo que permite que la barra lateral se repinte cada segundo sin
     * tocar la base de datos ni una sola vez.
     */
    private static final java.util.Map<java.util.UUID, PlayerSnapshot> CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    private MenuService() {}

    /** Carga la ficha del jugador y abre El Almanaque. */
    public static void openAlmanac(ServerPlayerEntity player) {
        loadSnapshot(player, snapshot -> new AlmanacMenu(snapshot).open(player));
    }

    /**
     * Abre un submenú recargando primero los datos.
     *
     * <p>Se recarga a propósito: entre que se abrió El Almanaque y se pulsó
     * una sección, el saldo puede haber cambiado. Enseñar datos viejos en una
     * pantalla de dinero es peor que tardar 50 ms más.
     */
    public static void openChild(ServerPlayerEntity player, Menu parent,
                                 java.util.function.Function<PlayerSnapshot, Menu> factory) {
        loadSnapshot(player, snapshot -> parent.openChild(player, factory.apply(snapshot)));
    }

    /** Ficha en caché, o {@code null} si todavía no se ha cargado. */
    public static PlayerSnapshot cached(ServerPlayerEntity player) {
        return CACHE.get(player.getUuid());
    }

    public static void forget(ServerPlayerEntity player) {
        CACHE.remove(player.getUuid());
    }

    /** Recarga desde la base y actualiza la caché. */
    public static void refresh(ServerPlayerEntity player) {
        loadSnapshot(player, s -> {});
    }

    /**
     * Lee la base en segundo plano y entrega la ficha ya en el hilo del
     * servidor, con el estado del mundo incorporado.
     */
    public static void loadSnapshot(ServerPlayerEntity player,
                                    java.util.function.Consumer<PlayerSnapshot> then) {
        var server = player.getServer();
        if (server == null) return;

        var profile = player.getGameProfile();

        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(profile.getId(), profile.getName());
                var snap = new PlayerSnapshot(id, profile.getName());
                for (Currency c : Currency.values()) {
                    snap.setBalance(c, LunaEternal.economy().balance(id, c));
                }
                snap.paths = LunaEternal.progression().all(id);
                snap.recent = LunaEternal.economy().recentEntries(id, 14);

                var dominant = LunaEternal.progression().dominant(id);
                if (dominant != null && dominant.level() > 0) {
                    snap.dominantPath = dominant.path().displayName;
                    snap.dominantLevel = dominant.level();
                } else {
                    snap.dominantPath = "Sin definir";
                    snap.dominantLevel = 0;
                }

                // De vuelta al hilo del servidor: el mundo no se toca desde fuera.
                server.execute(() -> {
                    if (player.isRemoved()) return;
                    var world = player.getWorld();
                    snap.moonPhase = world.getMoonPhase();
                    snap.night = !world.isDay();
                    CACHE.put(player.getUuid(), snap);
                    then.accept(snap);
                });

            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo cargar la ficha de {}",
                                      profile.getName(), e);
                server.execute(() -> player.sendMessage(Text.literal(
                    "§cNo se pudo abrir el Almanaque. Inténtalo de nuevo."), false));
            }
        });
    }
}
