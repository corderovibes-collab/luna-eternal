package net.pokereport.luna.client.pokepad;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;

/**
 * EL DIÁLOGO DEL LÍDER DE GIMNASIO.
 *
 * <h2>Lo que pidió el usuario, literal</h2>
 *
 * <i>«Si le das clic derecho al NPC va a interactuar contigo, se muestra un
 * diálogo interfazmente, no quiero nada en el chat»</i>. Así que esto es una
 * pantalla, con el chasis del PokePad, y por el chat no pasa <b>nada</b>.
 *
 * <h2>⚠⚠ EL CANDADO SE ENSEÑA, NO SE ESCONDE</h2>
 *
 * Si te faltan medallas, el botón de retar está <b>apagado y con el motivo al
 * lado</b>, no ausente. Un botón que no está no dice cuántas te faltan; un botón
 * gris con «te faltan 2 medallas» es una regla que se entiende a la primera. Es
 * la misma decisión que la tienda tomó con los artículos que no puedes pagar.
 *
 * <h2>⚠⚠ Y LAS OCHO MEDALLAS SE DIBUJAN SIEMPRE</h2>
 *
 * Apagadas las que no tienes. Igual que en el PokePad, y por el mismo motivo:
 * <b>un hueco vacío no dice cuántas faltan</b>, y saber cuántas faltan es lo que
 * hace que alguien vaya a por la siguiente.
 *
 * <h2>⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 */
public class GimnasioScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    private static final int MARGEN = 12;

    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int CONTORNO_OSCURO = 0xFF080B12;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int ORO = 0xFFFFD65C;
    private static final int VERDE = 0xFF2E9E56;
    private static final int ROJO = 0xFF8C3A3A;
    private static final int APAGADO = 0xFF6E7899;
    private static final int TINTA = 0xFF16203A;
    private static final int TINTA_SUAVE = 0xFF5A668C;

    /**
     * Los ocho de Kanto, <b>leídos del servidor</b>.
     *
     * <h2>⚠⚠⚠ NO HAY UNA LISTA DE MEDALLAS AQUÍ, Y ESE ES EL ARREGLO</h2>
     *
     * Empezó siendo un array de nombres de textura en orden de gimnasio — la
     * <b>tercera</b> copia del mismo orden, junto a la del PokePad y la de
     * {@code Gimnasio.TODOS}. Nada las obligaba a coincidir, y si se
     * desordenaran, ganar a Brock encendería la medalla de Misty <b>sin dar
     * ningún error</b>.
     *
     * <p>Hoy la lista es una sola y vive donde se reparten las medallas. Es
     * mejor que una comprobación que detecte el desajuste: así no puede
     * existir.
     *
     * <p>⚠ Las texturas son las del mod de medallas, <b>referenciadas y no
     * copiadas</b>: va instalado en el cliente, así que apuntarlas cuesta cero
     * bytes en nuestro jar.
     */
    private static final java.util.List<net.pokereport.luna.gym.Gimnasio.Gimnasio_>
            GIMNASIOS = net.pokereport.luna.gym.Gimnasio.TODOS;

    /** El tinte de una medalla que no se tiene: oscura, pero se ve que es. */
    private static final int MEDALLA_APAGADA = 0xFF3C4258;

    private float k;
    private int ancho, alto, x0, y0;
    private Red.EstadoGimnasio estado;
    private long pulsado;

    public GimnasioScreen() {
        super(Text.translatable("gimnasio.lunaeternal.titulo"));
    }

    @Override
    protected void init() {
        recalcular();
        estado = EstadoCliente.gimnasio();
    }

    private void recalcular() {
        var m = Escalado.aplicar(client, width, height, CHASIS, CERRAR, CERRAR);
        k = m.k();
        ancho = m.ancho();
        alto = m.alto();
        x0 = m.x0();
        y0 = m.y0();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int px(int a) {
        return x0 + Math.round(a * k);
    }

    private int py(int a) {
        return y0 + Math.round(a * k);
    }

    private int pl(int a) {
        return Math.max(1, Math.round(a * k));
    }

    /**
     * ⚠ Con salida a los 1,5 s. Sin ella, un paquete perdido deja el botón
     * muerto y hay que reabrir — la lección de la pantalla del inicial, que
     * dejó atrapado a un jugador de verdad.
     */
    private boolean esperando() {
        return pulsado > 0 && System.currentTimeMillis() - pulsado < 1500;
    }

    private boolean listo() {
        return estado != null;
    }

    /** ¿Se puede retar ahora mismo? Lo decide el servidor, no esta pantalla. */
    private boolean puede() {
        return listo() && estado.motivo().isEmpty();
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        var nuevo = EstadoCliente.gimnasio();
        if (nuevo != null && nuevo != estado) {
            estado = nuevo;
            pulsado = 0;
        }

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanel(ctx);
        dibujarDialogo(ctx, rx, ry);
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4,
                    BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.translatable("gimnasio.lunaeternal.titulo"),
                PANEL_X + 22, cy - 14, 26, 0xFFFFFFFF, false, 0);
    }

    // ---- el panel: las ocho medallas ---------------------------------------

    private void dibujarPanel(DrawContext ctx) {
        int y = PANEL_Y + NAV_ALTO + 20;
        texto(ctx, Text.translatable("gimnasio.lunaeternal.tus_medallas"),
                PANEL_X + PANEL_W / 2, y, 22, 0xFFFFFFFF, true, CONTORNO_OSCURO);
        y += 34;
        separador(ctx, y);
        y += 18;

        // Dos filas de cuatro: en el panel de 315 no caben ocho seguidas a un
        // tamaño en el que se distingan unas de otras.
        int lado = 58, sep = 12, cols = 4;
        int anchoFila = cols * lado + (cols - 1) * sep;
        int inicio = PANEL_X + (PANEL_W - anchoFila) / 2;
        int mascara = listo() ? estado.medallas() : 0;
        for (int i = 0; i < GIMNASIOS.size(); i++) {
            int mx = inicio + (i % cols) * (lado + sep);
            int my = y + (i / cols) * (lado + sep);
            // ⚠ El bit es la SALA del gimnasio, no el indice del bucle: si algun
            //   dia la lista se ordenara de otra forma, esto seguiria siendo
            //   correcto. El indice solo decide donde se dibuja.
            var g = GIMNASIOS.get(i);
            boolean tiene = (mascara & (1 << g.sala())) != 0;
            dibujarTextura(ctx, g.textura(), px(mx), py(my), pl(lado), pl(lado),
                    16, 16, tiene ? 0xFFFFFFFF : MEDALLA_APAGADA);
        }
        y += 2 * lado + sep + 22;

        texto(ctx, Text.translatable("gimnasio.lunaeternal.llevas",
                        Integer.bitCount(mascara), GIMNASIOS.size()),
                PANEL_X + PANEL_W / 2, y, 20, ORO, true, CONTORNO_OSCURO);
    }

    // ---- la pantalla: el diálogo -------------------------------------------

    private int botonY() {
        return PANT_Y + PANT_H - MARGEN - 66;
    }

    private int botonW() {
        return (PANT_W - 2 * MARGEN - 16) / 2;
    }

    private int botonSiX() {
        return PANT_X + MARGEN;
    }

    private int botonNoX() {
        return PANT_X + MARGEN + botonW() + 16;
    }

    private void dibujarDialogo(DrawContext ctx, int rx, int ry) {
        if (!listo()) {
            texto(ctx, Text.translatable("gimnasio.lunaeternal.cargando"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2 - 14, 24,
                    TINTA_SUAVE, true, 0);
            return;
        }
        String id = estado.gimnasio();
        int y = PANT_Y + MARGEN + 6;

        // El nombre del líder, grande. Es lo primero que hay que leer.
        texto(ctx, Text.translatable("gimnasio.lunaeternal.lider." + id),
                PANT_X + MARGEN, y, 34, TINTA, false, 0);
        y += 42;
        texto(ctx, Text.translatable("gimnasio.lunaeternal.tipo." + id),
                PANT_X + MARGEN, y, 18, TINTA_SUAVE, false, 0);
        y += 30;
        separadorPantalla(ctx, y);
        y += 20;

        // Lo que dice. La clave depende del estado: ya la ganaste, te falta
        // algo, está lleno, o está listo para pelear.
        //
        // ⚠⚠ «LLENO» TIENE FRASE PROPIA POR PERSONAJE, y es petición del
        //    usuario: si las ocho salas están ocupadas, en vez de un «vuelve
        //    luego» seco sale algo del personaje —Brock persiguiendo
        //    enfermeras, Misty buscando a Ash para que le pague la bici—. Es el
        //    único mensaje que un jugador va a leer entero, porque es el único
        //    que le obliga a esperar.
        String clave = estado.yaGanada() ? "ya_ganada"
                     : estado.motivo().isEmpty() ? "reto"
                     : estado.motivo().equals("lleno") ? "lleno" : "no_puedes";
        var frase = Text.translatable(
                "gimnasio.lunaeternal.dice." + id + "." + clave);
        for (String l : partir(frase.getString(), PANT_W - 2 * MARGEN, 22)) {
            texto(ctx, Text.literal(l), PANT_X + MARGEN, y, 22, TINTA, false, 0);
            y += 30;
        }

        // Y el motivo concreto, si lo hay, en su propio renglón y en ámbar: no
        // es lo que dice el personaje, es la regla.
        if (!estado.motivo().isEmpty() && !estado.yaGanada()) {
            y += 8;
            var motivo = Text.translatable(
                    "gimnasio.lunaeternal.no." + estado.motivo(), estado.dato());
            for (String l : partir(motivo.getString(), PANT_W - 2 * MARGEN, 19)) {
                texto(ctx, Text.literal(l), PANT_X + MARGEN, y, 19, 0xFFB4711A,
                        false, 0);
                y += 26;
            }
        }

        // Cuántas copias quedan libres. Es información honesta: si están las
        // ocho ocupadas, el botón está apagado por un motivo que no es tuyo.
        textoDer(ctx, Text.translatable("gimnasio.lunaeternal.libres",
                        estado.libres()),
                PANT_X + PANT_W - MARGEN, botonY() - 30, 16, TINTA_SUAVE);

        boton(ctx, rx, ry, botonSiX(), botonY(), botonW(), 66,
                Text.translatable("gimnasio.lunaeternal.si"),
                puede() && !esperando(), VERDE);
        boton(ctx, rx, ry, botonNoX(), botonY(), botonW(), 66,
                Text.translatable("gimnasio.lunaeternal.no"), true, ROJO);
    }

    // ---- interacción -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        int rx = (int) mx, ry = (int) my;

        int cy = PANEL_Y + NAV_ALTO / 2;
        int cx = PANEL_X + PANEL_W - 18 - 80;
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            sonar();
            close();
            return true;
        }
        if (listo()) {
            if (dentro(rx, ry, px(botonNoX()), py(botonY()), pl(botonW()), pl(66))) {
                sonar();
                close();
                return true;
            }
            if (puede() && !esperando()
                    && dentro(rx, ry, px(botonSiX()), py(botonY()),
                              pl(botonW()), pl(66))) {
                sonar();
                pulsado = System.currentTimeMillis();
                // ⚠ Viaja el identificador y nada más: si puede o no lo decide
                //   el servidor otra vez, con su copia (P6).
                ClientPlayNetworking.send(new Red.AccionGimnasio(estado.gimnasio()));
                // ⚠ Y se cierra la pantalla: si sale bien, el jugador aparece en
                //   la arena, y aparecer con un diálogo delante sería tener que
                //   cerrarlo antes de poder mirar dónde estás.
                close();
                return true;
            }
        }
        return super.mouseClicked(mx, my, boton);
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        if (tecla == 256) {
            close();
            return true;
        }
        return super.keyPressed(tecla, escaneo, mods);
    }

    private void sonar() {
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.0f);
        }
    }

    // ---- utilidades --------------------------------------------------------

    private void boton(DrawContext ctx, int rx, int ry, int ax, int ay, int aw,
                       int ah, Text etiqueta, boolean activo, int color) {
        boolean enc = activo && dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah),
                !activo ? APAGADO : (enc ? aclarar(color) : color));
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), CONTORNO_OSCURO,
                Math.max(1, pl(2)));
        texto(ctx, etiqueta, ax + aw / 2, ay + ah / 2 - 14, 28,
                activo ? 0xFFFFFFFF : 0xFFD8DEEA, true, 0);
    }

    private static int aclarar(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 40);
        int b = Math.min(255, (color & 0xFF) + 40);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private List<String> partir(String s, int anchoMax, int alto) {
        var salida = new ArrayList<String>();
        var actual = new StringBuilder();
        for (String palabra : s.split(" ")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (anchoArte(prueba, alto) > anchoMax && !actual.isEmpty()) {
                salida.add(actual.toString());
                actual = new StringBuilder(palabra);
            } else {
                actual = new StringBuilder(prueba);
            }
        }
        if (!actual.isEmpty()) {
            salida.add(actual.toString());
        }
        return salida;
    }

    private int anchoArte(String linea, int alto) {
        return Math.round(textRenderer.getWidth(linea) * alto
                / (float) textRenderer.fontHeight);
    }

    private void separador(DrawContext ctx, int artY) {
        ctx.fill(px(PANEL_X + 28), py(artY), px(PANEL_X + PANEL_W - 28),
                py(artY) + Math.max(1, pl(2)), SEPARADOR);
    }

    private void separadorPantalla(DrawContext ctx, int artY) {
        ctx.fill(px(PANT_X + MARGEN), py(artY), px(PANT_X + PANT_W - MARGEN),
                py(artY) + Math.max(1, pl(2)), 0x33000000);
    }

    private void textoDer(DrawContext ctx, Text linea, int derecha, int arriba,
                          int alto, int color) {
        int a = Math.round(textRenderer.getWidth(linea) * alto
                / (float) textRenderer.fontHeight);
        texto(ctx, linea, derecha - a, arriba, alto, color, false, 0);
    }

    private void texto(DrawContext ctx, Text linea, int cx, int arriba, int alto,
                       int color, boolean centrado, int contorno) {
        float escala = alto * k / textRenderer.fontHeight;
        if (escala <= 0) {
            return;
        }
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x0, y0, 0);
        m.scale(escala, escala, 1f);
        int anchoTexto = textRenderer.getWidth(linea);
        int tx = Math.round(cx * k / escala) - (centrado ? anchoTexto / 2 : 0);
        int ty = Math.round(arriba * k / escala);
        if (contorno != 0) {
            ctx.drawText(textRenderer, linea, tx - 1, ty, contorno, false);
            ctx.drawText(textRenderer, linea, tx + 1, ty, contorno, false);
            ctx.drawText(textRenderer, linea, tx, ty - 1, contorno, false);
            ctx.drawText(textRenderer, linea, tx, ty + 1, contorno, false);
        }
        ctx.drawText(textRenderer, linea, tx, ty, color, false);
        m.pop();
    }

    private static boolean dentro(int rx, int ry, int x, int y, int w, int h) {
        return rx >= x && rx < x + w && ry >= y && ry < y + h;
    }

    private void marco(DrawContext ctx, int x, int y, int w, int h, int color,
                       int grosor) {
        ctx.fill(x, y, x + w, y + grosor, color);
        ctx.fill(x, y + h - grosor, x + w, y + h, color);
        ctx.fill(x, y, x + grosor, y + h, color);
        ctx.fill(x + w - grosor, y, x + w, y + h, color);
    }

    private void dibujarTextura(DrawContext ctx, Identifier tex, int x, int y,
                                int w, int h, int nw, int nh) {
        dibujarTextura(ctx, tex, x, y, w, h, nw, nh, 0xFFFFFFFF);
    }

    /**
     * ⚠⚠ {@code enableBlend} A MANO, SIEMPRE. Sin eso el juego trata cualquier
     * alfa &gt; 0 como opaco y las medallas salen con cerco negro. Es la regla 1
     * de {@code docs/ui/dibujado.md} y costó una noche entera.
     *
     * <p>⚠ Y {@code natW/natH} son el tamaño <b>real de la PNG</b>: con el
     * tamaño en pantalla, Minecraft tomaría solo esa esquina de la textura.
     */
    private void dibujarTextura(DrawContext ctx, Identifier tex, int x, int y,
                                int w, int h, int nw, int nh, int tinte) {
        boolean tenido = tinte != 0xFFFFFFFF;
        if (tenido) {
            // El color del shader MULTIPLICA a la textura, así que va antes de
            // dibujar. Después no tiñe nada.
            ctx.setShaderColor(((tinte >> 16) & 0xFF) / 255f,
                    ((tinte >> 8) & 0xFF) / 255f, (tinte & 0xFF) / 255f, 1f);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, nw, nh, nw, nh);
        RenderSystem.disableBlend();
        if (tenido) {
            ctx.setShaderColor(1f, 1f, 1f, 1f);
        }
    }
}
