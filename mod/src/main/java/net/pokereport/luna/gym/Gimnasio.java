package net.pokereport.luna.gym;

import java.util.Arrays;
import java.util.List;

import net.minecraft.util.math.BlockPos;

/**
 * LOS GIMNASIOS DE KANTO, Y SUS RANURAS.
 *
 * <h2>⚠⚠⚠ VARIOS JUGADORES RETAN AL MISMO LÍDER A LA VEZ</h2>
 *
 * Es el caso que rompe el diseño obvio, y lo señaló el usuario: con una sola
 * sala y un solo Brock, el segundo retador entra y ve el combate del primero,
 * sin nadie contra quien luchar.
 *
 * <p>La salida es <b>instanciar</b>, y se puede porque <b>un combate de
 * Cobblemon no necesita la sala</b>: el combate es una interfaz, y la sala es la
 * puesta en escena. Así que cada retador entra en <b>su propia copia</b>.
 *
 * <pre>
 *   x = sala * SEPARACION      cada gimnasio
 *   z = ranura * PASO_RANURA   cada copia dentro del gimnasio
 * </pre>
 *
 * <h2>⚠⚠ LA RANURA 0 ES EL MAESTRO Y NO SE JUEGA EN ELLA</h2>
 *
 * Es donde se pega el esquema una sola vez. Las demás se <b>clonan de ella</b>
 * la primera vez que hacen falta, y la copia se queda hecha: se paga una vez por
 * ranura, no una vez por combate.
 *
 * <p>⚠ Y jugar en el maestro sería jugar sobre el original: un combate que
 * rompiera un bloque estropearía la plantilla de la que salen todas las demás.
 */
public final class Gimnasio {

    /**
     * Un gimnasio.
     *
     * @param id         identificador estable. Da la clave de traducción
     * @param entrenador el id del entrenador en rctmod, tal cual
     * @param sala       a qué sala de la dimensión pertenece
     * @param medallas   cuántas medallas hay que traer para entrar
     */
    public record Gimnasio_(String id, String entrenador, int sala, int medallas) {}

    /** Cuánto separa un gimnasio del siguiente. */
    public static final int SEPARACION = 1024;

    /**
     * Cuánto separa una ranura de la siguiente, en Z.
     *
     * <h2>⚠⚠⚠ 128 PORQUE EL GIMNASIO DE BROCK MIDE 86 DE FONDO</h2>
     *
     * Estaba en 64 <b>a ojo</b>, y el gimnasio de verdad no cabía: las copias se
     * habrían pisado 22 bloques cada una. No habría dado ningún error — habría
     * dado dos gimnasios fundidos, con el segundo jugador apareciendo dentro de
     * la pared del primero.
     *
     * <p>⚠ Y no lo caza ninguna comprobación de arranque, porque el tamaño del
     * gimnasio <b>está en el mundo, no en el código</b>. Lo que sí lo caza es
     * {@code Arenas.clonar}, que mide antes de copiar y <b>se niega</b> si no
     * cabe.
     *
     * <p>⚠ Las ranuras crecen en Z y los gimnasios se separan en X, así que
     * subir esto <b>no acerca un gimnasio a otro</b>: son ejes distintos.
     */
    public static final int PASO_RANURA = 128;

    /** Cuántos pueden estar retando al mismo líder a la vez. */
    public static final int RANURAS = 8;

    /** La altura del suelo de todas las salas. */
    public static final int SUELO = 64;

    /**
     * Los ocho de Kanto, en el orden del juego.
     *
     * <p>⚠⚠ Los identificadores de entrenador <b>no se inventan</b>: salen del
     * datapack que ya está instalado ({@code data/rctmod/trainers/}). Uno mal
     * escrito <b>no da error al arrancar</b>: da un gimnasio en el que no
     * aparece nadie, y eso se descubre con el jugador dentro.
     */
    public static final List<Gimnasio_> TODOS = Arrays.asList(
        new Gimnasio_("brock",    "kanto_brock",    0, 0),
        new Gimnasio_("misty",    "kanto_misty",    1, 1),
        new Gimnasio_("surge",    "kanto_lt_surge", 2, 2),
        new Gimnasio_("erika",    "kanto_erika",    3, 3),
        new Gimnasio_("koga",     "kanto_koga",     4, 4),
        new Gimnasio_("sabrina",  "kanto_sabrina",  5, 5),
        new Gimnasio_("blaine",   "kanto_blaine",   6, 6),
        new Gimnasio_("giovanni", "kanto_giovanni", 7, 7));

    private Gimnasio() {}

    public static Gimnasio_ de(String id) {
        if (id == null) {
            return null;
        }
        for (Gimnasio_ g : TODOS) {
            if (g.id().equals(id)) {
                return g;
            }
        }
        return null;
    }

    /**
     * El origen de una ranura: su esquina noroeste.
     *
     * <p>La ranura 0 es la del maestro, y ese punto es el que se le da a
     * Litematica o a WorldEdit al pegar el esquema.
     */
    public static BlockPos origen(Gimnasio_ g, int ranura) {
        return new BlockPos(g.sala() * SEPARACION, SUELO, ranura * PASO_RANURA);
    }

    /** El maestro: donde se pega el gimnasio. */
    public static BlockPos maestro(Gimnasio_ g) {
        return origen(g, 0);
    }

    /**
     * DÓNDE APARECE CADA COSA DENTRO DE UNA SALA.
     *
     * <h2>⚠⚠⚠ SON DESFASES DESDE EL ORIGEN, NO COORDENADAS ABSOLUTAS</h2>
     *
     * El usuario los mide <b>en el maestro</b>, que está en (0, 64, 0), así que
     * lo que dice es directamente el desfase. Guardarlos como coordenadas
     * absolutas parecería más simple y estaría mal: <b>en la ranura 3 el jugador
     * aparecería fuera de su sala</b>, dentro del vacío o encima de la de otro.
     *
     * <p>⚠ Y viven aquí, los dos juntos y en dos líneas. Cuando el gimnasio
     * cambie —o cuando se construya el de Misty— se ajustan aquí y no hay que
     * buscarlos repartidos por el código.
     */
    private record Punto(double x, double y, double z, float giro) {}

    /**
     * La entrada de cada gimnasio, medida en su maestro.
     *
     * <p>⚠ Brock: medido por el usuario tras pegar el esquema (48, 78, 17.26)
     * sobre un origen en (0, 64, 0).
     */
    private static final java.util.Map<String, Punto> ENTRADAS = java.util.Map.of(
        "brock", new Punto(48.0, 14.0, 17.26, 0f));

    /**
     * La tarima del líder.
     *
     * <p>⚠ Sin medir todavía: hasta que el usuario diga dónde está la tarima de
     * Brock, se usa el respaldo —24 bloques al fondo de la entrada— que es una
     * SUPOSICIÓN. Un líder que aparezca dentro de una pared no da ningún error:
     * aparece, y no se le ve.
     */
    private static final java.util.Map<String, Punto> LIDERES = java.util.Map.of();

    /** Dónde aparece el jugador al entrar en una ranura. */
    public static net.minecraft.util.math.Vec3d entrada(Gimnasio_ g, int ranura) {
        Punto p = ENTRADAS.get(g.id());
        BlockPos o = origen(g, ranura);
        if (p == null) {
            // ⚠ Respaldo para los gimnasios aún sin construir: al lado del
            //   origen, sobre la plataforma de anclaje. No es «dentro del
            //   gimnasio», pero al menos no es dentro de una pared ni el vacío.
            return new net.minecraft.util.math.Vec3d(
                    o.getX() + 4.5, o.getY() + 1, o.getZ() + 4.5);
        }
        return new net.minecraft.util.math.Vec3d(
                o.getX() + p.x(), o.getY() + p.y(), o.getZ() + p.z());
    }

    /** Hacia dónde mira al aparecer. */
    public static float giroEntrada(Gimnasio_ g) {
        Punto p = ENTRADAS.get(g.id());
        return p == null ? 0f : p.giro();
    }

    /** Dónde aparece el líder. */
    public static net.minecraft.util.math.Vec3d lider(Gimnasio_ g, int ranura) {
        Punto p = LIDERES.get(g.id());
        BlockPos o = origen(g, ranura);
        if (p == null) {
            var e = entrada(g, ranura);
            return new net.minecraft.util.math.Vec3d(e.x, e.y, e.z + 20);
        }
        return new net.minecraft.util.math.Vec3d(
                o.getX() + p.x(), o.getY() + p.y(), o.getZ() + p.z());
    }
}
