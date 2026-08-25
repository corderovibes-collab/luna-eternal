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

    /** LUPA: un aro y el mango en diagonal. */
    public static void lupa(DrawContext ctx, int cx, int cy, int lado, int color) {
        int r = lado * 7 / 20;
        int g = Math.max(1, lado / 10);
        aro(ctx, cx - lado / 10, cy - lado / 10, r, g, color);
        linea(ctx, cx + r / 2, cy + r / 2, cx + lado / 3, cy + lado / 3,
                Math.max(2, g + 1), color);
    }

    /**
     * EMBUDO: el filtro.
     *
     * <p>⚠ Un embudo y no unas rayas con flechas: la forma de embudo se entiende
     * sin saber leer, que es de lo que va un icono.
     */
    public static void embudo(DrawContext ctx, int cx, int cy, int lado, int color) {
        int ancho = lado * 3 / 4;
        int alto = lado / 2;
        trianguloAbajo(ctx, cx, cy - lado / 3, ancho, alto, color);
        int g = Math.max(2, lado / 8);
        ctx.fill(cx - g / 2, cy - lado / 3 + alto, cx + g / 2, cy + lado / 3, color);
    }

    /** MÁS: dos barras. Publicar algo nuevo. */
    public static void mas(DrawContext ctx, int cx, int cy, int lado, int color) {
        int b = lado / 2;
        int g = Math.max(2, lado / 6);
        ctx.fill(cx - b, cy - g / 2, cx + b, cy + g / 2 + g % 2, color);
        ctx.fill(cx - g / 2, cy - b, cx + g / 2 + g % 2, cy + b, color);
    }

    /** LISTA: tres renglones. Lo tuyo publicado. */
    public static void lista(DrawContext ctx, int cx, int cy, int lado, int color) {
        int b = lado / 2;
        int g = Math.max(2, lado / 8);
        for (int i = -1; i <= 1; i++) {
            int y = cy + i * (lado / 3) - g / 2;
            ctx.fill(cx - b, y, cx - b + g, y + g, color);
            ctx.fill(cx - b + g * 2, y, cx + b, y + g, color);
        }
    }

    /** FLECHA CIRCULAR: refrescar. */
    public static void refrescar(DrawContext ctx, int cx, int cy, int lado, int color) {
        int r = lado * 2 / 5;
        int g = Math.max(2, lado / 8);
        // El aro abierto por arriba a la derecha: se salta las franjas de ese
        // cuadrante para que se vea que es una flecha y no un círculo.
        for (int dy = -r; dy <= r; dy++) {
            int fuera = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            int interior = Math.max(0, r - g);
            int dentro = Math.abs(dy) <= interior
                    ? (int) Math.sqrt(Math.max(0, interior * interior - dy * dy))
                    : 0;
            if (fuera <= 0) {
                continue;
            }
            boolean arriba = dy < -r / 3;
            if (dentro <= 0) {
                ctx.fill(cx - fuera, cy + dy, cx + fuera, cy + dy + 1, color);
            } else {
                ctx.fill(cx - fuera, cy + dy, cx - dentro, cy + dy + 1, color);
                if (!arriba) {
                    ctx.fill(cx + dentro, cy + dy, cx + fuera, cy + dy + 1, color);
                }
            }
        }
        // La punta de la flecha, arriba a la derecha.
        int px = cx + r - g / 2, py = cy - r + g;
        for (int i = 0; i < g * 2; i++) {
            ctx.fill(px - i, py - i, px + i, py - i + 1, color);
        }
    }

    /**
     * POKÉ BALL: alternar al mercado de Pokémon.
     *
     * <p>⚠ Los colores van fijos —roja arriba, blanca abajo— y no siguen al
     * botón. Una Poké Ball gris no se reconoce, y el trabajo de un icono es
     * reconocerse antes de leerse.
     */
    public static void pokeball(DrawContext ctx, int cx, int cy, int lado) {
        int r = lado * 2 / 5;
        int negro = 0xFF1A1A1A;
        disco(ctx, cx, cy, r, 0xFFF0F0F0);
        // La mitad de arriba, roja. Se recorta por franjas para que el borde
        // siga la curva en vez de cortarse en recto.
        for (int dy = -r; dy < 0; dy++) {
            int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            if (dx > 0) {
                ctx.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, 0xFFE03A2F);
            }
        }
        int g = Math.max(2, lado / 9);
        ctx.fill(cx - r, cy - g / 2, cx + r, cy + g / 2 + g % 2, negro);
        aro(ctx, cx, cy, r, Math.max(1, lado / 14), negro);
        disco(ctx, cx, cy, Math.max(2, lado / 6), negro);
        disco(ctx, cx, cy, Math.max(1, lado / 10), 0xFFF0F0F0);
    }

    /** CAJA: alternar al mercado de objetos. */
    public static void caja(DrawContext ctx, int cx, int cy, int lado, int color) {
        int b = lado * 2 / 5;
        int g = Math.max(2, lado / 10);
        // El contorno.
        ctx.fill(cx - b, cy - b, cx + b, cy - b + g, color);
        ctx.fill(cx - b, cy + b - g, cx + b, cy + b, color);
        ctx.fill(cx - b, cy - b, cx - b + g, cy + b, color);
        ctx.fill(cx + b - g, cy - b, cx + b, cy + b, color);
        // La cinta, que es lo que la hace una caja y no un cuadrado.
        ctx.fill(cx - b, cy - g / 2, cx + b, cy + g / 2 + g % 2, color);
        ctx.fill(cx - g / 2, cy - b, cx + g / 2 + g % 2, cy + b, color);
    }
}
