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

    /**
     * Cuánto se copia al clonar una ranura.
     *
     * <p>⚠ Generoso a propósito: el gimnasio que medimos son 46×16×40 y hay que
     * dejar sitio a uno más grande. Clonar de más cuesta unos milisegundos <b>una
     * sola vez por ranura</b>; clonar de menos deja el gimnasio cortado, y eso
     * no se ve hasta que alguien camina hasta el borde.
     */
    private static final int COPIA_ANCHO = 96;
    private static final int COPIA_ALTO = 48;
    private static final int COPIA_FONDO = 56;

    private Arenas() {}

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
        int puestos = 0;
        var pos = new BlockPos.Mutable();
        var destino = new BlockPos.Mutable();
        for (int dy = -8; dy < COPIA_ALTO; dy++) {
            for (int dz = 0; dz < COPIA_FONDO; dz++) {
                for (int dx = 0; dx < COPIA_ANCHO; dx++) {
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
        LunaEternal.LOG.info("Gimnasio {}: ranura {} clonada ({} bloques)",
                g.id(), ranura, puestos);
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
        BlockPos p = Gimnasio.entrada(g, ranura);
        jugador.closeHandledScreen();
        // ⚠ Mirando al fondo de la sala (yaw 0 = hacia +Z), que es donde está la
        //   tarima. Sin fijarlo entra mirando a donde mirara en la ciudadela.
        jugador.teleport(mundo, p.getX() + 0.5, p.getY(), p.getZ() + 0.5,
                java.util.Set.of(), 0f, 0f);
        return true;
    }
}
