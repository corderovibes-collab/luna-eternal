package net.pokereport.luna.puerta;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.ui.Cartel;
import net.pokereport.luna.world.Decorativos;
import net.pokereport.luna.world.LunaDimensions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EL GUARDIAN DEL LOBBY: el unico camino al mundo.
 *
 * <p>Un Pokemon enorme plantado delante de la salida, con su cartel flotante.
 * Clic derecho <b>o</b> izquierdo: los dos abren la puerta.
 *
 * <h2>&#9888;&#9888; LOS DOS CLICS, Y NO ES UN CAPRICHO</h2>
 *
 * Es la primera cosa con la que interactua alguien que acaba de entrar al
 * servidor, y todavia no sabe nada: no sabe que aqui se habla con clic derecho,
 * ni que el izquierdo suele pegar. Aceptar los dos convierte «adivinar el
 * control» en «tocar al bicho». Si esta pantalla se le atraganta, <b>no llega a
 * jugar</b>.
 *
 * <p>&#9888;&#9888;&#9888; ES DE LA ESPECIE MAS GRANDE QUE HAY, no de una elegida
 * por gusto. El NPC tiene que verse <b>desde donde apareces</b> sin tener que
 * buscarlo: un lobby con un muñeco de tamaño normal en una esquina es un lobby
 * donde el jugador nuevo da vueltas. La especie se puede cambiar por comando
 * ({@code /luna puerta npc <especie>}) para probar cual queda mejor con la
 * construccion, y por eso <b>no esta escrita a fuego</b>.
 *
 * <p>&#9888; No se escala con la API de Cobblemon <b>a proposito</b>: no se ha
 * podido verificar contra el jar de 1.8.0 que instala este servidor (no hay
 * copia local de su fuente), y llamar por reflexion a un metodo que no se ha
 * comprobado es la clase de cosa que funciona hoy y un dia deja de hacerlo
 * <b>sin dar ningun error</b> -- que es justo el fallo que este proyecto lleva
 * documentado media docena de veces. Con una especie grande no hace falta.
 */
public final class PuertaNpc {

    private PuertaNpc() {
    }

    /** La marca del guardian. De ella cuelga el clic. */
    public static final String MARCA = "luna_puerta";

    private static final String MARCA_CARTEL = "luna_puerta_cartel";

    /** Radio que se barre al limpiar antes de colocar. */
    private static final double RADIO = 8.0;

    /** Lo que flota el cartel por encima de los pies. */
    private static final double ALTURA_CARTEL = 4.2;

    /** La especie por defecto. Se cambia por comando. */
    public static final String ESPECIE = "lugia";

    /**
     * Cuanto se ignora un segundo clic del mismo jugador, en milisegundos.
     *
     * <h2>&#9888;&#9888; SIN ESTO, UN CLIC IZQUIERDO MANTENIDO ENCOLA VEINTE
     * VIAJES</h2>
     *
     * Golpear se repite solo mientras tengas el boton pulsado. Cada golpe
     * llamaria a {@link Puerta#cruzar}, que escribe en la base y teletransporta:
     * veinte escrituras y veinte viajes por un clic que el jugador cree que ha
     * dado una vez. Y el sintoma no seria un error, seria «el servidor se ha
     * quedado pillado al entrar».
     */
    private static final long ESPERA_MS = 2_000L;

    private static final Map<UUID, Long> ULTIMO = new ConcurrentHashMap<>();

    // ------------------------------------------------------------ colocar

    /** Lo pone donde este el jugador. Solo en el lobby. */
    public static boolean colocar(ServerPlayerEntity jugador) {
        var mundo = jugador.getServerWorld();
        if (!LunaDimensions.LOBBY.equals(mundo.getRegistryKey())) {
            return false;
        }
        return colocar(mundo, jugador.getPos(), jugador.getYaw(), ESPECIE);
    }

    /**
     * Lo pone donde se le diga.
     *
     * <p>&#9888; Borra antes de poner. Es una entidad y se queda en el mundo:
     * sin limpiar quedaria un guardian <b>mas</b> en cada colocacion, y como
     * lleva la marca de los decorativos <b>{@code /kill} no se los lleva</b> --
     * la consola diria «Killed 3 entities» y no se iria ninguno. Es la leccion
     * de los tres Brocks apilados.
     */
    public static boolean colocar(ServerWorld mundo, Vec3d donde, float giro,
                                  String especie) {
        quitar(mundo, donde, RADIO);

        var e = Decorativos.colocar(mundo, especie, Decorativos.Postura.FLOTANDO,
                donde, giro);
        if (e == null) {
            LunaEternal.LOG.error("Puerta: la especie '{}' no existe, no se coloca "
                    + "el guardian. Sin guardian NADIE PUEDE SALIR DEL LOBBY.",
                    especie);
            return false;
        }
        e.addCommandTag(MARCA);

        Cartel.poner(mundo, donde, ALTURA_CARTEL, MARCA_CARTEL,
                Cartel.texto("ENTRAR AL MUNDO", "Registrate y tocame para empezar"));

        LunaEternal.LOG.info("Puerta: guardian ({}) colocado en {}",
                especie, e.getBlockPos());
        return true;
    }

    /**
     * Borra los guardianes y sus carteles que haya cerca.
     *
     * @return cuantas entidades se quitaron
     */
    public static int quitar(ServerWorld mundo, Vec3d centro, double radio) {
        Box caja = Box.of(centro, radio * 2, radio * 2, radio * 2);
        int n = 0;
        for (Entity e : mundo.getEntitiesByClass(Entity.class, caja,
                x -> x.getCommandTags().contains(MARCA))) {
            e.discard();
            n++;
        }
        n += Cartel.quitar(mundo, centro, radio, MARCA_CARTEL);
        return n;
    }

    // ------------------------------------------------------------ el clic

    /**
     * Engancha los dos clics.
     *
     * <p>&#9888;&#9888; Se corta en <b>los dos lados</b> (cliente y servidor),
     * como las paradas: el evento se dispara en ambos, y cortando solo en el
     * servidor el cliente enseñaria un instante el menu de Cobblemon antes de
     * que llegue lo nuestro.
     *
     * <p>&#9888; Y el izquierdo se corta <b>antes</b> de que el golpe llegue a
     * ninguna parte. La proteccion de los decorativos ya impide el daño, pero
     * eso ocurre mas abajo: sin cortar aqui, el jugador veria la animacion de
     * golpe y oiria el sonido cada vez que abre la puerta.
     */
    public static void engancharClic() {
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
                (jugador, mundo, mano, entidad, golpe) -> {
                    if (!esGuardian(entidad)) {
                        return ActionResult.PASS;
                    }
                    // ⚠ Una sola mano: sin esto el evento llega dos veces
                    //   (principal y secundaria) y se cruzaria la puerta dos.
                    if (mano != Hand.MAIN_HAND) {
                        return ActionResult.SUCCESS;
                    }
                    tocar(jugador);
                    return ActionResult.SUCCESS;
                });

        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(
                (jugador, mundo, mano, entidad, golpe) -> {
                    if (!esGuardian(entidad)) {
                        return ActionResult.PASS;
                    }
                    tocar(jugador);
                    return ActionResult.SUCCESS;
                });

        LunaEternal.LOG.info("Puerta: el guardian responde a los dos clics");
    }

    private static boolean esGuardian(Entity entidad) {
        return entidad != null && entidad.getCommandTags().contains(MARCA);
    }

    private static void tocar(net.minecraft.entity.player.PlayerEntity jugador) {
        if (!(jugador instanceof ServerPlayerEntity sp)) {
            return;
        }
        long ahora = System.currentTimeMillis();
        Long ultimo = ULTIMO.get(sp.getUuid());
        if (ultimo != null && ahora - ultimo < ESPERA_MS) {
            return;
        }
        ULTIMO.put(sp.getUuid(), ahora);
        Puerta.cruzar(sp);
    }

    public static void olvidar(UUID uuid) {
        ULTIMO.remove(uuid);
    }
}
