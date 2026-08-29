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
     * <p>⚠ El nombre del líder y el de la medalla van <b>en el registro</b> y no
     * en dos listas paralelas. Una lista al lado es una lista que se queda vieja:
     * bastaría con insertar un gimnasio en medio para que Brock diera la medalla
     * de Misty, y eso no da ningún error.
     *
     * @param id         identificador estable. Da la clave de traducción
     * @param entrenador el id del entrenador en rctmod, tal cual
     * @param sala       a qué sala de la dimensión pertenece. <b>Y también qué
     *                   bit de medalla es</b>: ver {@link #bitMedalla}
     * @param medallas   cuántas medallas hay que traer para entrar
     * @param lider      cómo se llama. Es un nombre propio: igual en los dos
     *                   idiomas, así que no pasa por el fichero de textos
     * @param medalla    cómo se llama su medalla
     * @param insignia   el nombre de su textura en el mod de medallas
     */
    public record Gimnasio_(String id, String entrenador, int sala, int medallas,
                            String lider, String medalla, String insignia) {

        /**
         * LA TEXTURA DE SU MEDALLA.
         *
         * <h2>⚠⚠⚠ ESTO EXISTE PARA QUE HAYA UNA SOLA LISTA</h2>
         *
         * El PokePad dibuja dieciséis medallas y el diálogo del gimnasio ocho, y
         * cada pantalla tenía <b>su propio array</b> de nombres en orden de
         * gimnasio. Tres listas —esta, la del Pad y la del diálogo— que nada
         * obligaba a mantener sincronizadas.
         *
         * <p>Si se desordenaran, ganar a Brock encendería la medalla de Misty
         * <b>sin dar ningún error</b>: el jugador vería una medalla que no ha
         * ganado y no vería la que sí. Ahora las pantallas leen de aquí y el
         * problema no puede existir, que es mejor que una comprobación que lo
         * detecte.
         *
         * <p>⚠ Se <b>referencia</b> y no se copia: el mod de medallas va
         * instalado en el cliente, así que apuntar su textura cuesta cero bytes
         * en nuestro jar y no redistribuye nada suyo.
         */
        public net.minecraft.util.Identifier textura() {
            return net.minecraft.util.Identifier.of("cobbleversebadges",
                    "textures/item/" + insignia + "_badge.png");
        }
    }

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
        new Gimnasio_("brock",    "kanto_brock",    0, 0,
                      "Brock",    "Roca",     "kanto_boulder"),
        new Gimnasio_("misty",    "kanto_misty",    1, 1,
                      "Misty",    "Cascada",  "kanto_cascade"),
        new Gimnasio_("surge",    "kanto_lt_surge", 2, 2,
                      "Teniente Surge", "Trueno", "kanto_thunder"),
        new Gimnasio_("erika",    "kanto_erika",    3, 3,
                      "Erika",    "Arcoíris", "kanto_rainbow"),
        new Gimnasio_("koga",     "kanto_koga",     4, 4,
                      "Koga",     "Alma",     "kanto_soul"),
        new Gimnasio_("sabrina",  "kanto_sabrina",  5, 5,
                      "Sabrina",  "Pantano",  "kanto_marsh"),
        new Gimnasio_("blaine",   "kanto_blaine",   6, 6,
                      "Blaine",   "Volcán",   "kanto_volcano"),
        new Gimnasio_("giovanni", "kanto_giovanni", 7, 7,
                      "Giovanni", "Tierra",   "kanto_earth"));

    /**
     * LAS OCHO DE JOHTO, que todavía no tienen gimnasio construido.
     *
     * <p>⚠ Están aquí y no en la pantalla por lo mismo que las de Kanto: para
     * que exista <b>una sola lista</b>. El día que se declare el gimnasio de
     * Falkner, su nombre sale de aquí y entra en {@link #TODOS}.
     */
    public static final List<String> INSIGNIAS_JOHTO = Arrays.asList(
        "johto_zephyr", "johto_hive", "johto_plain", "johto_fog",
        "johto_storm", "johto_mineral", "johto_glacier", "johto_rising");

    /**
     * Las dieciséis insignias en orden de medalla: Kanto y después Johto.
     *
     * <p>⚠⚠ El índice <b>es</b> el bit de la máscara, porque el de Kanto es
     * {@code sala()} y Johto va detrás. Es lo que dibuja el PokePad.
     */
    public static List<String> insignias() {
        var salida = new java.util.ArrayList<String>(16);
        for (Gimnasio_ g : TODOS) {
            salida.add(g.insignia());
        }
        salida.addAll(INSIGNIAS_JOHTO);
        return salida;
    }

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
     * La tarima del líder, medida en su maestro.
     *
     * <p>⚠ Brock: el usuario se puso encima y leyó (48, 72, 40.45) sobre un
     * origen en (0, 64, 0), o sea el desfase (48, 8, 40.45). <b>Ya no es una
     * suposición</b> — antes se usaba «24 bloques al fondo de la entrada», y un
     * líder dentro de una pared no da ningún error: aparece, y no se le ve.
     *
     * <p>⚠⚠ Y está SEIS BLOQUES POR DEBAJO de la entrada (14 → 8). No es un
     * error de medida: se entra por arriba y se combate abajo. Si algún día
     * alguien «corrige» uno de los dos para que cuadren, el jugador aparecerá
     * dentro del suelo.
     *
     * <p>⚠ El giro es 180: la entrada está al norte (z 17,26) y el líder al sur
     * (z 40,45), así que mirar a quien llega es mirar hacia −Z. Sin fijarlo,
     * Brock aparece mirando a donde mire el norte del mundo — que aquí es la
     * pared del fondo.
     */
    private static final java.util.Map<String, Punto> LIDERES = java.util.Map.of(
        "brock", new Punto(48.0, 8.0, 40.45, 180f));

    /**
     * DÓNDE ESPERA CADA LÍDER EN LA CIUDADELA, Y CON QUÉ POKÉMON AL LADO.
     *
     * <h2>⚠⚠ AQUÍ SÍ SON COORDENADAS ABSOLUTAS, Y ES LO CONTRARIO DE ARRIBA</h2>
     *
     * Dentro del gimnasio todo va por desfase porque hay <b>ocho copias</b> de
     * la misma sala y el número tiene que valer para las ocho. En la ciudadela
     * hay <b>una</b>: la sala de recepción se construye a mano, existe una sola
     * vez, y su Brock está en un sitio concreto del mundo. Guardarlo como
     * desfase de algo obligaría a inventar un origen que no existe.
     *
     * <p>⚠ Y por eso viven en el código y no en la base: son parte de la
     * ciudadela, que se construye a mano y cambia con ella. Igual que las siete
     * paradas del moto taxi.
     *
     * @param x,y,z    dónde está de pie el líder
     * @param giro     hacia dónde mira. Se puede sobrescribir desde el comando
     * @param px,py,pz dónde va su Pokémon
     * @param especie  qué Pokémon. <b>Decorativo y nada más</b>: no se le puede
     *                 pegar, ni capturar, ni retar, ni escanear
     */
    public record Recepcion(double x, double y, double z, float giro,
                            double px, double py, double pz, String especie) {
        public net.minecraft.util.math.Vec3d lider() {
            return new net.minecraft.util.math.Vec3d(x, y, z);
        }

        public net.minecraft.util.math.Vec3d pokemon() {
            return new net.minecraft.util.math.Vec3d(px, py, pz);
        }
    }

    /**
     * Las recepciones, medidas por el usuario dentro del juego.
     *
     * <p>⚠ El Pokémon de Brock es <b>Geodude</b>, que es el primero de su
     * equipo (Geodude 16, Bonsly 16, Cranidos 18, Onix 20). Leído del datapack
     * que ya está instalado, no de memoria. Y encaja con el hueco: entre Brock y
     * su sitio hay <b>2,66 bloques</b>, y un Onix mide casi nueve de largo — se
     * comería la sala entera. Cambiar de Pokémon es cambiar esta palabra.
     */
    private static final java.util.Map<String, Recepcion> RECEPCIONES =
        java.util.Map.of(
            "brock", new Recepcion(-137.95, 69, 49.37, 0f,
                                   -137.544, 69, 52, "geodude"));

    /** Dónde espera un líder en la ciudadela, o {@code null} si aún no tiene sitio. */
    public static Recepcion recepcion(Gimnasio_ g) {
        return RECEPCIONES.get(g.id());
    }

    /** Los gimnasios que ya tienen recepción construida. */
    public static java.util.List<Gimnasio_> conRecepcion() {
        var salida = new java.util.ArrayList<Gimnasio_>();
        for (Gimnasio_ g : TODOS) {
            if (RECEPCIONES.containsKey(g.id())) {
                salida.add(g);
            }
        }
        return salida;
    }

    /** ¿Está medida la tarima de su líder, o se va a usar el respaldo? */
    public static boolean tieneTarima(Gimnasio_ g) {
        return LIDERES.containsKey(g.id());
    }

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

    /** Hacia dónde mira el líder en su tarima. */
    public static float giroLider(Gimnasio_ g) {
        Punto p = LIDERES.get(g.id());
        return p == null ? 180f : p.giro();
    }

    /**
     * EL BIT DE SU MEDALLA.
     *
     * <h2>⚠⚠⚠ ES {@code sala()}, Y ESO ATA DOS LISTAS QUE VIVEN SEPARADAS</h2>
     *
     * El PokePad dibuja dieciséis medallas en orden de gimnasio —boulder,
     * cascade, thunder…— y enciende el bit <i>i</i> en la casilla <i>i</i>. Aquí
     * el bit de un gimnasio es su número de sala. <b>Las dos listas están en
     * ficheros distintos</b> (esta y {@code PokePadScreen.MEDALLAS}) y nada las
     * obliga a coincidir.
     *
     * <p>Si dejaran de hacerlo, ganar a Brock encendería la medalla de Misty
     * <b>sin dar ningún error</b>: el jugador vería una medalla que no ha ganado
     * y no vería la que sí. Por eso hay una comprobación en el autotest que
     * compara las dos, y por eso el orden de {@link #TODOS} <b>es</b> el orden de
     * las medallas y no una casualidad.
     */
    public static int bitMedalla(Gimnasio_ g) {
        return 1 << g.sala();
    }
}
