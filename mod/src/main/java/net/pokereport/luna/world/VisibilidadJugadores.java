package net.pokereport.luna.world;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A CUANTOS JUGADORES VES A LA VEZ.
 *
 * <p>En el lobby, a ninguno. En la ciudadela, a los {@link #TOPE_CIUDADELA} mas
 * cercanos. En el resto del mundo, a todos.
 *
 * <h2>&#9888;&#9888;&#9888; ESTO NO BAJA EL LAG DEL SERVIDOR, Y CONVIENE NO
 * CREERSELO</h2>
 *
 * Lo que quita es <b>ancho de banda y FPS del cliente</b>, que es real y se
 * nota: dibujar 200 jugadores --cada uno con su modelo, su nombre, su armadura y
 * sus cosmeticos-- es trabajo de la tarjeta grafica de quien mira. Pero el
 * servidor <b>sigue tickeando a los 200</b> y cargando sus chunks: ninguna
 * entidad deja de existir por no verse.
 *
 * <p>Es la misma leccion que ya esta escrita para las dimensiones --«repartir
 * gente entre mundos no baja el lag, todas se tickean en el mismo hilo»-- y por
 * eso se apunta aqui: para que nadie mire este fichero dentro de seis meses
 * buscando por que el TPS sigue bajo con 200 dentro.
 *
 * <h2>&#9888;&#9888; UN SOLO BOTON PARA LAS DOS COSAS</h2>
 *
 * «En el lobby no se ve nadie» y «en la ciudadela ves a treinta» <b>son el mismo
 * mecanismo con dos numeros</b>, no dos funciones. Con dos sistemas distintos
 * --uno que esconde y otro que recorta-- llega el dia en que dicen cosas
 * distintas sobre el mismo jugador, y el sintoma es alguien invisible para unos
 * y visible para otros.
 */
public final class VisibilidadJugadores {

    private VisibilidadJugadores() {
    }

    /** En la ciudadela se ve a los treinta mas cercanos (decision del usuario). */
    public static final int TOPE_CIUDADELA = 30;

    /** En el lobby, a nadie. */
    public static final int TOPE_LOBBY = 0;

    /** Lo que devuelve {@link #topeDe} cuando no hay recorte. */
    public static final int SIN_TOPE = -1;

    /**
     * Cada cuantos ticks se rehace la lista.
     *
     * <h2>&#9888;&#9888; UNA VEZ POR SEGUNDO, NO EN CADA COMPROBACION</h2>
     *
     * El mixin pregunta <b>por cada pareja (jugador, quien mira)</b> y muchas
     * veces por tick. Calcular ahi «¿esta entre mis treinta mas cercanos?»
     * seria recorrer la lista entera en cada pregunta: con 200 dentro, cientos
     * de miles de distancias por tick. Aqui se calcula una vez por segundo y el
     * mixin solo mira si un conjunto contiene un identificador.
     *
     * <p>&#9888; Y no hace falta mas fino: la lista <b>solo cambia cuando la
     * gente se mueve</b>, y cuando la gente se mueve vanilla vuelve a preguntar
     * por su cuenta. Si nadie se mueve, la respuesta de hace un segundo sigue
     * siendo la correcta.
     */
    public static final int CADA = 20;

    /** Para cada jugador, a quienes puede ver. Solo en dimensiones con tope. */
    private static final Map<UUID, Set<UUID>> VISIBLES = new ConcurrentHashMap<>();

    /**
     * El tope de una dimension, o {@link #SIN_TOPE}.
     *
     * <p>&#9888; El Salvaje y el Hogar <b>no llevan tope a proposito</b>: ahi la
     * gente esta repartida por kilometros y el problema no existe. Poner un tope
     * donde no hace falta solo añade una forma de que alguien se vuelva
     * invisible sin motivo.
     */
    public static int topeDe(RegistryKey<World> mundo) {
        if (LunaDimensions.LOBBY.equals(mundo)) {
            return TOPE_LOBBY;
        }
        if (LunaDimensions.CIUDADELA.equals(mundo)) {
            return TOPE_CIUDADELA;
        }
        return SIN_TOPE;
    }

    /**
     * ¿Puede {@code visor} ver a {@code objetivo}?
     *
     * <p>&#9888;&#9888; ANTE LA DUDA, SE VE. Si la lista todavia no se ha
     * calculado --el jugador acaba de entrar, o de cambiar de mundo-- se deja
     * ver. De las dos equivocaciones, «un segundo de mas viendo a alguien» no la
     * nota nadie y «un jugador invisible para siempre porque su lista nunca se
     * calculo» es un fallo que parece un fantasma.
     */
    public static boolean visible(ServerPlayerEntity visor, ServerPlayerEntity objetivo) {
        if (visor == objetivo) {
            return true;
        }
        int tope = topeDe(visor.getWorld().getRegistryKey());
        if (tope == SIN_TOPE) {
            return true;
        }
        if (tope == 0) {
            return false;
        }
        Set<UUID> suyos = VISIBLES.get(visor.getUuid());
        return suyos == null || suyos.contains(objetivo.getUuid());
    }

    /**
     * Rehace las listas. Se llama una vez por segundo desde el tick.
     *
     * <p>&#9888; Solo recorre las dimensiones <b>con tope</b>: en el Salvaje no
     * hay nada que calcular, y recorrerlo seria pagar por una respuesta que ya
     * se sabe.
     */
    public static void tick(MinecraftServer servidor) {
        // Reparte a los conectados por dimension, una sola pasada.
        Map<RegistryKey<World>, List<ServerPlayerEntity>> porMundo = new java.util.HashMap<>();
        for (ServerPlayerEntity p : servidor.getPlayerManager().getPlayerList()) {
            RegistryKey<World> k = p.getWorld().getRegistryKey();
            if (topeDe(k) == SIN_TOPE) {
                // ⚠ Se le borra la lista al salir a un mundo sin tope. Sin esto,
                //   quien se fue al Salvaje conservaria la lista de la ciudadela
                //   y volveria a entrar viendo justo a los de antes.
                VISIBLES.remove(p.getUuid());
                continue;
            }
            porMundo.computeIfAbsent(k, x -> new ArrayList<>()).add(p);
        }

        for (var entrada : porMundo.entrySet()) {
            int tope = topeDe(entrada.getKey());
            List<ServerPlayerEntity> gente = entrada.getValue();
            if (tope == 0) {
                // Nadie ve a nadie: no hace falta ninguna lista.
                for (ServerPlayerEntity p : gente) {
                    VISIBLES.remove(p.getUuid());
                }
                continue;
            }
            // ⚠ Si caben todos, no se guarda lista: `visible` devuelve true ante
            //   la ausencia, asi que con veinte personas dentro esto no cuesta
            //   nada y no hay nada que mantener.
            if (gente.size() <= tope + 1) {
                for (ServerPlayerEntity p : gente) {
                    VISIBLES.remove(p.getUuid());
                }
                continue;
            }
            for (ServerPlayerEntity yo : gente) {
                VISIBLES.put(yo.getUuid(), masCercanos(yo, gente, tope));
            }
        }
    }

    /**
     * Los {@code tope} mas cercanos a {@code yo}.
     *
     * <p>&#9888; Se ordena por distancia <b>al cuadrado</b>: la raiz cuadrada no
     * cambia el orden y es lo unico caro de este calculo.
     */
    private static Set<UUID> masCercanos(ServerPlayerEntity yo,
                                         List<ServerPlayerEntity> gente, int tope) {
        List<ServerPlayerEntity> otros = new ArrayList<>(gente.size());
        for (ServerPlayerEntity p : gente) {
            if (p != yo) {
                otros.add(p);
            }
        }
        otros.sort(java.util.Comparator.comparingDouble(p -> p.squaredDistanceTo(yo)));
        Set<UUID> salida = new HashSet<>(tope * 2);
        for (int i = 0; i < Math.min(tope, otros.size()); i++) {
            salida.add(otros.get(i).getUuid());
        }
        return salida;
    }

    public static void olvidar(UUID uuid) {
        VISIBLES.remove(uuid);
    }

    /** Cuantas listas hay vivas. Lo usa el autotest. */
    public static int listas() {
        return VISIBLES.size();
    }

    // ------------------------------------------------------- ¿esta vivo?

    private static volatile boolean vivo;

    /**
     * &#9888;&#9888;&#9888; LA UNICA PRUEBA DE QUE EL MIXIN SE APLICO.
     *
     * <p>Un mixin se <b>compila</b> siempre --es una clase Java normal-- y se
     * <b>aplica</b> en el arranque, sobre una clase que pudo cambiar de nombre o
     * que otro mod toco antes. Si no aplicara, lo que quedaria es un recorte de
     * jugadores <b>que no existe</b>: la plaza se veria igual que siempre y no
     * habria ningun error que mirar.
     *
     * <p>&#9888;&#9888; <b>No se puede comprobar en el autotest, y por eso vive
     * aqui y se mira por comando.</b> Lo natural seria preguntar por la clase
     * objetivo con {@code Class.forName}, pero en el servidor de verdad las
     * clases de Minecraft <b>no se llaman como en el codigo</b>: en desarrollo
     * llevan nombres de Yarn y en produccion los <i>intermediary</i>, asi que esa
     * comprobacion pasaria aqui y fallaria alli -- justo al reves de para lo que
     * sirve. Y en el arranque, con el servidor vacio, todavia no ha corrido.
     *
     * <p>Lo honesto es esto: el mixin lo enciende la primera vez que corre, y
     * {@code /luna puerta} lo enseña. Se mira <b>con alguien dentro</b>, que es
     * cuando la respuesta significa algo.
     */
    public static void marcarVivo() {
        vivo = true;
    }

    public static boolean vivo() {
        return vivo;
    }
}
