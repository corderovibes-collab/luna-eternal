package net.pokereport.luna.world;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.pokereport.luna.LunaEternal;

/**
 * EL MUNDO SALVAJE: borde, aterrizaje aleatorio y reinicio.
 *
 * <h2>⚠⚠⚠ EL ENEMIGO NO ES EL TAMAÑO DEL MUNDO: ES GENERAR CHUNKS</h2>
 *
 * Un teletransporte aleatorio a un sitio no visitado <b>genera chunks en
 * vivo</b>, y generar es lo más caro que hace un servidor de Minecraft —
 * con 155 mods y Cobblemon encima, muchísimo más. Cargar un chunk ya generado
 * es entre 10 y 50 veces más barato.
 *
 * <p>De ahí sale todo lo demás de esta clase:
 *
 * <table>
 *   <tr><td><b>El borde</b></td>
 *       <td>no es una restricción, es <b>lo que hace posible pre-generar</b>.
 *           Sin un mundo finito no se puede pre-generar, ni predecir el disco,
 *           ni predecir el lag</td></tr>
 *   <tr><td><b>El aterrizaje</b></td>
 *       <td>busca <b>dentro</b> del borde y con reintentos, en vez de caer donde
 *           sea y confiar</td></tr>
 *   <tr><td><b>El reinicio</b></td>
 *       <td>semanal. Es lo que impide que el disco crezca sin fin</td></tr>
 * </table>
 *
 * <h2>⚠ Lo que ESTA clase no hace, y hay que hacer aparte</h2>
 *
 * <b>Pre-generar.</b> Sin ello, el primer jugador que caiga en una zona virgen
 * paga la generación con su propio lag. Es la medida que más se nota de todas y
 * se hace con el servidor vacío. Ver {@code docs/world/salvaje.md}.
 */
public final class Salvaje {

    private Salvaje() {}

    /**
     * Radio del borde, en bloques. <b>3.000 por decisión del usuario</b>, que
     * lo bajó de 5.000 al ver el coste de pre-generar tres mundos:
     *
     * <pre>
     *   3.000   36 km²   140.000 chunks   25-45 min por mundo   2-4 h los tres
     *   5.000  100 km²   390.000 chunks    1-2 h  por mundo    9-18 h con reservas
     * </pre>
     *
     * <p>⚠ Y esas horas <b>compiten con los jugadores por los mismos 3
     * núcleos</b>. Es lo que hace que 36 km² por mundo sea la decisión correcta
     * teniendo tres mundos en vez de uno.
     */
    public static final int RADIO = 3000;

    /**
     * Dónde NO se puede aterrizar.
     *
     * <p>⚠ Se deja un anillo de 200 bloques sin usar pegado al borde: caer justo
     * en el límite deja al jugador contra una pared invisible, que es de las
     * cosas que peor primera impresión dan.
     */
    private static final int MARGEN_BORDE = 200;

    /**
     * ⚠ Cuántas veces se intenta antes de rendirse. Un punto al azar puede caer
     * en océano, en lava o en una cueva; con un solo intento, uno de cada
     * varios viajes acabaría en el agua.
     */
    private static final int INTENTOS = 24;

    /**
     * Pone el borde. Se llama al arrancar, en cada arranque.
     *
     * <p>⚠ El borde de un mundo <b>se guarda en el nivel</b>, así que ponerlo
     * una vez bastaría... hasta que alguien lo cambie con un comando o restaure
     * un respaldo viejo. Aplicarlo siempre cuesta nada y lo deja fijo.
     */
    public static void ponerBorde(MinecraftServer servidor) {
        int puestos = 0;
        // ⚠ A LOS SEIS, no solo a los activos. Un mundo de reserva se
        //   pre-genera antes de entrar en servicio, y pre-generar sin borde
        //   generaria hasta el infinito.
        for (var clave : LunaDimensions.SALVAJES) {
            ServerWorld mundo = servidor.getWorld(clave);
            if (mundo == null) {
                continue;
            }
            var borde = mundo.getWorldBorder();
            borde.setCenter(0.5, 0.5);
            borde.setSize(RADIO * 2.0);
            // ⚠ Sin daño y sin margen: el borde es una regla de diseño, no una
            //   trampa. Que empuje, no que mate.
            borde.setDamagePerBlock(0.0);
            borde.setSafeZone(0.0);
            borde.setWarningBlocks(32);
            puestos++;
        }
        LunaEternal.LOG.info("Salvaje: {} mundos con borde de {} de radio · "
                + "{} activos", puestos, RADIO, ACTIVOS);
    }

    // ---- qué mundos están en servicio --------------------------------------

    /**
     * Cuántos de los seis están abiertos a la vez. <b>Decisión del usuario:
     * tres activos y tres de reserva.</b>
     *
     * <p>⚠ Los otros tres NO están vacíos esperando: están <b>pre-generados</b>
     * para que el día de la rotación no haya que generar nada. Es lo que
     * convierte el reinicio semanal en cambiar tres números.
     */
    public static final int ACTIVOS = 3;

    /**
     * A partir de cuánta gente un mundo se considera lleno.
     *
     * <p>⚠ NO es un tope: es el número a partir del cual el reparto deja de
     * mandar gente ahí. Quien quiera entrar igual —para ir con su clan— entra.
     * Ver {@link #conJugador}.
     */
    public static final int LLENO = 40;

    /**
     * Los tres en servicio este ciclo.
     *
     * <p>⚠ Hoy son los tres primeros. Cuando exista la rotación semanal, esto
     * leerá qué trío toca — y por eso está en un método y no escrito en cada
     * sitio que lo necesita.
     */
    public static java.util.List<net.minecraft.registry.RegistryKey<net.minecraft.world.World>> activos() {
        return LunaDimensions.SALVAJES.subList(0, ACTIVOS);
    }

    public static boolean esSalvaje(net.minecraft.registry.RegistryKey<net.minecraft.world.World> clave) {
        return LunaDimensions.SALVAJES.contains(clave);
    }

    /** Cuánta gente hay en cada mundo activo, en orden. */
    public static int[] poblacion(MinecraftServer servidor) {
        var xs = activos();
        int[] n = new int[xs.size()];
        for (var j : servidor.getPlayerManager().getPlayerList()) {
            int i = xs.indexOf(j.getWorld().getRegistryKey());
            if (i >= 0) {
                n[i]++;
            }
        }
        return n;
    }

    /**
     * El mundo con menos gente.
     *
     * <h2>⚠⚠ ESTO REPARTE DENSIDAD, NO CARGA</h2>
     *
     * Merece decirlo aquí porque es lo que casi todo el mundo entiende al
     * revés: <b>todas las dimensiones se tickean en el mismo hilo</b>, así que
     * repartir gente entre mundos no baja el lag. Cuarenta jugadores repartidos
     * por tres mundos cargan los mismos chunks que cuarenta repartidos por uno.
     *
     * <p>Lo que sí arregla es que cuarenta personas se peleen por el mismo
     * legendario, que es un problema de <b>juego</b> y muy real.
     */
    public static net.minecraft.registry.RegistryKey<net.minecraft.world.World> menosPoblado(
            MinecraftServer servidor) {
        var xs = activos();
        int[] n = poblacion(servidor);
        int mejor = 0;
        for (int i = 1; i < n.length; i++) {
            if (n[i] < n[mejor]) {
                mejor = i;
            }
        }
        return xs.get(mejor);
    }

    /**
     * Un sitio donde soltar a alguien: superficie, seco y con aire encima.
     *
     * <h2>⚠⚠ CARGA EL CHUNK A PROPÓSITO, Y ES EL COSTE DE ESTO</h2>
     *
     * {@code getTopY} necesita el chunk cargado. En un mundo <b>pre-generado</b>
     * eso es leer de disco y es barato; en uno virgen es generarlo, y ahí está
     * el lag que hay que evitar pre-generando.
     *
     * @return el sitio, o {@code null} si tras varios intentos no salió ninguno
     */
    public static BlockPos aterrizaje(ServerWorld mundo) {
        int util = RADIO - MARGEN_BORDE;
        var azar = ThreadLocalRandom.current();
        for (int i = 0; i < INTENTOS; i++) {
            int x = azar.nextInt(-util, util + 1);
            int z = azar.nextInt(-util, util + 1);
            // ⚠ MOTION_BLOCKING_NO_LEAVES y no WORLD_SURFACE: con el segundo, la
            //   copa de un árbol cuenta como suelo y el jugador aparece encima
            //   de las hojas, que al romperse le deja caer.
            int y = mundo.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= mundo.getBottomY() + 1 || y >= mundo.getTopY() - 2) {
                continue;
            }
            var suelo = new BlockPos(x, y - 1, z);
            var pies = new BlockPos(x, y, z);
            if (!mundo.getFluidState(suelo).isEmpty()
                    || !mundo.getFluidState(pies).isEmpty()) {
                continue;   // océano, río o lava
            }
            if (mundo.getBlockState(suelo).isAir()) {
                continue;   // no hay dónde apoyarse
            }
            if (mundo.getFluidState(suelo).isIn(FluidTags.LAVA)) {
                continue;
            }
            return pies;
        }
        return null;
    }

    /**
     * Manda a alguien a un punto al azar del salvaje.
     *
     * @return {@code true} si llegó
     */
    public static boolean llevar(ServerPlayerEntity jugador) {
        var servidor = jugador.getServer();
        if (servidor == null) {
            return false;
        }
        ServerWorld mundo = servidor.getWorld(menosPoblado(servidor));
        if (mundo == null) {
            return false;
        }
        BlockPos donde = aterrizaje(mundo);
        if (donde == null) {
            // ⚠ Se dice, no se disimula. Si no hubo suerte en 24 intentos es que
            //   pasa algo con el mundo, y dejar al jugador donde estaba sin
            //   explicación parece que el botón no funciona.
            LunaEternal.LOG.warn("Salvaje: sin sitio de aterrizaje tras {} intentos",
                    INTENTOS);
            return false;
        }
        // ⚠ Se cierra la pantalla ANTES de mover: si el jugador viaja con el
        //   PokePad abierto, la pantalla se queda enseñando datos del sitio de
        //   donde salió. Misma razón que en `TravelService`.
        jugador.closeHandledScreen();
        jugador.teleport(mundo, donde.getX() + 0.5, donde.getY(), donde.getZ() + 0.5,
                java.util.Set.of(), jugador.getYaw(), jugador.getPitch());
        jugador.playSoundToPlayer(net.minecraft.sound.SoundEvents.BLOCK_PORTAL_TRAVEL,
                net.minecraft.sound.SoundCategory.MASTER, 0.2f, 1.4f);
        jugador.sendMessage(net.minecraft.text.Text.literal(
                "§8» §fMundo Salvaje " + numeroDe(mundo) + " §8· §7"
                + donde.getX() + ", " + donde.getZ()), false);
        return true;
    }

    /** 1..6, para enseñárselo al jugador. */
    public static int numeroDe(ServerWorld mundo) {
        return LunaDimensions.SALVAJES.indexOf(mundo.getRegistryKey()) + 1;
    }

    /**
     * Ir DONDE ESTÁ OTRO JUGADOR, aunque su mundo esté lleno.
     *
     * <h2>⚠⚠ ES LA SALIDA AL REPARTO, Y ES DELIBERADA</h2>
     *
     * Decisión del usuario: el reparto manda al mundo con menos gente, pero
     * <i>«hay grupos, clanes… si ellos quieren ir a ese mundo y hay más de 40,
     * lo pueden hacer»</i>.
     *
     * <p>Y es lo correcto: un reparto que <b>separa a un clan</b> no es
     * equilibrio, es una avería. El automático resuelve el caso normal —entrar
     * solo— y esto resuelve el que importa —entrar con los tuyos—.
     *
     * <p>⚠ Solo funciona hacia un mundo SALVAJE. Sin esa comprobación esto
     * sería un teletransporte a cualquiera, en cualquier sitio, gratis — y eso
     * es otra cosa muy distinta que nadie ha pedido.
     */
    public static boolean conJugador(ServerPlayerEntity quien, ServerPlayerEntity destino) {
        var servidor = quien.getServer();
        if (servidor == null || destino == null || destino.isRemoved()) {
            return false;
        }
        ServerWorld mundo = destino.getServerWorld();
        if (!esSalvaje(mundo.getRegistryKey())) {
            return false;
        }
        // ⚠ Se busca sitio CERCA de él, no encima: aparecer dentro de otro
        //   jugador los empuja a los dos y a veces atraviesa un bloque.
        var azar = ThreadLocalRandom.current();
        for (int i = 0; i < 12; i++) {
            int dx = azar.nextInt(-6, 7), dz = azar.nextInt(-6, 7);
            int x = destino.getBlockX() + dx, z = destino.getBlockZ() + dz;
            int y = mundo.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            var suelo = new BlockPos(x, y - 1, z);
            if (y <= mundo.getBottomY() + 1 || mundo.getBlockState(suelo).isAir()
                    || !mundo.getFluidState(suelo).isEmpty()) {
                continue;
            }
            quien.closeHandledScreen();
            quien.teleport(mundo, x + 0.5, y, z + 0.5, java.util.Set.of(),
                    quien.getYaw(), quien.getPitch());
            quien.playSoundToPlayer(net.minecraft.sound.SoundEvents.BLOCK_PORTAL_TRAVEL,
                    net.minecraft.sound.SoundCategory.MASTER, 0.2f, 1.4f);
            quien.sendMessage(net.minecraft.text.Text.literal(
                    "§8» §fJunto a §6" + destino.getName().getString()
                    + " §8· Mundo Salvaje " + numeroDe(mundo)), false);
            return true;
        }
        return false;
    }
}
