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
    private static final Identifier PANEL =
        Identifier.of("lunaeternal", "textures/pad/panel.png");
    private static final Identifier CELDA =
        Identifier.of("lunaeternal", "textures/pad/celda.png");
    private static final Identifier CELDA_ENCIMA =
        Identifier.of("lunaeternal", "textures/pad/celda_encima.png");
    private static final Identifier CELDA_BLOQUEADA =
        Identifier.of("lunaeternal", "textures/pad/celda_bloqueada.png");

    /** Tamaño de una celda y separación, en píxeles de interfaz. */
    private static final int CELDA_PX = 32;
    private static final int SEPARACION = 6;
    private static final int MARGEN = 14;
    private static final int CABECERA = 26;
    private static final int PIE_LINEA = 10;

    private final PadPayloads.Abrir datos;
    private int panelX, panelY, panelAncho, panelAlto;
    private int encima = -1;

    public PadScreen(PadPayloads.Abrir datos) {
        super(Text.literal(datos.titulo()));
        this.datos = datos;
    }

    @Override
    protected void init() {
        int rejillaAncho = datos.columnas() * CELDA_PX
                         + (datos.columnas() - 1) * SEPARACION;
        int rejillaAlto = datos.filas() * CELDA_PX
                        + (datos.filas() - 1) * SEPARACION;

        panelAncho = rejillaAncho + MARGEN * 2;
        panelAlto = CABECERA + rejillaAlto + MARGEN
                  + datos.pie().size() * PIE_LINEA;
        panelX = (this.width - panelAncho) / 2;
        panelY = (this.height - panelAlto) / 2;
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

        // El panel se estira desde una textura de 9 rodajas, para que el
        // marco no se deforme al cambiar de tamaño la rejilla.
        nueveRodajas(ctx, PANEL, panelX, panelY, panelAncho, panelAlto, 8);

        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title,
            panelX + panelAncho / 2, panelY + 9, 0xFFE2D6FF);

        encima = -1;
        List<PadPayloads.Celda> celdas = datos.celdas();
        for (int i = 0; i < celdas.size(); i++) {
            var c = celdas.get(i);
            int x = panelX + MARGEN + c.columna() * (CELDA_PX + SEPARACION);
            int y = panelY + CABECERA + c.fila() * (CELDA_PX + SEPARACION);

            boolean dentro = mouseX >= x && mouseX < x + CELDA_PX
                          && mouseY >= y && mouseY < y + CELDA_PX;
            if (dentro && !c.bloqueada()) encima = i;

            Identifier fondo = c.bloqueada() ? CELDA_BLOQUEADA
                             : dentro ? CELDA_ENCIMA : CELDA;
            ctx.drawTexture(fondo, x, y, 0, 0, CELDA_PX, CELDA_PX,
                            CELDA_PX, CELDA_PX);

            // El icono es de 32x32 y se dibuja a 24: se pierde algo de
            // nitidez, pero gana silueta. A 16 px reales no se distinguia
            // una cartera de una tienda.
            Identifier icono = Identifier.of("lunaeternal",
                "textures/pad/icono/" + c.icono() + ".png");
            ctx.drawTexture(icono, x + 4, y + 4, 24, 24, 0, 0, 32, 32, 32, 32);
        }

        int py = panelY + panelAlto - MARGEN / 2 - datos.pie().size() * PIE_LINEA;
        for (String linea : datos.pie()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(linea), panelX + panelAncho / 2, py, 0xFFB9A8E0);
            py += PIE_LINEA;
        }

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
     * Dibuja una textura estirable sin deformar el marco.
     *
     * <p>Se parte en nueve trozos: cuatro esquinas que no se escalan, cuatro
     * bordes que se estiran en un solo eje, y el centro que se estira en los
     * dos. Es lo que permite que el mismo arte valga para una rejilla de 3×2 y
     * para una de 9×6 — que es justo lo que el menú de cofre no permitía.
     */
    private static void nueveRodajas(DrawContext ctx, Identifier tex,
                                     int x, int y, int w, int h, int b) {
        int t = 64;  // la textura de origen es de 64x64
        int c = t - b * 2;
        int iw = w - b * 2, ih = h - b * 2;

        ctx.drawTexture(tex, x, y, b, b, 0, 0, b, b, t, t);
        ctx.drawTexture(tex, x + w - b, y, b, b, t - b, 0, b, b, t, t);
        ctx.drawTexture(tex, x, y + h - b, b, b, 0, t - b, b, b, t, t);
        ctx.drawTexture(tex, x + w - b, y + h - b, b, b, t - b, t - b, b, b, t, t);

        ctx.drawTexture(tex, x + b, y, iw, b, b, 0, c, b, t, t);
        ctx.drawTexture(tex, x + b, y + h - b, iw, b, b, t - b, c, b, t, t);
        ctx.drawTexture(tex, x, y + b, b, ih, 0, b, b, c, t, t);
        ctx.drawTexture(tex, x + w - b, y + b, b, ih, t - b, b, b, c, t, t);

        ctx.drawTexture(tex, x + b, y + b, iw, ih, b, b, c, c, t, t);
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
