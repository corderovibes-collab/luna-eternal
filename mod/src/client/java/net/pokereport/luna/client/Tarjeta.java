package net.pokereport.luna.client;

import net.minecraft.client.gui.DrawContext;

/**
 * La tarjeta del Pad, dibujada por código.
 *
 * <p>Antes se estiraba un PNG de 128×128. Estirar una textura con esquinas
 * redondeadas a una tarjeta alta y estrecha las deforma en óvalos, y se veía.
 * Dibujarla con rectángulos sale <b>nítida a cualquier tamaño</b>, no pesa en
 * el pack y permite cambiar el color por estado sin generar tres imágenes.
 *
 * <p>No hay primitiva de rectángulo redondeado en {@code DrawContext}, así que
 * se compone: un cuerpo, dos rectángulos que recortan las esquinas y un borde.
 * Es lo mismo que hace la interfaz de Minecraft por dentro.
 */
public final class Tarjeta {

    private Tarjeta() {}

    /** Esquina en píxeles. 4 basta para que se lea como redondeada. */
    private static final int R = 4;

    public static void dibujar(DrawContext ctx, int x, int y, int w, int h,
                               int arriba, int abajo, int borde) {
        // Cuerpo con degradado, recortado en las esquinas por los laterales.
        ctx.fillGradient(x + R, y, x + w - R, y + h, arriba, abajo);
        ctx.fillGradient(x, y + R, x + R, y + h - R, arriba, abajo);
        ctx.fillGradient(x + w - R, y + R, x + w, y + h - R, arriba, abajo);

        // Borde: cuatro líneas, sin tocar las esquinas.
        ctx.fill(x + R, y, x + w - R, y + 1, borde);
        ctx.fill(x + R, y + h - 1, x + w - R, y + h, borde);
        ctx.fill(x, y + R, x + 1, y + h - R, borde);
        ctx.fill(x + w - 1, y + R, x + w, y + h - R, borde);

        // Un píxel en diagonal en cada esquina: barato y suficiente.
        for (int i = 0; i < R; i++) {
            int d = R - i;
            ctx.fill(x + d, y + i, x + d + 1, y + i + 1, borde);
            ctx.fill(x + w - d - 1, y + i, x + w - d, y + i + 1, borde);
            ctx.fill(x + d, y + h - i - 1, x + d + 1, y + h - i, borde);
            ctx.fill(x + w - d - 1, y + h - i - 1, x + w - d, y + h - i, borde);
        }

        // Brillo interior arriba: es lo que la hace parecer una carta con
        // cuerpo y no un rectángulo pintado.
        ctx.fill(x + R, y + 1, x + w - R, y + 2, 0x60FFFFFF);
    }

    /**
     * Estrellas de rareza. Una sola línea de texto sería más simple, pero a
     * este tamaño el jugador cuenta estrellas de un vistazo y no lee números.
     */
    public static void estrellas(DrawContext ctx,
                                 net.minecraft.client.font.TextRenderer fuente,
                                 int cx, int y, int nivel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) sb.append(i < nivel ? "★" : "☆");
        ctx.drawCenteredTextWithShadow(fuente,
            net.minecraft.text.Text.literal(sb.toString()), cx, y, 0xFFFFC83C);
    }
}
