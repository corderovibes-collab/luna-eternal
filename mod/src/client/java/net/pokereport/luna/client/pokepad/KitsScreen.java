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
import net.pokereport.luna.client.Trajes;
import net.pokereport.luna.net.Red;

/**
 * KITS: los trajes de rango, los exclusivos y los tuyos.
 *
 * <h2>La disposición la puso el usuario</h2>
 *
 * Tres pestañas a la izquierda —KITS DE RANGO, KITS EXCLUSIVOS, MIS KITS—, el
 * saldo de LunaCoins con su «+», y al pasar el ratón por un kit sale su detalle;
 * al pulsarlo, el previsualizador 3D se cambia a ese.
 *
 * <h2>⚠⚠⚠ SE PREVISUALIZA SOBRE EL JUGADOR DE VERDAD, NO SOBRE UN MANIQUÍ</h2>
 *
 * Un traje se elige para verse <b>uno mismo</b> con él puesto. Con un modelo
 * genérico, la pantalla enseñaría cómo le queda a otro. Y sale gratis: la
 * entidad ya existe y ya está cargada con su skin.
 *
 * <h2>⚠⚠ EL PROBADO SE QUITA EN UN {@code finally}</h2>
 *
 * El dibujado pasa por código de vainilla que no admite parámetros nuestros, así
 * que lo que se está probando viaja en una estática. Si una excepción se llevara
 * por delante el «deja de probar», <b>el jugador saldría al mundo llevando el
 * traje que estaba mirando sin tenerlo</b>. Es la misma trampa que ya está
 * escrita en {@code Mascota3D}.
 *
 * <h2>⚠⚠ Y ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 *
 * Lo 3D va en la segunda pasada, después de {@code ctx.draw()}. La geometría
 * sale de {@link Escalado}, como en todas.
 */
public class KitsScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier MONEDA =
            Identifier.of("lunaeternal", "textures/gui/pokepad/lunacoin_oro.png");
    private static final Identifier MAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_mas_luna.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    private static final int MARGEN = 12;

    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int CONTORNO_OSCURO = 0xFF080B12;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int VERDE = 0xFF2E9E56;
    private static final int ROJO = 0xFF9E3A3A;
    private static final int APAGADO = 0xFF6E7899;
    private static final int ORO = 0xFFFFD65C;

    /** Las tres pestañas, en el orden que las puso el usuario. */
    private record Pestana(String id, int color) {}

    private static final Pestana[] PESTANAS = {
        new Pestana("rango", 0xFFC08A1E),
        new Pestana("exclusivos", 0xFF2E6E8C),
        new Pestana("mios", 0xFF6B3FA0),
    };

    /** El color de cada rango, el mismo que en el chat. */
    private static final int[] COLOR_RANGO = {
        0xFFBFC6D4, 0xFF58C86C, 0xFF56C8D6, 0xFF965CC8, 0xFFE8B038,
    };

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private int pestana;
    private int elegido = -1;
    private long pulsado;
    private Red.EstadoTrajes estado;

    public KitsScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.kits"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirTrajes());
        ClientPlayNetworking.send(new Red.PedirSaldo());
    }

    /**
     * ⚠⚠ SE DEJA DE PROBAR AL CERRAR, SIEMPRE. Si no, cerrar la pantalla con un
     *    traje seleccionado te dejaría llevándolo por el mundo sin tenerlo.
     */
    @Override
    public void removed() {
        Trajes.previsualizar(null);
        super.removed();
    }

    private void recalcular() {
        var m = Escalado.aplicar(client, width, height, CHASIS, ATRAS, CERRAR, MONEDA, MAS);
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

    /**
     * ¿Esta fila es un kit de objetos en vez de un traje que se dibuja?
     *
     * <p>⚠⚠ LO DICE EL SERVIDOR, no una lista de identificadores aquí. Una lista
     * paralela en el cliente es exactamente lo que esta misma pantalla ya tuvo
     * —los cinco rangos escritos a mano— y lo que se queda mintiendo el día que
     * cambia el otro lado, sin dar ningún error.
     */
    private static boolean esKit(Red.FichaTraje f) {
        return f != null && f.espera() >= 0;
    }

    /** «2 h 15 min», para el botón y para el pie de la fila. */
    private static Text cuanto(int segundos) {
        int h = segundos / 3600;
        int m = (segundos % 3600) / 60;
        if (h > 0) {
            return Text.translatable("pokepad.lunaeternal.trajes.kit_espera_h", h, m);
        }
        return Text.translatable("pokepad.lunaeternal.trajes.kit_espera_m",
                Math.max(1, m));
    }

    private List<Red.FichaTraje> fichas() {
        return estado == null ? List.of() : estado.fichas();
    }

    private Red.FichaTraje ficha(int i) {
        var f = fichas();
        return i >= 0 && i < f.size() ? f.get(i) : null;
    }

    private boolean loLleva(Red.FichaTraje f) {
        return estado != null && f != null && f.id().equals(estado.puesto());
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        var nuevo = EstadoCliente.trajes();
        if (nuevo != null && nuevo != estado) {
            estado = nuevo;
            pulsado = 0;
            if (elegido < 0) {
                // Al abrir, se enseña el que llevas; y si no llevas ninguno, el
                // más alto que puedas ponerte -- que es el que quieres ver.
                elegido = elegidoInicial();
            }
        }

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPestanas(ctx, rx, ry);
        dibujarSaldo(ctx, rx, ry);

        if (pestana == 0) {
            dibujarRango(ctx, rx, ry);
        } else {
            dibujarVacia(ctx);
        }

        // ⚠ SEGUNDA PASADA: lo 3D va después de vaciar lo 2D. Regla 2 de
        //   dibujado.md -- mezclarlas deja el modelo debajo del panel.
        ctx.draw();
        if (pestana == 0) {
            dibujarPrevisualizador(ctx, rx, ry);
        }

        if (pestana == 0) {
            dibujarDetalle(ctx, rx, ry);
        }
    }

    private int elegidoInicial() {
        var f = fichas();
        for (int i = 0; i < f.size(); i++) {
            if (loLleva(f.get(i))) {
                return i;
            }
        }
        int mejor = 0;
        for (int i = 0; i < f.size(); i++) {
            if (f.get(i).puede()) {
                mejor = i;
            }
        }
        return mejor;
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 120, 96);
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2, pl(60) + 4, pl(48) + 4,
                    BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicio"),
                PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false, 0);

        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4,
                    BORDE_ENCIMA, 2);
        }
    }

    // ---- las pestañas ------------------------------------------------------

    private int pestanaY(int i) {
        return PANEL_Y + NAV_ALTO + 24 + i * 84;
    }

    private void dibujarPestanas(DrawContext ctx, int rx, int ry) {
        int w = PANEL_W - 56, h = 72;
        for (int i = 0; i < PESTANAS.length; i++) {
            int x = PANEL_X + 28, y = pestanaY(i);
            boolean sel = i == pestana;
            boolean enc = dentro(rx, ry, px(x), py(y), pl(w), pl(h));
            ctx.fill(px(x), py(y), px(x + w), py(y + h),
                    sel ? PESTANAS[i].color() : (enc ? 0xFF2A3145 : 0xFF1E2434));
            marco(ctx, px(x), py(y), pl(w), pl(h),
                    sel ? BORDE_ENCIMA : 0xFF39415C, Math.max(2, pl(sel ? 3 : 2)));
            var et = Text.translatable("pokepad.lunaeternal.kits." + PESTANAS[i].id());
            var lineas = partir(et.getString(), w - 24, 20);
            int ty = y + (h - lineas.size() * 24) / 2;
            for (String l : lineas) {
                texto(ctx, Text.literal(l), x + w / 2, ty, 20,
                        sel ? 0xFFFFFFFF : 0xFFC2CCE2, true, CONTORNO_OSCURO);
                ty += 24;
            }
        }
    }

    // ---- el saldo, con su «+» ----------------------------------------------

    private void dibujarSaldo(DrawContext ctx, int rx, int ry) {
        int y = PANEL_Y + PANEL_H - 92, x = PANEL_X + 28, w = PANEL_W - 56, h = 64;
        ctx.fill(px(x), py(y), px(x + w), py(y + h), 0xFF161B28);
        marco(ctx, px(x), py(y), pl(w), pl(h), 0xFF39415C, Math.max(2, pl(2)));
        dibujarTextura(ctx, MONEDA, px(x + 10), py(y + 14), pl(36), pl(36), 100, 100);
        var s = EstadoCliente.saldo();
        long saldo = s == null ? 0 : s.reportcoins();
        texto(ctx, Text.literal(String.format("%,d", saldo)), x + 56, y + 20, 26, ORO,
                false, CONTORNO_OSCURO);
        int bx = x + w - 52;
        dibujarTextura(ctx, MAS, px(bx), py(y + 12), pl(40), pl(40), 100, 100);
        if (dentro(rx, ry, px(bx), py(y + 12), pl(40), pl(40))) {
            marco(ctx, px(bx) - 2, py(y + 12) - 2, pl(40) + 4, pl(40) + 4, BORDE_ENCIMA, 2);
        }
    }

    // ---- KITS DE RANGO -----------------------------------------------------

    private int visorX() {
        return PANT_X + MARGEN;
    }

    private int visorW() {
        return 250;
    }

    private int listaX() {
        return visorX() + visorW() + 16;
    }

    private int listaW() {
        return PANT_W - 2 * MARGEN - visorW() - 16;
    }

    private int filaY(int i) {
        return PANT_Y + MARGEN + 44 + i * 60;
    }

    private void dibujarRango(DrawContext ctx, int rx, int ry) {
        texto(ctx, Text.translatable("pokepad.lunaeternal.trajes.titulo"),
                PANT_X + MARGEN, PANT_Y + MARGEN + 6, 22, 0xFF16203A, false, 0);

        // el hueco del previsualizador, que se rellena en la segunda pasada
        ctx.fill(px(visorX()), py(PANT_Y + MARGEN + 40),
                px(visorX() + visorW()), py(PANT_Y + PANT_H - MARGEN - 66), 0xFF141A28);
        marco(ctx, px(visorX()), py(PANT_Y + MARGEN + 40), pl(visorW()),
                pl(PANT_H - 2 * MARGEN - 40 - 66), 0xFF39415C, Math.max(2, pl(2)));

        var f = fichas();
        for (int i = 0; i < f.size(); i++) {
            var t = f.get(i);
            int y = filaY(i), w = listaW(), h = 52;
            boolean sel = i == elegido;
            boolean enc = dentro(rx, ry, px(listaX()), py(y), pl(w), pl(h));
            boolean llevo = loLleva(t);

            ctx.fill(px(listaX()), py(y), px(listaX() + w), py(y + h),
                    sel ? 0xFF2B3550 : (enc ? 0xFF232B40 : 0xFF1A2030));
            marco(ctx, px(listaX()), py(y), pl(w), pl(h),
                    sel ? BORDE_ENCIMA : 0xFF39415C, Math.max(2, pl(sel ? 3 : 2)));
            // la pastilla de color del rango: dice de quién es sin leer
            ctx.fill(px(listaX() + 6), py(y + 6), px(listaX() + 14), py(y + h - 6),
                    t.puede() ? COLOR_RANGO[Math.min(i, COLOR_RANGO.length - 1)]
                              : 0xFF3C4356);

            // ⚠⚠ EL NOMBRE SALE DEL PROPIO TRAJE, NO DE UNA LISTA PARALELA.
            //    Aqui habia un `switch` por indice con los cinco rangos escritos
            //    a mano: una SEGUNDA lista que nada obligaba a coincidir con la
            //    del servidor. Al renombrar NOVATO se habria quedado mintiendo
            //    SIN DAR NINGUN ERROR -- la trampa de las tres listas de
            //    medallas, que ya nos costo una vez.
            var nombre = Text.translatable("pokepad.lunaeternal.rango." + t.id());
            texto(ctx, nombre, listaX() + 24, y + 8, 20,
                    t.puede() ? 0xFFFFFFFF : 0xFF8892AC, false, CONTORNO_OSCURO);

            Text pie;
            int colorPie;
            if (esKit(t)) {
                // ⚠ Un kit no se «lleva puesto»: se reclama. Por eso su pie no
                //   pasa por ninguno de los estados de abajo.
                if (t.espera() == 0) {
                    pie = Text.translatable("pokepad.lunaeternal.trajes.kit_listo");
                    colorPie = 0xFF5CD68A;
                } else {
                    pie = cuanto(t.espera());
                    colorPie = 0xFF9FB6D8;
                }
            } else if (llevo) {
                pie = Text.translatable("pokepad.lunaeternal.trajes.puesto");
                colorPie = 0xFF5CD68A;
            } else if (!t.listo()) {
                pie = Text.translatable("pokepad.lunaeternal.trajes.preparacion");
                colorPie = TEXTO_SUAVE;
            } else if (!t.puede()) {
                // ⚠⚠ YA NO DICE «necesitas el rango X», y el cambio no es de
                //    estilo: desde V028 cada traje se adquiere por separado, asi
                //    que tener el rango NO lo desbloquea. El mensaje viejo
                //    mandaba a la gente a subir de rango para conseguir algo que
                //    subir de rango no da.
                pie = Text.translatable("pokepad.lunaeternal.trajes.bloqueado");
                colorPie = 0xFFB07A3A;
            } else {
                pie = Text.translatable("pokepad.lunaeternal.trajes.disponible");
                colorPie = 0xFF9FB6D8;
            }
            texto(ctx, pie, listaX() + 24, y + 30, 14, colorPie, false, 0);
        }

        // ---- el botón --------------------------------------------------
        var sel = ficha(elegido);
        boolean llevo = loLleva(sel);
        Text etiqueta;
        boolean activo;
        int color;
        if (esKit(sel)) {
            activo = !esperando() && sel.espera() == 0;
            etiqueta = sel.espera() == 0
                    ? Text.translatable("pokepad.lunaeternal.trajes.reclamar")
                    : cuanto(sel.espera());
            color = VERDE;
        } else {
            activo = sel != null && !esperando()
                    && (llevo || (sel.listo() && sel.puede()));
            etiqueta = Text.translatable(llevo ? "pokepad.lunaeternal.trajes.quitar"
                                               : "pokepad.lunaeternal.trajes.poner");
            color = llevo ? ROJO : VERDE;
        }
        boton(ctx, rx, ry, listaX(), PANT_Y + PANT_H - MARGEN - 56, listaW(), 50,
                etiqueta, activo, color);
    }

    /**
     * El jugador, con el traje elegido puesto.
     *
     * <p>⚠⚠ EL {@code finally} NO ES OPCIONAL: ver el javadoc de la clase.
     */
    private void dibujarPrevisualizador(DrawContext ctx, int rx, int ry) {
        if (client == null || client.player == null) {
            return;
        }
        var sel = ficha(elegido);
        // ⚠⚠ UN KIT TAMBIEN SE PREVISUALIZA, aunque no este `listo`. `listo`
        //    significa «se puede EQUIPAR», y un kit no se equipa: se reclama. Su
        //    modelo existe justo para esto -- para que veas QUE te vas a llevar
        //    ANTES de gastar el reclamo de 24 h.
        boolean probando = sel != null && (sel.listo() || esKit(sel));
        if (probando) {
            Trajes.previsualizar(sel.id());
        }
        try {
            int x = px(visorX()), y = py(PANT_Y + MARGEN + 40);
            int w = pl(visorW()), h = pl(PANT_H - 2 * MARGEN - 40 - 66);
            int cx = x + w / 2, cy = y + h / 2;
            net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(
                    ctx, x, y, x + w, y + h,
                    Math.round(Math.min(w, h) * 0.34f), 0.0f, cx, cy, client.player);
        } finally {
            if (probando) {
                Trajes.previsualizar(null);
            }
        }
    }

    /** El detalle del elegido, debajo del visor. */
    private void dibujarDetalle(DrawContext ctx, int rx, int ry) {
        var sel = ficha(elegido);
        if (sel == null) {
            return;
        }
        int y = PANT_Y + PANT_H - MARGEN - 62;
        var clave = "pokepad.lunaeternal.traje." + sel.id();
        int alto = 13;
        for (String l : partir(Text.translatable(clave).getString(), visorW(), alto)) {
            texto(ctx, Text.literal(l), visorX(), y, alto, 0xFF3A4560, false, 0);
            y += alto + 4;
        }
    }

    private void dibujarVacia(DrawContext ctx) {
        var et = Text.translatable("pokepad.lunaeternal.kits.proximamente");
        int a = anchoArte(et.getString(), 22);
        texto(ctx, et, PANT_X + PANT_W / 2 - a / 2, PANT_Y + PANT_H / 2 - 20, 22,
                0xFF6E7899, false, 0);
    }

    // ---- interacción -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        int rx = (int) mx, ry = (int) my;

        int cy = PANEL_Y + NAV_ALTO / 2;
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            sonar();
            if (client != null) {
                client.setScreen(anterior);
            }
            return true;
        }
        int cxx = PANEL_X + PANEL_W - 18 - 80;
        if (dentro(rx, ry, px(cxx), py(cy) - pl(32), pl(80), pl(64))) {
            sonar();
            close();
            return true;
        }

        int w = PANEL_W - 56;
        for (int i = 0; i < PESTANAS.length; i++) {
            if (dentro(rx, ry, px(PANEL_X + 28), py(pestanaY(i)), pl(w), pl(72))) {
                pestana = i;
                sonar();
                return true;
            }
        }

        if (pestana == 0) {
            var f = fichas();
            for (int i = 0; i < f.size(); i++) {
                if (dentro(rx, ry, px(listaX()), py(filaY(i)), pl(listaW()), pl(52))) {
                    // ⚠ Un clic ELIGE y enseña; ponérselo es el botón. Con la
                    //   acción en la fila, un clic despistado te cambia de ropa.
                    elegido = i;
                    sonar();
                    return true;
                }
            }
            var sel = ficha(elegido);
            boolean llevo = loLleva(sel);
            boolean puedePulsar = esKit(sel)
                    ? sel.espera() == 0
                    : (sel != null && (llevo || (sel.listo() && sel.puede())));
            if (sel != null && !esperando() && puedePulsar
                    && dentro(rx, ry, px(listaX()), py(PANT_Y + PANT_H - MARGEN - 56),
                              pl(listaW()), pl(50))) {
                sonar();
                pulsado = System.currentTimeMillis();
                // ⚠⚠ DOS PAQUETES DISTINTOS Y NO UNO QUE SIGNIFIQUE DOS COSAS.
                //    Reutilizar `AccionTraje` mirando si el id es un kit se lee
                //    bien el dia que se escribe y mal cualquier otro.
                if (esKit(sel)) {
                    ClientPlayNetworking.send(new Red.ReclamarKit(sel.id()));
                } else {
                    ClientPlayNetworking.send(new Red.AccionTraje(llevo ? "" : sel.id()));
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, boton);
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        if (tecla == 256) {
            if (client != null) {
                client.setScreen(anterior);
            }
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
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), 0xFF10331E, Math.max(1, pl(2)));
        texto(ctx, etiqueta, ax + aw / 2, ay + ah / 2 - 11, 22,
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
