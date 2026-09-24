package net.pokereport.luna.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.puerta.Puerta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QUIEN SE QUEDA QUIETO DIEZ MINUTOS SE VA AL LOBBY.
 *
 * <p>Peticion del usuario (2026-09-10): <i>«si quedan quietos por 10 minutos te
 * saca y te manda de nuevo para el lobby que ahi no puedes hacer nada»</i>.
 *
 * <h2>&#9888;&#9888;&#9888; NO ES UN EXPULSOR: ES UN APARCADERO, Y ESO ES LO QUE
 * LO HACE BARATO</h2>
 *
 * Echar del servidor a quien esta AFK obliga a volver a conectarse, a esperar la
 * carga y --pasados 15 minutos-- a volver a hacer {@code /login}. Mandarlo al
 * lobby consigue lo mismo por dentro y cuesta un clic: <b>el lobby ya apaga el
 * PokePad, ya recorta la visibilidad a cero y ya tiene un guardian que te
 * devuelve a la ciudadela</b>. No hubo que construir nada de eso; esto solo lo
 * usa.
 *
     * <h2>SOLO EL LOBBY ESTA EXENTO</h2>
 *
     * <p>La politica de produccion es global: combate, evento, creativo, OP y
     * espectador no pueden quedarse farmeando horas. La unica excepcion es
     * <b>el propio Lobby</b>. Sin ella habria un <b>bucle</b>: el AFK del
     *       lobby dispara un viaje al lobby, que cuenta como movimiento, que
     *       reinicia el contador... y vuelta a empezar. Ademas no tiene sentido:
     *       ya esta donde lo mandariamos.
 *
 * <h2>&#9888;&#9888; SE MIRA LA POSICION, NO LA MIRADA</h2>
 *
 * Es la misma decision que {@code world/Espera}: si contara la rotacion, mover
 * el raton un pixel valdria como «sigo aqui» -- y eso lo hace un macro, o el
 * propio pulso de una mano apoyada. Lo que hay que detectar es <b>una silla
 * vacia</b>.
 *
 * <p>&#9888; Con un margen: {@code MARGEN} de bloque. Un jugador parado sobre
 * una barca, en agua o empujado por una entidad se mueve unas milesimas, y sin
 * margen eso contaria como estar jugando.
 */
public final class Afk {

    private Afk() {
    }

    /** Cuanto se aguanta quieto antes de ir al lobby. Diez minutos. */
    public static final long LIMITE_MS = 10L * 60L * 1000L;

    /**
     * Cuanto antes se avisa. Un minuto.
     *
     * <p>&#9888;&#9888; <b>Mover a alguien sin avisar es lo que hace que un
     * sistema correcto parezca un fallo.</b> Quien vuelve del baño y se
     * encuentra en otra dimension sin explicacion cree que el servidor lo ha
     * echado; con un aviso a los nueve minutos, sabe lo que pasa y le da tiempo
     * a dar un paso.
     */
    public static final long AVISO_MS = 60L * 1000L;

    /** Cada cuantos ticks se comprueba. Una vez por segundo sobra y de largo. */
    public static final int CADA = 20;

    /**
     * Cuanto hay que moverse para contar como movimiento, en bloques.
     *
     * <p>&#9888; Al cuadrado en la comparacion: {@code squaredDistanceTo} evita
     * una raiz cuadrada por jugador y por segundo, y aqui solo se compara.
     */
    private static final double MARGEN = 0.25;

    private record Estado(Vec3d donde, long desde, boolean avisado) {
    }

    private static final Map<UUID, Estado> QUIETOS = new ConcurrentHashMap<>();

    private static int contador;

    /**
     * Se llama en el tick del servidor.
     *
     * <p>&#9888; Se corta a {@link #CADA} ticks aqui dentro y no fuera para que
     * este sistema lleve <b>su propio ritmo</b>: encadenarlo al corte de otro lo
     * ataria a un numero que no es suyo. Misma razon que `ConstructorBuffs`.
     */
    public static void tick(MinecraftServer servidor) {
        if (++contador < CADA) {
            return;
        }
        contador = 0;
        long ahora = System.currentTimeMillis();
        for (ServerPlayerEntity jugador : servidor.getPlayerManager().getPlayerList()) {
            revisar(jugador, ahora);
        }
    }

    private static void revisar(ServerPlayerEntity jugador, long ahora) {
        UUID uuid = jugador.getUuid();
        if (exento(jugador)) {
            // ⚠ Se OLVIDA, no se congela: al salir del combate o del creativo el
            //   contador arranca de cero. Congelarlo dejaria a alguien a un
            //   segundo del limite nada mas terminar un combate de media hora.
            QUIETOS.remove(uuid);
            return;
        }
        Vec3d donde = jugador.getPos();
        Estado previo = QUIETOS.get(uuid);
        if (previo == null || donde.squaredDistanceTo(previo.donde()) > MARGEN * MARGEN) {
            QUIETOS.put(uuid, new Estado(donde, ahora, false));
            return;
        }
        long quieto = ahora - previo.desde();
        if (quieto >= LIMITE_MS) {
            QUIETOS.remove(uuid);
            aparcar(jugador);
            return;
        }
        if (!previo.avisado() && quieto >= LIMITE_MS - AVISO_MS) {
            QUIETOS.put(uuid, new Estado(previo.donde(), previo.desde(), true));
            jugador.sendMessage(Text.literal(
                    "§e§lATENCION §8» §7Llevas un rato sin moverte. Si sigues asi, "
                    + "en un minuto se te llevara al §blobby§7."), false);
        }
    }

    /**
     * &#9888;&#9888; LAS TRES EXENCIONES. Ver el javadoc de la clase: cada una
     * tapa un fallo concreto, no son cortesias.
     */
    private static boolean exento(ServerPlayerEntity jugador) {
        return LunaDimensions.LOBBY.equals(jugador.getServerWorld().getRegistryKey());
    }

    /**
     * Al lobby, y diciendo por que y como volver.
     *
     * <p>&#9888;&#9888; Va por {@code TravelService}, que es el unico camino por
     * el que se mueve a un jugador (30-ago). Un teletransporte a mano aqui se
     * saltaria la carga del chunk de destino y el apunte de donde estaba -- y el
     * lobby es una dimension que puede estar fria del todo.
     */
    private static void aparcar(ServerPlayerEntity jugador) {
        var puerta = LunaEternal.puerta();
        if (puerta != null) {
            // AFK es una nueva entrada funcional: no conserva el permiso del
            // clic anterior, pero tampoco borra historial ni datos del jugador.
            puerta.reiniciarSesion(jugador.getUuid());
        }
        boolean movido = TravelService.travel(jugador, LunaDimensions.LOBBY, "el Lobby");
        if (!movido) {
            LunaEternal.LOG.error("AFK_TRANSFER_FAILED jugador={}",
                    jugador.getGameProfile().getName());
            return;
        }
        jugador.sendMessage(Text.literal(
                "§b§lLOBBY §8» §7Estabas quieto, asi que te hemos aparcado aqui."), false);
        jugador.sendMessage(Text.literal(
                "§7Habla con el §bguardian§7 para volver. §8(")
                .append(net.pokereport.luna.ui.Iconos.clicDerecho())
                .append(Text.literal("§8 clic derecho o izquierdo)")), false);
        LunaEternal.LOG.info("AFK: {} llevaba {} min quieto; al lobby",
                jugador.getGameProfile().getName(), LIMITE_MS / 60000L);
    }

    /**
     * Se olvida al desconectar.
     *
     * <p>&#9888; Sin esto el mapa crece con cada jugador que pasa por el
     * servidor y no se vacia nunca. Es la primera cosa que busca el barrido de
     * «mapas por jugador contra lo que se limpia al desconectar».
     */
    public static void olvidar(UUID uuid) {
        QUIETOS.remove(uuid);
    }

    /** Cuantos se estan vigilando. Para el diagnostico del comando. */
    public static int vigilados() {
        return QUIETOS.size();
    }

    /**
     * Cuantos segundos lleva quieto, o {@code -1} si no se le esta contando.
     *
     * <p>&#9888; Existe para que {@code /luna afk} pueda decir la verdad en vez
     * de «funciona»: un sistema que solo se ve cuando actua es un sistema que no
     * se puede comprobar sin esperar diez minutos.
     */
    public static long quietoSegundos(UUID uuid) {
        Estado e = QUIETOS.get(uuid);
        return e == null ? -1 : (System.currentTimeMillis() - e.desde()) / 1000L;
    }

    /** &#9888; Solo para el comando de prueba: lo pone al borde del limite. */
    public static boolean forzarCasi(ServerPlayerEntity jugador) {
        Estado e = QUIETOS.get(jugador.getUuid());
        if (e == null) {
            return false;
        }
        QUIETOS.put(jugador.getUuid(), new Estado(
                e.donde(), System.currentTimeMillis() - (LIMITE_MS - AVISO_MS - 1000L), false));
        return true;
    }
}
