package net.pokereport.luna.client.pokepad;

import net.minecraft.client.gui.DrawContext;

/**
 * Iconos dibujados con código, sin texturas.
 *
 * <h2>Por qué esto no es arte</h2>
 *
 * Una lupa, un embudo, un «más» y tres rayas son <b>formas geométricas</b>: se
 * dibujan con rectángulos y salen mejor así que generadas, porque a 24 píxeles
 * una ilustración se convierte en una mancha. El PokePad ya tomó esta decisión
 * con las celdas de la rejilla, que también se dibujan por código.
 *
 * <p>La ventaja de verdad no es ahorrarse el arte: es que <b>escalan</b>. El Pad
 * se dibuja a un tamaño distinto según el GUI Scale y la ventana, y un icono
 * calculado sale nítido en todos; uno de 24×24 se ve borroso en cuanto crece.
 *
 * <h2>⚠ Lo único que hay es {@code fill}</h2>
 *
 * {@code DrawContext} solo sabe pintar rectángulos, así que las curvas se hacen
 * por <b>franjas horizontales</b> —una fila de píxeles cada vez— y las diagonales
 * avanzando por el eje largo. Es la misma técnica de siempre, y con estos
 * tamaños no se nota que no hay antialiasing.
 *
 * <p>⚠ Todas las medidas de aquí son <b>píxeles de pantalla ya escalados</b>, no
 * unidades de arte: quien llama ya ha aplicado el factor.
 */
public final class Iconos {

    private Iconos() {}

    /** Un disco. Se pinta por franjas, que es lo único que permite `fill`. */
    public static void disco(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            if (dx > 0) {
                ctx.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, color);
            }
        }
    }

    /**
     * Un aro.
     *
     * <p>⚠ Se dibuja como dos discos —uno del color y otro del fondo dentro— y
     * NO restando: pintar el hueco de un color concreto obliga a saber qué hay
     * debajo, y aquí debajo hay un botón que cambia de color al pasar el ratón.
     * Por eso se calculan las dos circunferencias por franja.
     */
    public static void aro(DrawContext ctx, int cx, int cy, int r, int grosor,
                           int color) {
        int interior = Math.max(0, r - grosor);
        for (int dy = -r; dy <= r; dy++) {
            int fuera = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            int dentro = Math.abs(dy) <= interior
                    ? (int) Math.sqrt(Math.max(0, interior * interior - dy * dy))
                    : 0;
            if (fuera <= 0) {
                continue;
            }
            if (dentro <= 0) {
                ctx.fill(cx - fuera, cy + dy, cx + fuera, cy + dy + 1, color);
            } else {
                ctx.fill(cx - fuera, cy + dy, cx - dentro, cy + dy + 1, color);
                ctx.fill(cx + dentro, cy + dy, cx + fuera, cy + dy + 1, color);
            }
        }
    }

    /** Una línea de cualquier ángulo, avanzando por el eje más largo. */
    public static void linea(DrawContext ctx, int x1, int y1, int x2, int y2,
                             int grosor, int color) {
        int dx = x2 - x1, dy = y2 - y1;
        int pasos = Math.max(Math.abs(dx), Math.abs(dy));
        if (pasos == 0) {
            ctx.fill(x1, y1, x1 + grosor, y1 + grosor, color);
            return;
        }
        for (int i = 0; i <= pasos; i++) {
            int x = x1 + dx * i / pasos;
            int y = y1 + dy * i / pasos;
            ctx.fill(x, y, x + grosor, y + grosor, color);
        }
    }

    /** Un triángulo relleno, apuntando abajo. Para el embudo y las flechas. */
    public static void trianguloAbajo(DrawContext ctx, int cx, int cy, int ancho,
                                      int alto, int color) {
        for (int i = 0; i < alto; i++) {
            int semi = ancho / 2 - (ancho / 2) * i / Math.max(1, alto);
            if (semi > 0) {
                ctx.fill(cx - semi, cy + i, cx + semi, cy + i + 1, color);
            }
        }
    }

    // ---- los iconos --------------------------------------------------------
    //
    // ⚠⚠ TODOS SE DIBUJAN CON TRAZO GRUESO, `lado/6` COMO MINIMO.
    //
    //    Antes iban a `lado/10`: dos pixeles de arte. A esa escala, y con el
    //    filtrado lineal que hace falta para el escalado por medios pasos, una
    //    linea de dos pixeles se convierte en una mancha gris. Un icono se lee
    //    por su SILUETA, y una silueta necesita masa.
    //
    // ⚠ Y por eso las formas son MAS SIMPLES que antes, no mas detalladas: a 28
    //   pixeles el detalle no se ve, solo ensucia.

    private static int trazo(int lado) {
        return Math.max(2, lado / 6);
    }

    /** LUPA: un aro grueso y el mango en diagonal. */
    public static void lupa(DrawContext ctx, int cx, int cy, int lado, int color) {
        int g = trazo(lado);
        int r = lado * 2 / 5;
        int ox = cx - lado / 8, oy = cy - lado / 8;
        aro(ctx, ox, oy, r, g, color);
        // El mango sale del borde del aro, no del centro: si sale del centro
        // cruza el cristal y parece una señal de prohibido.
        int d = (int) (r * 0.71);
        linea(ctx, ox + d, oy + d, cx + lado / 2, cy + lado / 2, g + 1, color);
    }

    /** EMBUDO: los filtros. Se entiende sin saber leer. */
    public static void embudo(DrawContext ctx, int cx, int cy, int lado, int color) {
        int g = trazo(lado);
        int mitad = lado / 2;
        int alto = lado * 2 / 5;
        // El cono, por franjas: ancho arriba y estrecho abajo.
        for (int i = 0; i < alto; i++) {
            int w = mitad - i * mitad / Math.max(1, alto) * 3 / 4;
            ctx.fill(cx - w, cy - mitad + i, cx + w, cy - mitad + i + 1, color);
        }
        // El tallo.
        ctx.fill(cx - g / 2, cy - mitad + alto, cx + (g + 1) / 2, cy + mitad, color);
    }

    /** MÁS: publicar algo nuevo. */
    public static void mas(DrawContext ctx, int cx, int cy, int lado, int color) {
        int b = lado / 2;
        int g = trazo(lado) + 1;
        ctx.fill(cx - b, cy - g / 2, cx + b, cy + (g + 1) / 2, color);
        ctx.fill(cx - g / 2, cy - b, cx + (g + 1) / 2, cy + b, color);
    }

    /**
     * LISTA: lo tuyo publicado.
     *
     * <p>⚠ Punto + renglón, no tres rayas sueltas. Tres rayas iguales son un
     * icono de menú; con el punto delante se lee como una lista.
     */
    public static void lista(DrawContext ctx, int cx, int cy, int lado, int color) {
        int b = lado / 2;
        int g = trazo(lado);
        int paso = lado / 3;
        for (int i = -1; i <= 1; i++) {
            int y = cy + i * paso - g / 2;
            ctx.fill(cx - b, y, cx - b + g, y + g, color);
            ctx.fill(cx - b + g + Math.max(2, g / 2), y, cx + b, y + g, color);
        }
    }

    /**
     * FLECHA CIRCULAR: refrescar.
     *
     * <p>⚠ El hueco va ARRIBA A LA DERECHA y la punta justo al lado. Con el
     * hueco en otro sitio, el ojo ve un círculo roto en vez de una flecha.
     */
    public static void refrescar(DrawContext ctx, int cx, int cy, int lado, int color) {
        int g = trazo(lado);
        int r = lado * 2 / 5;
        for (int dy = -r; dy <= r; dy++) {
            int fuera = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            int dentro = (int) Math.sqrt(Math.max(0, (r - g) * (r - g) - dy * dy));
            for (int dx = -fuera; dx <= fuera; dx++) {
                if (Math.abs(dx) < dentro) {
                    continue;
                }
                if (dx > 0 && dy < 0 && dy > -r + g) {
                    continue;   // el hueco
                }
                ctx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
            }
        }
        // La punta, un triángulo macizo en el hueco.
        int p = g + lado / 8;
        for (int i = 0; i < p; i++) {
            ctx.fill(cx + r - p + i, cy - r + g / 2 - (p - i),
                     cx + r - p + i + 1, cy - r + g / 2 + (p - i), color);
        }
    }

    /**
     * ETIQUETA DE PRECIO: las ofertas.
     *
     * <p>⚠ Rombo con agujero, no un rectángulo con una esquina cortada: el
     * agujero es lo que lo convierte en etiqueta y no en una nota.
     */
    public static void etiqueta(DrawContext ctx, int cx, int cy, int lado, int color) {
        int b = lado * 2 / 5;
        // Cuerpo: un cuadrado girado 45º, dibujado por franjas.
        for (int dy = -b; dy <= b; dy++) {
            int w = b - Math.abs(dy);
            ctx.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
        // El agujero, arriba a la izquierda del eje.
        int r = Math.max(2, lado / 8);
        disco(ctx, cx - b / 3, cy - b / 3, r, 0x00000000);
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            ctx.fill(cx - b / 3 - w, cy - b / 3 + dy,
                     cx - b / 3 + w + 1, cy - b / 3 + dy + 1, 0x00000000);
        }
    }

    /** POKÉ BALL. */
    public static void pokeball(DrawContext ctx, int cx, int cy, int lado) {
        int r = lado / 2;
        int g = Math.max(2, lado / 8);
        disco(ctx, cx, cy, r, 0xFFF2F2F2);
        for (int dy = -r; dy < 0; dy++) {
            int w = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            ctx.fill(cx - w, cy + dy, cx + w, cy + dy + 1, 0xFFD3403A);
        }
        ctx.fill(cx - r, cy - g / 2, cx + r, cy + (g + 1) / 2, 0xFF16181D);
        disco(ctx, cx, cy, g + 2, 0xFF16181D);
        disco(ctx, cx, cy, g, 0xFFF2F2F2);
    }

    /** CAJA: los objetos. */
    public static void caja(DrawContext ctx, int cx, int cy, int lado, int color) {
        int b = lado / 2;
        int g = trazo(lado);
        ctx.fill(cx - b, cy - b, cx + b, cy - b + g, color);
        ctx.fill(cx - b, cy + b - g, cx + b, cy + b, color);
        ctx.fill(cx - b, cy - b, cx - b + g, cy + b, color);
        ctx.fill(cx + b - g, cy - b, cx + b, cy + b, color);
        // La cinta, que es lo que la distingue de un cuadrado.
        ctx.fill(cx - g / 2, cy - b, cx + (g + 1) / 2, cy + b, color);
    }

    /**
     * UNA ESTRELLA DE CINCO PUNTAS, dibujada con código.
     *
     * <p>⚠ Se dibuja por FILAS, no con polígonos: `DrawContext.fill` solo sabe
     * hacer rectángulos. Para cada altura se calcula qué tramo horizontal
     * pertenece a la estrella y se pinta de una.
     *
     * <p>La forma sale de los cinco vértices de un pentagrama; el relleno usa
     * la regla par-impar, que es lo que le da el hueco central característico
     * — sin ella sale un pentágono con picos, que no se lee como estrella.
     */
    public static void estrella(DrawContext ctx, int cx, int cy, int lado, int color) {
        int r = Math.max(2, lado / 2);
        double[] xs = new double[10];
        double[] ys = new double[10];
        for (int i = 0; i < 10; i++) {
            double ang = Math.toRadians(-90 + i * 36);
            double radio = (i % 2 == 0) ? r : r * 0.42;
            xs[i] = cx + Math.cos(ang) * radio;
            ys[i] = cy + Math.sin(ang) * radio;
        }
        for (int y = cy - r; y <= cy + r; y++) {
            double py = y + 0.5;
            var cortes = new java.util.ArrayList<Double>();
            for (int i = 0; i < 10; i++) {
                int j = (i + 1) % 10;
                double y1 = ys[i], y2 = ys[j];
                if ((y1 <= py && y2 > py) || (y2 <= py && y1 > py)) {
                    double t = (py - y1) / (y2 - y1);
                    cortes.add(xs[i] + t * (xs[j] - xs[i]));
                }
            }
            java.util.Collections.sort(cortes);
            for (int i = 0; i + 1 < cortes.size(); i += 2) {
                int a = (int) Math.round(cortes.get(i));
                int b = (int) Math.round(cortes.get(i + 1));
                if (b > a) {
                    ctx.fill(a, y, b, y + 1, color);
                }
            }
        }
    }
}
