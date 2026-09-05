package net.pokereport.luna.santuario;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.world.Decorativos;
import net.pokereport.luna.world.LunaDimensions;

/**
 * LA MEW DE LA ENTRADA DEL MONUMENTO: el recibidor del santuario.
 *
 * <h2>⚠⚠ ES UN DECORATIVO CON UNA TERCERA ETIQUETA, no un sistema nuevo</h2>
 *
 * La Mew es un Pokémon decorativo exactamente igual que el Kabutops del
 * laboratorio --quieta, sin nivel, sin captura-- y lo único que la distingue
 * es la etiqueta {@link #MARCA}: el clic derecho en ella abre la app Santuario,
 * igual que la etiqueta de parada abre Viajes. Toda la maquinaria pesada (las
 * diez protecciones del decorativo) ya existe y no se toca.
 *
 * <p>⚠ Decisión del usuario: la Mew es el recibidor, no un vendedor. El
 * dinero nunca pasa por ella -- cobra el servidor en su transacción, como
 * siempre (P6).
 */
public final class SantuarioNpc {

    /** La etiqueta que convierte a un decorativo en la puerta del santuario. */
    public static final String MARCA = "luna_santuario";

    private SantuarioNpc() {}

    /**
     * Clic derecho en la Mew: abre la app.
     *
     * <p>⚠ Como en Viajes: {@code SUCCESS} corta el camino de Cobblemon (su
     * menú de interacción no debe abrirse) y se corta en los dos lados porque el
     * evento corre en los dos.
     */
    public static void registrarClic() {
        UseEntityCallback.EVENT.register((jugador, mundo, mano, entidad, golpe) -> {
            if (!entidad.getCommandTags().contains(MARCA)) {
                return ActionResult.PASS;
            }
            if (mano != Hand.MAIN_HAND) {
                return ActionResult.SUCCESS;
            }
            if (jugador instanceof ServerPlayerEntity sp) {
                net.pokereport.luna.net.Red.enviarAbrirSantuario(sp);
            }
            return ActionResult.SUCCESS;
        });
    }

    /**
     * Coloca la Mew donde esta el jugador.
     *
     * <p>⚠ Se quita la que hubiera cerca ANTES de poner la nueva: el comando se
     * puede repetir y un decorativo no se puede ni capturar ni matar, asi que
     * una Mew de mas se quedaria ahi para siempre.
     *
     * @return {@code true} si quedo colocada
     */
    public static boolean colocar(ServerPlayerEntity jugador) {
        var mundo = jugador.getServerWorld();
        if (!LunaDimensions.CIUDADELA.equals(mundo.getRegistryKey())) {
            return false;
        }
        var pos = jugador.getPos();
        Decorativos.quitar(mundo, pos, 3);
        var e = Decorativos.colocar(mundo, "mew", Decorativos.Postura.FLOTANDO,
                pos, jugador.getYaw());
        if (e == null) {
            return false;
        }
        e.addCommandTag(MARCA);
        LunaEternal.LOG.info("Santuario: Mew colocada en {} por {}",
                e.getBlockPos(), jugador.getGameProfile().getName());
        return true;
    }
}
