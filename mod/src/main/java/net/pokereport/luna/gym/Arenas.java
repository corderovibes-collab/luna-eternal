package net.pokereport.luna.gym;

import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.world.LunaDimensions;

/**
 * LAS SALAS DE LA DIMENSIÓN DE GIMNASIOS: plataforma, clonado y viaje.
 *
 * <h2>⚠⚠ LA PLATAFORMA ES PEQUEÑA A PROPÓSITO</h2>
 *
 * 9×9, lo justo para no caerse al vacío mientras se pega el esquema. Grande
 * estorbaría: habría que borrarla a mano antes de construir, y borrar a mano es
 * lo que hace que un día quede un trozo debajo del suelo del gimnasio y nadie
 * sepa por qué hay piedra flotando.
 *
 * <h2>⚠ Y NO SE PONE SOLA AL ARRANCAR</h2>
 *
 * {@link #preparar} se ejecuta cuando alguien lo pide. Puesta en cada arranque
 * <b>pisaría el gimnasio ya construido</b> cada vez que se reinicia el servidor.
 */
public final class Arenas {

    /** El lado de la plataforma de anclaje. */
    private static final int LADO = 9;

    private Arenas() {}

    /**
     * Mide la sala construida: la caja de lo que NO es aire.
     *
     * <h2>⚠⚠⚠ ESTO EXISTE PARA DEJAR DE SUPONER</h2>
     *
     * El area a clonar estaba escrita a ojo (96x48x56). Clonar de menos deja el
     * gimnasio <b>cortado</b>, y eso no se ve hasta que alguien camina hasta el
     * borde de su copia y se encuentra media pared. Clonar de mas puede llegar a
     * <b>copiar la sala de al lado</b>.
     *
     * <p>Midiendo, el numero deja de ser una opinion.
     *
     * @return {@code {minX, minY, minZ, ancho, alto, fondo}} relativo al origen,
     *         o {@code null} si no hay nada construido
     */
    public static int[] medir(MinecraftServer servidor, Gimnasio.Gimnasio_ g) {
        ServerWorld mundo = mundo(servidor);
        if (mundo == null) {
            return null;
        }
        BlockPos o = Gimnasio.maestro(g);
        int minX = 9999, minY = 9999, minZ = 9999;
        int maxX = -9999, maxY = -9999, maxZ = -9999;
        var pos = new BlockPos.Mutable();
        // ⚠ Se barre un area GENEROSA (medio hueco de gimnasio) porque medir es
        //   barato y quedarse corto al medir es el mismo fallo que se venia a
        //   arreglar.
        int alcance = Gimnasio.SEPARACION / 2;
        for (int dy = -16; dy < 120; dy++) {
            for (int dz = 0; dz < Gimnasio.PASO_RANURA; dz++) {
                for (int dx = 0; dx < alcance; dx++) {
                    pos.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                    if (mundo.getBlockState(pos).isAir()) {
                        continue;
                    }
                    if (dx < minX) minX = dx;
                    if (dy < minY) minY = dy;
                    if (dz < minZ) minZ = dz;
                    if (dx > maxX) maxX = dx;
                    if (dy > maxY) maxY = dy;
                    if (dz > maxZ) maxZ = dz;
                }
            }
        }
        if (maxX < minX) {
            return null;
        }
        return new int[] {minX, minY, minZ,
                          maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1};
    }

    public static ServerWorld mundo(MinecraftServer servidor) {
        return servidor.getWorld(LunaDimensions.GIMNASIOS);
    }

    /**
     * Pone la plataforma de anclaje del maestro.
     *
     * <p>⚠ Con la esquina noroeste EXACTAMENTE en el origen: ese punto es el
     * que se le da a Litematica o a WorldEdit al pegar, así que tiene que ser el
     * mismo número que devuelve {@link Gimnasio#maestro}.
     */
    public static BlockPos preparar(MinecraftServer servidor, Gimnasio.Gimnasio_ g) {
        ServerWorld mundo = mundo(servidor);
        if (mundo == null) {
            return null;
        }
        BlockPos o = Gimnasio.maestro(g);
        for (int dx = 0; dx < LADO; dx++) {
            for (int dz = 0; dz < LADO; dz++) {
                // ⚠ Piedra pulida y no hierba: al pegar el esquema encima, lo
                //   que sobresalga tiene que verse que NO es del gimnasio.
                mundo.setBlockState(o.add(dx, 0, dz),
                        Blocks.POLISHED_ANDESITE.getDefaultState());
            }
        }
        // la esquina exacta marcada, para no dudar nunca de cuál es el origen
        mundo.setBlockState(o, Blocks.GOLD_BLOCK.getDefaultState());
        LunaEternal.LOG.info("Gimnasio {}: maestro en {} {} {}",
                g.id(), o.getX(), o.getY(), o.getZ());
        return o;
    }

    /**
     * Clona el maestro a una ranura, si no estaba ya.
     *
     * <p>⚠⚠ SOLO UNA VEZ POR RANURA. Copiar ocho mil bloques es rápido, pero
     * hacerlo en cada combate serían ocho mil escrituras por reto — y con gente
     * retando en cadena eso sí se nota en el hilo del servidor.
     *
     * <p>⚠ Y se copia de abajo arriba y de norte a sur, en el mismo orden en
     * ambos lados: copiar en distinto orden con las áreas solapadas se pisa a sí
     * mismo. Aquí no solapan —van a 64 de distancia— pero el día que alguien
     * baje {@code PASO_RANURA} el orden es lo único que lo salva.
     */
    public static void clonar(MinecraftServer servidor, Gimnasio.Gimnasio_ g,
                              int ranura) {
        if (Ranuras.marcarConstruida(g, ranura)) {
            return;   // ya estaba
        }
        ServerWorld mundo = mundo(servidor);
        if (mundo == null) {
            return;
        }
        BlockPos src = Gimnasio.maestro(g);
        BlockPos dst = Gimnasio.origen(g, ranura);
        // ⚠⚠ SE MIDE LO CONSTRUIDO EN VEZ DE SUPONERLO. Antes iba a ojo
        //    (96x48x56): de menos deja el gimnasio CORTADO --y eso no se ve
        //    hasta que alguien camina hasta el borde de su copia y se encuentra
        //    media pared-- y de mas puede llegar a copiar la sala de al lado.
        int[] m = medir(servidor, g);
        if (m == null) {
            LunaEternal.LOG.warn("Gimnasio {}: el maestro esta vacio, "
                    + "no hay nada que clonar", g.id());
            return;
        }
        int x0 = m[0], y0 = m[1], z0 = m[2], ancho = m[3], alto = m[4], fondo = m[5];
        int puestos = 0;
        var pos = new BlockPos.Mutable();
        var destino = new BlockPos.Mutable();
        for (int dy = y0; dy < y0 + alto; dy++) {
            for (int dz = z0; dz < z0 + fondo; dz++) {
                for (int dx = x0; dx < x0 + ancho; dx++) {
                    pos.set(src.getX() + dx, src.getY() + dy, src.getZ() + dz);
                    var estado = mundo.getBlockState(pos);
                    if (estado.isAir()) {
                        continue;   // el vacío no se copia: la sala flota
                    }
                    destino.set(dst.getX() + dx, dst.getY() + dy, dst.getZ() + dz);
                    mundo.setBlockState(destino, estado, 2);
                    puestos++;
                }
            }
        }
        LunaEternal.LOG.info("Gimnasio {}: ranura {} clonada, {} bloques de una sala de {}x{}x{}", g.id(), ranura, puestos, ancho, alto, fondo);
    }

    /**
     * Lleva a un jugador a su ranura.
     *
     * <p>⚠ Se apunta dónde estaba ANTES de moverlo. Después ya está en la
     * arena, y volver le devolvería a la arena — la misma trampa que ya estaba
     * resuelta en el viaje al Mundo Hogar.
     */
    public static boolean llevar(ServerPlayerEntity jugador,
                                 Gimnasio.Gimnasio_ g, int ranura) {
        MinecraftServer servidor = jugador.getServer();
        if (servidor == null) {
            return false;
        }
        ServerWorld mundo = mundo(servidor);
        if (mundo == null) {
            LunaEternal.LOG.error("No existe la dimension de gimnasios");
            return false;
        }
        net.pokereport.luna.world.Regreso.apuntar(jugador);
        var p = Gimnasio.entrada(g, ranura);
        jugador.closeHandledScreen();
        // ⚠ Con giro fijo: sin fijarlo entra mirando a donde estuviera mirando
        //   en la ciudadela, que puede ser a una pared.
        jugador.teleport(mundo, p.x, p.y, p.z, java.util.Set.of(),
                Gimnasio.giroEntrada(g), 0f);
        return true;
    }
}
