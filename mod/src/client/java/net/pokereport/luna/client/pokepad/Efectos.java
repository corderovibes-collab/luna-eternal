package net.pokereport.luna.client.pokepad;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.DrawContext;

/**
 * ANIMACIONES Y EFECTOS PARA LAS PANTALLAS DEL POKEPAD.
 *
 * <h2>&#9888;&#9888; LO UNICO QUE HAY ES {@code fill}</h2>
 *
 * {@code DrawContext} sabe pintar rectangulos y poco mas, asi que <b>todo</b> lo
 * de aqui se construye con rectangulos: los degradados son franjas, los halos
 * son marcos concentricos con menos alfa cada uno, y las particulas son
 * cuadraditos. Es la misma tecnica que ya usa {@link Iconos} para los discos.
 *
 * <p>&#9888; Y {@code fill} <b>MEZCLA</b>: un color con alfa se compone sobre lo
 * que hay debajo. Eso es lo que hace posible el halo y el brillo &mdash; y a la
 * vez es la razon por la que rellenar algo con alfa 0 no borra nada (mezclar con
 * transparente es no hacer nada), que ya mordio en el icono del Miraidon.
 *
 * <h2>&#9888;&#9888;&#9888; EL RELOJ ES {@code System.currentTimeMillis()}, NUNCA
 * EL DEL MUNDO</h2>
 *
 * La ciudadela tiene la hora <b>congelada</b> ({@code fixed_time 18000}, noche
 * permanente), asi que cualquier animacion colgada de {@code world.getTime()} se
 * queda clavada: o repitiendo el mismo fotograma o sin avanzar nunca, segun el
 * resto. Es la leccion que ya pago {@code Auras}, y aqui vale igual.
 *
 * <h2>&#9888; EL COSTE SE CUENTA EN {@code fill} POR FOTOGRAMA</h2>
 *
 * Cada uno es un cuadrilatero. Un halo son 6, un brillo 24 y una particula 1, y
 * eso hay que tenerlo en la cabeza al usarlos: dibujar un brillo por tarjeta en
 * una rejilla de veinte serian 480 y se nota. Los numeros de aqui estan elegidos
 * para que <b>solo lo que hay que mirar</b> se anime.
 */
public final class Efectos {

    private Efectos() {
    }

    // =======================================================================
    // CURVAS
    // =======================================================================

    /**
     * Entrada con frenada (easeOutCubic). De 0 a 1, deprisa y frenando.
     *
     * <p>&#9888; Una animacion LINEAL se ve mecanica: empieza y para de golpe.
     * Frenar al final es lo que hace que parezca que algo pesa.
     */
    public static double suave(double t) {
        double x = Math.max(0, Math.min(1, t));
        return 1 - Math.pow(1 - x, 3);
    }

    /**
     * Entrada con rebote (easeOutBack): se pasa un poco y vuelve.
     *
     * <p>Para lo que aparece de golpe &mdash;un premio recien cobrado&mdash;.
     * Puede devolver mas de 1, que es justo la gracia.
     */
    public static double rebote(double t) {
        double x = Math.max(0, Math.min(1, t));
        double c = 1.70158;
        return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
    }

    /** Va y vuelve entre 0 y 1 con ese periodo en milisegundos. */
    public static float pulso(int periodoMs) {
        double f = (System.currentTimeMillis() % Math.max(1, periodoMs))
                / (double) Math.max(1, periodoMs);
        return (float) ((Math.sin(f * Math.PI * 2) + 1) / 2);
    }

    /** Avanza de 0 a 1 y vuelve a empezar. Para los barridos. */
    public static float rampa(int periodoMs) {
        return (float) ((System.currentTimeMillis() % Math.max(1, periodoMs))
                / (double) Math.max(1, periodoMs));
    }

    // =======================================================================
    // COLOR
    // =======================================================================

    public static int mezclar(int a, int b, float t) {
        float u = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 0xFF) * (1 - u) + ((b >> 16) & 0xFF) * u);
        int g = (int) (((a >> 8) & 0xFF) * (1 - u) + ((b >> 8) & 0xFF) * u);
        int z = (int) ((a & 0xFF) * (1 - u) + (b & 0xFF) * u);
        int al = (int) (((a >>> 24) & 0xFF) * (1 - u) + ((b >>> 24) & 0xFF) * u);
        return (al << 24) | (r << 16) | (g << 8) | z;
    }

    /** El mismo color con otro alfa (0..1). */
    public static int conAlfa(int color, float alfa) {
        int a = (int) (Math.max(0, Math.min(1, alfa)) * 255);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    // =======================================================================
    // FORMAS
    // =======================================================================

    /**
     * Un HALO alrededor de una caja: marcos concentricos que se apagan.
     *
     * <p>&#9888; De fuera hacia dentro y con el alfa al cuadrado, no lineal. Con
     * alfa lineal el halo se ve como <b>escalones</b> --se distinguen los marcos
     * uno a uno-- y parece un fallo de dibujado; al cuadrado el borde exterior
     * casi no se ve y la transicion se lee continua.
     */
    public static void halo(DrawContext ctx, int x, int y, int w, int h,
                            int capas, int color, float fuerza) {
        for (int i = capas; i >= 1; i--) {
            float t = i / (float) capas;
            float alfa = fuerza * (1 - t) * (1 - t);
            if (alfa <= 0.01f) {
                continue;
            }
            int c = conAlfa(color, alfa);
            ctx.fill(x - i, y - i, x + w + i, y - i + 1, c);
            ctx.fill(x - i, y + h + i - 1, x + w + i, y + h + i, c);
            ctx.fill(x - i, y - i, x - i + 1, y + h + i, c);
            ctx.fill(x + w + i - 1, y - i, x + w + i, y + h + i, c);
        }
    }

    /**
     * Un BRILLO que barre la caja de izquierda a derecha.
     *
     * <p>Es lo que en cualquier juego dice «esto se puede coger». Se dibuja
     * como franjas verticales con el alfa en campana, asi que el ojo lo lee como
     * una banda de luz aunque sean veinticuatro rectangulos.
     *
     * @param fase de 0 a 1: donde esta la banda
     */
    public static void brillo(DrawContext ctx, int x, int y, int w, int h,
                              float fase, int color, float fuerza) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int franjas = 24;
        int ancho = Math.max(1, w / franjas);
        // La banda entra por la izquierda y sale por la derecha, asi que el
        // recorrido es mas ancho que la caja.
        float centro = fase * (w + w * 0.6f) - w * 0.3f;
        float radio = w * 0.16f;
        for (int i = 0; i < franjas; i++) {
            int fx = x + i * ancho;
            float d = Math.abs((i * ancho + ancho / 2f) - centro);
            if (d > radio) {
                continue;
            }
            float alfa = fuerza * (1 - d / radio) * (1 - d / radio);
            ctx.fill(fx, y, Math.min(x + w, fx + ancho), y + h,
                    conAlfa(color, alfa));
        }
    }

    /**
     * Un marco cuyo color RECORRE la caja, como una luz dando la vuelta.
     *
     * <p>&#9888; Se dibuja por tramos y no como cuatro lados de un color: lo que
     * lo hace parecer que gira es que cada tramo tiene su propia fase.
     */
    public static void marcoVivo(DrawContext ctx, int x, int y, int w, int h,
                                 int grosor, int base, int luz, float fase) {
        int tramos = 28;
        int perimetro = 2 * (w + h);
        for (int i = 0; i < tramos; i++) {
            float t = i / (float) tramos;
            // Distancia a la luz, dando la vuelta por el borde.
            float d = Math.abs(t - fase);
            d = Math.min(d, 1 - d);
            int color = mezclar(base, luz, (float) Math.pow(Math.max(0, 1 - d * 4), 2));
            int desde = (int) (t * perimetro);
            int hasta = (int) ((i + 1) / (float) tramos * perimetro);
            pintarBorde(ctx, x, y, w, h, grosor, desde, hasta, color);
        }
    }

    /** Pinta el tramo del perimetro entre dos distancias. */
    private static void pintarBorde(DrawContext ctx, int x, int y, int w, int h,
                                    int g, int desde, int hasta, int color) {
        for (int lado = 0; lado < 4; lado++) {
            int ini = switch (lado) {
                case 0 -> 0;
                case 1 -> w;
                case 2 -> w + h;
                default -> 2 * w + h;
            };
            int largo = (lado % 2 == 0) ? w : h;
            int a = Math.max(desde, ini);
            int b = Math.min(hasta, ini + largo);
            if (b <= a) {
                continue;
            }
            int p = a - ini;
            int q = b - ini;
            switch (lado) {
                case 0 -> ctx.fill(x + p, y, x + q, y + g, color);
                case 1 -> ctx.fill(x + w - g, y + p, x + w, y + q, color);
                case 2 -> ctx.fill(x + w - q, y + h - g, x + w - p, y + h, color);
                default -> ctx.fill(x, y + h - q, x + g, y + h - p, color);
            }
        }
    }

    /**
     * Un ARCO de progreso, por segmentos.
     *
     * <p>&#9888; Por segmentos y no pixel a pixel: un anillo de radio 60 tiene
     * ~11.000 pixeles dentro de su caja, y eso serian 11.000 {@code fill} por
     * fotograma. Con 160 segmentos que se solapan sale igual de continuo y
     * cuesta 160.
     */
    public static void arco(DrawContext ctx, int cx, int cy, int r, int grosor,
                            double fraccion, int color) {
        double f = Math.max(0, Math.min(1, fraccion));
        if (f <= 0) {
            return;
        }
        int total = 160;
        int pasos = (int) (total * f);
        int radio = r - grosor / 2;
        for (int i = 0; i <= pasos; i++) {
            double a = -Math.PI / 2 + (i / (double) total) * Math.PI * 2;
            int x = cx + (int) Math.round(Math.cos(a) * radio);
            int y = cy + (int) Math.round(Math.sin(a) * radio);
            ctx.fill(x - grosor / 2, y - grosor / 2,
                     x + (grosor + 1) / 2, y + (grosor + 1) / 2, color);
        }
    }

    /** Una barra con relleno y, opcionalmente, un brillo que la recorre. */
    public static void barra(DrawContext ctx, int x, int y, int w, int h,
                             double fraccion, int fondo, int color, boolean viva) {
        ctx.fill(x, y, x + w, y + h, fondo);
        int lleno = (int) Math.round(Math.max(0, Math.min(1, fraccion)) * w);
        if (lleno <= 0) {
            return;
        }
        ctx.fill(x, y, x + lleno, y + h, color);
        if (viva && lleno > h * 2) {
            brillo(ctx, x, y, lleno, h, rampa(2600), 0xFFFFFFFF, 0.45f);
        }
    }

    /**
     * Una estrella de cuatro puntas, de las de «esto brilla».
     *
     * <p>Dos rombos cruzados. A este tamaño una de cinco puntas se ve como una
     * mancha; esta se lee incluso a seis pixeles.
     */
    public static void destello(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int i = 0; i < r; i++) {
            int semi = Math.max(1, (r - i) / 3);
            ctx.fill(cx - semi, cy - r + i, cx + semi, cy - r + i + 1, color);
            ctx.fill(cx - semi, cy + r - i - 1, cx + semi, cy + r - i, color);
            ctx.fill(cx - r + i, cy - semi, cx - r + i + 1, cy + semi, color);
            ctx.fill(cx + r - i - 1, cy - semi, cx + r - i, cy + semi, color);
        }
    }

    // =======================================================================
    // PARTICULAS
    // =======================================================================

    /**
     * UN PUÑADO DE CHISPAS. Nacen, suben, se apagan y desaparecen.
     *
     * <h2>&#9888;&#9888; VIVEN EN LA PANTALLA Y NO EN UNA ESTATICA</h2>
     *
     * Una lista estatica compartida acumularia chispas de pantallas ya cerradas
     * y las dibujaria encima de la siguiente &mdash; y peor, crecerian sin
     * limite durante toda la partida. Cada pantalla tiene las suyas y se van con
     * ella.
     *
     * <p>&#9888; Y estan ACOTADAS a {@link #TOPE}: un emisor sin tope es una
     * fuga de memoria con forma de efecto bonito.
     */
    public static final class Chispas {

        /** Cuantas puede haber a la vez. Pasado esto se descartan las nuevas. */
        private static final int TOPE = 120;

        private static final class Chispa {
            double x, y, vx, vy;
            long nace;
            int vida, color, lado;
        }

        private final List<Chispa> vivas = new ArrayList<>();

        /** Suelta {@code cuantas} chispas desde ese punto. */
        public void soltar(int x, int y, int cuantas, int color, double fuerza) {
            var azar = java.util.concurrent.ThreadLocalRandom.current();
            for (int i = 0; i < cuantas && vivas.size() < TOPE; i++) {
                Chispa c = new Chispa();
                double ang = azar.nextDouble() * Math.PI * 2;
                double v = fuerza * (0.4 + azar.nextDouble() * 0.9);
                c.x = x;
                c.y = y;
                c.vx = Math.cos(ang) * v;
                // Hacia arriba: el ojo espera que una chispa suba antes de caer.
                c.vy = Math.sin(ang) * v - fuerza * 0.5;
                c.nace = System.currentTimeMillis();
                c.vida = 700 + azar.nextInt(600);
                c.color = color;
                c.lado = 2 + azar.nextInt(3);
                vivas.add(c);
            }
        }

        /**
         * Dibuja y envejece. Devuelve cuantas quedan.
         *
         * <p>&#9888; El movimiento va por TIEMPO TRANSCURRIDO y no por
         * fotograma: con 30 fps las chispas irian a la mitad de velocidad que
         * con 60, y el efecto duraria el doble en un ordenador lento.
         */
        public int dibujar(DrawContext ctx) {
            long ahora = System.currentTimeMillis();
            vivas.removeIf(c -> ahora - c.nace > c.vida);
            for (Chispa c : vivas) {
                double t = (ahora - c.nace) / 1000.0;
                int px = (int) (c.x + c.vx * t * 60);
                // Gravedad: sube y cae.
                int py = (int) (c.y + c.vy * t * 60 + 90 * t * t);
                float alfa = 1 - (ahora - c.nace) / (float) c.vida;
                ctx.fill(px, py, px + c.lado, py + c.lado,
                        conAlfa(c.color, alfa * alfa));
            }
            return vivas.size();
        }

        public void limpiar() {
            vivas.clear();
        }
    }
}
