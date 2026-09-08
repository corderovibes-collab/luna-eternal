package net.pokereport.luna.pase;

/**
 * LA CURVA DEL PASE: cuanta XP cuesta cada nivel, y que nivel da una XP.
 *
 * <p>Todo lo de aqui es <b>funcion pura</b>: no toca la base, no toca el
 * servidor y no depende de nada. Por eso el autotest puede comprobar la curva
 * entera sin un jugador conectado, y por eso cambiar la calibracion es cambiar
 * <b>dos numeros</b> y no repasar el codigo.
 *
 * <h2>⚠⚠⚠ EL NUMERO QUE DE VERDAD IMPORTA NO ES LA CURVA: ES EL TOPE DIARIO</h2>
 *
 * La curva dice cuanto cuesta el pase entero. El <b>tope diario</b> dice en
 * cuantos dias se puede completar como muy pronto, y esa es la propiedad que el
 * usuario pidio: <i>«que no consigan todo rapido»</i>.
 *
 * <pre>
 *   coste(n)     = BASE + PASO * n        XP para pasar del nivel n al n+1
 *   total(100)   = 53.700 XP
 *   tope diario  = 1.200 XP
 *   ────────────────────────────────────────────────────────────────────
 *   53.700 / 1.200 = 44,75  ->  MINIMO 45 DIAS NATURALES
 * </pre>
 *
 * <p>Y ese minimo <b>no depende de cuanto juegue nadie</b>. Por muchas horas
 * que se echen, por muchas fuentes de XP que se añadan mañana y por muy
 * generosa que sea la Torre en la ronda 100, la XP acumulada en D dias
 * naturales nunca puede pasar de {@code 900 * D} — el descanso acumulado
 * reparte el tope, no lo crea. La temporada dura 60 dias, asi que quedan 16
 * dias de holgura para quien no juegue a diario.
 *
 * <p>&#9888;&#9888; Y CON EL PASE DE PAGO ESO IMPORTA MAS, NO MENOS: quien ha
 * pagado espera poder terminarlo, asi que la temporada tiene que dar de sobra
 * para los 45 dias. Pero <b>tampoco puede acabarse en una semana</b>, que es lo
 * que el usuario pidio -- si no, el pase deja de ser una temporada y pasa a ser
 * una compra que se agota el primer fin de semana.
 *
 * <p><b>Esto es lo que hace segura a cualquier fuente nueva.</b> Sin el tope,
 * cada vez que se añadiera una forma de ganar XP habria que recalcular el pase
 * entero; con el, la pregunta es solo si esa fuente es la mas comoda del dia.
 *
 * <h2>Por que 100 niveles y una curva creciente</h2>
 *
 * Cien porque lo pidio el usuario, y porque con una sola via de pago cada nivel
 * tiene que llevar algo: cincuenta premios en una temporada de dos meses se
 * quedan cortos para lo que cuesta el pase.
 *
 * <p>Lineal creciente ({@code 240 + 6n}) y no exponencial: con una exponencial
 * los ultimos niveles se vuelven inalcanzables y el pase se abandona a la
 * mitad, que es lo contrario de lo que hace un pase. Aqui el ultimo nivel
 * cuesta 834 y el primero 240 — tres veces y media, no cien.
 *
 * <p>&#9888; Y el PASO baja de 20 a 6 al doblar los niveles: con 20 el nivel
 * 100 costaria 2.220 y el pase entero 129.000 XP, o sea <b>107 dias</b> — mas
 * que dos temporadas.
 */
public final class PaseNivel {

    private PaseNivel() {
    }

    /** Niveles del pase. El 0 es «recien empezado» y no da nada. */
    public static final int MAX = 100;

    /** Lo que cuesta el primer nivel. */
    public static final long BASE = 240;

    /** Cuanto sube el coste por cada nivel. */
    public static final long PASO = 6;

    /**
     * Tope de XP del pase que se puede ganar en un dia.
     *
     * <p>⚠ Es del PASE y solo del pase: pasado el tope se siguen ganando Plata,
     * XP de oficio, misiones y todo lo demas. Lo unico que se para es la barra
     * del pase, y la pantalla lo dice con todas las letras.
     */
    public static final int TOPE_DIARIO = 1_200;

    /**
     * Cuantos dias de tope se pueden acumular sin jugar.
     *
     * <p>⚠⚠ EL DESCANSO NO ROMPE EL MINIMO DE 44 DIAS, y conviene ver por que:
     * lo que se acumula es el tope de dias que YA HAN PASADO. Tres dias sin
     * jugar dan 2.700 el cuarto, que es exactamente lo que se habria ganado
     * jugando los tres. Reparte, no regala.
     */
    public static final int DIAS_DESCANSO = 3;

    /** El tope maximo que puede tener un solo dia, con el descanso al maximo. */
    public static final int TOPE_MAXIMO = TOPE_DIARIO * DIAS_DESCANSO;

    /** XP para pasar del nivel {@code n} al {@code n + 1}. */
    public static long coste(int n) {
        if (n < 0 || n >= MAX) {
            return 0;
        }
        return BASE + PASO * n;
    }

    /**
     * XP total acumulada necesaria para <b>estar</b> en ese nivel.
     *
     * <p>Es la suma cerrada de {@link #coste}, no un bucle: se llama al dibujar
     * la barra sesenta veces por segundo.
     */
    public static long acumulada(int nivel) {
        int n = Math.max(0, Math.min(MAX, nivel));
        // sum_{i=0}^{n-1} (BASE + PASO*i) = BASE*n + PASO*n*(n-1)/2
        return BASE * n + PASO * n * (n - 1) / 2;
    }

    /** La XP que cuesta el pase entero. */
    public static long total() {
        return acumulada(MAX);
    }

    /**
     * El nivel que corresponde a esa XP total.
     *
     * <p>⚠ Se recorre en vez de despejar la ecuacion de segundo grado. Son
     * cincuenta vueltas como mucho, y una raiz cuadrada en coma flotante puede
     * devolver 11,999999 justo en el borde de un nivel: el jugador veria el
     * premio y no lo podria cobrar, una vez de cada muchas y sin traza.
     */
    public static int nivelDe(long xp) {
        int nivel = 0;
        while (nivel < MAX && xp >= acumulada(nivel + 1)) {
            nivel++;
        }
        return nivel;
    }

    /** XP dentro del nivel actual (lo que llena la barra). */
    public static long enNivel(long xp) {
        return Math.max(0, xp - acumulada(nivelDe(xp)));
    }

    /** XP que falta para el siguiente nivel. {@code 0} si esta al maximo. */
    public static long faltaPara(long xp) {
        int nivel = nivelDe(xp);
        if (nivel >= MAX) {
            return 0;
        }
        return acumulada(nivel + 1) - xp;
    }

    /**
     * El minimo de dias naturales en los que se puede completar el pase.
     *
     * <p>Existe como metodo <b>para que el autotest lo compruebe</b>. Es la
     * propiedad de diseño del sistema entero, y si alguien toca la curva o el
     * tope sin mirar, esto es lo que se pone en rojo.
     */
    public static int diasMinimos() {
        return (int) Math.ceil(total() / (double) TOPE_DIARIO);
    }
}
