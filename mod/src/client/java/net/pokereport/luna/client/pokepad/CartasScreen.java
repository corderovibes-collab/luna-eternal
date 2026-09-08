package net.pokereport.luna.client.pokepad;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.cards.CartasService.Sobre;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;

/**
 * CARTAS: tres zonas, y las tres hacen lo mismo — abrir un sobre.
 *
 * <pre>
 *   DIARIO   gratis          uno cada 24 h
 *   PLATA    cuesta Plata    uno cada 24 h
 *   LUNA     cuesta LunaCoins  SIN LÍMITE, con opción a boleto divino
 * </pre>
 *
 * Es diseño del usuario (2026-09-02) y está escrito en
 * {@code docs/analysis/cobblemon-cards.md} §5.
 *
 * <h2>⚠⚠ TRES PANELES IGUALES SERÍAN UN MURO DE TEXTO</h2>
 *
 * Por eso cada zona lleva <b>su sobre dibujado</b>, y el color del sobre es
 * <b>el de la moneda que lo compra</b>: la Plata es blanca (D-034) y la
 * LunaCoin dorada (D-033). A la tercera visita se va a la zona de siempre sin
 * leer nada. Es la misma decisión que el color de cada parada en Viajes.
 *
 * <p>El diario va azul justo por eso: si se pareciera a una de las dos de pago,
 * la zona gratuita parecería su versión pobre.
 *
 * <h2>⚠⚠⚠ EL SOBRE ES UN OBJETO DE OTRO MOD, Y PUEDE NO ESTAR</h2>
 *
 * Lo comprueba el cliente contra <b>su propio registro</b>, que es el mismo que
 * el del servidor —los registros se sincronizan, así que o lo tienen los dos o
 * no entra nadie—. Si no está, la pantalla <b>lo dice</b> en vez de enseñar
 * tres botones que no harían nada.
 *
 * <h2>⚠⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 *
 * Y la geometría sale de {@link Escalado}, que es lo que sustituyó a once
 * copias literales de {@code recalcular()}.
 */
public class CartasScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier ICONO =
            Identifier.of("lunaeternal", "textures/gui/pokepad/cartas.png");
    private static final Identifier MONEDA_PLATA =
            Identifier.of("lunaeternal", "textures/gui/pokepad/plata.png");
    private static final Identifier MONEDA_LUNA =
            Identifier.of("lunaeternal", "textures/gui/pokepad/lunacoin.png");

    /** El sobre de Cobblemon Cards. Si no existe, el mod no está. */
    private static final Identifier SOBRE_ITEM =
            Identifier.of("cobblemon-cards", "booster_pack");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    private static final int MARGEN = 14;

    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int VERDE = 0xFF3FBF5F;
    private static final int AMBAR = 0xFFE0A845;
    private static final int APAGADO = 0xFF6E7899;

    /**
     * Las tres zonas, en el orden en que se dibujan.
     *
     * <h2>⚠⚠⚠ SALEN DEL ENUM DEL SERVIDOR, NO DE UNA LISTA DE AQUÍ</h2>
     *
     * Lo que viaja al pulsar es el {@code name()} de {@code CartasService.Sobre}.
     * Con la lista escrita aquí a mano, un identificador mal copiado daría un
     * botón que <b>no hace nada</b> —sin error en pantalla, solo una línea en el
     * log— y un sobre dibujado que no es el que se compra. Recorriendo el enum,
     * eso no puede pasar: añadir una zona es añadir una constante.
     *
     * <p>Es la misma decisión que llevó las medallas a {@code Gimnasio.insignias()}
     * y las aplicaciones a {@code CatalogoPad}.
     */
    private static final Sobre[] ZONAS = Sobre.values();

    private static Identifier tex(String nombre) {
        return Identifier.of("lunaeternal", "textures/gui/pokepad/" + nombre + ".png");
    }

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private Red.EstadoCartas estado;

    /**
     * Cuándo llegó el estado, para descontar el tiempo que lleva abierta.
     *
     * <p>⚠⚠ El servidor manda <b>segundos ya restados</b>, no el instante en que
     * toca (ver {@code Red.EstadoCartas}). Sin este contador, el reloj se
     * quedaría clavado en el número que llegó y solo bajaría al reabrir la
     * pantalla.
     */
    private long recibido;

    /**
     * Cuándo se pulsó, para no dejar el botón vivo mientras vuela el paquete.
     *
     * <p>⚠ Con salida a los 1,5 s: sin ella, un paquete perdido deja los tres
     * botones muertos y hay que reabrir. Es la lección de la pantalla del
     * inicial, que llegó a dejar a alguien atrapado.
     */
    private long pulsado;

    public CartasScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.cartas"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirCartas());
    }

    private void recalcular() {
        var m = Escalado.aplicar(client, width, height, CHASIS, ATRAS, CERRAR, ICONO);
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

    private boolean esperando() {
        return pulsado > 0 && System.currentTimeMillis() - pulsado < 1500;
    }

    /** ¿Está instalado Cobblemon Cards? Lo dice el registro del propio cliente. */
    private static boolean hayCartas() {
        return Registries.ITEM.get(SOBRE_ITEM) != Items.AIR;
    }

    /**
     * Segundos que le quedan a esa zona <b>ahora mismo</b>.
     *
     * <p>Al valor que mandó el servidor se le resta lo que lleva la pantalla
     * abierta, así que el número baja solo sin pedir nada más.
     */
    private long restan(Sobre z) {
        if (estado == null || !z.llevaReloj()) {
            return 0;
        }
        long base = z == Sobre.DIARIO ? estado.segDiario() : estado.segPlata();
        long transcurrido = (System.currentTimeMillis() - recibido) / 1000;
        return Math.max(0, base - transcurrido);
    }

    /** Lo que cuesta esa zona. 0 = gratis. */
    private long precio(Sobre z) {
        if (estado == null) {
            return 0;
        }
        return switch (z) {
            case PLATA -> estado.precioPlata();
            case LUNA -> estado.precioLuna();
            default -> 0;
        };
    }

    private long saldo(Sobre z) {
        if (estado == null) {
            return 0;
        }
        return z == Sobre.LUNA ? estado.lunacoins() : estado.plata();
    }

    /**
     * ¿Se puede pulsar esa zona?
     *
     * <p>⚠ Las tres condiciones son del servidor menos «tengo dinero», que el
     * cliente ya conoce porque el saldo viaja en el mismo paquete. Y aunque el
     * cliente se equivocara, <b>el servidor lo vuelve a comprobar</b> (P6): esto
     * es para no encender un botón que va a fallar, no para autorizar nada.
     */
    private boolean activa(Sobre z) {
        if (estado == null || esperando() || !hayCartas()) {
            return false;
        }
        return restan(z) <= 0 && saldo(z) >= precio(z);
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);

        var nuevo = EstadoCliente.cartas();
        if (nuevo != null && nuevo != estado) {
            estado = nuevo;
            recibido = System.currentTimeMillis();
            pulsado = 0;
        }

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanel(ctx);
        dibujarZonas(ctx, rx, ry);
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 120, 96);
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2, pl(60) + 4, pl(48) + 4,
                    0xFFF35C0C, 2);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicio"),
                PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false, false);

        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4, 0xFFF35C0C, 2);
        }
    }

    /** Izquierda: el icono, para qué sirve esto, y los dos saldos. */
    private void dibujarPanel(DrawContext ctx) {
        int cx = PANEL_X + PANEL_W / 2;

        dibujarTextura(ctx, ICONO, px(cx - 60), py(PANEL_Y + NAV_ALTO + 20),
                pl(120), pl(120), 100, 100);

        texto(ctx, Text.translatable("pokepad.lunaeternal.app.cartas"),
                cx, PANEL_Y + NAV_ALTO + 156, 26, 0xFFFFFFFF, true, false);
        int y = PANEL_Y + NAV_ALTO + 190;
        for (String linea : partir(
                Text.translatable("pokepad.lunaeternal.cartas.explica").getString(),
                PANEL_W - 60, 15)) {
            texto(ctx, Text.literal(linea), cx, y, 15, TEXTO_SUAVE, true, false);
            y += 18;
        }

        separador(ctx, y + 12);

        // Los dos saldos, con su moneda al lado. El mismo par que en Cosméticos.
        int sy = y + 34;
        moneda(ctx, MONEDA_PLATA, PANEL_X + 40, sy);
        texto(ctx, Text.literal(estado == null ? "--" : fmt(estado.plata())),
                PANEL_X + 84, sy + 6, 24, 0xFFFFFFFF, false, false);
        moneda(ctx, MONEDA_LUNA, PANEL_X + 40, sy + 46);
        texto(ctx, Text.literal(estado == null ? "--" : fmt(estado.lunacoins())),
                PANEL_X + 84, sy + 52, 24, 0xFFF5D46A, false, false);

        if (!hayCartas()) {
            // ⚠ Se dice, no se esconde. Una pantalla que existe y no funciona
            //   sin explicar por qué se lee como una avería.
            texto(ctx, Text.translatable("pokepad.lunaeternal.cartas.sin_mod"),
                    cx, PANEL_Y + PANEL_H - 90, 16, 0xFFE07A6A, true, false);
        }
    }

    private void moneda(DrawContext ctx, Identifier tex, int artX, int artY) {
        dibujarTextura(ctx, tex, px(artX), py(artY), pl(36), pl(36), 40, 40);
    }

    /**
     * Derecha: las tres zonas, una al lado de otra.
     *
     * <p>⚠⚠ EL ANCHO SE CALCULA, no se escribe. Tres columnas escritas a mano
     * cuadran hasta que alguien mueve un margen, y entonces la tercera se sale
     * del marco sin dar ningún error — que es exactamente lo que llevaba
     * pasando la maqueta del Pad con la cuarta fila.
     */
    private void dibujarZonas(DrawContext ctx, int rx, int ry) {
        int hueco = 10;
        int aw = (PANT_W - 2 * MARGEN - hueco * (ZONAS.length - 1)) / ZONAS.length;
        int ah = PANT_H - 2 * MARGEN;
        int ay = PANT_Y + MARGEN;

        for (int i = 0; i < ZONAS.length; i++) {
            Sobre z = ZONAS[i];
            int ax = PANT_X + MARGEN + i * (aw + hueco);
            boolean activa = activa(z);
            boolean encima = dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));

            ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah),
                    encima ? 0xFFCBD6F0 : 0xFFBFCBE8);
            marco(ctx, px(ax), py(ay), pl(aw), pl(ah),
                    encima ? z.color : 0xFF7C89B4, Math.max(1, pl(2)));

            // La banda de color: lo que distingue las tres de un vistazo.
            ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + 6), z.color);

            texto(ctx, Text.translatable("pokepad.lunaeternal.cartas." + z.name().toLowerCase()),
                    ax + aw / 2, ay + 20, 22, TEXTO_OSCURO, true, true);

            // El sobre, grande y centrado. Es el sujeto de la tarjeta.
            int lado = Math.min(aw - 60, 190);
            dibujarTextura(ctx, tex(z.arte), px(ax + (aw - lado) / 2), py(ay + 56),
                    pl(lado), pl(lado), 512, 512);

            int ty = ay + 56 + lado + 10;
            for (String linea : partir(Text.translatable(
                    "pokepad.lunaeternal.cartas." + z.name().toLowerCase() + ".desc").getString(),
                    aw - 30, 14)) {
                texto(ctx, Text.literal(linea), ax + aw / 2, ty, 14, TEXTO_SUAVE, true, false);
                ty += 17;
            }

            // El precio, o «GRATIS».
            long p = precio(z);
            if (p <= 0) {
                texto(ctx, Text.translatable("pokepad.lunaeternal.cartas.gratis"),
                        ax + aw / 2, ty + 8, 22, VERDE, true, true);
            } else {
                boolean puede = saldo(z) >= p;
                dibujarTextura(ctx, z == Sobre.LUNA ? MONEDA_LUNA : MONEDA_PLATA,
                        px(ax + aw / 2 - 46), py(ty + 6), pl(26), pl(26), 40, 40);
                texto(ctx, Text.literal(fmt(p)), ax + aw / 2 - 12, ty + 8, 22,
                        puede ? TEXTO_OSCURO : 0xFFC0392B, false, true);
            }

            // El reloj, debajo. Solo en las dos que lo tienen.
            long faltan = restan(z);
            int by = ay + ah - 58;
            if (z.llevaReloj() && faltan > 0) {
                texto(ctx, Text.literal(reloj(faltan)), ax + aw / 2, by - 26, 20,
                        AMBAR, true, true);
            }

            // El botón.
            //
            // ⚠ Se APAGA, no desaparece. Que la zona exista y hoy no puedas
            //   usarla ES información; que no exista es otra pantalla.
            ctx.fill(px(ax + 16), py(by), px(ax + aw - 16), py(by + 40),
                    !activa ? APAGADO : (encima ? 0xFF4FD07A : 0xFF2E9E56));
            marco(ctx, px(ax + 16), py(by), pl(aw - 32), pl(40),
                    0xFF10331E, Math.max(1, pl(2)));
            texto(ctx, Text.translatable(botonDe(z)),
                    ax + aw / 2, by + 10, 20, activa ? 0xFFFFFFFF : 0xFFD8DEEA, true, false);
        }
    }

    /** Qué dice el botón. Un botón se etiqueta con la ACCIÓN o con el motivo. */
    private String botonDe(Sobre z) {
        if (esperando()) {
            return "pokepad.lunaeternal.cartas.abriendo";
        }
        if (!hayCartas()) {
            return "pokepad.lunaeternal.cartas.no_disponible";
        }
        if (z.llevaReloj() && restan(z) > 0) {
            return "pokepad.lunaeternal.cartas.espera";
        }
        if (saldo(z) < precio(z)) {
            return "pokepad.lunaeternal.cartas.sin_saldo";
        }
        return "pokepad.lunaeternal.cartas.abrir";
    }

    /** «7 h 12 m» / «12:30». Un número de segundos obliga a dividir de cabeza. */
    private static String reloj(long s) {
        if (s >= 3600) {
            return (s / 3600) + " h " + ((s % 3600) / 60) + " m";
        }
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private static String fmt(long n) {
        return String.format("%,d", n);
    }

    // ---- interacción -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) {
            return super.mouseClicked(mx, my, boton);
        }
        int rx = (int) mx, ry = (int) my;

        int cy = py(PANEL_Y + NAV_ALTO / 2);
        if (dentro(rx, ry, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            if (client != null) {
                client.setScreen(anterior);
            }
            return true;
        }
        if (dentro(rx, ry, px(PANEL_X + PANEL_W - 18) - pl(80), cy - pl(32), pl(80), pl(64))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            close();
            return true;
        }

        // ⚠⚠ EL MISMO REPARTO QUE EL DIBUJADO, y por eso sale de aquí y no de
        //    números escritos aparte: si cada uno calculara su columna, pulsar
        //    una zona abriría la de al lado. Es el fallo de la rejilla del Pad.
        int hueco = 10;
        int aw = (PANT_W - 2 * MARGEN - hueco * (ZONAS.length - 1)) / ZONAS.length;
        int ah = PANT_H - 2 * MARGEN;
        int ay = PANT_Y + MARGEN;
        for (int i = 0; i < ZONAS.length; i++) {
            Sobre z = ZONAS[i];
            int ax = PANT_X + MARGEN + i * (aw + hueco);
            int by = ay + ah - 58;
            if (!dentro(rx, ry, px(ax + 16), py(by), pl(aw - 32), pl(40))) {
                continue;
            }
            if (!activa(z)) {
                sonar(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
                return true;
            }
            // ⚠ No se pinta el resultado: se manda y se espera. El servidor
            //   reenvía el estado salga bien o mal, así que la pantalla vuelve
            //   sola a la verdad. Adelantarse haría que un rechazo se viera
            //   como un reloj que arranca y se deshace.
            pulsado = System.currentTimeMillis();
            sonar(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.2f);
            ClientPlayNetworking.send(new Red.AbrirSobre(z.name()));
            return true;
        }
        return super.mouseClicked(mx, my, boton);
    }

    private void sonar(net.minecraft.sound.SoundEvent sonido, float tono) {
        if (client != null && client.player != null) {
            client.player.playSound(sonido, 0.7f, tono);
        }
    }

    // ---- utilidades --------------------------------------------------------

    private java.util.List<String> partir(String texto, int anchoArte, int altoArte) {
        var salida = new java.util.ArrayList<String>();
        var actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (anchoArte(prueba, altoArte) > anchoArte && !actual.isEmpty()) {
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
        return Math.round(textRenderer.getWidth(linea) * alto / (float) textRenderer.fontHeight);
    }

    private void separador(DrawContext ctx, int artY) {
        ctx.fill(px(PANEL_X + 28), py(artY), px(PANEL_X + PANEL_W - 28),
                py(artY) + Math.max(1, pl(2)), SEPARADOR);
    }

    private void texto(DrawContext ctx, Text linea, int cx, int arriba, int alto,
                       int color, boolean centrado, boolean contorno) {
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
        if (contorno) {
            ctx.drawText(textRenderer, linea, tx - 1, ty, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, tx + 1, ty, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, tx, ty - 1, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, tx, ty + 1, TEXTO_CONTORNO, false);
        }
        ctx.drawText(textRenderer, linea, tx, ty, color, false);
        m.pop();
    }

    private static boolean dentro(int rx, int ry, int x, int y, int w, int h) {
        return rx >= x && rx < x + w && ry >= y && ry < y + h;
    }

    private static void marco(DrawContext ctx, int x, int y, int w, int h, int color, int g) {
        ctx.fill(x, y, x + w, y + g, color);
        ctx.fill(x, y + h - g, x + w, y + h, color);
        ctx.fill(x, y, x + g, y + h, color);
        ctx.fill(x + w - g, y, x + w, y + h, color);
    }

    /** ⚠ `enableBlend()` a mano: regla 1 de dibujado.md. */
    private static void dibujarTextura(DrawContext ctx, Identifier tex,
                                       int x, int y, int w, int h, int natW, int natH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
    }
}
