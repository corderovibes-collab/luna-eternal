package net.pokereport.luna.gym;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;

/**
 * QUIÉN ESTÁ EN QUÉ RANURA.
 *
 * <h2>⚠⚠⚠ VIVE EN MEMORIA Y NO EN LA BASE, Y ES DELIBERADO</h2>
 *
 * Una ranura ocupada es un estado <b>de esta sesión</b>: significa «hay alguien
 * combatiendo ahí ahora mismo». Guardarlo en la base tendría un fallo mudo y
 * permanente: <b>al reiniciar el servidor las ocho ranuras figurarían ocupadas
 * para siempre</b> y nadie podría volver a retar a Brock, sin un solo error en
 * el log.
 *
 * <p>En memoria, un reinicio las deja todas libres — que es exactamente la
 * verdad, porque tras un reinicio no hay nadie combatiendo.
 *
 * <h2>⚠⚠ Y HAY QUE SOLTARLA EN LOS TRES CAMINOS</h2>
 *
 * Ganar, perder y <b>desconectarse</b>. El tercero es el que se olvida, y es el
 * que deja la ranura pillada: alguien cierra el juego a mitad de combate y esa
 * copia del gimnasio queda reservada a un jugador que ya no está.
 */
public final class Ranuras {

    /** gimnasio -> (ranura -> jugador). */
    private static final Map<String, Map<Integer, UUID>> OCUPADAS =
            new ConcurrentHashMap<>();

    /** Qué ranuras ya se han clonado del maestro, para no clonar dos veces. */
    private static final Map<String, java.util.Set<Integer>> CONSTRUIDAS =
            new ConcurrentHashMap<>();

    private Ranuras() {}

    /**
     * Reserva una ranura libre.
     *
     * @return el número de ranura, o -1 si están todas ocupadas
     */
    public static int reservar(Gimnasio.Gimnasio_ g, UUID jugador) {
        var mapa = OCUPADAS.computeIfAbsent(g.id(), k -> new ConcurrentHashMap<>());
        // ⚠ Desde la 1: la 0 es el MAESTRO y no se juega en ella. Combatir sobre
        //   el original estropearía la plantilla de la que salen las copias.
        for (int i = 1; i < Gimnasio.RANURAS; i++) {
            if (mapa.putIfAbsent(i, jugador) == null) {
                return i;
            }
        }
        return -1;
    }

    /** ¿Ya está clonada esta ranura? Marca que sí de paso. */
    public static boolean marcarConstruida(Gimnasio.Gimnasio_ g, int ranura) {
        return !CONSTRUIDAS.computeIfAbsent(g.id(),
                k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(ranura);
    }

    /**
     * Suelta la ranura de un jugador, esté donde esté.
     *
     * <p>⚠ Se busca por JUGADOR y no por (gimnasio, ranura): al desconectarse
     * no sabemos en cuál estaba, y pedir que quien llama lo recuerde es pedir
     * que un día se le olvide.
     */
    public static void soltar(UUID jugador) {
        for (var e : OCUPADAS.entrySet()) {
            e.getValue().entrySet().removeIf(r -> jugador.equals(r.getValue()));
        }
    }

    /** En qué ranura está, o -1. */
    public static int ranuraDe(Gimnasio.Gimnasio_ g, UUID jugador) {
        var mapa = OCUPADAS.get(g.id());
        if (mapa != null) {
            for (var e : mapa.entrySet()) {
                if (jugador.equals(e.getValue())) {
                    return e.getKey();
                }
            }
        }
        return -1;
    }

    /** Cuántas quedan libres, para decirlo en la pantalla. */
    public static int libres(Gimnasio.Gimnasio_ g) {
        var mapa = OCUPADAS.get(g.id());
        return Gimnasio.RANURAS - 1 - (mapa == null ? 0 : mapa.size());
    }

    /**
     * Al desconectar: soltar la ranura.
     *
     * <p>⚠⚠ ESTE ES EL QUE SE OLVIDA. Sin él, quien cierra el juego a mitad de
     * combate deja su copia del gimnasio reservada para siempre, y al octavo
     * nadie más puede retar al líder. Y no da ningún error: solo deja de
     * funcionar.
     */
    public static void alSalir(ServerPlayerEntity jugador) {
        soltar(jugador.getUuid());
    }

    /**
     * Al entrar: si estaba dentro de una arena, fuera.
     *
     * <p>⚠⚠ Alguien que se desconecta dentro del gimnasio vuelve a aparecer
     * ahí, en una sala que ya no tiene reservada y con un líder que ya no
     * existe. Sin esto se queda encerrado en una copia muerta.
     */
    public static boolean estabaEnArena(ServerPlayerEntity jugador) {
        return net.pokereport.luna.world.LunaDimensions.GIMNASIOS
                .equals(jugador.getServerWorld().getRegistryKey());
    }

    public static void olvidarTodo() {
        OCUPADAS.clear();
        CONSTRUIDAS.clear();
        LunaEternal.LOG.info("Gimnasios: ranuras liberadas");
    }
}
