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

    /**
     * Las reclamaciones que ya se ha dicho que no se pueden leer.
     *
     * <h2>⚠⚠ UN ERROR QUE SALE EN CADA LOGIN, PARA SIEMPRE, ENSEÑA A IGNORARLOS</h2>
     *
     * Una reclamación ilegible <b>no se marca entregada</b>, y eso está bien: si
     * el payload dejó de leerse porque falta un mod, marcarla destruiría el
     * objeto para siempre. Pero como no se marca, <b>se reintenta en cada
     * conexión</b> — y escribía un {@code ERROR} cada vez.
     *
     * <p>Es exactamente la lección del diagnóstico de la gráfica del launcher:
     * <b>un aviso que salta siempre es peor que no tenerlo</b>, porque el día
     * que haya uno de verdad tampoco lo va a leer nadie.
     *
     * <p>Ahora se dice <b>una vez por arranque del servidor</b>. Y sobre todo:
     * <b>se le dice al jugador</b>, que es quien tiene algo pendiente que no va
     * a recibir y hasta hoy no se enteraba.
     */
    private static final java.util.Set<Long> YA_AVISADAS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void avisarIlegible(ServerPlayerEntity player, long listingId,
                                       String nombre) {
        if (YA_AVISADAS.add(listingId)) {
            LunaEternal.LOG.error(
                "Reclamación #{} ({}) ilegible; NO se marca entregada. "
                + "No se repetirá hasta el próximo arranque.", listingId, nombre);
        }
        player.sendMessage(Text.literal(
            "§8[§6GTS§8] §cNo se pudo entregar §f" + nombre
            + " §8(#" + listingId + ")§c. Avisa a un administrador."), false);
    }

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
                            avisarIlegible(player, claim.listingId(),
                                           claim.displayName());
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
