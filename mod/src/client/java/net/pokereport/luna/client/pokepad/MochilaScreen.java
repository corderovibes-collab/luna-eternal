package net.pokereport.luna.client.pokepad;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.pokereport.luna.backpack.Mochila;
import net.pokereport.luna.backpack.MochilaHandler;

/**
 * LA MOCHILA.
 *
 * <h2>⚠⚠ ES LA ÚNICA PANTALLA DEL PROYECTO CON CONTENEDOR, Y NO ES UN CAPRICHO</h2>
 *
 * Todas las demás solo dibujan: el servidor manda datos y la pantalla los pinta.
 * Ésta tiene que dejar <b>arrastrar objetos</b>, y eso lo hace el propio
 * Minecraft dentro de un {@code ScreenHandler}. No hay forma de reimplementarlo
 * desde fuera: el arrastre, el mayúsculas-clic, el reparto con clic derecho y la
 * sincronización de la pila en la mano viven ahí dentro.
 *
 * <p>Lo que sí seguimos cumpliendo de P9-bis: <b>el dibujo es nuestro</b>. No se
 * usa la textura del cofre de Minecraft — el marco, los huecos y los candados se
 * pintan por código, como las celdas del PokePad.
 *
 * <h2>Los candados</h2>
 *
 * Se dibujan las <b>siete filas siempre</b> y se tachan las bloqueadas. Enseñar
 * solo las abiertas escondería que hay más — y entonces nadie sabría que subir
 * de rango da algo.
 */
public class MochilaScreen extends HandledScreen<MochilaHandler> {

    private static final int ANCHO = 176;

    private static final int FONDO = 0xFF2A2E38;
    private static final int BORDE = 0xFF15181F;
    private static final int MARCO_CLARO = 0xFF4A505F;
    private static final int HUECO = 0xFF1B1E26;
    private static final int HUECO_BORDE = 0xFF3A4050;
    private static final int CANDADO = 0xFFB03A2E;
    private static final int CANDADO_FONDO = 0xFF3A2020;
    private static final int TITULO = 0xFFFFC420;
    private static final int TEXTO_SUAVE = 0xFF9AA4BC;

    public MochilaScreen(MochilaHandler handler, PlayerInventory inv, Text titulo) {
        super(handler, inv, titulo);
        this.backgroundWidth = ANCHO;
        // 18 arriba + 7 filas + 13 de separación + 3 filas + 4 + barra + 7
        this.backgroundHeight = 18 + Mochila.FILAS_MAX * 18 + 13 + 3 * 18 + 4 + 18 + 7;
    }

    @Override
    protected void init() {
        super.init();
        // ⚠ El título va donde lo ponemos nosotros, no donde lo pone el cofre.
        this.titleY = 6;
        this.playerInventoryTitleY = backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        int x = this.x, y = this.y;
        ctx.fill(x - 1, y - 1, x + backgroundWidth + 1, y + backgroundHeight + 1, BORDE);
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, FONDO);
        ctx.fill(x, y, x + backgroundWidth, y + 1, MARCO_CLARO);
        ctx.fill(x, y, x + 1, y + backgroundHeight, MARCO_CLARO);

        // --- los huecos de la mochila
        for (int fila = 0; fila < Mochila.FILAS_MAX; fila++) {
            boolean abierta = fila < handler.filas();
            for (int col = 0; col < Mochila.COLUMNAS; col++) {
                int hx = x + 8 + col * 18, hy = y + 18 + fila * 18;
                dibujarHueco(ctx, hx, hy, abierta);
            }
        }
        // --- los del jugador
        int base = y + 18 + Mochila.FILAS_MAX * 18 + 13;
        for (int fila = 0; fila < 3; fila++) {
            for (int col = 0; col < 9; col++) {
                dibujarHueco(ctx, x + 8 + col * 18, base + fila * 18, true);
            }
        }
        for (int col = 0; col < 9; col++) {
            dibujarHueco(ctx, x + 8 + col * 18, base + 58, true);
        }
    }

    private void dibujarHueco(DrawContext ctx, int hx, int hy, boolean abierto) {
        ctx.fill(hx - 1, hy - 1, hx + 17, hy + 17, HUECO_BORDE);
        ctx.fill(hx, hy, hx + 16, hy + 16, abierto ? HUECO : CANDADO_FONDO);
        if (abierto) {
            return;
        }
        // ⚠ Una barra diagonal, no un candado dibujado: a 16 px un candado es
        //   una mancha. Lo que se lee de un vistazo es «esto está tachado».
        for (int i = 0; i < 16; i++) {
            ctx.fill(hx + i, hy + 15 - i, hx + i + 1, hy + 16 - i, CANDADO);
        }
        ctx.fill(hx, hy, hx + 16, hy + 1, CANDADO);
        ctx.fill(hx, hy + 15, hx + 16, hy + 16, CANDADO);
        ctx.fill(hx, hy, hx + 1, hy + 16, CANDADO);
        ctx.fill(hx + 15, hy, hx + 16, hy + 16, CANDADO);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mx, int my) {
        ctx.drawText(textRenderer, title, titleX, titleY, TITULO, false);
        ctx.drawText(textRenderer, playerInventoryTitle, titleX,
                playerInventoryTitleY, TEXTO_SUAVE, false);
    }

    /**
     * El cartel de «fila bloqueada».
     *
     * <p>Lo pidió el usuario con capturas: al pasar el ratón por un hueco
     * bloqueado, decir <b>qué rango hace falta</b>. Sin eso, un hueco tachado
     * solo dice «no» y no dice cómo.
     *
     * <p>⚠ Se dibuja en {@code render} y no en {@code drawForeground} porque
     * tiene que ir <b>encima de los objetos</b>, y el primer plano se pinta
     * antes que ellos.
     */
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);

        Slot slot = this.focusedSlot;
        if (slot instanceof MochilaHandler.HuecoMochila hueco && !hueco.abierto()) {
            int fila = hueco.getIndex() / Mochila.COLUMNAS;
            var rango = Mochila.rangoParaFila(fila);
            List<Text> lineas = new ArrayList<>();
            lineas.add(Text.translatable("pokepad.lunaeternal.mochila.bloqueada")
                    .styled(s -> s.withColor(0xFF6B6B).withBold(true)));
            lineas.add(Text.translatable("pokepad.lunaeternal.mochila.bloqueada_desc")
                    .styled(s -> s.withColor(0xB8C0D0)));
            lineas.add(Text.literal(""));
            lineas.add(Text.translatable("pokepad.lunaeternal.mochila.necesitas")
                    .styled(s -> s.withColor(0x9AA4BC)));
            // ⚠ La etiqueta del rango YA lleva su color en códigos §, así que
            //   se pasa como literal y Minecraft la pinta. Ponerle un color
            //   encima la dejaría toda de un tono.
            lineas.add(Text.literal(rango.tag));
            ctx.drawTooltip(textRenderer, lineas, mx, my);
            return;
        }
        drawMouseoverTooltip(ctx, mx, my);
    }

    /**
     * ⚠ Se anula el del padre: {@code super.render} ya llama al nuestro arriba,
     * y dejar los dos dibujaría el consejo del objeto <b>debajo</b> del cartel
     * de bloqueo — dos carteles superpuestos.
     */
    @Override
    protected void drawMouseoverTooltip(DrawContext ctx, int mx, int my) {
        if (this.focusedSlot instanceof MochilaHandler.HuecoMochila hueco
                && !hueco.abierto()) {
            return;
        }
        super.drawMouseoverTooltip(ctx, mx, my);
    }
}
