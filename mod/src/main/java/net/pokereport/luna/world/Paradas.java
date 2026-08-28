package net.pokereport.luna.world;

import java.util.List;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;

/**
 * LAS PARADAS DE LA CIUDADELA: el «moto taxi».
 *
 * <p>Petición del usuario: puntos de viaje rápido por la ciudadela, con un
 * <b>Miraidon estático</b> marcando cada uno.
 *
 * <h2>⚠⚠ EL MIRAIDON NO ES EL BOTÓN, ES EL CARTEL</h2>
 *
 * Se viaja desde el PokePad, no haciendo clic en él — el usuario lo pidió
 * explícitamente sin interacción. El Miraidon está ahí para que <b>desde lejos
 * se vea dónde hay una parada</b>, que es lo que convierte una lista de nombres
 * en un sitio reconocible.
 *
 * <h2>⚠ Las coordenadas van AQUÍ y no en la base de datos</h2>
 *
 * Son parte de la ciudadela, que se construye a mano y cambia con ella. Una
 * tabla obligaría a un comando para editarla y a acordarse de usarlo; en el
 * código cambian cuando cambia la construcción, en el mismo sitio y a la vista.
 *
 * <p>⚠ Y por eso {@link #colocarTodas} se puede volver a llamar: si alguien
 * mueve un edificio, se borran los Miraidon y se ponen otra vez.
 */
public final class Paradas {

    private Paradas() {}

    /**
     * @param id      el que viaja en el paquete. NO se traduce ni se cambia:
     *                es lo que el cliente manda al servidor
     * @param x,y,z   dónde aparece el jugador
     */
    public record Parada(String id, double x, double y, double z) {
        public Vec3d pos() {
            return new Vec3d(x, y, z);
        }
    }

    /**
     * Las paradas, en el orden en que salen.
     *
     * <p>⚠ El orden es el de la lista y no alfabético: la ciudadela se recorre,
     * y una lista ordenada por nombre pondría «Centro de curación» antes que
     * «Laboratorio» sin que eso signifique nada sobre dónde están.
     */
    public static final List<Parada> TODAS = List.of(
        new Parada("torre_batalla",  56.5,  68, -13.9),
        new Parada("laboratorio",   -25.0,  68, -52.0),
        new Parada("palacio",       -78.6,  68, -10.8),
        new Parada("monumentos",    -63.9,  68, 142.0),
        new Parada("torre_comercial", 14.97, 68, 78.8),
        new Parada("centro_curacion", 72.3, 68, 66.11),
        new Parada("montana",        48.6,  92, -94.9));

    public static Parada de(String id) {
        for (Parada p : TODAS) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Lleva a alguien a una parada.
     *
     * <p>⚠⚠ SOLO DESDE LA CIUDADELA. Si se pudiera desde cualquier sitio, esto
     * sería un «volver a casa» instantáneo desde el mundo salvaje — y entonces
     * salir a explorar dejaría de tener ningún riesgo, que es de lo que va el
     * salvaje.
     */
    public static boolean llevar(ServerPlayerEntity jugador, String id) {
        var servidor = jugador.getServer();
        if (servidor == null) {
            return false;
        }
        if (!LunaDimensions.CIUDADELA.equals(jugador.getServerWorld().getRegistryKey())) {
            jugador.sendMessage(net.minecraft.text.Text.literal(
                    "§cLas paradas solo funcionan dentro de la ciudadela."), true);
            return false;
        }
        Parada p = de(id);
        if (p == null) {
            return false;
        }
        var mundo = servidor.getWorld(LunaDimensions.CIUDADELA);
        if (mundo == null) {
            return false;
        }
        jugador.closeHandledScreen();
        jugador.teleport(mundo, p.x(), p.y(), p.z(), java.util.Set.of(),
                jugador.getYaw(), jugador.getPitch());
        jugador.playSoundToPlayer(net.minecraft.sound.SoundEvents.BLOCK_PORTAL_TRAVEL,
                net.minecraft.sound.SoundCategory.MASTER, 0.2f, 1.6f);
        return true;
    }

    /**
     * Quita los Miraidon de las siete paradas.
     *
     * <p>⚠ Existe porque {@code /luna decorar quitar} solo borra <b>alrededor
     * de quien lo ejecuta</b>, y las paradas están repartidas por toda la
     * ciudadela: quitarlas obligaba a ir andando a las siete. Y andando es fácil
     * dejarse una — que además no se puede atacar ni capturar, así que se
     * quedaría ahí para siempre sin que nadie sepa por qué.
     *
     * <p>⚠ El radio es 3, el mismo que usa {@link #colocarTodas} para limpiar
     * antes de poner. Los dos números tienen que ser el mismo: si quitar barriera
     * menos que poner, quedarían restos justo fuera del alcance.
     */
    public static int quitarTodas(net.minecraft.server.MinecraftServer servidor) {
        var mundo = servidor.getWorld(LunaDimensions.CIUDADELA);
        if (mundo == null) {
            return 0;
        }
        int n = 0;
        for (Parada p : TODAS) {
            n += Decorativos.quitar(mundo, p.pos(), 3);
        }
        LunaEternal.LOG.info("Paradas: {} decorativos quitados", n);
        return n;
    }

    /**
     * Pone un Miraidon en cada parada.
     *
     * <p>⚠ Primero BORRA los que hubiera cerca. Sin eso, llamarlo dos veces
     * dejaría dos Miraidon superpuestos en cada sitio — y como no se pueden
     * atacar ni capturar, quitarlos a mano sería imposible.
     */
    public static int colocarTodas(net.minecraft.server.MinecraftServer servidor) {
        var mundo = servidor.getWorld(LunaDimensions.CIUDADELA);
        if (mundo == null) {
            return 0;
        }
        int n = 0;
        for (Parada p : TODAS) {
            Decorativos.quitar(mundo, p.pos(), 3);
            var e = Decorativos.colocar(mundo, "miraidon",
                    Decorativos.Postura.QUIETO, p.pos(), 0f);
            if (e != null) {
                n++;
            }
        }
        LunaEternal.LOG.info("Paradas: {} de {} Miraidon colocados", n, TODAS.size());
        return n;
    }
}
