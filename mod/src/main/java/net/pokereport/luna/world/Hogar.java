package net.pokereport.luna.world;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.pokereport.luna.LunaEternal;

/**
 * EL MUNDO HOGAR: la primera vez al azar, y después a tu casa.
 *
 * <p>Petición del usuario: <i>«en Mundo Hogar la primera ida es aleatoria, la
 * segunda ya vas donde te quedaste la última vez»</i>.
 *
 * <h2>⚠⚠ LA PRIMERA AL AZAR NO ES UN CAPRICHO: ES LO QUE REPARTE LAS CASAS</h2>
 *
 * Si todo el mundo apareciera en el mismo punto, todo el mundo construiría
 * alrededor de ese punto — y en un servidor con protecciones eso significa que
 * quien llegue tarde no encuentra sitio en kilómetros. Repartir la primera
 * llegada hace que las casas nazcan separadas solas, sin tener que pedírselo a
 * nadie.
 *
 * <h2>⚠ Y por eso el radio es 2.000 y no más</h2>
 *
 * Bastante para que la gente no se pise, y poco para que los vecinos sigan
 * siendo alcanzables a pie. Con 10.000 cada casa sería una isla y el mundo
 * compartido dejaría de sentirse compartido.
 */
public final class Hogar {

    private Hogar() {}

    /** Radio del reparto de primera llegada. Ver la cabecera. */
    public static final int RADIO_LLEGADA = 2000;

    private static final int INTENTOS = 24;

    /**
     * Lleva a alguien al Hogar.
     *
     * <h2>⚠⚠ VA POR EL EXECUTOR PORQUE HAY QUE CONSULTAR</h2>
     *
     * Saber si es su primera vez es una consulta, y consultar desde el hilo del
     * servidor está prohibido. Así que: se pregunta fuera, y cuando contesta se
     * vuelve al hilo del servidor para moverlo.
     */
    public static void llevar(ServerPlayerEntity jugador) {
        var servidor = jugador.getServer();
        var svc = LunaEternal.regresos();
        if (servidor == null) {
            return;
        }
        ServerWorld mundo = servidor.getWorld(LunaDimensions.HOGAR);
        if (mundo == null) {
            return;
        }
        // ⚠ Se apunta dónde está ANTES de nada: si venía de otro mundo que se
        //   recuerde, esta es la última oportunidad de guardarlo.
        Regreso.apuntar(jugador);

        if (svc == null) {
            aterrizar(jugador, mundo, null);
            return;
        }
        final var uuid = jugador.getUuid();
        final String nombre = jugador.getName().getString();
        LunaEternal.submit(() -> {
            Vec3d guardada = null;
            try {
                long id = LunaEternal.players().resolve(uuid, nombre);
                guardada = svc.leer(id, LunaDimensions.HOGAR);
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo leer el regreso de {}: {}",
                        nombre, e.toString());
            }
            final Vec3d donde = guardada;
            servidor.execute(() -> {
                if (!jugador.isRemoved()) {
                    aterrizar(jugador, mundo, donde);
                }
            });
        });
    }

    /**
     * @param guardada dónde estaba, o {@code null} si es su primera vez
     */
    private static void aterrizar(ServerPlayerEntity jugador, ServerWorld mundo,
                                  Vec3d guardada) {
        Vec3d destino = guardada;
        boolean primera = destino == null;
        if (primera) {
            BlockPos p = alAzar(mundo);
            if (p == null) {
                // ⚠ Sin sitio, al spawn del mundo: dejarlo donde estaba sería
                //   que el botón «no hace nada», que es lo peor de los dos.
                p = mundo.getSpawnPos();
            }
            destino = new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        }
        // ⚠ Por `Traslado`: carga el chunk de destino. En el Hogar importa
        //   especialmente la PRIMERA vez, que cae en un punto al azar donde no
        //   ha estado nadie nunca.
        Traslado.ir(jugador, mundo, destino);
        jugador.playSoundToPlayer(net.minecraft.sound.SoundEvents.BLOCK_PORTAL_TRAVEL,
                net.minecraft.sound.SoundCategory.MASTER, 0.2f, 1.4f);
        jugador.sendMessage(net.minecraft.text.Text.literal(primera
                ? "§8» §fMundo Hogar §8· §7tu sitio de partida"
                : "§8» §fMundo Hogar §8· §7donde lo dejaste"), false);

        // ⚠ La primera se apunta YA. Si no, salir sin moverse dejaría la fila
        //   sin crear y la siguiente entrada volvería a ser aleatoria — o sea,
        //   «la segunda vez» nunca llegaría.
        if (primera) {
            Regreso.apuntar(jugador);
        }
    }

    /** Un sitio de superficie, seco, dentro del radio de reparto. */
    private static BlockPos alAzar(ServerWorld mundo) {
        var azar = ThreadLocalRandom.current();
        for (int i = 0; i < INTENTOS; i++) {
            int x = azar.nextInt(-RADIO_LLEGADA, RADIO_LLEGADA + 1);
            int z = azar.nextInt(-RADIO_LLEGADA, RADIO_LLEGADA + 1);
            int y = mundo.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= mundo.getBottomY() + 1 || y >= mundo.getTopY() - 2) {
                continue;
            }
            var suelo = new BlockPos(x, y - 1, z);
            var pies = new BlockPos(x, y, z);
            if (mundo.getBlockState(suelo).isAir()
                    || !mundo.getFluidState(suelo).isEmpty()
                    || !mundo.getFluidState(pies).isEmpty()) {
                continue;
            }
            return pies;
        }
        return null;
    }
}
