package net.pokereport.luna.santuario;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * HACIA DONDE SE ABRE UN NICHO, MEDIDO EN EL MUNDO Y NO DECLARADO.
 *
 * <h2>&#9888;&#9888;&#9888; TRES COSAS DEPENDIAN DE ESTO Y LAS TRES LO DABAN POR
 * SUPUESTO EN Z+</h2>
 *
 * <ul>
 *   <li>el <b>teletransporte</b> al nicho dejaba al jugador en {@code z + 2,5}
 *       <b>siempre</b>, o sea DENTRO DE LA PARED en tres de cada cuatro
 *       orientaciones;</li>
 *   <li>y mirando con <b>yaw 0</b>, que en Minecraft es el sur: de espaldas al
 *       memorial que se acaba de abrir;</li>
 *   <li>el <b>holograma</b> se dibuja centrado en el proyector, asi que la mitad
 *       de la foto queda <b>metida en la pared del fondo</b> en cuanto se mira
 *       de lado.</li>
 * </ul>
 *
 * <h2>&#9888;&#9888; SE MIDE, NO SE DECLARA</h2>
 *
 * Lo facil era añadir un campo {@code orientacion} a la config. Pero eso es un
 * cuarto numero que declarar por nicho --y con «muchisimos» nichos, un cuarto
 * numero que un dia no cuadra con los otros tres, sin dar ningun error--. Un
 * nicho es un hueco en una pared: <b>por donde se entra ya lo dice el mundo</b>,
 * que es aire por un lado y bloque por los otros tres.
 *
 * <p>Es la misma decision que ya tomo {@code clonar} al medir los gimnasios:
 * <i>«hoy corta en el aire, que es un dato del mundo y no un numero nuestro»</i>.
 */
public final class Orientacion {

    /** Cuantos bloques se miran hacia cada lado antes de decidir. */
    private static final int ALCANCE = 4;

    /**
     * Lo medido, por posicion de proyector.
     *
     * <h2>&#9888;&#9888;&#9888; SIN ESTO, CADA REPARTO DEL ESTADO LEIA BLOQUES</h2>
     *
     * El estado del Santuario se manda a <b>todos los conectados</b> cada vez
     * que alguien reclama, honra o aprueba una foto. Midiendo en cada envio,
     * eso son ocho lecturas de bloque por nicho y por jugador -- y una lectura
     * en un chunk frio <b>lo carga de forma sincrona en el hilo del servidor</b>.
     * Con «muchisimos» nichos y nadie cerca de Monumentos, un solo clic de
     * honor habria cargado el barrio entero. No daria ningun error: daria un
     * tiron.
     *
     * <p><b>Un nicho no se mueve</b>, asi que se mide una vez y se guarda. La
     * cache se vacia al recargar la config, que es lo unico que puede cambiar
     * donde esta un proyector.
     */
    private static final java.util.Map<BlockPos, Direction> MEDIDO =
            new java.util.concurrent.ConcurrentHashMap<>();

    private Orientacion() {
    }

    /**
     * Olvida lo medido.
     *
     * <p>&#9888; Se llama al recargar la config: si un nicho cambia de sitio, su
     * orientacion guardada pasa a describir un hueco que ya no esta ahi -- y eso
     * se veria como una foto adelantada hacia la pared equivocada, sin ningun
     * error.
     */
    public static void olvidar() {
        MEDIDO.clear();
    }

    /**
     * La direccion hacia la que se abre el nicho.
     *
     * <p>&#9888; Nunca devuelve {@code null}: un nicho tapiado por los cuatro
     * lados devuelve {@code SOUTH}, que es lo que habia escrito a mano antes.
     * Degradar al comportamiento viejo es peor que acertar y mucho mejor que
     * reventar.
     *
     * <p>&#9888;&#9888; Con el chunk sin cargar tambien devuelve {@code SOUTH},
     * y <b>no lo guarda</b>: se vuelve a mirar cuando alguien se acerque. Nadie
     * puede ver un holograma a esa distancia --el dibujado corta a 96 bloques--
     * asi que una respuesta provisional ahi no la ve nunca nadie.
     */
    public static Direction de(World mundo, BlockPos proyector) {
        Direction guardada = MEDIDO.get(proyector);
        if (guardada != null) {
            return guardada;
        }
        // ⚠⚠⚠ SE PREGUNTA POR EL CHUNK, NO SE DEDUCE DE LO MEDIDO. El primer
        //    intento fue «si sale todo aire, es que no estaba cargado», y ESO
        //    ESTABA MAL: un nicho abierto de verdad a una sala grande TAMBIEN
        //    mide todo aire, asi que su medida buena se habria tirado siempre y
        //    la cache no habria guardado nada nunca -- volviendo a leer bloques
        //    en cada reparto, que es justo lo que esta cache existe para evitar.
        //    ⚠ Y sin la pregunta, `getBlockState` sobre un chunk frio LO CARGA
        //      de forma sincrona: la lectura «barata» seria la cara.
        if (!mundo.isChunkLoaded(proyector.getX() >> 4, proyector.getZ() >> 4)) {
            return Direction.SOUTH;
        }
        Direction mejor = Direction.SOUTH;
        int masAire = -1;
        for (Direction d : Direction.Type.HORIZONTAL) {
            int aire = aireHacia(mundo, proyector, d);
            if (aire > masAire) {
                masAire = aire;
                mejor = d;
            }
        }
        // El chunk estaba cargado, asi que esto es una medida de verdad.
        MEDIDO.put(proyector.toImmutable(), mejor);
        return mejor;
    }

    /**
     * Cuantos huecos libres hay en esa direccion.
     *
     * <p>&#9888; Se miran <b>dos alturas</b>, la del proyector y la de debajo:
     * un nicho suele tener un banco o un pedestal delante, asi que a una sola
     * altura el lado bueno podria contar menos aire que un lado tapiado con una
     * losa. Sumar las dos hace falsa una coincidencia y no dos.
     */
    private static int aireHacia(World mundo, BlockPos desde, Direction d) {
        int n = 0;
        for (int i = 1; i <= ALCANCE; i++) {
            BlockPos p = desde.offset(d, i);
            if (mundo.getBlockState(p).isAir()) {
                n++;
            } else {
                break;
            }
        }
        for (int i = 1; i <= ALCANCE; i++) {
            BlockPos p = desde.offset(d, i).down();
            if (mundo.getBlockState(p).isAir()) {
                n++;
            } else {
                break;
            }
        }
        return n;
    }

    /**
     * El indice horizontal (0-3) con el que la direccion viaja en el paquete.
     *
     * <p>&#9888;&#9888; VIAJA EL INDICE Y NO EL {@code ordinal()} DEL ENUM: los
     * seis valores de {@code Direction} incluyen arriba y abajo, y su orden es
     * cosa de Minecraft. Es la leccion del ENUM de MariaDB y del {@code escalon}
     * de los rangos -- un numero que significa una posicion en una lista ajena
     * cambia de significado el dia que la lista cambie, sin dar ningun error.
     */
    public static int indice(Direction d) {
        return d.getHorizontal();
    }

    /** Del indice del paquete a la direccion. */
    public static Direction porIndice(int i) {
        return Direction.fromHorizontal(((i % 4) + 4) % 4);
    }
}
