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
     * Cuánto separa una ranura de la siguiente.
     *
     * <p>⚠ 64 y no 48: la sala que medimos son 40 de fondo, y hay que dejar
     * hueco por si un gimnasio sale más largo. Con las ocho ranuras se ocupan
     * 512 de los 1024 que separan un gimnasio del siguiente, así que aún cabe
     * el doble sin tocar nada.
     */
    public static final int PASO_RANURA = 64;

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
     * Dónde aparece el jugador al entrar en una ranura.
     *
     * <p>⚠ Por delante del origen y mirando hacia dentro: apareciendo justo en
     * la esquina entraría de espaldas a la sala.
     */
    public static BlockPos entrada(Gimnasio_ g, int ranura) {
        return origen(g, ranura).add(0, 1, 4);
    }

    /**
     * Dónde aparece el líder.
     *
     * <p>⚠ Provisional hasta que se pegue el esquema: 24 bloques al fondo.
     * Cuando el gimnasio esté construido se ajusta a su tarima, y por eso vive
     * aquí en <b>una línea</b> y no repartido por el código.
     */
    public static BlockPos lider(Gimnasio_ g, int ranura) {
        return origen(g, ranura).add(0, 1, 24);
    }
}
