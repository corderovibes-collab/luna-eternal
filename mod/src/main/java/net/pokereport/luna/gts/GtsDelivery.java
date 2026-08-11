package net.pokereport.luna.gts;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;

/**
 * Entrega lo que el jugador tenga pendiente del GTS.
 *
 * <p>Se ejecuta al conectar y cada pocos minutos. Es la red que hace que
 * <b>nada se pierda</b> aunque el servidor se caiga en mitad de una compra,
 * y la única forma de que un listado caducado vuelva a su dueño.
 */
public final class GtsDelivery {

    private GtsDelivery() {}

    /** Entrega lo pendiente. Silencioso si no hay nada. */
    public static void claimAll(ServerPlayerEntity player, long playerId) {
        var server = player.getServer();
        if (server == null) return;

        LunaEternal.submit(() -> {
            try {
                var claims = LunaEternal.gts().pendingClaims(playerId);
                if (claims.isEmpty()) return;

                server.execute(() -> {
                    int entregados = 0;
                    for (var claim : claims) {
                        var stack = ItemCodec.decode(claim.payload(),
                                                     player.getRegistryManager());
                        if (stack.isEmpty()) {
                            LunaEternal.LOG.error(
                                "Reclamación #{} ilegible; NO se marca entregada",
                                claim.listingId());
                            continue;   // se reintentará al volver a conectar
                        }
                        // Primero el objeto, después la marca. Al revés, un
                        // fallo aquí lo haría desaparecer para siempre.
                        player.getInventory().offerOrDrop(stack);
                        entregados++;

                        player.sendMessage(Text.literal(
                            "§8[§6GTS§8] §7Recibido §f" + claim.displayName()
                            + " §8(" + claim.reason() + ")"), false);

                        long id = claim.listingId();
                        LunaEternal.submit(() -> {
                            try {
                                LunaEternal.gts().markDelivered(id);
                            } catch (Exception e) {
                                LunaEternal.LOG.error(
                                    "No se pudo marcar entregada la reclamación #{}", id, e);
                            }
                        });
                    }
                    if (entregados > 0) {
                        LunaEternal.LOG.info("GTS: {} entregas pendientes resueltas para {}",
                            entregados, player.getGameProfile().getName());
                    }
                });

            } catch (Exception e) {
                LunaEternal.LOG.error("Error resolviendo entregas pendientes", e);
            }
        });
    }
}
