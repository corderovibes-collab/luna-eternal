package net.pokereport.luna.world;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;

/**
 * «NO TE MUEVAS DURANTE CINCO SEGUNDOS».
 *
 * <p>Petición del usuario: todo viaje —y todo reto a un entrenador— avisa y
 * espera. Si el jugador se mueve, se cancela.
 *
 * <h2>Para qué sirve de verdad</h2>
 *
 * Un teletransporte instantáneo es cómodo y <b>quita la única cosa que hace que
 * viajar cueste algo</b>: estar quieto y expuesto. Con cinco segundos, huir de
 * un sitio peligroso deja de ser gratis — y en un mundo salvaje eso es la
 * diferencia entre explorar y no arriesgar nada.
 *
 * <p>Y de propina resuelve un problema técnico: da tiempo a que el chunk de
 * destino termine de cargarse antes de que llegue nadie.
 *
 * <h2>⚠⚠ SE CANCELA POR MOVERSE, NO POR MIRAR</h2>
 *
 * La comprobación es <b>de posición</b> y con margen. Si mirase también la
 * rotación, girar el ratón cancelaría el viaje — y nadie se queda cinco
 * segundos sin mover el ratón. El margen existe porque un jugador quieto
 * <i>no está del todo quieto</i>: el rebote de la gravedad, un bloque de arena
 * que cae, una barca. Medio bloque es «te has movido» sin ser un castigo.
 *
 * <h2>⚠⚠ Y HAY QUE CANCELARLA AL SALIR</h2>
 *
 * Si alguien se desconecta durante la cuenta, la tarea sigue en la lista y al
 * dispararse intentaría mover a una entidad que ya no está. Se comprueba al
 * disparar <b>y</b> se limpia al desconectar: lo primero por si acaso, lo
 * segundo para no dejar basura.
 */
public final class Espera {

    private Espera() {}

    /** Cuántos segundos hay que estar quieto. */
    public static final int SEGUNDOS = 5;

    /**
     * Cuánto se puede uno mover sin que cuente.
     *
     * <p>⚠ Al cuadrado, para no sacar raíces sesenta veces por segundo.
     */
    private static final double MARGEN2 = 0.5 * 0.5;

    private record Cuenta(Vec3d desde, int quedan, Runnable accion,
                          Runnable alCancelar, String motivo) {}

    private static final Map<UUID, Cuenta> CUENTAS = new ConcurrentHashMap<>();

    /**
     * Pide la cuenta atrás. Si ya había una, la sustituye.
     *
     * <p>⚠ Sustituir y no rechazar: pulsar «ir» dos veces es lo normal, y
     * contestar «ya estás esperando» sería castigar la impaciencia. La segunda
     * pulsación reinicia la cuenta, que es lo que el jugador espera.
     *
     * @param motivo qué va a pasar: «viajar», «entrar al gimnasio»…
     * @param accion qué hacer si aguanta quieto. Corre en el hilo del servidor
     */
    public static void pedir(ServerPlayerEntity jugador, String motivo,
                             Runnable accion) {
        pedir(jugador, motivo, accion, null);
    }

    /**
     * Igual, pero avisando si se cancela.
     *
     * <h2>⚠⚠ EL AVISO DE CANCELACION NO ES CORTESIA: HAY COSAS RESERVADAS</h2>
     *
     * Retar a un gimnasio <b>aparta una ranura</b> antes de empezar la cuenta.
     * Si el jugador se mueve y nadie la suelta, esa copia del gimnasio queda
     * apartada para alguien que no va a ir — y al octavo, nadie puede retar al
     * lider. No da ningun error: deja de funcionar.
     */
    public static void pedir(ServerPlayerEntity jugador, String motivo,
                             Runnable accion, Runnable alCancelar) {
        // ⚠ Si ya habia una cuenta, la anterior se cancela DE VERDAD: su
        //   `alCancelar` tiene que correr, o lo que tuviera reservado se queda
        //   reservado.
        var previa = CUENTAS.remove(jugador.getUuid());
        if (previa != null && previa.alCancelar() != null) {
            previa.alCancelar().run();
        }
        anotar(jugador.getUuid(), jugador.getPos(), accion, alCancelar, motivo);
        jugador.sendMessage(Text.translatable(
                "espera.lunaeternal.empieza", SEGUNDOS), false);
        barra(jugador, SEGUNDOS);
    }

    /**
     * El apunte, sin jugador delante.
     *
     * <p>⚠ Existe separado para que el autotest pueda comprobar <b>lo que de
     * verdad puede romperse</b>: que cancelar suelte lo reservado. Con la
     * versión que recibe un jugador no se puede probar sin uno conectado, y ese
     * es justo el invariante que deja un gimnasio inservible cuando falla.
     */
    static void anotar(UUID uuid, Vec3d desde, Runnable accion,
                       Runnable alCancelar, String motivo) {
        CUENTAS.put(uuid, new Cuenta(desde, SEGUNDOS, accion, alCancelar, motivo));
    }

    /** Solo para el autotest: apunta una cuenta sin jugador. */
    public static void pedirDePrueba(UUID uuid, Runnable accion,
                                     Runnable alCancelar) {
        anotar(uuid, Vec3d.ZERO, accion, alCancelar, "prueba");
    }

    /** ¿Está esperando ahora mismo? */
    public static boolean esperando(UUID uuid) {
        return CUENTAS.containsKey(uuid);
    }

    /**
     * Se cancela sola al salir: no hay a quién mover.
     *
     * <p>⚠ Y corre su {@code alCancelar}: desconectarse a mitad de la cuenta
     * tiene que soltar lo que hubiera reservado, igual que moverse.
     */
    public static void olvidar(UUID uuid) {
        var c = CUENTAS.remove(uuid);
        if (c != null && c.alCancelar() != null) {
            try {
                c.alCancelar().run();
            } catch (Throwable t) {
                LunaEternal.LOG.error("Fallo al cancelar la espera", t);
            }
        }
    }

    /** Para las pruebas. */
    public static void olvidarTodo() {
        CUENTAS.clear();
    }

    public static int pendientes() {
        return CUENTAS.size();
    }

    /**
     * Un segundo de cuenta. Se llama <b>una vez por segundo</b>, no por tick.
     *
     * <p>⚠ Por segundo porque lo que se enseña es un número de segundos: mirar
     * la posición veinte veces por segundo no haría la cancelación más justa,
     * solo más quisquillosa — un tirón de red movería al jugador medio bloque y
     * volvería atrás sin que él tocara nada.
     */
    public static void tick(MinecraftServer servidor) {
        if (CUENTAS.isEmpty()) {
            return;
        }
        for (var e : CUENTAS.entrySet()) {
            UUID uuid = e.getKey();
            Cuenta c = e.getValue();
            var jugador = servidor.getPlayerManager().getPlayer(uuid);
            if (jugador == null) {
                CUENTAS.remove(uuid);
                continue;
            }
            if (jugador.getPos().squaredDistanceTo(c.desde()) > MARGEN2) {
                CUENTAS.remove(uuid);
                if (c.alCancelar() != null) {
                    c.alCancelar().run();
                }
                jugador.sendMessage(
                        Text.translatable("espera.lunaeternal.cancelada"), false);
                jugador.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                        SoundCategory.MASTER, 0.6f, 0.7f);
                continue;
            }
            int quedan = c.quedan() - 1;
            if (quedan > 0) {
                CUENTAS.put(uuid, new Cuenta(c.desde(), quedan, c.accion(),
                        c.alCancelar(), c.motivo()));
                barra(jugador, quedan);
                jugador.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(),
                        SoundCategory.MASTER, 0.3f, 1.4f);
                continue;
            }
            // ⚠ Se saca de la lista ANTES de ejecutar. La acción teletransporta,
            //   y un teletransporte que fallara dejaría la cuenta viva para
            //   siempre — repitiéndose cada segundo.
            CUENTAS.remove(uuid);
            try {
                c.accion().run();
            } catch (Throwable t) {
                LunaEternal.LOG.error("Fallo al terminar la espera de {}",
                        jugador.getName().getString(), t);
            }
        }
    }

    /**
     * El número, en la barra de acción.
     *
     * <p>⚠ En la barra y no en el chat: una línea de chat por segundo son cinco
     * líneas por viaje, y con veinte viajes al día el chat deja de servir para
     * nada. El aviso de que empieza sí va al chat, porque se lee una vez.
     */
    private static void barra(ServerPlayerEntity jugador, int quedan) {
        jugador.sendMessage(
                Text.translatable("espera.lunaeternal.barra", quedan), true);
    }
}
