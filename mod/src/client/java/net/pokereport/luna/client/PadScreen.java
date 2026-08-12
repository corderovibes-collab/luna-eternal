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

    /** Icono especial: en vez de textura, el modelo 3D de un Pokémon. */
    private static final String MARCA_POKEMON = "pokemon:";

    /** Botón «+» de un panel lateral. Envía el índice reservado -3. */
    private static final String MARCA_MAS = "@mas";

    /** «@estrellas:2» pinta ★★☆ en vez de una línea de texto. */
    private static final String MARCA_ESTRELLAS = "@estrellas:";

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
    private int celdaAncho, celdaAlto;
    private int masX, masY, masLado;
    private boolean masEncima;
    private boolean hayNav;
    private int mouseXAct, mouseYAct;

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
        // La fila de navegacion solo existe si hay botones. En la pantalla
        // raiz no hay "atras" ni tiene sentido "inicio" —ya estas en el
        // inicio—, asi que reservar su altura solo encogia los iconos.
        hayNav = datos.hayAtras();
        navY = py + TITULO_ALTO;
        navX = px + 2;
        int alturaNav = hayNav ? NAV + NAV_HUECO : 0;
        int util = ph - TITULO_ALTO - alturaNav - pieAlto;

        // El tamaño de celda SALE de la pantalla, no al revés. Así el Pad
        // vale para una rejilla de 3x2 y para una de 7x5 sin tocar nada.
        int porAncho = (pw - (cols - 1) * SEPARACION) / cols;
        int porAlto = (util - (filas - 1) * SEPARACION) / filas;

        if ("tarjetas".equals(datos.estilo())) {
            // Tarjeta vertical: ocupa TODO el alto disponible y su parte del
            // ancho. Es lo que la hace parecer una carta y no una casilla con
            // texto debajo.
            celdaAncho = porAncho;
            celdaAlto = Math.max(48, porAlto);
            textoAlto = 0;
        } else {
            // Rejilla: casillas cuadradas con la etiqueta debajo.
            int lado = Math.max(16, Math.min(porAncho, porAlto - ETIQUETA));
            celdaAncho = lado;
            celdaAlto = lado;
            textoAlto = ETIQUETA;
        }
        celdaPx = celdaAlto;

        int rejAncho = cols * celdaAncho + (cols - 1) * SEPARACION;
        int rejAlto = filas * (celdaAlto + textoAlto) + (filas - 1) * SEPARACION;
        rejillaX = px + (pw - rejAncho) / 2;
        rejillaY = py + TITULO_ALTO + alturaNav + (util - rejAlto) / 2;
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
        if (hayNav) {
            int pw = (int) (panelAncho * (PX1 - PX0));
            if (boton(ctx, navX, navY, "<", mouseX, mouseY)) navEncima = -1;
            if (boton(ctx, navX + pw - NAV - 4, navY, "⌂", mouseX, mouseY)) {
                navEncima = -2;
            }
        }

        encima = -1;
        List<PadPayloads.Celda> celdas = datos.celdas();
        for (int i = 0; i < celdas.size(); i++) {
            var c = celdas.get(i);
            int x = rejillaX + c.columna() * (celdaAncho + SEPARACION);
            int y = rejillaY + c.fila() * (celdaAlto + textoAlto + SEPARACION);

            boolean dentro = mouseX >= x && mouseX < x + celdaAncho
                          && mouseY >= y && mouseY < y + celdaAlto;
            if (dentro && !c.bloqueada()) encima = i;

            boolean tarjeta = "tarjetas".equals(datos.estilo());
            if (tarjeta) {
                // Dibujada, no estirada: un PNG de 128x128 llevado a una
                // tarjeta alta y estrecha deforma las esquinas en ovalos.
                int arriba = c.bloqueada() ? 0xFFB9C0CC
                           : dentro ? 0xFFFFFFFF : 0xFFF2F8FF;
                int abajo  = c.bloqueada() ? 0xFF7C8595
                           : dentro ? 0xFFBFE4FF : 0xFF96D4F5;
                int borde  = c.bloqueada() ? 0xFF4A5260
                           : dentro ? 0xFFFFE08C : 0xFF2C78B4;
                Tarjeta.dibujar(ctx, x, y, celdaAncho, celdaAlto,
                                arriba, abajo, borde);
            } else {
                Identifier fondo = c.bloqueada() ? CELDA_BLOQUEADA
                                 : dentro ? CELDA_ENCIMA : CELDA;
                ctx.drawTexture(fondo, x, y, celdaAncho, celdaAlto,
                                0, 0, 128, 128, 128, 128);
            }

            // OJO: el margen sale del ARTE, no de la celda. Con celdaPx
            // —que en una tarjeta es su ALTO— salia m=43 sobre un arte de
            // 89, y el icono se dibujaba de 3 px. Eso era lo diminuto.
            // En tarjeta el arte manda: 60 % del alto. Con la mitad, los
            // Pokemon salian pequeños en una tarjeta grande y medio vacia.
            int arte = tarjeta
                     ? Math.min(celdaAncho - 6, (int) (celdaAlto * 0.55))
                     : celdaAncho;
            int m = Math.max(1, arte / 12);
            int ax = x + (celdaAncho - arte) / 2;
            int ay = y + (tarjeta ? 6 : 0);

            if (c.icono().startsWith(MARCA_POKEMON)) {
                // El modelo se dibuja desde la BASE de la celda y centrado:
                // un Snorlax y un Caterpie no miden lo mismo, y anclarlos
                // arriba dejaria a uno flotando.
                PokemonRender.dibujar(ctx,
                    c.icono().substring(MARCA_POKEMON.length()),
                    ax, ay, arte, delta);
            } else {
                Identifier icono = Identifier.of("lunaeternal",
                    "textures/pad/icono/" + c.icono() + ".png");
                ctx.drawTexture(icono, ax + m, ay + m, arte - m * 2,
                                arte - m * 2, 0, 0, 128, 128, 128, 128);
            }

            // Recorte al ancho de la CELDA, no mas: con celdaPx + 8 las
            // etiquetas de dos celdas vecinas se tocaban ("Centro PPuerta d").
            // En tarjeta el texto va DENTRO, bajo la imagen. En rejilla,
            // debajo de la casilla.
            int ty = tarjeta ? ay + arte + 4 : y + celdaAlto + 1;
            int anchoTexto = celdaAncho - 4;
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(this.textRenderer.trimToWidth(
                    quitarColores(c.titulo()), anchoTexto)),
                x + celdaAncho / 2, ty,
                // En tarjeta el titulo va DENTRO (fondo blanco): tinta
                // oscura. En rejilla va fuera, sobre la pantalla azul:
                // blanco. Con un solo color uno de los dos no se leia.
                // Calido sobre la tarjeta clara: el azul apagado se perdia
                // contra el degradado azul del fondo de la propia tarjeta.
                c.bloqueada() ? 0xFF7A8698
                              : tarjeta ? 0xFFB4471F : 0xFFFFFFFF);

            // En tarjetas, la descripción se pinta debajo del título: es el
            // sitio donde de verdad se lee, no en un tooltip que hay que
            // buscar con el ratón.
            if (tarjeta) {
                int dy = ty + PIE_LINEA + 2;
                for (String l : c.descripcion()) {
                    if (l.isEmpty()) continue;
                    if (dy > y + celdaAlto - PIE_LINEA) break;
                    if (l.startsWith(MARCA_ESTRELLAS)) {
                        Tarjeta.estrellas(ctx, this.textRenderer,
                            x + celdaAncho / 2, dy,
                            l.charAt(MARCA_ESTRELLAS.length()) - '0');
                    } else {
                        for (String w : partir(l, anchoTexto)) {
                            ctx.drawCenteredTextWithShadow(this.textRenderer,
                                Text.literal(w), x + celdaAncho / 2, dy,
                                0xFF6B3A14);
                            dy += PIE_LINEA;
                        }
                        dy -= PIE_LINEA;
                    }
                    dy += PIE_LINEA;
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
        mouseXAct = mouseX;
        mouseYAct = mouseY;
        masEncima = false;
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
            } else if (linea.equals(MARCA_MAS)) {
                int b = 14;
                int bx = px + (pw - b) / 2;
                masX = bx; masY = y; masLado = b;
                boolean dentro = mouseXAct >= bx && mouseXAct < bx + b
                              && mouseYAct >= y && mouseYAct < y + b;
                if (dentro) masEncima = true;
                ctx.drawTexture(dentro ? CELDA_ENCIMA : CELDA, bx, y, b, b,
                                0, 0, 128, 128, 128, 128);
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("+"), bx + b / 2, y + 3, 0xFF16324B);
            } else if (!linea.isEmpty()) {
                Text t = Text.literal(this.textRenderer.trimToWidth(linea, pw - 2));
                ctx.drawCenteredTextWithShadow(this.textRenderer, t,
                                               px + pw / 2, y, 0xFFFFFFFF);
            }
            y += h;
        }
    }

    private static int altoLinea(String l, int grafico) {
        if (l.equals(MARCA_MAS)) return 16;
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
        Tarjeta.dibujar(ctx, x, y, NAV, NAV,
                        dentro ? 0xFFFFFFFF : 0xFFF2F8FF,
                        dentro ? 0xFFBFE4FF : 0xFF96D4F5,
                        dentro ? 0xFFFFE08C : 0xFF2C78B4);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(glifo),
                                       x + NAV / 2, y + (NAV - 8) / 2,
                                       0xFF16324B);
        return dentro;
    }

    /**
     * Parte un texto en las líneas que quepan en {@code ancho}.
     *
     * <p>Se corta por palabras; solo si una sola palabra no cabe se parte por
     * letras. Recortar con puntos suspensivos era peor: «Colecci» no dice
     * nada, «Coleccio / nista» sí.
     */
    private List<String> partir(String texto, int ancho) {
        List<String> out = new ArrayList<>();
        if (this.textRenderer.getWidth(texto) <= ancho) {
            out.add(texto);
            return out;
        }
        StringBuilder linea = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            String prueba = linea.isEmpty() ? palabra : linea + " " + palabra;
            if (this.textRenderer.getWidth(prueba) <= ancho) {
                linea = new StringBuilder(prueba);
                continue;
            }
            if (!linea.isEmpty()) {
                out.add(linea.toString());
                linea = new StringBuilder();
            }
            // Palabra sola demasiado larga: se trocea por letras.
            while (this.textRenderer.getWidth(palabra) > ancho) {
                int corte = 1;
                while (corte < palabra.length()
                       && this.textRenderer.getWidth(
                              palabra.substring(0, corte + 1)) <= ancho) {
                    corte++;
                }
                out.add(palabra.substring(0, corte));
                palabra = palabra.substring(corte);
            }
            linea = new StringBuilder(palabra);
        }
        if (!linea.isEmpty()) out.add(linea.toString());
        return out;
    }

    /** Quita los códigos §c: en la etiqueta estorban, en el tooltip no. */
    private static String quitarColores(String s) {
        return s.replaceAll("§.", "");
    }

    // ------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (masEncima && button == 0) {
            clic();
            ClientPlayNetworking.send(new PadPayloads.Pulsar(
                datos.pantalla(), PadPayloads.COMPRAR, false));
            return true;
        }
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
        PokemonRender.olvidar();
        super.removed();
    }

    /** No pausa la partida: es un menú de servidor, el mundo sigue vivo. */
    @Override
    public boolean shouldPause() {
        return false;
    }
}
