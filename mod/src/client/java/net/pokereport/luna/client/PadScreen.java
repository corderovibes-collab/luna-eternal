package net.pokereport.luna.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
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

    /**
     * Marcas que el SERVIDOR puede meter en una linea de panel lateral.
     *
     * <p>Mantienen el protocolo generico: no hay campos nuevos por cada cosa
     * que se quiera dibujar. El servidor manda texto y decide con estas
     * marcas si esa linea es un icono, una cabeza o una frase.
     */
    private static final String MARCA_ICONO = "@icono:";
    private static final String MARCA_CABEZA = "@cabeza";

    /** Botones de navegación: alto de la fila y tamaño del botón. */
    private static final int NAV = 18;
    private static final int NAV_HUECO = 4;
    private static final int PIE_LINEA = 10;

    private final PadPayloads.Abrir datos;
    private int panelX, panelY, panelAncho, panelAlto;
    private int encima = -1;

    public PadScreen(PadPayloads.Abrir datos) {
        super(Text.literal(datos.titulo()));
        this.datos = datos;
    }

    private int rejillaX, rejillaY, celdaPx, panelPie, navX, navY;
    private int navEncima = 0;   // 0 nada · -1 atrás · -2 inicio
    private int textoAlto = ETIQUETA;

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
        navY = py + TITULO_ALTO;
        navX = px + 2;
        int util = ph - TITULO_ALTO - NAV - NAV_HUECO - pieAlto;

        // El tamaño de celda SALE de la pantalla, no al revés. Así el Pad
        // vale para una rejilla de 3x2 y para una de 7x5 sin tocar nada.
        int porAncho = (pw - (cols - 1) * SEPARACION) / cols;
        boolean tarjetas = "tarjetas".equals(datos.estilo());
        int textoAlto = tarjetas ? PIE_LINEA * 4 : ETIQUETA;
        int porAlto = (util - (filas - 1) * SEPARACION) / filas - textoAlto;
        celdaPx = Math.max(16, Math.min(porAncho, porAlto));

        int rejAncho = cols * celdaPx + (cols - 1) * SEPARACION;
        this.textoAlto = textoAlto;
        int rejAlto = filas * (celdaPx + textoAlto) + (filas - 1) * SEPARACION;
        rejillaX = px + (pw - rejAncho) / 2;
        rejillaY = py + TITULO_ALTO + NAV + NAV_HUECO + (util - rejAlto) / 2;
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

        // Fila de navegación: atrás a la izquierda, inicio a la derecha.
        navEncima = 0;
        int pw = (int) (panelAncho * (PX1 - PX0));
        if (datos.hayAtras()) {
            if (boton(ctx, navX, navY, "<", mouseX, mouseY)) navEncima = -1;
        }
        int inicioX = navX + pw - NAV - 4;
        if (boton(ctx, inicioX, navY, "⌂", mouseX, mouseY)) navEncima = -2;

        encima = -1;
        List<PadPayloads.Celda> celdas = datos.celdas();
        for (int i = 0; i < celdas.size(); i++) {
            var c = celdas.get(i);
            int x = rejillaX + c.columna() * (celdaPx + SEPARACION);
            int y = rejillaY + c.fila() * (celdaPx + textoAlto + SEPARACION);

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

            // Recorte al ancho de la CELDA, no mas: con celdaPx + 8 las
            // etiquetas de dos celdas vecinas se tocaban ("Centro PPuerta d").
            int ty = y + celdaPx + 1;
            int anchoTexto = "tarjetas".equals(datos.estilo())
                           ? celdaPx + SEPARACION : celdaPx;
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(this.textRenderer.trimToWidth(
                    quitarColores(c.titulo()), anchoTexto)),
                x + celdaPx / 2, ty,
                c.bloqueada() ? 0xFF7A8698 : 0xFFFFFFFF);

            // En tarjetas, la descripción se pinta debajo del título: es el
            // sitio donde de verdad se lee, no en un tooltip que hay que
            // buscar con el ratón.
            if ("tarjetas".equals(datos.estilo())) {
                int dy = ty + PIE_LINEA;
                for (String l : c.descripcion()) {
                    if (l.isEmpty()) continue;
                    ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal(this.textRenderer.trimToWidth(l, anchoTexto)),
                        x + celdaPx / 2, dy, 0xFFDCEBFF);
                    dy += PIE_LINEA;
                    if (dy > ty + textoAlto) break;
                }
            }
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
        if (encima >= 0 && !"tarjetas".equals(datos.estilo())) {
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
        int grafico = Math.min(28, pw - 8);

        // Se mide antes de pintar, para poder centrar verticalmente.
        int alto = 0;
        for (String l : lineas) alto += altoLinea(l, grafico);
        int y = panelY + (int) (panelAlto * LY0) + Math.max(0, (ph - alto) / 2);

        for (String linea : lineas) {
            int h = altoLinea(linea, grafico);
            if (linea.startsWith(MARCA_ICONO)) {
                Identifier ic = Identifier.of("lunaeternal",
                    "textures/pad/icono/" + linea.substring(MARCA_ICONO.length())
                    + ".png");
                ctx.drawTexture(ic, px + (pw - grafico) / 2, y,
                                grafico, grafico, 0, 0, 128, 128, 128, 128);
            } else if (linea.equals(MARCA_CABEZA)) {
                cabeza(ctx, px + (pw - grafico) / 2, y, grafico);
            } else if (!linea.isEmpty()) {
                Text t = Text.literal(this.textRenderer.trimToWidth(linea, pw - 2));
                ctx.drawCenteredTextWithShadow(this.textRenderer, t,
                                               px + pw / 2, y, 0xFFFFFFFF);
            }
            y += h;
        }
    }

    private static int altoLinea(String l, int grafico) {
        return (l.startsWith(MARCA_ICONO) || l.equals(MARCA_CABEZA))
             ? grafico + 2 : PIE_LINEA;
    }

    /**
     * La cabeza del jugador, con su skin real.
     *
     * <p>Es lo unico del Pad que el cliente saca por su cuenta, y puede
     * hacerlo porque no es informacion de juego: es la textura que el propio
     * cliente ya tiene cargada para dibujarse a si mismo.
     */
    private void cabeza(DrawContext ctx, int x, int y, int tam) {
        if (this.client == null || this.client.player == null) return;
        try {
            PlayerSkinDrawer.draw(ctx,
                this.client.player.getSkinTextures().texture(), x, y, tam);
        } catch (Exception e) {
            // Si la skin no esta lista todavia, mejor un hueco que un crash.
            LunaClient.LOG.debug("No se pudo dibujar la cabeza", e);
        }
    }

    /** Botón cuadrado de la fila de navegación. Devuelve si el ratón está. */
    private boolean boton(DrawContext ctx, int x, int y, String glifo,
                          int mouseX, int mouseY) {
        boolean dentro = mouseX >= x && mouseX < x + NAV
                      && mouseY >= y && mouseY < y + NAV;
        ctx.drawTexture(dentro ? CELDA_ENCIMA : CELDA, x, y, NAV, NAV,
                        0, 0, 128, 128, 128, 128);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(glifo),
                                       x + NAV / 2, y + (NAV - 8) / 2,
                                       0xFF16324B);
        return dentro;
    }

    /** Quita los códigos §c: en la etiqueta estorban, en el tooltip no. */
    private static String quitarColores(String s) {
        return s.replaceAll("§.", "");
    }

    // ------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (navEncima != 0 && button == 0) {
            clic();
            ClientPlayNetworking.send(new PadPayloads.Pulsar(
                datos.pantalla(), navEncima, false));
            return true;
        }
        if (encima >= 0 && (button == 0 || button == 1)) {
            clic();
            ClientPlayNetworking.send(new PadPayloads.Pulsar(
                datos.pantalla(), encima, button == 1));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void clic() {
        if (this.client != null) {
            this.client.getSoundManager().play(PositionedSoundInstance
                .master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
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
