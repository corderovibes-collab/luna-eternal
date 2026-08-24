package net.pokereport.luna.market;

import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;

/**
 * Entrega lo que el mercado le debe a un jugador.
 *
 * <h2>⚠⚠ Por qué esto tiene que existir</h2>
 *
 * Un mercado es <b>asíncrono</b>: tu orden se llena cuando a otro le apetece
 * vender, y eso pasa sobre todo cuando tú no estás. Si los objetos se metieran
 * en el inventario en el momento del cruce, habría que elegir entre dos cosas
 * malas: <b>no dejar cruzar contra alguien desconectado</b> —lo que mata la
 * mitad del mercado— o <b>perder los objetos</b>.
 *
 * <p>El GTS ya aprendió esta lección por las malas en V006: un listado que
 * caducaba se quedaba en {@code EXPIRED} y el objeto no volvía a su dueño jamás.
 *
 * <h2>⚠ El dinero no pasa por aquí, y los objetos sí</h2>
 *
 * Un saldo vive en la base: se abona y ya está, estés dentro o no. Un objeto
 * tiene que entrar en un inventario, y un inventario solo existe mientras su
 * dueño está conectado. Esa asimetría es la razón de que exista
 * {@code market_claim}.
 */
public final class MarketDelivery {

    private MarketDelivery() {}

    /**
     * Entrega todo lo pendiente. <b>Se llama desde el hilo de E/S.</b>
     *
     * <h2>⚠⚠ El orden importa, y es al revés de lo que parece</h2>
     *
     * Se <b>mete primero y se marca después</b>. Si se marcara antes y la
     * entrega fallara, el jugador perdería los objetos y la fila diría que los
     * tiene — irrecuperable. Al revés, lo peor que puede pasar es entregar dos
     * veces, y eso lo impide el {@code WHERE delivered_at IS NULL} de
     * {@code marcarEntregada}, que solo afecta a una fila.
     *
     * <p>⚠ Y se marca <b>una a una</b>, no todas al final: si el servidor se cae
     * a mitad de la lista, lo ya entregado queda marcado y lo que falta sigue
     * pendiente. Marcar en bloque al terminar convertiría una caída en una
     * entrega doble de todo el lote.
     */
    public static void entregarTodo(ServerPlayerEntity jugador, long playerId) {
        var svc = LunaEternal.market();
        var servidor = jugador.getServer();
        if (svc == null || servidor == null) {
            return;
        }
        try {
            var deudas = svc.deudas(playerId);
            if (deudas.isEmpty()) {
                return;
            }
            int entregadas = 0;
            for (var d : deudas) {
                var item = Inventarios.objeto(d.itemId());
                if (item == null) {
                    // El objeto ya no existe --se desinstaló el mod que lo
                    // traía--. Se marca para no reintentarlo eternamente, y
                    // queda en el log: perder un objeto en silencio es peor.
                    LunaEternal.LOG.warn("Deuda {} de un objeto que ya no existe: {}",
                            d.id(), d.itemId());
                    svc.marcarEntregada(d.id());
                    continue;
                }
                servidor.execute(() -> {
                    if (!jugador.isRemoved()) {
                        Inventarios.meter(jugador, item, d.qty());
                    }
                });
                if (svc.marcarEntregada(d.id())) {
                    entregadas++;
                }
            }
            if (entregadas > 0) {
                final int n = entregadas;
                servidor.execute(() -> jugador.sendMessage(
                        net.minecraft.text.Text.literal(
                                "§aEl mercado te ha entregado " + n
                                + (n == 1 ? " lote." : " lotes.")), false));
            }
        } catch (Exception e) {
            LunaEternal.LOG.error("No se pudieron entregar las deudas del mercado", e);
        }
    }
}
