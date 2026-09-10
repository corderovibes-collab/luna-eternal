package net.pokereport.luna.starter;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.starter.StarterHandler;
import com.cobblemon.mod.common.config.starter.StarterCategory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;

import java.util.List;

/**
 * EL REPARTIDOR DE INICIALES DE COBBLEMON, APAGADO.
 *
 * <p>Aqui el inicial se elige <b>ante el Profesor Oak</b> (D-048) y lo entrega
 * {@code StarterService}, que marca {@code kit_claim} y avanza la mision. El de
 * Cobblemon es un segundo repartidor que no sabe nada de eso.
 *
 * <h2>&#9888;&#9888;&#9888; NO ERA TEORICO: EL USUARIO COGIO UN INICIAL DESDE EL
 * LOBBY PULSANDO UNA TECLA</h2>
 *
 * Y con Hoenn y Sinnoh en la lista, que es <b>el doble de generaciones de las
 * que este servidor admite</b> (D-017). Peor todavia: un Pokemon repartido por
 * ahi <b>no marca {@code kit_claim}</b>, asi que despues se le puede pedir OTRO
 * a Oak. Dos iniciales por jugador, sin un solo error.
 *
 * <h2>&#9888;&#9888;&#9888; SE APAGA EN EL SERVIDOR, NO SE ESCONDE LA TECLA</h2>
 *
 * Lo facil era quitar el atajo del cliente. No sirve, y se comprobo en el
 * bytecode antes de escribir esto: {@code SelectStarterPacketHandler} llama a
 * {@code Cobblemon.getStarterHandler().chooseStarter(...)}, o sea que <b>quien
 * reparte el Pokemon es este objeto</b>. Un cliente modificado manda el paquete
 * sin necesitar ninguna pantalla (P6). Apagando el repartidor no hay nada que
 * mandar.
 *
 * <p>&#9888;&#9888; Y de propina <b>la pantalla deja de abrirse sola</b>: el
 * cliente PIDE (`RequestStarterScreenPacket`) y el servidor contesta llamando a
 * {@link #requestStarterChoice}. Si eso no hace nada, no se manda el paquete que
 * abre la interfaz. Un solo cambio tapa la puerta y la ventana.
 *
 * <p>&#9888; {@code setStarterHandler} es <b>API publica</b> de Cobblemon, no un
 * mixin ni un parche: esto es el hueco que ellos dejan para justamente esto.
 *
 * <h2>&#9888;&#9888; LO QUE ESTO CORRIGE DE CLAUDE.md</h2>
 *
 * D-048 dejo escrito que <i>«no habia ninguna tecla que quitar»</i> y que el
 * unico hueco era <i>«un CONSTRUCTOR con {@code /openstarterscreen}, o sea op 2:
 * es de staff, no se blinda»</i>. <b>Eso ha dejado de ser cierto</b> --el
 * usuario lo abrio como jugador normal-- y por eso hoy se blinda. La conclusion
 * de entonces era correcta para lo que se midio; lo que cambio es el mundo.
 */
public final class SinIniciales implements StarterHandler {

    private SinIniciales() {
    }

    /**
     * Sustituye al repartidor de Cobblemon. Se llama una vez, al arrancar.
     *
     * <p>&#9888; Va en un {@code try}: si algun dia Cobblemon cambia esa firma,
     * lo que NO puede pasar es que el servidor no arranque por esto. Se avisa
     * fuerte y se sigue -- el aviso es lo que hace que alguien lo mire.
     */
    public static void instalar() {
        try {
            Cobblemon.INSTANCE.setStarterHandler(new SinIniciales());
            LunaEternal.LOG.info("Inicial: el repartidor de Cobblemon queda "
                    + "APAGADO. El inicial se coge ante Oak (D-048)");
        } catch (Throwable t) {
            LunaEternal.LOG.error("NO SE PUDO APAGAR EL REPARTIDOR DE INICIALES DE "
                    + "COBBLEMON. Un jugador puede coger un inicial saltandose a "
                    + "Oak, y sin marcar kit_claim -- o sea DOS iniciales.", t);
        }
    }

    /**
     * LE DICE A COBBLEMON QUE ESTE JUGADOR YA TIENE SU INICIAL.
     *
     * <h2>&#9888;&#9888;&#9888; APAGAR EL REPARTIDOR NO CALLA A COBBLEMON</h2>
     *
     * Con el repartidor apagado seguia saliendo su mensaje:
     *
     * <blockquote>«Aun no has seleccionado a tu inicial, pero ya recibiste un
     * Pokemon. Para seleccionar tu inicial, coloca todos tus Pokemon en el PC y
     * pulsa "C".»</blockquote>
     *
     * Y no lo dice nuestro handler: lo dice {@code RequestStarterScreenHandler}
     * <b>antes</b> de llegar a el, mirando SU propia marca
     * ({@code GeneralPlayerData.starterSelected}). Como el inicial se lo dio
     * Oak, esa marca sigue en {@code false} -- <b>desde el punto de vista de
     * Cobblemon el jugador no ha elegido nunca</b>.
     *
     * <p>&#9888;&#9888; <b>Y el arreglo no es callar el mensaje: es que deje de
     * ser mentira.</b> El jugador SI tiene su inicial. Poner la marca dice la
     * verdad, y de paso apaga el aviso, el bloqueo y cualquier otra cosa que
     * Cobblemon cuelgue de ella el dia de mañana. Taparlo con un mixin habria
     * dejado la marca mintiendo y el siguiente sitio que la mire volveria a
     * fallar.
     *
     * <p>&#9888; <b>Se llama en CADA entrada</b>, no solo al conceder. Es
     * idempotente y cuesta nada, y asi tambien queda arreglado todo el que ya
     * cogio su inicial antes de que esto existiera -- que hoy son todos.
     *
     * <p>&#9888; No se guarda a mano: el propio Cobblemon persiste sus datos por
     * su tarea programada y al desconectar. Buscar su constante de tipo de
     * almacen para forzar un guardado seria adivinar una API interna para no
     * ganar nada; y si algun dia no persistiera, la siguiente entrada lo vuelve
     * a poner.
     */
    public static void marcarComoElegido(ServerPlayerEntity jugador) {
        try {
            var datos = Cobblemon.INSTANCE.getPlayerDataManager()
                    .getGenericData(jugador);
            if (datos.getStarterSelected()) {
                return;
            }
            datos.setStarterPrompted(true);
            datos.setStarterSelected(true);
        } catch (Throwable t) {
            // ⚠ Nunca revienta la entrada de un jugador por esto: lo peor que
            //   pasa si falla es que Cobblemon le siga ofreciendo una pantalla
            //   que no reparte nada.
            LunaEternal.LOG.warn("No se pudo marcar el inicial de {} en Cobblemon: {}",
                    jugador.getGameProfile().getName(), t.toString());
        }
    }

    /**
     * &#9888; Lista vacia y no {@code null}: quien la lea espera una lista, y un
     * nulo aqui seria cambiar «no hay iniciales» por una excepcion en mitad de
     * su codigo.
     */
    @Override
    public List<StarterCategory> getStarterList(ServerPlayerEntity jugador) {
        return List.of();
    }

    /** Al entrar no pasa nada. De avisar ya se encarga el mensaje de Oak. */
    @Override
    public void handleJoin(ServerPlayerEntity jugador) {
    }

    /**
     * &#9888;&#9888; SE CONTESTA CON UN MENSAJE, NO CON SILENCIO. Quien pulsa esa
     * tecla espera algo; si no pasa nada, lo natural es pensar que el servidor
     * va mal y volver a pulsar. Decirle a donde ir convierte un boton roto en
     * una indicacion.
     */
    @Override
    public void requestStarterChoice(ServerPlayerEntity jugador) {
        jugador.sendMessage(Text.literal(
                "§6§lPROF. OAK §8» §fTu primer Pokemon te lo doy §eyo§f, en el "
                + "Laboratorio de la ciudadela."), false);
    }

    /**
     * &#9888;&#9888;&#9888; ESTA ES LA QUE IMPORTA. Aqui es donde Cobblemon
     * entregaba el Pokemon, y por aqui pasa <b>tambien</b> un cliente modificado
     * que mande el paquete sin abrir ninguna pantalla.
     *
     * <p>&#9888; Se deja rastro en el log: con la pantalla apagada, nadie deberia
     * llegar aqui por accidente. Si aparece un nombre, es que alguien lo esta
     * intentando a mano -- y eso es justo lo que hay que poder ver.
     */
    @Override
    public void chooseStarter(ServerPlayerEntity jugador, String categoria, int cual) {
        LunaEternal.LOG.warn("Inicial: {} ha intentado coger un inicial de "
                + "Cobblemon ({} #{}). Rechazado: aqui se coge ante Oak.",
                jugador.getGameProfile().getName(), categoria, cual);
    }
}
