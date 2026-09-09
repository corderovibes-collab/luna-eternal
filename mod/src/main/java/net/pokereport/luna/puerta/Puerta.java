package net.pokereport.luna.puerta;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.world.LunaDimensions;
import net.pokereport.luna.world.TravelService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EL LOBBY ES LA UNICA ENTRADA AL MUNDO.
 *
 * <p>Quien no ha cruzado nunca aparece en el lobby. Alli no hay PokePad, ni
 * teclas, ni nada que hacer salvo registrarse y hablar con el NPC. El NPC
 * comprueba que su cliente esta al dia y lo suelta en la ciudadela.
 *
 * <h2>&#9888;&#9888;&#9888; EL AGUJERO QUE ESTO TAPA</h2>
 *
 * Hasta hoy un jugador nuevo aparecia en el Mundo Hogar --{@code HOGAR} es el
 * {@code OVERWORLD}, o sea el spawn de vainilla-- y recibia por el chat un
 * mensaje diciendole que fuera a ver a Oak <b>al laboratorio de la
 * ciudadela</b>. Y no habia forma de llegar: {@code Explorar} solo ofrece hogar
 * y salvaje, {@code Viajes} solo funciona <b>dentro</b> de la ciudadela, y
 * {@code /luna ir} es de operador. Sin inicial no arranca ninguna cadena de
 * misiones, asi que el recorrido entero estaba cerrado y <b>no daba ningun
 * error</b>: el servidor se comportaba como debe.
 *
 * <h2>&#9888;&#9888; LO QUE ESTA CAPA **NO** HACE, Y CONVIENE NO CONFUNDIRLO</h2>
 *
 * <ul>
 *   <li><b>No autentica.</b> Eso es de EasyAuth, que ya congela al no
 *       autenticado en su spawn y lo devuelve a su sitio al hacer
 *       {@code /login}. Montar aqui un segundo control seria dos sistemas
 *       peleandose por donde esta el jugador -- el fallo de las tres listas de
 *       medallas, con la sesion de por medio.</li>
 *   <li><b>No comprueba «todos los mods».</b> Eso ya lo hace Fabric al
 *       sincronizar los registros, y <b>echa al cliente descuadrado en la
 *       puerta</b>, antes de que exista como jugador (el
 *       {@code Registry remapping failed} que ya conocemos). Quien falla ahi no
 *       llega nunca al lobby. Lo que si comprueba esta clase es lo que Fabric
 *       <b>no</b> mira: que tenga NUESTRO jar y al dia.</li>
 * </ul>
 */
public final class Puerta {

    private Puerta() {
    }

    /**
     * Cuantos ticks se espera antes de mover a nadie al entrar.
     *
     * <h2>&#9888;&#9888;&#9888; ESTO NO ES UN ADORNO: ROMPIO LA SESION DE UN
     * JUGADOR</h2>
     *
     * Teletransportar <b>dentro</b> del evento de conexion deja el apunte del
     * gestor de tickets de chunk a medias, y el apunte roto <b>dura toda la
     * sesion</b>: revienta minutos despues, en el siguiente cambio de dimension,
     * con un {@code NullPointerException} en {@code ChunkTicketManager} que no
     * nombra la causa por ningun lado. Ya paso con {@code Combate.alEntrar} y
     * quedo escrito. Dos segundos no los nota nadie.
     */
    public static final int RETRASO = 40;

    /**
     * La version del protocolo que exige el servidor.
     *
     * <h2>&#9888;&#9888;&#9888; POR QUE NO SE COMPARA LA VERSION DEL MOD</h2>
     *
     * Porque <b>no cambia nunca</b>: {@code mod_version} lleva en {@code 0.1.0}
     * desde el primer dia y lo que distingue un jar de otro es la <b>huella del
     * nombre del fichero</b> ({@code lunaeternal-0.1.0-3598884202.jar}), que el
     * mod no puede leerse a si mismo. Comparar «0.1.0 contra 0.1.0» habria dado
     * SIEMPRE que todo el mundo esta al dia -- una comprobacion que no comprueba
     * nada, que es peor que no tenerla (la leccion del autotest de gimnasios que
     * comparaba dos ejes distintos).
     *
     * <p>&#9888;&#9888; <b>SE SUBE A MANO, Y SOLO CUANDO EL CLIENTE **TIENE** QUE
     * ACTUALIZARSE</b> — es decir, cuando se añade una pantalla o un paquete que
     * el jar viejo no sabe dibujar. Subirlo por costumbre manda al lobby a gente
     * que estaba bien; no subirlo cuando toca deja entrar a quien vera pantallas
     * que «no abren», que es el sintoma que mas veces ha despistado en este
     * proyecto.
     */
    public static final int PROTOCOLO = 1;

    /**
     * &#9888;&#9888;&#9888; LA PUERTA NACE APAGADA, Y ESO NO ES PRUDENCIA: SIN
     * ESTO, DESPLEGARLA ENCIERRA A TODO EL QUE ENTRE.
     *
     * <p>El dia que este codigo llegue al servidor, el lobby es una dimension
     * <b>vacia</b>: no hay construccion y no hay guardian, porque las dos cosas
     * se ponen a mano y dentro del juego. Con la puerta encendida desde el
     * primer arranque, cada jugador nuevo apareceria en un vacio sin nada que
     * tocar -- y {@code Traslado} le impediria salir, que es precisamente lo que
     * la hace funcionar. Quedaria <b>atrapado</b>, y de la dimension del lobby
     * no se sale andando.
     *
     * <p>&#9888;&#9888; Es la misma familia que «un provisional que funciona se
     * queda»: aqui el peligro es el contrario --una funcion a medias que se
     * enciende sola-- y se resuelve igual, haciendo que <b>encenderla sea un
     * acto deliberado</b>: {@code /luna puerta activar}, cuando el lobby este
     * construido y el guardian colocado.
     *
     * <p>&#9888; Vive en un fichero y no en la base porque se lee <b>al
     * arrancar</b>, en el hilo del servidor, donde consultar la base esta
     * prohibido. Mismo motivo que {@code luna-recepciones.properties}.
     */
    private static volatile boolean activa;

    private static final java.nio.file.Path FICHERO =
            java.nio.file.Path.of("config", "lunaeternal", "puerta.properties");

    /** Lee el interruptor del disco. Se llama al arrancar. */
    public static void cargarInterruptor() {
        boolean valor = false;
        try {
            if (java.nio.file.Files.exists(FICHERO)) {
                var props = new java.util.Properties();
                try (var in = java.nio.file.Files.newInputStream(FICHERO)) {
                    props.load(in);
                }
                valor = Boolean.parseBoolean(props.getProperty("activa", "false"));
            }
        } catch (Exception e) {
            // ⚠ Ante un fichero ilegible, APAGADA. De las dos equivocaciones, la
            //   que deja a la gente encerrada es la que no se puede permitir.
            LunaEternal.LOG.warn("No se pudo leer {}: la puerta queda apagada",
                    FICHERO, e);
        }
        activa = valor;
        LunaEternal.LOG.info("Puerta: {}", valor
                ? "ACTIVA (los jugadores nuevos empiezan en el lobby)"
                : "apagada (nadie pasa por el lobby todavia)");
    }

    public static boolean activa() {
        return activa;
    }

    /** Enciende o apaga, y lo deja escrito para el siguiente arranque. */
    public static void activar(boolean valor) throws java.io.IOException {
        java.nio.file.Files.createDirectories(FICHERO.getParent());
        var props = new java.util.Properties();
        props.setProperty("activa", Boolean.toString(valor));
        try (var out = java.nio.file.Files.newOutputStream(FICHERO)) {
            props.store(out, "La puerta del lobby. Encender solo con el "
                    + "guardian ya colocado: sin el, nadie puede salir.");
        }
        activa = valor;
    }

    /** Que protocolo dijo tener cada cliente. Sin entrada = no ha saludado. */
    private static final Map<UUID, Integer> SALUDOS = new ConcurrentHashMap<>();

    /** Por que no se puede pasar. {@code null} en {@link Veredicto#pasa}. */
    public enum Fallo {
        /** No tiene nuestro mod, o es tan viejo que ni saluda. */
        SIN_MOD,
        /** Tiene el mod, pero de una version que ya no sirve. */
        DESFASADO
    }

    public record Veredicto(boolean pasa, Fallo fallo) {
        static final Veredicto OK = new Veredicto(true, null);

        static Veredicto no(Fallo f) {
            return new Veredicto(false, f);
        }
    }

    // ------------------------------------------------------------ saludo

    /**
     * Lo que dice el cliente. Llega por {@code Red.Saludo}.
     *
     * @return {@code true} si es la primera vez de este jugador, para que el
     *         log no repita la misma linea cada vez que se le reenvia el estado
     */
    public static boolean saludar(UUID uuid, int protocolo) {
        return SALUDOS.put(uuid, protocolo) == null;
    }

    public static void olvidar(UUID uuid) {
        SALUDOS.remove(uuid);
    }

    /**
     * &#9888;&#9888; LA AUSENCIA DE SALUDO **ES** LA SEÑAL, y por eso no hace
     * falta preguntarle la version a un cliente viejo: un jar que no conoce
     * {@code Saludo} no puede mandarlo. «No ha contestado» y «ha contestado un
     * numero viejo» son los dos casos, y los dos se ven aqui.
     *
     * <p>&#9888; Se comprueba TAMBIEN {@code canSend}, que es lo que dice si el
     * cliente registro nuestros canales: un cliente sin el mod nunca saluda,
     * pero distinguirlo del que lo tiene desfasado cambia el mensaje que se le
     * enseña -- y el mensaje es lo unico que le dira que hacer.
     */
    public static Veredicto veredicto(ServerPlayerEntity jugador) {
        boolean tieneMod = net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
                .canSend(jugador, net.pokereport.luna.net.Red.EstadoPuerta.ID);
        Integer suyo = SALUDOS.get(jugador.getUuid());
        if (!tieneMod && suyo == null) {
            return Veredicto.no(Fallo.SIN_MOD);
        }
        if (suyo == null || suyo != PROTOCOLO) {
            return Veredicto.no(Fallo.DESFASADO);
        }
        return Veredicto.OK;
    }

    // ------------------------------------------------------------ el estado

    /** {@code true} si esta ahora mismo en la dimension del lobby. */
    public static boolean enElLobby(ServerPlayerEntity jugador) {
        return LunaDimensions.LOBBY.equals(jugador.getWorld().getRegistryKey());
    }

    /**
     * SI ESTA EN EL LOBBY, NO PUEDE HACER NADA.
     *
     * <h2>&#9888;&#9888;&#9888; SE COMPRUEBA EN EL SERVIDOR AUNQUE EL CLIENTE YA
     * LO APAGUE</h2>
     *
     * El cliente esconde el PokePad porque el servidor le manda
     * {@code EstadoPuerta}, y eso basta para la gente normal. Pero <b>un cliente
     * modificado no dibuja nada y manda el paquete que quiere</b> (P6): sin esta
     * comprobacion, alguien podria comprar en la tienda, mover el GTS o cobrar
     * el pase desde el lobby, sin haber pasado por la puerta y sin haberse
     * registrado siquiera. Las dos mitades hacen falta: el cliente para que se
     * vea bien, el servidor para que sea verdad.
     */
    public static boolean bloqueado(ServerPlayerEntity jugador) {
        return enElLobby(jugador);
    }

    // ------------------------------------------------------------ entrar

    /**
     * Al entrar: si nunca ha cruzado, al lobby.
     *
     * <p>&#9888; Corre <b>encolado</b> ({@link #RETRASO}), nunca dentro del
     * evento de conexion. Ver la constante.
     *
     * <p>&#9888;&#9888; Y solo mueve a quien <b>sabemos</b> que no ha cruzado. Si
     * la base todavia no ha contestado --{@code null} en la cache-- no se toca a
     * nadie: mover a un veterano a un lobby por una consulta que iba lenta es
     * mucho peor que enseñarle la ciudadela a un novato un segundo antes de
     * tiempo.
     */
    public static void alEntrar(ServerPlayerEntity jugador) {
        var svc = LunaEternal.puerta();
        if (svc == null || !activa) {
            return;
        }
        Boolean cruzada = svc.cruzadaEnCache(jugador.getUuid());
        if (cruzada == null || cruzada) {
            return;
        }
        if (enElLobby(jugador)) {
            bienvenida(jugador);
            return;
        }
        TravelService.travel(jugador, LunaDimensions.LOBBY, "el Lobby");
        bienvenida(jugador);
    }

    /**
     * Lo que se le dice a quien acaba de aparecer en el lobby.
     *
     * <p>&#9888; Se nombra {@code /register} explicitamente. EasyAuth avisa por
     * su cuenta, pero su mensaje se pierde entre el de conexion y lo que
     * escriban los demas -- es la misma razon por la que el aviso de Oak lleva
     * sesenta ticks de retraso.
     */
    private static void bienvenida(ServerPlayerEntity jugador) {
        jugador.sendMessage(Text.literal(
                "§6§lPOKEREPORT §8» §fBienvenido. Estas en el §eLobby§f."), false);
        jugador.sendMessage(Text.literal(
                "§71. Registrate con §e/register <clave> <clave>§7."), false);
        jugador.sendMessage(Text.literal(
                "§72. Habla con el §bguardian§7 para entrar al mundo. §8(")
                .append(net.pokereport.luna.ui.Iconos.clicDerecho())
                .append(Text.literal("§8 clic derecho o izquierdo)")), false);
    }

    /**
     * ¿SE LE PUEDE MOVER A ESE MUNDO?
     *
     * <h2>&#9888;&#9888;&#9888; ESTO ES LO QUE HACE QUE LA PUERTA SEA UNA PUERTA</h2>
     *
     * Sin esta comprobacion el lobby es <b>decoracion</b>: un cliente modificado
     * manda {@code AccionExplorar("hogar")} desde el lobby y se planta en el
     * mundo sin haber pasado por el guardian, sin haberse registrado y sin que
     * nadie haya mirado si su cliente esta al dia. Y no daria ningun error --
     * el viaje funciona perfectamente, que es el problema.
     *
     * <p>&#9888;&#9888; <b>Va en {@code Traslado.ir} y no en los receptores.</b>
     * Hay mas de treinta paquetes que acaban moviendo a alguien --explorar,
     * viajes, gimnasios, torre, nichos-- y ponerlo en cada uno seria treinta
     * sitios que un dia dejan de estar de acuerdo; el trigesimo primero, el que
     * alguien añada el mes que viene, nacera sin el. {@code Traslado} es el
     * unico camino por el que se mueve a un jugador en este proyecto --quedo
     * escrito el 30-ago, cuando se unificaron los ocho teletransportes-- asi que
     * es el unico sitio donde esta regla no se puede eludir por olvido.
     *
     * <p>&#9888; <b>No hace falta ninguna bandera de «me mueve la puerta».</b>
     * {@link #cruzar} escribe la fila y actualiza la cache <b>antes</b> de
     * viajar, asi que cuando el viaje llega aqui el jugador ya consta como
     * cruzado. Una bandera seria un segundo estado que mantener sincronizado
     * para no ganar nada.
     *
     * <p>&#9888; Y ante «no lo se» (la base aun no ha contestado) <b>se deja
     * pasar</b>, por lo mismo que {@code PuertaService.cargar}: bloquear a un
     * veterano por una consulta lenta le deja encerrado sin explicacion.
     */
    public static boolean puedeIrA(ServerPlayerEntity jugador,
                                   net.minecraft.registry.RegistryKey<
                                           net.minecraft.world.World> destino) {
        var svc = LunaEternal.puerta();
        if (svc == null || !activa) {
            return true;
        }
        Boolean cruzada = svc.cruzadaEnCache(jugador.getUuid());
        if (cruzada == null || cruzada) {
            return true;
        }
        // Entrar al lobby si; salir de el, no.
        return LunaDimensions.LOBBY.equals(destino);
    }

    /**
     * QUIEN NO HA CRUZADO ESTA EN EL LOBBY. SIEMPRE, NO SOLO AL ENTRAR.
     *
     * <h2>&#9888;&#9888;&#9888; SIN ESTO, EASYAUTH SE SALTA LA PUERTA SIN
     * QUERER</h2>
     *
     * EasyAuth con {@code hide-player-coords} activado <b>apunta donde estabas,
     * te retiene en su spawn, y te devuelve a tu sitio al hacer
     * {@code /login}</b>. Para un veterano es justo lo que se quiere. Para un
     * jugador nuevo es un desastre silencioso: entra al mundo (spawn de
     * vainilla, o sea el Hogar), la puerta lo manda al lobby, se registra... y
     * <b>EasyAuth lo devuelve al Hogar</b>, que es «donde estaba». Sin ningun
     * error, y sin haber pasado por el guardian.
     *
     * <p>No se arregla pidiendole a EasyAuth que avise --no expone nada para
     * eso-- y tampoco habria que intentarlo: <b>encadenar nuestra puerta a los
     * eventos de otro mod la rompe el dia que ese mod cambie</b>. Se arregla
     * dejando de tratar la puerta como un momento y tratandola como <b>una
     * verdad que se mantiene</b>: <i>si no has cruzado, estas en el lobby</i>.
     *
     * <p>&#9888;&#9888; Y de propina cubre todo lo demas que puede sacar a
     * alguien de ahi sin pasar por {@code Traslado}: un operador con
     * {@code /tp}, otro mod, una cama, un portal. La lista de formas de mover a
     * un jugador <b>no se puede enumerar</b>; el estado correcto, si.
     *
     * <p>&#9888; Cuesta un recorrido de la lista de conectados por segundo y una
     * lectura de un mapa en memoria por cabeza. Con 200 dentro eso es ruido.
     */
    public static void vigilar(net.minecraft.server.MinecraftServer servidor) {
        if (!activa) {
            return;
        }
        var svc = LunaEternal.puerta();
        if (svc == null) {
            return;
        }
        for (ServerPlayerEntity j : servidor.getPlayerManager().getPlayerList()) {
            Boolean cruzada = svc.cruzadaEnCache(j.getUuid());
            // ⚠ `null` es «aun no lo se» y se deja en paz: mover a un veterano
            //   porque su consulta iba lenta es el error caro.
            if (cruzada == null || cruzada) {
                continue;
            }
            if (enElLobby(j)) {
                continue;
            }
            TravelService.travel(j, LunaDimensions.LOBBY, "el Lobby");
        }
    }

    // ------------------------------------------------------------ cruzar

    /**
     * El NPC: comprueba y suelta al jugador en la ciudadela.
     *
     * <p>&#9888;&#9888; <b>Se apunta que ha cruzado ANTES de moverlo, y en la
     * base primero.</b> Al reves, una caida entre el viaje y el apunte lo
     * dejaria en la ciudadela y sin fila: el siguiente arranque lo devolveria al
     * lobby <b>desde su casa</b>. Es la misma decision que
     * {@code StarterService.conceder} --marcar antes de entregar-- y por el
     * mismo motivo: de las dos formas de equivocarse, esta es la que se arregla
     * sola.
     *
     * @return {@code true} si cruzo
     */
    public static boolean cruzar(ServerPlayerEntity jugador) {
        Veredicto v = veredicto(jugador);
        if (!v.pasa()) {
            negar(jugador, v.fallo());
            return false;
        }
        var svc = LunaEternal.puerta();
        var perfil = jugador.getGameProfile();
        var server = jugador.getServer();
        if (svc == null || server == null) {
            return false;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(perfil.getId(), perfil.getName());
                svc.cruzar(perfil.getId(), id);
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo apuntar la puerta de {}",
                        perfil.getName(), e);
                return;
            }
            server.execute(() -> {
                if (jugador.isRemoved()) {
                    return;
                }
                TravelService.travel(jugador, LunaDimensions.CIUDADELA, "la Ciudadela");
                net.pokereport.luna.net.Red.enviarPuerta(jugador);
            });
        });
        return true;
    }

    /**
     * &#9888; El mensaje dice QUE HACER, no que ha fallado. «Protocolo 1 != 2»
     * es exacto y no sirve de nada; «reabre el launcher» es lo unico que el
     * jugador puede hacer con esa informacion.
     */
    private static void negar(ServerPlayerEntity jugador, Fallo fallo) {
        jugador.sendMessage(Text.literal(
                "§c§lNO PUEDES PASAR TODAVIA"), false);
        if (fallo == Fallo.SIN_MOD) {
            jugador.sendMessage(Text.literal(
                    "§7Te falta el mod de §6PokeReport§7. Entra con el "
                    + "§elauncher oficial§7, no con Minecraft a secas."), false);
        } else {
            jugador.sendMessage(Text.literal(
                    "§7Tu version esta §edesfasada§7. Cierra el juego, "
                    + "§eabre el launcher§7 y deja que actualice."), false);
        }
        jugador.playSoundToPlayer(net.minecraft.sound.SoundEvents.BLOCK_ANVIL_LAND,
                net.minecraft.sound.SoundCategory.MASTER, 0.6f, 1.4f);
    }
}
