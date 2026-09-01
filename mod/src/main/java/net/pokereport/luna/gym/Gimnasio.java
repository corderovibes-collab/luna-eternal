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
     * @param sala       a qué sala de la dimensión pertenece: da su X
     * @param bit        QUÉ BIT DE LA MÁSCARA ES SU MEDALLA. Ver {@link #bitMedalla}
     * @param medallas   cuántas medallas hay que traer para entrar
     * @param nivel      A QUÉ NIVEL SE PELEA. Ver {@link #NIVELES} arriba
     * @param pericia    lo lista que juega su IA, 0..5. Ver {@link #PERICIA}
     * @param lider      cómo se llama. Es un nombre propio: igual en los dos
     *                   idiomas, así que no pasa por el fichero de textos
     * @param medalla    cómo se llama su medalla
     * @param insignia   el nombre COMPLETO de su textura, sin `.png`
     * @param propia     ¿la textura es NUESTRA en vez del mod de medallas?
     */
    public record Gimnasio_(String id, String entrenador, int sala, int bit,
                            int medallas, int nivel, int pericia,
                            String lider, String medalla, String insignia,
                            boolean propia) {

        /**
         * LA TEXTURA DE SU MEDALLA.
         *
         * <h2>⚠⚠⚠ ESTO EXISTE PARA QUE HAYA UNA SOLA LISTA</h2>
         *
         * El PokePad dibuja las medallas y el diálogo del gimnasio también, y
         * cada pantalla tenía <b>su propio array</b> de nombres en orden de
         * gimnasio. Tres listas que nada obligaba a mantener sincronizadas.
         *
         * <p>Si se desordenaran, ganar a Brock encendería la medalla de Misty
         * <b>sin dar ningún error</b>. Ahora las pantallas leen de aquí y el
         * problema no puede existir, que es mejor que una comprobación.
         *
         * <p>⚠ Las de Kanto y Johto se <b>referencian</b> al mod de medallas,
         * que va instalado en el cliente: cuesta cero bytes en nuestro jar y no
         * redistribuye nada suyo.
         *
         * <p>⚠⚠ LAS DE LA LIGA NARANJA SON NUESTRAS, y por un motivo medido: se
         * abrió {@code CobbleverseBadges-1.3.jar} y tiene <b>45 texturas</b>,
         * las de Kanto, Johto, Hoenn y Sinnoh. <b>Del Equipo Naranja no hay
         * ninguna</b> — ni ahí ni en el datapack de entrenadores, donde de 156
         * no aparece ni Cissy, ni Danny, ni Rudy, ni Luana (el único Drake que
         * hay es el Alto Mando de Hoenn).
         *
         * <p>⚠ Y el nombre va <b>completo</b>, sin añadirle {@code _badge}
         * detrás: los trofeos de campeón se llaman {@code kanto_league_trophy}
         * y no {@code kanto_league_trophy_badge}. Pegar el sufijo a mano daba
         * una textura que no existe, que se ve como el cuadro morado.
         */
        public net.minecraft.util.Identifier textura() {
            return propia
                ? net.minecraft.util.Identifier.of("lunaeternal",
                        "textures/gui/medallas/" + insignia + ".png")
                : net.minecraft.util.Identifier.of("cobbleversebadges",
                        "textures/item/" + insignia + ".png");
        }

        /**
         * CUÁNTO MIDE SU TEXTURA, en píxeles.
         *
         * <h2>⚠⚠ NO ES SIEMPRE 16, Y ESTABA ESCRITO A MANO EN CUATRO SITIOS</h2>
         *
         * Las dieciocho de Kanto y Johto vienen del mod de medallas y son de
         * 16×16. Las cinco de la Liga Naranja las genero el usuario con IA y
         * <b>son de 64×64</b>.
         *
         * <p>⚠ Se probo reducirlas a 16 para que todas midieran lo mismo, y se
         * descarto <b>mirando el resultado</b>: a 16 px la estrella de jade
         * pierde las puntas, el trofeo pierde las asas y la concha pierde su
         * estrella de mar. El original se ve mejor, y Minecraft dibuja una
         * textura de 64 sin ningun problema.
         *
         * <p>⚠⚠⚠ Y EL NUMERO EQUIVOCADO NO DA ERROR: `drawTexture` usa este
         * valor para saber que trozo del PNG coger, asi que pedirle 16 a una
         * textura de 64 <b>dibuja la esquina de arriba a la izquierda</b>
         * estirada. Se ve como una medalla borrosa y cortada, no como un fallo.
         * Por eso vive aqui, junto a la ruta, y no repetido en cada pantalla.
         */
        public int lado() {
            return propia ? 64 : 16;
        }

        /** ¿Es un campeón de región? Los trofeos se dibujan un poco mayores. */
        public boolean campeon() {
            return insignia.endsWith("_trophy") || insignia.endsWith("_trofeo");
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
     * LOS VEINTITRÉS, en el orden en que se juegan.
     *
     * <h2>⚠⚠⚠ EL BIT ES UNA COLUMNA, NO LA POSICIÓN EN ESTA LISTA</h2>
     *
     * Hasta el 2026-08-30 el bit de una medalla era {@code sala()}, o sea la
     * posición. Y entonces entró el Equipo Naranja <b>en mitad de la lista</b>,
     * entre Giovanni y el Campeón de Kanto.
     *
     * <p>Con el bit posicional eso habría corrido cinco puestos todo lo de
     * abajo: quien tuviera la medalla del Campeón de Kanto se habría despertado
     * con la de Cissy, y quien tuviera la de Débora con otra cualquiera.
     * <b>Sin un solo error</b>, porque la máscara es un número y un número
     * siempre se puede leer.
     *
     * <p>Es exactamente la lección del ENUM de MariaDB —que guarda el índice, y
     * reordenar convierte a unos jugadores en otros— y la del {@code escalon}
     * de los rangos, que es un número explícito por lo mismo. Aquí, tercera vez.
     *
     * <p><b>Un bit, una vez, para siempre.</b> Un gimnasio nuevo coge el
     * siguiente libre y no toca los demás.
     *
     * <h2>⚠⚠ LOS IDENTIFICADORES DE ENTRENADOR NO SE INVENTAN</h2>
     *
     * Salen del datapack instalado ({@code COBBLEVERSE-RCT-DP-v20.zip}), leídos
     * uno a uno. Uno mal escrito <b>no da error al arrancar</b>: da un gimnasio
     * en el que no aparece nadie, y eso se descubre con el jugador dentro. Ya
     * pasó: {@code kanto_lt_surge} no existe — es {@code kanto_ltsurge}, sin
     * guion. Lo caza el autotest, que se lo pregunta a rctmod.
     *
     * <p>⚠⚠⚠ Y LOS DE JOHTO TIENEN NOMBRE ITALIANO EN EL DATAPACK. No hay
     * ningún {@code johto_falkner}. Se identificaron <b>por su equipo</b> y se
     * confirmaron por el nivel, que sube en orden de gimnasio:
     *
     * <pre>
     *   valerio    volador  → Pegaso     angelo   fantasma → Morti
     *   raffaello  bicho    → Antón      furio    lucha    → Aníbal
     *   chiara     normal   → Blanca     jasmine  acero    → Yasmina
     *   alfredo    hielo    → Fredo      sandra   dragón   → Débora
     * </pre>
     *
     * <p>⚠ Los nombres visibles son los <b>españoles</b>, que es como los llamó
     * el usuario. El identificador de rctmod sigue siendo el italiano: son cosas
     * distintas y mezclarlas es lo que da ocho gimnasios vacíos.
     *
     * <h2>⚠⚠ EL EQUIPO NARANJA NO EXISTE EN NINGÚN DATO, Y VA IGUAL</h2>
     *
     * Ni entrenadores ni medallas: comprobado abriendo los dos ficheros. Se
     * declaran aquí para que la pantalla enseñe hacia dónde va la progresión,
     * y {@link #construido} impide entrar a una sala que no existe.
     *
     * <p>⚠ Sus cinco identificadores {@code naranja_*} son <b>nuestros</b> y
     * todavía no los sirve nadie. El autotest los excluye de la comprobación
     * contra rctmod a propósito, y lo dice: si los diera por buenos, el día que
     * se construya la sala aparecería vacía.
     *
     * <h2>Los niveles, decisión del usuario (2026-08-30)</h2>
     *
     * A ese nivel se pelea, y <b>los dos lados</b>: ver {@code Adaptador}. El
     * Campeón de Kanto pasó de 60 a 63 para dejar sitio a la Liga Naranja, que
     * en el anime va justo después de los ocho gimnasios de Kanto.
     */
    public static final List<Gimnasio_> TODOS = Arrays.asList(
        //             id          entrenador             sala bit  med niv per
        new Gimnasio_("brock",     "kanto_brock",           0,  0,   0,  15, 3,
                      "Brock",     "Roca",       "kanto_boulder_badge",  false),
        new Gimnasio_("misty",     "kanto_misty",           1,  1,   1,  19, 3,
                      "Misty",     "Cascada",    "kanto_cascade_badge",  false),
        // ⚠ `kanto_ltsurge`, SIN guion. `kanto_lt_surge` no existe.
        new Gimnasio_("surge",     "kanto_ltsurge",         2,  2,   2,  24, 3,
                      "Teniente Surge", "Trueno", "kanto_thunder_badge", false),
        new Gimnasio_("erika",     "kanto_erika",           3,  3,   3,  28, 4,
                      "Erika",     "Arcoíris",   "kanto_rainbow_badge",  false),
        new Gimnasio_("koga",      "kanto_koga",            4,  4,   4,  33, 4,
                      "Koga",      "Alma",       "kanto_soul_badge",     false),
        new Gimnasio_("sabrina",   "kanto_sabrina",         5,  5,   5,  37, 4,
                      "Sabrina",   "Pantano",    "kanto_marsh_badge",    false),
        new Gimnasio_("blaine",    "kanto_blaine",          6,  6,   6,  42, 4,
                      "Blaine",    "Volcán",     "kanto_volcano_badge",  false),
        new Gimnasio_("giovanni",  "kanto_giovanni",        7,  7,   7,  46, 5,
                      "Giovanni",  "Tierra",     "kanto_earth_badge",    false),

        // ---- LIGA NARANJA. Ni entrenador ni medalla existen todavía. -------
        new Gimnasio_("cissy",     "naranja_cissy",         8,  8,   8,  49, 4,
                      "Cissy",     "Ojo de Coral",  "naranja_ojo_coral",  true),
        new Gimnasio_("danny",     "naranja_danny",         9,  9,   9,  52, 4,
                      "Danny",     "Rubí Marino",   "naranja_rubi_marino", true),
        new Gimnasio_("rudy",      "naranja_rudy",         10, 10,  10,  55, 4,
                      "Rudy",      "Caracol",       "naranja_caracol",    true),
        new Gimnasio_("luana",     "naranja_luana",        11, 11,  11,  58, 5,
                      "Luana",     "Estrella de Jade", "naranja_jade",    true),
        new Gimnasio_("drake",     "naranja_drake",        12, 12,  12,  62, 5,
                      "Drake",     "Liga Naranja",  "naranja_trofeo",     true),

        new Gimnasio_("campeon_kanto", "kanto_champion_blue", 13, 13, 13, 63, 5,
                      "Blue",      "Campeón de Kanto", "kanto_league_trophy", false),

        new Gimnasio_("pegaso",    "johto_valerio",        14, 14,  14,  64, 4,
                      "Pegaso",    "Céfiro",     "johto_zephyr_badge",   false),
        new Gimnasio_("anton",     "johto_raffaello",      15, 15,  15,  68, 4,
                      "Antón",     "Colmena",    "johto_hive_badge",     false),
        new Gimnasio_("blanca",    "johto_chiara",         16, 16,  16,  71, 4,
                      "Blanca",    "Llanura",    "johto_plain_badge",    false),
        new Gimnasio_("morti",     "johto_angelo",         17, 17,  17,  75, 4,
                      "Morti",     "Niebla",     "johto_fog_badge",      false),
        new Gimnasio_("anibal",    "johto_furio",          18, 18,  18,  79, 5,
                      "Aníbal",    "Tormenta",   "johto_storm_badge",    false),
        new Gimnasio_("yasmina",   "johto_jasmine",        19, 19,  19,  82, 5,
                      "Yasmina",   "Mineral",    "johto_mineral_badge",  false),
        new Gimnasio_("fredo",     "johto_alfredo",        20, 20,  20,  86, 5,
                      "Fredo",     "Glaciar",    "johto_glacier_badge",  false),
        new Gimnasio_("debora",    "johto_sandra",         21, 21,  21,  90, 5,
                      "Débora",    "Alzamiento", "johto_rising_badge",   false),

        new Gimnasio_("campeon_johto", "johto_champion_lance", 22, 22, 22, 100, 5,
                      "Lance",     "Campeón de Johto", "johto_league_trophy", false));

    /** El prefijo de los entrenadores que todavía no sirve nadie. */
    public static final String PREFIJO_PROPIO = "naranja_";

    /** ¿Su entrenador lo sirve rctmod, o es uno nuestro que aún no existe? */
    public static boolean entrenadorDeRct(Gimnasio_ g) {
        return !g.entrenador().startsWith(PREFIJO_PROPIO);
    }

    /**
     * Las veintitrés insignias, en orden de BIT.
     *
     * <p>⚠⚠ El indice <b>es</b> el bit de la mascara, porque el bit es
     * {@code sala()} y las salas van 0..15 en este mismo orden. Es lo que
     * dibuja el PokePad.
     *
     * <p>⚠ Antes habia una segunda lista suelta para Johto, que es justo lo que
     * este fichero avisa de no hacer: dos listas que nada obliga a coincidir.
     * Hoy sale de {@link #TODOS}.
     */
    public static List<String> insignias() {
        // ⚠⚠ ORDENADAS POR BIT, no por posición en la lista. Son lo mismo hoy
        //    y no tienen por qué serlo mañana: quien dibuja lee el bit i-ésimo
        //    de la máscara y espera la insignia i-ésima de aquí.
        var orden = new java.util.ArrayList<>(TODOS);
        orden.sort(java.util.Comparator.comparingInt(Gimnasio_::bit));
        var salida = new java.util.ArrayList<String>(orden.size());
        for (Gimnasio_ g : orden) {
            salida.add(g.insignia());
        }
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
     * <p>⚠⚠ El Pokémon de Brock es <b>Onix</b> desde el 2026-08-30, por orden
     * del usuario. Antes era Geodude, y el motivo por el que lo era sigue siendo
     * verdad y hay que dejarlo escrito: entre Brock y el sitio del Pokémon hay
     * <b>2,66 bloques</b>, y un Onix mide casi nueve de largo. <b>Va a ocupar
     * mucho más sitio que Geodude</b>, y si en la sala no cabe, lo que se mueve
     * es esta coordenada — no hace falta recompilar nada más.
     *
     * <p>⚠ Los dos son de su equipo real, leído del datapack instalado:
     * Geodude 16, Bonsly 16, Cranidos 18, <b>Onix 20</b>. Cambiar de Pokémon es
     * cambiar esta palabra.
     */
    private static final java.util.Map<String, Recepcion> RECEPCIONES =
        java.util.Map.of(
            "brock", new Recepcion(-137.95, 69, 49.37, 0f,
                                   -137.544, 69, 52, "onix"));

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

    /**
     * ¿ESTA CONSTRUIDO SU GIMNASIO?
     *
     * <p>Lo dice {@link #ENTRADAS}: si nadie ha medido dónde aparece el jugador,
     * es que la sala no existe. <b>No hay una bandera aparte</b>, y es a
     * propósito — una bandera es un segundo sitio que decir la verdad, y el día
     * que se contradiga con las coordenadas, el jugador entra a una sala vacía.
     *
     * <p>⚠ Los ocho de Johto están declarados y no construidos. Se enseñan como
     * «próximamente» en vez de esconderse: saber lo que viene es lo que hace que
     * alguien vaya a por la siguiente medalla.
     */
    public static boolean construido(Gimnasio_ g) {
        return ENTRADAS.containsKey(g.id());
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
        return 1 << g.bit();
    }

    /**
     * Las medallas ORDENADAS POR BIT, que es como se dibujan.
     *
     * <p>⚠ Hace falta porque {@link #insignias} da solo el nombre de la textura
     * y las pantallas necesitan también saber si es propia y si es un trofeo.
     */
    public static List<Gimnasio_> porBit() {
        var orden = new java.util.ArrayList<>(TODOS);
        orden.sort(java.util.Comparator.comparingInt(Gimnasio_::bit));
        return orden;
    }
}
