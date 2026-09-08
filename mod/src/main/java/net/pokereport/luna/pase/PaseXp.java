package net.pokereport.luna.pase;

/**
 * DE QUE SE SACA XP DEL PASE, Y CUANTA.
 *
 * <h2>⚠⚠⚠ ESTO NO SE ENGANCHA A NINGUN EVENTO, Y ES DELIBERADO</h2>
 *
 * Habia dos formas de recoger la XP del pase: suscribirse otra vez a los
 * eventos de Cobblemon y de Minecraft, o colgarse de los embudos que ya
 * existen. Se ha hecho lo segundo, y el motivo esta escrito en el javadoc de
 * {@code OficiosListener.anotar}: <i>«si las misiones se avanzaran desde otro
 * listener, el dia que alguien cambie de que evento cuelga la pesca lo
 * cambiaria en uno solo, y el otro se quedaria mirando un evento que ya no
 * ocurre — sin dar ningun error»</i>.
 *
 * <p>Con dos suscripciones separadas, el pase y los oficios pueden dejar de
 * estar de acuerdo sobre que cuenta como pescar. Colgados del mismo sitio, no
 * pueden.
 *
 * <h2>Las cifras, y de donde salen</h2>
 *
 * Salen de <b>cuantas veces por hora ocurre cada cosa</b>. El objetivo es que
 * ninguna actividad llene sola el tope diario (1.200) en menos de dos horas, y
 * que jugar variado sea lo mas rapido.
 *
 * <pre>
 *   accion            XP   veces/hora   XP/hora si SOLO haces eso
 *   ─────────────────────────────────────────────────────────────
 *   mena comun         1     ~400            400
 *   mena rara         12      ~15            180   (las dos juntas: 580)
 *   pescar             8      ~70            560
 *   cosechar           3     ~200            600
 *   capturar          12      ~25            300
 *   ganar combate      6      ~30            180
 *   eclosionar        40       ~5            200
 *   torre (ronda 20)  50      ~20          1.000  <- lo mas rapido, y aun asi
 *                                                    es UNA HORA de combates
 * </pre>
 *
 * <h2>⚠⚠⚠ LA MENA VALIA 4 Y ERA DEMASIADO, Y LA TABLA MENTIA</h2>
 *
 * Esta misma tabla decia <i>«mena comun 4, ~90 veces/hora, 360 XP/hora»</i>, y
 * <b>las 90 veces/hora estaban mal</b>: un minero dedicado saca del orden de
 * <b>400 menas por hora</b> —el carbon y el cobre salen en vetas de veinte y
 * treinta bloques, no de una en una—, asi que lo de verdad eran <b>1.600 XP a
 * la hora</b>: el tope diario entero <b>en cuarenta y cinco minutos</b>, y todo
 * lo demas de esta tabla convertido en adorno.
 *
 * <p>⚠⚠ <b>Es la MISMA regla que ya justificaba que la piedra valga cero</b>
 * —«son 2.500 bloques a la hora, asi que el pase se convertiria en un
 * temporizador»— solo que aplicada un escalon mas arriba y con el numero
 * equivocado. La regla estaba escrita y aun asi fallo, porque <b>la estimacion
 * de cuantas veces por hora pasa algo vivia en un comentario</b>, y un
 * comentario no se comprueba.
 *
 * <p>⚠ <b>Lo destapo el usuario jugando</b>, no una revision ni el autotest.
 * Por eso las tasas de arriba bajan a constantes ({@link #VECES_HORA_MENA} y
 * compañia) y el autotest cruza XP x tasa contra el tope: cambiar la mena a 4
 * otra vez ya no compila en silencio, se pone rojo.
 *
 * <h2>⚠ Por que 1 por mena y no «5 cada 10 menas»</h2>
 *
 * Las dos ideas eran del usuario y las dos arreglan el problema. «5 cada 10»
 * es <b>media XP por mena</b>, o sea la mitad otra vez — y para pagar por
 * tandas hay que <b>recordar cuantas lleva rotas entre pago y pago</b>: una
 * columna nueva, su migracion, y una escritura por bloque picado. Ademas se ve
 * raro desde dentro: rompes nueve menas y no pasa nada.
 *
 * <p>A cambio de todo eso, lo unico que se gana es dividir por dos otra vez. Si
 * hace falta bajar mas, la palanca sigue siendo <b>este numero</b>.
 *
 * <h2>⚠⚠ LA PIEDRA VALE CERO, Y NO ES UN OLVIDO</h2>
 *
 * El oficio MINERO paga 1 XP por bloque de piedra porque <i>«cavar un tunel
 * tambien es minar»</i>. Para el pase eso no sirve: son 2.500 bloques a la
 * hora, asi que a 1 XP el pase se convertiria en un temporizador y todo lo
 * demas de esta tabla daria igual. Lo que cuenta para el pase son las MENAS,
 * que es lo que hace que minar sea minar y no cavar.
 *
 * <h2>⚠ Y el escaneo tampoco da XP: la da el REGISTRO</h2>
 *
 * Escanear se puede repetir sobre el mismo Pokemon; registrar una especie
 * ocurre <b>una vez en toda la partida</b> y hay 251. Es la misma decision que
 * ya tomaron la pesca (cuenta el recogido, no el lanzamiento) y la cria
 * (cuenta el nacimiento, no el huevo).
 */
public final class PaseXp {

    private PaseXp() {
    }

    // ---- Cobblemon ---------------------------------------------------------

    /** Capturar un Pokemon cualquiera. */
    public static final long CAPTURA = 12;

    /**
     * Capturar una especie que <b>no estaba en la Pokedex</b>.
     *
     * <p>Es lo que el usuario llamo «descubrir en la Pokedex». Diez veces una
     * captura normal porque ocurre 251 veces en toda la vida del jugador, no
     * veinticinco veces por hora.
     */
    public static final long ESPECIE_NUEVA = 120;

    /** Que nazca un huevo. Cuenta al ECLOSIONAR, no al recogerlo. */
    public static final long ECLOSION = 40;

    /** Recoger la caña Poke con algo dentro. */
    public static final long PESCA = 8;

    /** Ganar un combate, sea salvaje o contra un entrenador. */
    public static final long COMBATE = 6;

    // ---- mundo -------------------------------------------------------------

    /**
     * Una mena corriente: carbon, hierro, cobre, redstone, lapis, oro.
     *
     * <p>⚠⚠ <b>Era 4 y se bajo a 1 el 2026-09-08</b>, por orden del usuario y
     * con razon: ver el aviso de arriba. Es la mena que sale <b>en vetas</b>,
     * o sea la que se cuenta por cientos.
     */
    public static final long MENA = 1;

    /**
     * Diamante o esmeralda.
     *
     * <p>⚠⚠ <b>Esta NO se toca, y es la mitad de la decision.</b> Lo que
     * estaba roto era el <b>volumen</b>, y un diamante no tiene volumen: no
     * sale en vetas de treinta, no se farmea a la hora y aparece del orden de
     * <b>quince veces</b> en una sesion larga de mina. Bajarla tambien habria
     * arreglado un problema que no tenia y habria dejado el hallazgo que de
     * verdad importa pagando lo mismo que picar cobre.
     *
     * <p>Hoy un diamante vale <b>doce menas corrientes</b> —y por rareza real
     * se queda corto— o lo mismo que capturar un Pokemon.
     */
    public static final long MENA_RARA = 12;

    /** Piedra. Ver el aviso de arriba: <b>cero a proposito</b>. */
    public static final long PIEDRA = 0;

    // ---- cada cuanto pasa cada cosa ---------------------------------------
    //
    // ⚠⚠⚠ ESTO ESTABA EN UN COMENTARIO Y POR ESO PUDO MENTIR UNA SEMANA. Son
    //    ESTIMACIONES del mundo, no decisiones nuestras, y por eso van
    //    separadas de la XP: la XP es la palanca, la tasa es el terreno. El
    //    autotest multiplica una por otra y la compara con el tope diario.
    //
    // ⚠⚠ Y LA COMPROBACION NO VALE MAS QUE LA ESTIMACION. La vieja decia 90
    //    menas/hora y era falsa; lo que la corrigio fue alguien PICANDO. Si
    //    algun dia se mide de verdad, se cambia AQUI y el resto se recoloca.

    /** Menas corrientes que saca a la hora un minero dedicado. */
    public static final int VECES_HORA_MENA = 400;

    /** Diamantes o esmeraldas en esa misma hora. */
    public static final int VECES_HORA_MENA_RARA = 15;

    /** Cosechar un cultivo, una baya o una bellota. */
    public static final long COSECHA = 3;

    // ---- hitos -------------------------------------------------------------
    //
    // ⚠ Los hitos pagan mucho y NO desequilibran nada, por el tope diario: 300
    //   de una medalla es un tercio del dia, no un salto de nivel gratis. Sin
    //   tope habria que rebajarlos; con tope, lo que hacen es que un dia en el
    //   que consigues algo importante llene el tope sin tener que farmear.

    /** Ganar una medalla de gimnasio. Hay dieciseis en toda la Liga. */
    public static final long MEDALLA = 300;

    /** Completar una mision del arbol. Hay veintiocho. */
    public static final long MISION = 50;

    /** Subir un nivel de Via o de oficio. */
    public static final long NIVEL_VIA = 100;

    // ---- Torre de Batalla --------------------------------------------------

    /** Lo que da la ronda 1. */
    private static final long TORRE_BASE = 10;

    /** Cuanto sube por ronda. */
    private static final long TORRE_PASO = 2;

    /**
     * Techo por ronda. Se alcanza en la ronda 55.
     *
     * <p>⚠ Sin techo, la ronda 100 pagaria 210 por un combate de tres minutos.
     * El tope diario lo taparia igual, pero el numero en pantalla enseñaria que
     * la Torre es la unica fuente que importa — y lo que el usuario pidio es
     * que <b>no este chetado</b>, no que este chetado y contenido.
     */
    private static final long TORRE_TECHO = 120;

    /**
     * XP por superar una ronda de la Torre.
     *
     * <p>Crece con la ronda porque la ronda crece en dificultad. Y crece
     * <b>despacio</b>: una tanda completa hasta la ronda 20 son 620 XP en unos
     * cuarenta minutos, que es del mismo orden que pescar ese rato.
     *
     * <p>⚠ Repetir rondas bajas no es rentable: cada intento empieza en la 1,
     * asi que quien se quede en la ronda 3 cobra 46 por tanda. Llegar lejos
     * paga mas que reiniciar, que es exactamente lo que la Torre quiere premiar.
     */
    public static long torre(int ronda) {
        if (ronda <= 0) {
            return 0;
        }
        return Math.min(TORRE_TECHO, TORRE_BASE + TORRE_PASO * ronda);
    }
}
