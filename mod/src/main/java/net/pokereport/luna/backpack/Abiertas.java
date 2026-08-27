package net.pokereport.luna.backpack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;

/**
 * Las mochilas que están abiertas ahora mismo, para poder guardarlas.
 *
 * <h2>⚠⚠ HACE FALTA PORQUE EL CIERRE NO TRAE EL INVENTARIO</h2>
 *
 * Cuando el jugador cierra la pantalla, lo que llega es «se cerró el
 * contenedor» — y para entonces el {@code ScreenHandler} ya se ha ido. Sin
 * guardar aquí una referencia al inventario, no habría nada que escribir.
 *
 * <h2>⚠⚠⚠ Y SIN ESTO, DESCONECTAR DENTRO DE LA MOCHILA LA VACÍA</h2>
 *
 * Al desconectar, Minecraft cierra el contenedor sin avisar a nadie. Si solo
 * guardáramos al cerrar la pantalla a mano, quien saliera con la mochila
 * abierta perdería <b>todo lo que hubiera movido</b> — y como el guardado borra
 * y reescribe, lo que se perdería es la mochila entera de esa sesión.
 */
public final class Abiertas {

    private Abiertas() {}

    private static final Map<UUID, SimpleInventory> ABIERTAS = new ConcurrentHashMap<>();

    public static void recordar(ServerPlayerEntity jugador, SimpleInventory inv) {
        ABIERTAS.put(jugador.getUuid(), inv);
    }

    /**
     * Guarda y olvida. <b>Va por el executor de E/S.</b>
     *
     * <p>⚠ Se quita del mapa ANTES de escribir. Si se quitara después, dos
     * cierres seguidos —cerrar y desconectar en el mismo tick— guardarían dos
     * veces, y la segunda podría pisar la primera con datos ya viejos.
     */
    public static void guardarYOlvidar(ServerPlayerEntity jugador) {
        SimpleInventory inv = ABIERTAS.remove(jugador.getUuid());
        if (inv == null) {
            return;
        }
        var svc = LunaEternal.backpacks();
        if (svc == null) {
            return;
        }
        // ⚠ Los registros se leen AQUI, en el hilo del servidor. Pedírselos al
        //   jugador desde el executor sería tocar el mundo desde fuera — y si
        //   ya se ha desconectado, ni siquiera están.
        RegistryWrapper.WrapperLookup registros = jugador.getRegistryManager();
        final UUID uuid = jugador.getUuid();
        final String nombre = jugador.getName().getString();

        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(uuid, nombre);
                svc.guardar(id, inv, registros);
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo guardar la mochila de {}",
                        nombre, e);
            }
        });
    }
}
