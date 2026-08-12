package net.pokereport.luna.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.net.PadPayloads;

import java.util.ArrayList;
import java.util.List;

/**
 * El Pad: la interfaz propia de Luna Eternal (D-025).
 *
 * <p>Es una {@link Screen} normal, no un menú de contenedor. Esa es toda la
 * diferencia y es enorme:
 *
 * <ul>
 *   <li>no aparece el inventario del jugador;</li>
 *   <li>la rejilla tiene las columnas que queramos, no nueve;</li>
 *   <li>los botones no tienen que caer sobre casillas;</li>
 *   <li>el panel mide lo que queramos, no 176 px.</li>
 * </ul>
 *
 * <p><b>No decide nada.</b> Dibuja la lista de celdas que le mandó el servidor
 * y le devuelve el índice pulsado. Si alguien modifica este código, lo único
 * que consigue es mandar índices — que el servidor valida contra la pantalla
 * que él mismo envió (P6).
 */
public class PadScreen extends Screen {

    // Texturas del resource pack. Si faltan, el juego dibuja el cuadro
    // morado-negro de textura ausente y se ve en el acto que falta el pack.
    private static final Identifier POKEPAD =
        Identifier.of("lunaeternal", "textures/pad/pokepad.png");
    private static final Identifier CELDA =
        Identifier.of("lunaeternal", "textures/pad/celda.png");
    private static final Identifier CELDA_ENCIMA =
        Identifier.of("lunaeternal", "textures/pad/celda_encima.png");
    private static final Identifier CELDA_BLOQUEADA =
        Identifier.of("lunaeternal", "textures/pad/celda_bloqueada.png");

    /** Proporción del arte: 1024x576. Se respeta siempre. */
    private static final double ASPECTO = 1024.0 / 576.0;

    /** Dónde cae la pantalla azul dentro del arte, medido, no adivinado. */
    private static double PX0 = 0.2401, PY0 = 0.1926, PX1 = 0.7479, PY1 = 0.8306;

    /** Los dos paneles laterales del arte, medidos igual que la pantalla. */
    private static final double VX0 = 0.0979, VX1 = 0.1964;   // verde
    private static final double MX0 = 0.7911, MX1 = 0.8917;   // morado
    private static final double LY0 = 0.2593, LY1 = 0.7972;   // ambos

    private static final int SEPARACION = 6;
    private static final int ETIQUETA = 10;
    private static final int TITULO_ALTO = 12;
    private static final int PIE_LINEA = 10;

    private final PadPayloads.Abrir datos;
    private int panelX, panelY, panelAncho, panelAlto;
    private int encima = -1;

    public PadScreen(PadPayloads.Abrir datos) {
        super(Text.literal(datos.titulo()));
        this.datos = datos;
    }

    private int rejillaX, rejillaY, celdaPx, panelPie;

    @Override
    protected void init() {
        // El aparato ocupa lo que quepa, sin deformarse y sin salirse.
        panelAncho = (int) Math.min(this.width * 0.94,
                                    this.height * 0.94 * ASPECTO);
        panelAlto = (int) Math.round(panelAncho / ASPECTO);
        panelX = (this.width - panelAncho) / 2;
        panelY = (this.height - panelAlto) / 2;

        // La pantalla azul, en píxeles.
        int px = panelX + (int) (panelAncho * PX0);
        int py = panelY + (int) (panelAlto * PY0);
        int pw = (int) (panelAncho * (PX1 - PX0));
        int ph = (int) (panelAlto * (PY1 - PY0));

        int filas = datos.filas(), cols = datos.columnas();
        int pieAlto = datos.pie().size() * PIE_LINEA;
        int util = ph - TITULO_ALTO - pieAlto;

        // El tamaño de celda SALE de la pantalla, no al revés. Así el Pad
        // vale para una rejilla de 3x2 y para una de 7x5 sin tocar nada.
        int porAncho = (pw - (cols - 1) * SEPARACION) / cols;
        int porAlto = (util - (filas - 1) * SEPARACION) / filas - ETIQUETA;
        celdaPx = Math.max(16, Math.min(porAncho, porAlto));

        int rejAncho = cols * celdaPx + (cols - 1) * SEPARACION;
        int rejAlto = filas * (celdaPx + ETIQUETA) + (filas - 1) * SEPARACION;
        rejillaX = px + (pw - rejAncho) / 2;
        rejillaY = py + TITULO_ALTO + (util - rejAlto) / 2;
        panelPie = py + ph - pieAlto;
    }

    // ------------------------------------------------------------ dibujo

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // super.render() YA difumina y oscurece el mundo: desde 1.20.5,
        // Screen.render llama a renderBackground() por su cuenta.
        //
        // Va PRIMERO, y todo lo nuestro después. Al revés —que es como lo
        // tenía— el desenfoque se aplicaba dos veces y el segundo pase
        // emborronaba el panel ya dibujado: el menú entero salía movido,
        // incluido el texto.
        super.render(ctx, mouseX, mouseY, delta);

        // El aparato, entero y proporcional. NO se estira: sus salientes y
        // los paneles laterales están compuestos y se romperían.
        ctx.drawTexture(POKEPAD, panelX, panelY, panelAncho, panelAlto,
                        0, 0, 1024, 576, 1024, 576);

        // El título va DENTRO de la pantalla azul: el marco de arriba tiene
        // relieve y una gema, y el texto encima quedaría ilegible.
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title,
            panelX + panelAncho / 2,
            panelY + (int) (panelAlto * PY0) + 2, 0xFFFFFFFF);

        encima = -1;
        List<PadPayloads.Celda> celdas = datos.celdas();
        for (int i = 0; i < celdas.size(); i++) {
            var c = celdas.get(i);
            int x = rejillaX + c.columna() * (celdaPx + SEPARACION);
            int y = rejillaY + c.fila() * (celdaPx + ETIQUETA + SEPARACION);

            boolean dentro = mouseX >= x && mouseX < x + celdaPx
                          && mouseY >= y && mouseY < y + celdaPx;
            if (dentro && !c.bloqueada()) encima = i;

            Identifier fondo = c.bloqueada() ? CELDA_BLOQUEADA
                             : dentro ? CELDA_ENCIMA : CELDA;
            ctx.drawTexture(fondo, x, y, celdaPx, celdaPx, 0, 0, 128, 128,
                            128, 128);

            // El icono, con margen dentro de la celda.
            int m = Math.max(2, celdaPx / 8);
            Identifier icono = Identifier.of("lunaeternal",
                "textures/pad/icono/" + c.icono() + ".png");
            ctx.drawTexture(icono, x + m, y + m, celdaPx - m * 2,
                            celdaPx - m * 2, 0, 0, 128, 128, 128, 128);

            String etiqueta = this.textRenderer.trimToWidth(
                quitarColores(c.titulo()), celdaPx + 8);
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(etiqueta), x + celdaPx / 2, y + celdaPx + 1,
                c.bloqueada() ? 0xFF7A8698 : 0xFFFFFFFF);
        }

        int py = panelPie;
        for (String linea : datos.pie()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(linea), panelX + panelAncho / 2, py, 0xFFEAF4FF);
            py += PIE_LINEA;
        }

        // Paneles laterales. Van despues de la rejilla porque no se solapan
        // con ella: estan fuera de la pantalla azul.
        panelLateral(ctx, datos.izquierda(), VX0, VX1);
        panelLateral(ctx, datos.derecha(), MX0, MX1);

        // El tooltip va el ultimo, para que quede por encima de todo.
        if (encima >= 0) {
            var c = celdas.get(encima);
            List<Text> lineas = new ArrayList<>();
            lineas.add(Text.literal(c.titulo()));
            for (String l : c.descripcion()) lineas.add(Text.literal(l));
            ctx.drawTooltip(this.textRenderer, lineas, mouseX, mouseY);
        }
    }

    /**
     * Texto centrado dentro de uno de los paneles laterales.
     *
     * <p>Son estrechos de verdad —entre 47 y 62 px segun la escala— asi que
     * cada linea se recorta al ancho real. Es preferible cortar a que el
     * texto se salga por encima del marco.
     */
    private void panelLateral(DrawContext ctx, List<String> lineas,
                              double x0, double x1) {
        if (lineas.isEmpty()) return;
        int px = panelX + (int) (panelAncho * x0);
        int pw = (int) (panelAncho * (x1 - x0));
        int ph = (int) (panelAlto * (LY1 - LY0));
        int alto = lineas.size() * PIE_LINEA;
        int y = panelY + (int) (panelAlto * LY0) + Math.max(0, (ph - alto) / 2);

        for (String linea : lineas) {
            if (!linea.isEmpty()) {
                Text t = Text.literal(this.textRenderer.trimToWidth(linea, pw - 2));
                ctx.drawCenteredTextWithShadow(this.textRenderer, t,
                                               px + pw / 2, y, 0xFFFFFFFF);
            }
            y += PIE_LINEA;
        }
    }

    /** Quita los códigos §c: en la etiqueta estorban, en el tooltip no. */
    private static String quitarColores(String s) {
        return s.replaceAll("§.", "");
    }

    // ------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (encima >= 0 && (button == 0 || button == 1)) {
            if (this.client != null) {
                this.client.getSoundManager().play(PositionedSoundInstance
                    .master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            ClientPlayNetworking.send(new PadPayloads.Pulsar(
                datos.pantalla(), encima, button == 1));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        // Avisar al servidor: sin esto se quedaria pensando que la pantalla
        // sigue abierta y aceptaria pulsaciones de una pantalla ya cerrada.
        ClientPlayNetworking.send(new PadPayloads.Cerrar(datos.pantalla()));
        super.removed();
    }

    /** No pausa la partida: es un menú de servidor, el mundo sigue vivo. */
    @Override
    public boolean shouldPause() {
        return false;
    }
}
