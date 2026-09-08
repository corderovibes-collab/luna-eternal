package net.pokereport.luna.santuario;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.ui.Cartel;
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

    /** La del cartel que flota encima. */
    private static final String MARCA_CARTEL = "luna_santuario_cartel";

    /**
     * Cuanto flota el cartel sobre la Mew.
     *
     * <p>⚠ Es mas alto que el de Oak (2,45) porque <b>la Mew ya flota</b>:
     * {@code Postura.FLOTANDO} le quita la gravedad y se queda a la altura a la
     * que se coloco. Con la altura de Oak, el cartel le habria salido a la
     * altura de la cara.
     */
    private static final double ALTURA_CARTEL = 2.6;

    /** Radio que se barre al limpiar antes de colocar. */
    private static final double RADIO = 3.0;

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
            if (jugador.isCreative() && jugador.isSneaking()) {
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
        // ⚠⚠ SE BARREN LAS DOS COSAS, y el cartel es la que se olvida:
        //    `Decorativos.quitar` solo mira Pokemon, asi que sin la segunda
        //    linea cada vez que se repitiera el comando quedaria un cartel mas
        //    flotando encima del bueno -- y a un TextDisplay `/kill` no le
        //    llega. Es la leccion de los tres Brocks apilados.
        Decorativos.quitar(mundo, pos, RADIO);
        Cartel.quitar(mundo, pos, RADIO, MARCA_CARTEL);
        var e = Decorativos.colocar(mundo, "mew", Decorativos.Postura.FLOTANDO,
                pos, jugador.getYaw());
        if (e == null) {
            return false;
        }
        e.addCommandTag(MARCA);
        // ⚠⚠ EL CARTEL ES LA MITAD DE QUE LA PUERTA SE ENTIENDA, igual que en
        //    Oak: una Mew flotando en la plaza de Monumentos es decoracion, y
        //    nadie le hace clic derecho a la decoracion. Con el cartel se lee
        //    desde lejos que ahi se reclama un nicho.
        Cartel.poner(mundo, pos, ALTURA_CARTEL, MARCA_CARTEL,
                Cartel.texto("Santuario", "Reclama tu nicho del memorial"));
        LunaEternal.LOG.info("Santuario: Mew colocada en {} por {}",
                e.getBlockPos(), jugador.getGameProfile().getName());
        return true;
    }

    /**
     * Quita la Mew y su cartel de donde esta el jugador.
     *
     * <p>⚠ Hace falta un comando propio porque las dos son <b>inmatables</b>: la
     * Mew lleva la marca de los decorativos --la unica proteccion que cubre el
     * creativo-- y un TextDisplay no recibe daño de nada. `/kill` diria «Killed
     * 2 entities» y no se iria ninguna.
     *
     * @return cuantas entidades se quitaron
     */
    public static int quitar(ServerPlayerEntity jugador) {
        var mundo = jugador.getServerWorld();
        var pos = jugador.getPos();
        return Decorativos.quitar(mundo, pos, RADIO)
                + Cartel.quitar(mundo, pos, RADIO, MARCA_CARTEL);
    }
}
