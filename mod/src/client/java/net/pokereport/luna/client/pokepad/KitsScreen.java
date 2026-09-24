package net.pokereport.luna.client.pokepad;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Util;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.client.Trajes;
import net.pokereport.luna.kit.KitCatalog;
import net.pokereport.luna.net.Red;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
    private int kitElegido;
    private int paginaMisKits;
    private String kitDetalleId;
    private boolean rotando360;
    private float rotacionYaw;
    private float rotacionPitch;
    private ItemStack stackHover;
    private KitCatalog catalogoKits;
    private String idKitCache;
    private List<ItemStack> stacksKitDetalle;
    private float scrollDetalle;
    private float maxScrollDetalle;
    private boolean arrastrandoScroll;

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

    private int huecosLibres() {
        if (client == null || client.player == null) return 0;
        int libres = 0;
        for (var stack : client.player.getInventory().main) {
            if (stack.isEmpty()) libres++;
        }
        return libres;
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

        stackHover = null;

        if (kitDetalleId != null) {
            dibujarDetalleKit(ctx, rx, ry);
        } else if (pestana == 0) {
            dibujarRango(ctx, rx, ry);
        } else {
            dibujarKits(ctx, rx, ry);
        }

        // ⚠ SEGUNDA PASADA: lo 3D va después de vaciar lo 2D. Regla 2 de
        //   dibujado.md -- mezclarlas deja el modelo debajo del panel.
        ctx.draw();
        if (kitDetalleId != null) {
            dibujarPrevisualizadorDetalle(ctx, rx, ry);
        } else if (pestana == 0) {
            dibujarPrevisualizador(ctx, rx, ry);
        } else {
            dibujarPrevisualizadoresKits(ctx);
        }

        if (pestana == 0 && kitDetalleId == null) {
            dibujarDetalle(ctx, rx, ry);
        }

        if (stackHover != null) {
            ctx.drawItemTooltip(textRenderer, stackHover, rx, ry);
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
            var kitRango = kitRango(t.id());
            if (kitRango != null) texto(ctx, Text.literal(kitRango.disponible() ? "RECLAMAR" :
                    (kitRango.espera().isBlank() ? "BLOQUEADO" : kitRango.espera())),
                    listaX() + w - 118, y + 19, 12,
                    kitRango.disponible() ? 0xFF7EF0A0 : 0xFF9CA6BC, false, 0);
        }

        // ---- el botón --------------------------------------------------
        var sel = ficha(elegido);
        boton(ctx, rx, ry, listaX(), PANT_Y + PANT_H - MARGEN - 56, listaW(), 50,
                Text.literal("VER CONTENIDO Y DETALLES 360°"), sel != null, 0xFF287BB0);
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
        var et = pestana == 2
                ? Text.literal("AÚN NO HAS ADQUIRIDO NINGÚN KIT")
                : Text.translatable("pokepad.lunaeternal.kits.proximamente");
        int a = anchoArte(et.getString(), 20);
        texto(ctx, et, PANT_X + PANT_W / 2 - a / 2, PANT_Y + PANT_H / 2 - 20, 20,
                0xFFFFFFFF, false, CONTORNO_OSCURO);
        if (pestana == 2) {
            var sub = Text.literal("Visita la pestaña 'Kits Exclusivos' para reclamar o comprar trajes legendarios.");
            int asub = anchoArte(sub.getString(), 13);
            texto(ctx, sub, PANT_X + PANT_W / 2 - asub / 2, PANT_Y + PANT_H / 2 + 6, 13,
                    TEXTO_SUAVE, false, 0);
        }
    }

    private List<Red.FichaKit> kitsVisibles() {
        var e = EstadoCliente.kits();
        if (e == null) return List.of();
        return e.fichas().stream().filter(f -> pestana == 1 ? f.tipo() == 1 : f.propio()).toList();
    }

    private void dibujarKits(DrawContext ctx, int rx, int ry) {
        var lista = kitsVisibles();
        if (lista.isEmpty()) { dibujarVacia(ctx); return; }

        int totalPaginas = Math.max(1, (lista.size() + 2) / 3);
        if (pestana != 2) paginaMisKits = 0;
        if (paginaMisKits >= totalPaginas) paginaMisKits = 0;
        int inicio = pestana == 2 ? paginaMisKits * 3 : 0;
        int fin = Math.min(lista.size(), inicio + 3);
        int totalEnPagina = fin - inicio;

        if (kitElegido < inicio || kitElegido >= fin) {
            kitElegido = inicio;
        }

        // Banner superior
        if (pestana == 1) {
            long rotaEn = EstadoCliente.rotaEnEpochMs();
            long restanteMs = Math.max(0, rotaEn - System.currentTimeMillis());
            long seg = restanteMs / 1000;
            long dias = seg / 86400;
            long horas = (seg % 86400) / 3600;
            long minutos = (seg % 3600) / 60;
            long segundos = seg % 60;
            String tiempo = String.format("⏳ LOS KITS EXCLUSIVOS SE ACTUALIZARÁN EN: %dd %02dh %02dm %02ds", dias, horas, minutos, segundos);
            int bx = PANT_X + MARGEN;
            int by = PANT_Y + 14;
            int bw = PANT_W - 2 * MARGEN;
            int bh = 30;
            ctx.fill(px(bx), py(by), px(bx + bw), py(by + bh), 0xFF10192A);
            marco(ctx, px(bx), py(by), pl(bw), pl(bh), 0xFF2E6E8C, pl(1));
            texto(ctx, Text.literal(tiempo), bx + bw / 2, by + 7, 13, ORO, true, CONTORNO_OSCURO);
        } else if (pestana == 2) {
            int bx = PANT_X + MARGEN;
            int by = PANT_Y + 14;
            int bw = PANT_W - 2 * MARGEN;
            int bh = 30;
            ctx.fill(px(bx), py(by), px(bx + bw), py(by + bh), 0xFF18152B);
            marco(ctx, px(bx), py(by), pl(bw), pl(bh), 0xFF6B3FA0, pl(1));
            texto(ctx, Text.literal("✦ COLECCIÓN DE KITS ADQUIRIDOS ✦"), bx + bw / 2, by + 7, 13, 0xFFD8B4F8, true, CONTORNO_OSCURO);
        }

        int w = (PANT_W - 2 * MARGEN - 24) / 3;
        int cardH = 368;
        for (int i = 0; i < totalEnPagina; i++) {
            int indice = inicio + i;
            var f = lista.get(indice);
            int x = PANT_X + MARGEN + i * (w + 12), y = PANT_Y + 54;
            boolean sel = indice == kitElegido;
            ctx.fill(px(x), py(y), px(x + w), py(y + cardH), sel ? 0xFF2B5580 : 0xFF287BB0);
            marco(ctx, px(x), py(y), pl(w), pl(cardH), sel ? ORO : 0xFF65BCE8, pl(sel ? 4 : 2));
            texto(ctx, Text.literal(nombreKit(f.id())), x + w / 2, y + 24, 18, 0xFFFFFFFF, true, CONTORNO_OSCURO);
            // El hueco central se pinta en la segunda pasada con la pieza real
            ctx.fill(px(x + 8), py(y + 40), px(x + w - 8), py(y + 306), 0xFF182033);
            marco(ctx, px(x + 8), py(y + 40), pl(w - 16), pl(266), 0xFF4C82A8, pl(2));
            if (pestana == 2) {
                boolean reclamado = "reclamado".equals(f.espera()) || !f.disponible();
                texto(ctx, Text.literal(reclamado ? "RECLAMADO" : "LISTO PARA RECLAMAR"),
                        x + w / 2, y + 324, 14, reclamado ? 0xFF8892AC : 0xFF5CD68A, true, CONTORNO_OSCURO);
            } else {
                texto(ctx, Text.literal(f.propio() ? "ADQUIRIDO (EN MIS KITS)" : String.format("%,d LunaCoins", f.precio())),
                        x + w / 2, y + 324, 14, f.propio() ? 0xFF7EF0A0 : ORO, true, CONTORNO_OSCURO);
            }
        }

        var f = lista.get(kitElegido);
        if (pestana == 1) {
            var s = EstadoCliente.saldo();
            long saldo = s == null ? 0 : s.reportcoins();
            boolean tieneSaldo = saldo >= KitCatalog.PRECIO_KIT_EXCLUSIVO;
            boolean disp = f.disponible() && !esperando();
            String lbl;
            boolean activo;
            int colorBtn;
            if (f.propio()) {
                lbl = "ADQUIRIDO (EN MIS KITS)";
                activo = false;
                colorBtn = APAGADO;
            } else if (!tieneSaldo) {
                long faltan = KitCatalog.PRECIO_KIT_EXCLUSIVO - saldo;
                lbl = "TE FALTAN " + String.format("%,d", faltan) + " LUNACOINS — CONSEGUIR LUNACOINS";
                activo = true;
                colorBtn = 0xFFD69A2E;
            } else if (disp) {
                lbl = "COMPRAR KIT EXCLUSIVO (" + String.format("%,d", f.precio()) + " LunaCoins)";
                activo = true;
                colorBtn = VERDE;
            } else {
                lbl = "NO DISPONIBLE";
                activo = false;
                colorBtn = APAGADO;
            }
            boton(ctx, rx, ry, PANT_X + MARGEN, PANT_Y + PANT_H - 68, PANT_W - 2 * MARGEN, 52,
                    Text.literal(lbl), activo, colorBtn);
        } else {
            // Mis Kits: botón ver detalles + paginación si procede
            int pagY = PANT_Y + PANT_H - 68;
            int totalBtnW = (totalPaginas > 1) ? (PANT_W - 2 * MARGEN - 170) : (PANT_W - 2 * MARGEN);
            boton(ctx, rx, ry, PANT_X + MARGEN, pagY, totalBtnW, 52,
                    Text.literal("VER DETALLES Y CONTENIDO"), true, 0xFF6B3FA0);

            if (totalPaginas > 1) {
                int btnAtrasX = PANT_X + PANT_W - MARGEN - 150;
                int btnSigX = PANT_X + PANT_W - MARGEN - 40;
                boolean puedeAtras = paginaMisKits > 0;
                boolean puedeSig = paginaMisKits < totalPaginas - 1;

                boton(ctx, rx, ry, btnAtrasX, pagY, 40, 52, Text.literal("◀"), puedeAtras, 0xFF4A2D70);
                texto(ctx, Text.literal((paginaMisKits + 1) + " / " + totalPaginas),
                        btnAtrasX + 55, pagY + 18, 16, 0xFFE0D4FC, true, CONTORNO_OSCURO);
                boton(ctx, rx, ry, btnSigX, pagY, 40, 52, Text.literal("▶"), puedeSig, 0xFF4A2D70);
            }
        }
    }

    /** Renderiza cada exclusivo con sus cuatro piezas reales y su arma, siempre mirando de frente fija. */
    private void dibujarPrevisualizadoresKits(DrawContext ctx) {
        if (client == null || client.player == null) return;
        var lista = kitsVisibles();
        if (lista.isEmpty()) return;

        int totalPaginas = Math.max(1, (lista.size() + 2) / 3);
        int inicio = pestana == 2 ? paginaMisKits * 3 : 0;
        int fin = Math.min(lista.size(), inicio + 3);
        int totalEnPagina = fin - inicio;

        int w = (PANT_W - 2 * MARGEN - 24) / 3;
        for (int i = 0; i < totalEnPagina; i++) {
            var f = lista.get(inicio + i);
            int x = px(PANT_X + MARGEN + i * (w + 12) + 8);
            int y = py(PANT_Y + 54 + 40);
            int ww = pl(w - 16), hh = pl(266);
            var jugador = client.player;
            var slots = new EquipmentSlot[] {
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET,
                    EquipmentSlot.MAINHAND};
            var anteriores = new ItemStack[slots.length];
            float oBodyYaw = jugador.bodyYaw;
            float oPrevBodyYaw = jugador.prevBodyYaw;
            float oHeadYaw = jugador.headYaw;
            float oPrevHeadYaw = jugador.prevHeadYaw;
            float oYaw = jugador.getYaw();
            float oPrevYaw = jugador.prevYaw;
            float oPitch = jugador.getPitch();
            float oPrevPitch = jugador.prevPitch;
            try {
                String ns = switch (f.id()) {
                    case "magikarp" -> "magikarparmor";
                    case "pikachu" -> "pikachuarmor";
                    case "eevee" -> "eeveelution";
                    case "armadura_elite", "armadura_campeon", "armadura_maestro" -> "lunaeternal";
                    default -> null;
                };
                if (ns == null) {
                    continue;
                }
                String prefijo = switch (f.id()) {
                    case "eevee" -> "eeveelution";
                    case "armadura_elite" -> "elite";
                    case "armadura_campeon" -> "campeon";
                    case "armadura_maestro" -> "maestro";
                    default -> f.id();
                };
                for (int s = 0; s < 4; s++) {
                    anteriores[s] = jugador.getEquippedStack(slots[s]).copy();
                    var item = Registries.ITEM.get(
                            Identifier.of(ns, prefijo + "_" + switch (s) {
                                case 0 -> "helmet";
                                case 1 -> "chestplate";
                                case 2 -> "leggings";
                                default -> "boots";
                            }));
                    jugador.equipStack(slots[s], new ItemStack(item));
                }
                anteriores[4] = jugador.getEquippedStack(EquipmentSlot.MAINHAND).copy();
                String swordName = switch (f.id()) {
                    case "magikarp" -> "magikarp_tidal_sword";
                    case "pikachu" -> "pikachu_volttail_sword";
                    case "eevee" -> "eevee_flareon_vaporeon_sword";
                    default -> null;
                };
                if (swordName != null) {
                    var swordItem = Registries.ITEM.get(Identifier.of("armaduraspokereport", swordName));
                    jugador.equipStack(EquipmentSlot.MAINHAND, new ItemStack(swordItem));
                }

                jugador.bodyYaw = 180.0f;
                jugador.prevBodyYaw = 180.0f;
                jugador.headYaw = 180.0f;
                jugador.prevHeadYaw = 180.0f;
                jugador.setYaw(180.0f);
                jugador.prevYaw = 180.0f;
                jugador.setPitch(0.0f);
                jugador.prevPitch = 0.0f;

                Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI);
                Quaternionf rotOverride = new Quaternionf();

                int cx = x + ww / 2;
                int cy = y + (int) (hh * 0.53f);
                int size = Math.round(Math.min(ww, hh) * 0.42f);

                ctx.enableScissor(x, y, x + ww, y + hh - pl(24));
                net.minecraft.client.render.DiffuseLighting.enableGuiDepthLighting();
                net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(
                        ctx,
                        (float) cx,
                        (float) cy,
                        (float) size,
                        new Vector3f(0f, jugador.getHeight() / 2f + 0.05f, 0f),
                        rot,
                        rotOverride,
                        jugador);
                net.minecraft.client.render.DiffuseLighting.enableGuiDepthLighting();
                ctx.disableScissor();
            } finally {
                jugador.bodyYaw = oBodyYaw;
                jugador.prevBodyYaw = oPrevBodyYaw;
                jugador.headYaw = oHeadYaw;
                jugador.prevHeadYaw = oPrevHeadYaw;
                jugador.setYaw(oYaw);
                jugador.prevYaw = oPrevYaw;
                jugador.setPitch(oPitch);
                jugador.prevPitch = oPrevPitch;
                for (int s = 0; s < slots.length; s++) {
                    if (anteriores[s] != null) {
                        jugador.equipStack(slots[s], anteriores[s]);
                    }
                }
            }
        }
    }

    private KitCatalog catalogo() {
        if (catalogoKits == null) {
            try {
                catalogoKits = KitCatalog.load();
            } catch (Exception e) {
                catalogoKits = null;
            }
        }
        return catalogoKits;
    }

    private ItemStack crearPila(KitCatalog.KitItem ki) {
        ItemStack stack = ki.pilaBase();
        if (client != null && client.world != null && !ki.encantamientos().isEmpty()) {
            var wrapper = client.world.getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
            for (var entry : ki.encantamientos().entrySet()) {
                var key = RegistryKey.of(RegistryKeys.ENCHANTMENT, entry.getKey());
                wrapper.getOptional(key).ifPresent(enc -> stack.addEnchantment(enc, entry.getValue()));
            }
        }
        return stack;
    }

    private List<ItemStack> itemsDelKit(String id) {
        if (id == null) return List.of();
        if (id.equals(idKitCache) && stacksKitDetalle != null) {
            return stacksKitDetalle;
        }
        idKitCache = id;
        var lista = new ArrayList<ItemStack>();
        var cat = catalogo();
        if (cat != null) {
            var kit = cat.byId(id);
            if (kit != null) {
                for (var it : kit.items()) {
                    lista.add(crearPila(it));
                }
            }
        }
        // Salvaguarda: garantizar que las 4 piezas de armadura estén al inicio de la lista
        String ns = switch (id) {
            case "magikarp" -> "magikarparmor";
            case "pikachu" -> "pikachuarmor";
            case "eevee" -> "eeveelution";
            default -> null;
        };
        if (ns != null) {
            String prefijo = id.equals("eevee") ? "eeveelution" : id;
            String[] tipos = {"helmet", "chestplate", "leggings", "boots"};
            for (int p = 0; p < tipos.length; p++) {
                Identifier itemId = Identifier.of(ns, prefijo + "_" + tipos[p]);
                boolean yaEsta = lista.stream().anyMatch(st -> Registries.ITEM.getId(st.getItem()).equals(itemId));
                if (!yaEsta) {
                    var item = Registries.ITEM.get(itemId);
                    if (item != null && item != net.minecraft.item.Items.AIR) {
                        var stack = new ItemStack(item);
                        if (client != null && client.world != null) {
                            var wrapper = client.world.getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
                            wrapper.getOptional(RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("protection"))).ifPresent(e -> stack.addEnchantment(e, 5));
                            wrapper.getOptional(RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("unbreaking"))).ifPresent(e -> stack.addEnchantment(e, 6));
                            wrapper.getOptional(RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("mending"))).ifPresent(e -> stack.addEnchantment(e, 1));
                        }
                        lista.add(p, stack);
                    }
                }
            }
        }
        stacksKitDetalle = lista;
        return lista;
    }

    /** Interfaz detallada de kit con 3D 360, cuadrícula de items y tooltips (estilo Diosesmon). */
    private void dibujarDetalleKit(DrawContext ctx, int rx, int ry) {
        var f = fichaDe(kitDetalleId);
        if (f == null) {
            var lista = kitsVisibles();
            if (!lista.isEmpty()) {
                f = lista.get(Math.min(kitElegido, lista.size() - 1));
                kitDetalleId = f.id();
            }
        }
        if (f == null) {
            kitDetalleId = null;
            return;
        }

        // 1. Cabecera con botón de volver y títulos
        int navY = PANT_Y + 12;
        int btnAtrasW = 100, btnAtrasH = 28;
        boolean encAtras = dentro(rx, ry, px(PANT_X + MARGEN), py(navY), pl(btnAtrasW), pl(btnAtrasH));
        ctx.fill(px(PANT_X + MARGEN), py(navY), px(PANT_X + MARGEN + btnAtrasW), py(navY + btnAtrasH),
                encAtras ? 0xFF2B5580 : 0xFF1A2234);
        marco(ctx, px(PANT_X + MARGEN), py(navY), pl(btnAtrasW), pl(btnAtrasH),
                encAtras ? ORO : 0xFF3D4E70, pl(encAtras ? 2 : 1));
        texto(ctx, Text.literal("← VOLVER"), PANT_X + MARGEN + btnAtrasW / 2, navY + 6, 15,
                encAtras ? ORO : 0xFFD8E4F8, true, CONTORNO_OSCURO);

        texto(ctx, Text.literal(nombreKit(f.id())), PANT_X + MARGEN + btnAtrasW + 14, navY + 2, 20,
                ORO, false, CONTORNO_OSCURO);
        var cat = catalogo();
        var kitDef = cat != null ? cat.byId(f.id()) : null;
        String desc = kitDef != null && !kitDef.description().isBlank()
                ? kitDef.description()
                : (pestana == 0 ? "Armadura de rango 3D, arsenal de combate y suministros exclusivos."
                                : "Armadura GeckoLib, arsenal 3D y suministros competitivos exclusivos.");
        texto(ctx, Text.literal(desc), PANT_X + MARGEN + btnAtrasW + 14, navY + 22, 12,
                TEXTO_SUAVE, false, 0);

        // 2. Columna izquierda: visor 3D
        int vx = PANT_X + MARGEN;
        int vy = PANT_Y + 48;
        int vw = 240;
        int vh = PANT_H - MARGEN - 48 - 56;
        ctx.fill(px(vx), py(vy), px(vx + vw), py(vy + vh), 0xFF121724);
        marco(ctx, px(vx), py(vy), pl(vw), pl(vh), 0xFF39415C, pl(2));

        // Botón 360 al pie del visor
        int b360X = vx + 14, b360Y = vy + vh - 34, b360W = vw - 28, b360H = 26;
        boolean enc360 = dentro(rx, ry, px(b360X), py(b360Y), pl(b360W), pl(b360H));
        ctx.fill(px(b360X), py(b360Y), px(b360X + b360W), py(b360Y + b360H),
                rotando360 ? 0xFF287BB0 : (enc360 ? 0xFF2A344A : 0xFF1B2232));
        marco(ctx, px(b360X), py(b360Y), pl(b360W), pl(b360H),
                rotando360 ? ORO : (enc360 ? BORDE_ENCIMA : 0xFF3D4A6B), pl(rotando360 ? 2 : 1));
        texto(ctx, Text.literal(rotando360 ? "ROTACIÓN 360° [ACTIVA]" : "VISTA 360°"),
                b360X + b360W / 2, b360Y + 7, 12,
                rotando360 ? 0xFFFFFFFF : (enc360 ? ORO : 0xFFB0C0DC), true, CONTORNO_OSCURO);

        // 3. Columna derecha: panel de contenido y cuadrícula de slots
        int cx = vx + vw + 14;
        int cy = vy;
        int cw = PANT_W - 2 * MARGEN - vw - 14;
        int ch = vh;
        ctx.fill(px(cx), py(cy), px(cx + cw), py(cy + ch), 0xFF121724);
        marco(ctx, px(cx), py(cy), pl(cw), pl(ch), 0xFF39415C, pl(2));

        // Cabecera interna del panel derecho
        texto(ctx, Text.literal("CONTENIDO DEL KIT"), cx + 16, cy + 12, 16, 0xFF56C8D6, false, CONTORNO_OSCURO);
        if (pestana == 1) {
            String badge = f.propio() ? "ADQUIRIDO (EN MIS KITS)" : String.format("%,d LunaCoins", f.precio());
            int colBadge = f.propio() ? 0xFF7EF0A0 : ORO;
            int bw = anchoArte(badge, 13);
            texto(ctx, Text.literal(badge), cx + cw - 16 - bw, cy + 14, 13, colBadge, false, CONTORNO_OSCURO);
        } else if (pestana == 0) {
            String badge;
            int colBadge;
            if (f.disponible()) {
                badge = "LISTO PARA RECLAMAR";
                colBadge = 0xFF5CD68A;
            } else if (f.espera() != null && !f.espera().isBlank()) {
                if (f.espera().toLowerCase().contains("superado")) {
                    badge = "BLOQUEADO (RANGO SUPERADO)";
                    colBadge = 0xFFE07040;
                } else if (f.espera().toLowerCase().contains("rango") || f.espera().toLowerCase().contains("bloqueado")) {
                    badge = "BLOQUEADO (REQUIERE RANGO)";
                    colBadge = 0xFFE07040;
                } else {
                    badge = "DISPONIBLE EN: " + f.espera().toUpperCase();
                    colBadge = 0xFF9FB6D8;
                }
            } else {
                badge = "BLOQUEADO (REQUIERE RANGO)";
                colBadge = 0xFFE07040;
            }
            int bw = anchoArte(badge, 13);
            texto(ctx, Text.literal(badge), cx + cw - 16 - bw, cy + 14, 13, colBadge, false, CONTORNO_OSCURO);
        } else {
            boolean yaReclamado = "reclamado".equals(f.espera()) || !f.disponible();
            String badge = yaReclamado ? "RECLAMADO" : "LISTO PARA RECLAMAR";
            int colBadge = yaReclamado ? 0xFF8892AC : 0xFF5CD68A;
            int bw = anchoArte(badge, 13);
            texto(ctx, Text.literal(badge), cx + cw - 16 - bw, cy + 14, 13, colBadge, false, CONTORNO_OSCURO);
        }

        // Línea divisoria
        ctx.fill(px(cx + 12), py(cy + 30), px(cx + cw - 12), py(cy + 31), 0xFF2A344A);

        // Cuadrícula espaciosa de items grandes con desplazamiento vertical (scroll)
        var items = itemsDelKit(f.id());
        int cols = 5;
        int slotW = 88;
        int slotH = 64;
        int gapX = 10;
        int gapY = 10;
        int paddingY = 8;
        int viewH = ch - 39;
        int rows = Math.max(1, (items.size() + cols - 1) / cols);
        int totalGridH = rows * slotH + (rows - 1) * gapY;
        maxScrollDetalle = Math.max(0f, (totalGridH + paddingY * 2) - viewH);
        scrollDetalle = Math.max(0f, Math.min(scrollDetalle, maxScrollDetalle));

        int gridTotalW = cols * slotW + (cols - 1) * gapX;
        // Margen horizontal restando el hueco para la barra de scroll
        int gx0 = cx + 8 + Math.max(0, ((cw - 22) - gridTotalW) / 2);
        int gy0 = cy + 33 + paddingY + (maxScrollDetalle <= 0 ? Math.max(0, (viewH - totalGridH) / 2) : 0) - (int) scrollDetalle;

        // Recorte scissor estricto para que absolutamente ningún item ni borde se salga del panel
        int scissorX1 = px(cx + 4);
        int scissorY1 = py(cy + 32);
        int scissorX2 = px(cx + cw - 4);
        int scissorY2 = py(cy + ch - 4);

        ctx.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);

        for (int i = 0; i < items.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int sx = gx0 + col * (slotW + gapX);
            int sy = gy0 + row * (slotH + gapY);

            int boxX = px(sx);
            int boxY = py(sy);
            int boxW = pl(slotW);
            int boxH = pl(slotH);

            // Optimización: omitir dibujar celdas fuera del rango visible
            if (boxY + boxH < scissorY1 || boxY > scissorY2) {
                continue;
            }

            boolean enc = dentro(rx, ry, boxX, boxY, boxW, boxH)
                    && dentro(rx, ry, scissorX1, scissorY1, scissorX2 - scissorX1, scissorY2 - scissorY1);
            ItemStack stack = items.get(i);

            ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, enc ? 0xFF283650 : 0xFF192030);
            marco(ctx, boxX, boxY, boxW, boxH, enc ? ORO : 0xFF364463, pl(enc ? 2 : 1));

            float targetSize = Math.max(26f, Math.min(boxW - pl(12), boxH - pl(12)));
            float scale = targetSize / 16.0f;
            float ix = boxX + (boxW - 16f * scale) / 2f;
            float iy = boxY + (boxH - 16f * scale) / 2f;

            MatrixStack matrices = ctx.getMatrices();
            matrices.push();
            matrices.translate(ix, iy, 0);
            matrices.scale(scale, scale, 1.0f);
            ctx.drawItem(stack, 0, 0);
            matrices.pop();

            if (stack.getCount() > 1) {
                String countStr = String.valueOf(stack.getCount());
                int strW = textRenderer.getWidth(countStr);
                matrices.push();
                matrices.translate(0, 0, 200);
                ctx.drawText(textRenderer, countStr, boxX + boxW - strW - pl(5), boxY + boxH - textRenderer.fontHeight - pl(3), 0xFFFFFFFF, true);
                matrices.pop();
            } else if (stack.isDamaged()) {
                int barW = boxW - pl(12);
                int barX = boxX + pl(6);
                int barY = boxY + boxH - pl(6);
                float ratio = (float) (stack.getMaxDamage() - stack.getDamage()) / (float) stack.getMaxDamage();
                ctx.fill(barX, barY, barX + barW, barY + pl(2), 0xFF000000);
                ctx.fill(barX, barY, barX + Math.round(barW * ratio), barY + pl(1), 0xFF00FF00);
            }

            if (enc) {
                stackHover = stack;
            }
        }

        ctx.disableScissor();

        // Barra de desplazamiento estilizada (scrollbar) a la derecha
        if (maxScrollDetalle > 0) {
            int sbX = px(cx + cw - 13);
            int sbY = py(cy + 35);
            int sbW = pl(6);
            int sbH = pl(ch - 42);

            // Pista
            ctx.fill(sbX, sbY, sbX + sbW, sbY + sbH, 0xFF141926);
            marco(ctx, sbX, sbY, sbW, sbH, 0xFF28344A, 1);

            // Control deslizante
            int thumbH = Math.max(pl(28), Math.round((float) viewH / (float) (totalGridH + paddingY * 2) * sbH));
            int thumbY = sbY + Math.round((sbH - thumbH) * (scrollDetalle / maxScrollDetalle));
            boolean encThumb = dentro(rx, ry, sbX - 2, thumbY, sbW + 4, thumbH);
            int colThumb = arrastrandoScroll ? ORO : (encThumb ? 0xFF65BCE8 : 0xFF3D5A80);
            ctx.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, colThumb);
            marco(ctx, sbX, thumbY, sbW, thumbH, arrastrandoScroll || encThumb ? ORO : 0xFF5278A6, 1);
        }

        // 4. Botón inferior de acción con comprobación estricta de inventario
        int btnY = PANT_Y + PANT_H - MARGEN - 50;
        int btnW = PANT_W - 2 * MARGEN;
        int btnH = 46;
        int libres = huecosLibres();
        int necesarios = Math.min(items.size(), 24);
        boolean espacioSuficiente = libres >= necesarios;

        String lbl;
        boolean activo;
        int colorBtn;
        if (pestana == 0) {
            if (f.disponible()) {
                if (!espacioSuficiente) {
                    lbl = "INVENTARIO LLENO (FALTAN " + (necesarios - libres) + " ESPACIOS LIBRES)";
                    activo = false;
                    colorBtn = ROJO;
                } else if (!esperando()) {
                    lbl = "RECLAMAR KIT DE RANGO";
                    activo = true;
                    colorBtn = VERDE;
                } else {
                    lbl = "PROCESANDO...";
                    activo = false;
                    colorBtn = APAGADO;
                }
            } else if (f.espera() != null && !f.espera().isBlank()) {
                if (f.espera().toLowerCase().contains("superado")) {
                    lbl = "BLOQUEADO (RANGO SUPERADO)";
                } else if (f.espera().toLowerCase().contains("rango") || f.espera().toLowerCase().contains("bloqueado")) {
                    lbl = f.espera().toUpperCase();
                } else {
                    lbl = "DISPONIBLE EN: " + f.espera().toUpperCase();
                }
                activo = false;
                colorBtn = APAGADO;
            } else {
                lbl = "NO DISPONIBLE (REQUIERE RANGO)";
                activo = false;
                colorBtn = APAGADO;
            }
        } else if (pestana == 1) {
            var s = EstadoCliente.saldo();
            long saldo = s == null ? 0 : s.reportcoins();
            boolean tieneSaldo = saldo >= KitCatalog.PRECIO_KIT_EXCLUSIVO;
            if (f.propio()) {
                lbl = "ADQUIRIDO (EN MIS KITS)";
                activo = false;
                colorBtn = APAGADO;
            } else if (!tieneSaldo) {
                long faltan = KitCatalog.PRECIO_KIT_EXCLUSIVO - saldo;
                lbl = "TE FALTAN " + String.format("%,d", faltan) + " LUNACOINS — CONSEGUIR LUNACOINS";
                activo = true;
                colorBtn = 0xFFD69A2E;
            } else if (f.disponible() && !esperando()) {
                lbl = "COMPRAR KIT EXCLUSIVO (" + String.format("%,d", f.precio()) + " LunaCoins)";
                activo = true;
                colorBtn = VERDE;
            } else {
                lbl = "NO DISPONIBLE";
                activo = false;
                colorBtn = APAGADO;
            }
        } else {
            boolean yaReclamado = "reclamado".equals(f.espera()) || !f.disponible();
            if (yaReclamado) {
                lbl = "CONTENIDO YA RECLAMADO";
                activo = false;
                colorBtn = APAGADO;
            } else if (!espacioSuficiente) {
                lbl = "INVENTARIO LLENO (FALTAN " + (necesarios - libres) + " ESPACIOS LIBRES)";
                activo = false;
                colorBtn = ROJO;
            } else if (!esperando()) {
                lbl = "RECLAMAR CONTENIDO DEL KIT";
                activo = true;
                colorBtn = VERDE;
            } else {
                lbl = "PROCESANDO...";
                activo = false;
                colorBtn = APAGADO;
            }
        }
        boton(ctx, rx, ry, PANT_X + MARGEN, btnY, btnW, btnH,
                Text.literal(lbl), activo, colorBtn);
    }

    private void dibujarPrevisualizadorDetalle(DrawContext ctx, int rx, int ry) {
        if (client == null || client.player == null || kitDetalleId == null) return;
        int vx = px(PANT_X + MARGEN);
        int vy = py(PANT_Y + 48);
        int vw = pl(240);
        int vh = pl(PANT_H - MARGEN - 48 - 56);

        var jugador = client.player;
        var slots = new EquipmentSlot[] {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND};
        var anteriores = new ItemStack[slots.length];
        float oBodyYaw = jugador.bodyYaw;
        float oPrevBodyYaw = jugador.prevBodyYaw;
        float oHeadYaw = jugador.headYaw;
        float oPrevHeadYaw = jugador.prevHeadYaw;
        float oYaw = jugador.getYaw();
        float oPrevYaw = jugador.prevYaw;
        float oPitch = jugador.getPitch();
        float oPrevPitch = jugador.prevPitch;

        try {
            // El visor usa las mismas pilas encantadas que el contenido y la entrega.
            var contenido = itemsDelKit(kitDetalleId);
            for (int s = 0; s < slots.length; s++) {
                anteriores[s] = jugador.getEquippedStack(slots[s]).copy();
                ItemStack vista = ItemStack.EMPTY;
                for (var pila : contenido) {
                    boolean corresponde = slots[s] == EquipmentSlot.MAINHAND
                            ? pila.getItem() instanceof net.minecraft.item.SwordItem
                            : pila.getItem() instanceof net.minecraft.item.ArmorItem armadura
                                && armadura.getSlotType() == slots[s];
                    if (corresponde) {
                        vista = pila.copy();
                        vista.setCount(1);
                        break;
                    }
                }
                jugador.equipStack(slots[s], vista);
            }

            if (rotando360) {
                rotacionYaw = (rotacionYaw + 1.2f) % 360f;
            }

            float yaw = 180.0f + rotacionYaw;
            jugador.bodyYaw = yaw;
            jugador.prevBodyYaw = yaw;
            jugador.headYaw = yaw;
            jugador.prevHeadYaw = yaw;
            jugador.setYaw(yaw);
            jugador.prevYaw = yaw;
            jugador.setPitch(-rotacionPitch);
            jugador.prevPitch = -rotacionPitch;

            Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf rotOverride = new Quaternionf().rotateX((float) Math.toRadians(rotacionPitch));
            rot.mul(rotOverride);

            int cx = vx + vw / 2;
            int cy = vy + vh / 2 - pl(12);
            int size = Math.round(Math.min(vw, vh) * 0.44f);

            ctx.enableScissor(vx, vy, vx + vw, vy + vh - pl(38));
            net.minecraft.client.render.DiffuseLighting.enableGuiDepthLighting();
            net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(
                    ctx,
                    (float) cx,
                    (float) cy,
                    (float) size,
                    new Vector3f(0f, jugador.getHeight() / 2f, 0f),
                    rot,
                    rotOverride,
                    jugador);
            net.minecraft.client.render.DiffuseLighting.enableGuiDepthLighting();
            ctx.disableScissor();
        } finally {
            jugador.bodyYaw = oBodyYaw;
            jugador.prevBodyYaw = oPrevBodyYaw;
            jugador.headYaw = oHeadYaw;
            jugador.prevHeadYaw = oPrevHeadYaw;
            jugador.setYaw(oYaw);
            jugador.prevYaw = oPrevYaw;
            jugador.setPitch(oPitch);
            jugador.prevPitch = oPrevPitch;
            for (int s = 0; s < slots.length; s++) {
                if (anteriores[s] != null) {
                    jugador.equipStack(slots[s], anteriores[s]);
                }
            }
        }
    }

    private static String nombreKit(String id) {
        return switch (id) {
            case "magikarp" -> "MAGIKARP TIDAL";
            case "pikachu" -> "PIKACHU THUNDERFORGE";
            case "eevee" -> "EEVEELUTION LEGACY";
            case "entrenador" -> "KIT ENTRENADOR";
            case "elite" -> "KIT ÉLITE";
            case "campeon" -> "KIT CAMPEÓN";
            case "maestro" -> "KIT MAESTRO";
            case "leyenda" -> "KIT LEYENDA";
            case "armadura_elite" -> "ARMADURA ÉLITE";
            case "armadura_campeon" -> "ARMADURA CAMPEÓN";
            case "armadura_maestro" -> "ARMADURA MAESTRO";
            default -> id.toUpperCase(java.util.Locale.ROOT);
        };
    }

    private Red.FichaKit fichaDe(String id) {
        if (id == null) return null;
        var e = EstadoCliente.kits();
        if (e != null) {
            for (var fk : e.fichas()) {
                if (fk.id().equals(id)) return fk;
            }
        }
        var fTraje = fichas();
        for (var ft : fTraje) {
            if (ft.id().equals(id)) {
                return new Red.FichaKit(id, 0, 0, false, ft.puede(),
                        ft.espera() > 0 ? cuanto(ft.espera()).getString() : (ft.puede() ? "" : "BLOQUEADO (REQUIERE RANGO)"));
            }
        }
        return new Red.FichaKit(id, 0, 0, false, false, "");
    }

    private Red.FichaKit kitRango(String id) {
        var e = EstadoCliente.kits();
        if (e == null) return null;
        return e.fichas().stream().filter(f -> f.tipo() == 0 && f.id().equals(id)).findFirst().orElse(null);
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

        // Clic en botón MAS (+) de saldo: abrir tienda oficial
        int sy = PANEL_Y + PANEL_H - 92, sx = PANEL_X + 28, sw = PANEL_W - 56;
        int sbx = sx + sw - 52;
        if (dentro(rx, ry, px(sbx), py(sy + 12), pl(40), pl(40))) {
            abrirTiendaWeb();
            return true;
        }

        int w = PANEL_W - 56;
        for (int i = 0; i < PESTANAS.length; i++) {
            if (dentro(rx, ry, px(PANEL_X + 28), py(pestanaY(i)), pl(w), pl(72))) {
                pestana = i;
                kitElegido = 0;
                paginaMisKits = 0;
                kitDetalleId = null;
                scrollDetalle = 0f;
                arrastrandoScroll = false;
                rotando360 = false;
                rotacionYaw = 0f;
                rotacionPitch = 0f;
                sonar();
                return true;
            }
        }

        if (kitDetalleId != null) {
            // Clic en Volver
            int navY = PANT_Y + 12;
            int btnAtrasW = 100, btnAtrasH = 28;
            if (dentro(rx, ry, px(PANT_X + MARGEN), py(navY), pl(btnAtrasW), pl(btnAtrasH))) {
                kitDetalleId = null;
                scrollDetalle = 0f;
                arrastrandoScroll = false;
                rotando360 = false;
                rotacionYaw = 0f;
                rotacionPitch = 0f;
                sonar();
                return true;
            }
            // Clic en Botón 360
            int vx = PANT_X + MARGEN;
            int vy = PANT_Y + 48;
            int vw = 240;
            int vh = PANT_H - MARGEN - 48 - 56;
            int b360X = vx + 14, b360Y = vy + vh - 34, b360W = vw - 28, b360H = 26;
            if (dentro(rx, ry, px(b360X), py(b360Y), pl(b360W), pl(b360H))) {
                rotando360 = !rotando360;
                sonar();
                return true;
            }
            // Clic en Barra de Scroll
            int cx = vx + vw + 14;
            cy = vy;
            int cw = PANT_W - 2 * MARGEN - vw - 14;
            int ch = vh;
            if (maxScrollDetalle > 0) {
                int sbX = px(cx + cw - 18);
                int sbY = py(cy + 35);
                int sbW = pl(14);
                int sbH = pl(ch - 42);
                if (dentro(rx, ry, sbX, sbY, sbW, sbH)) {
                    arrastrandoScroll = true;
                    int viewH = ch - 39;
                    int rows = Math.max(1, (itemsDelKit(kitDetalleId).size() + 5 - 1) / 5);
                    int totalGridH = rows * 64 + (rows - 1) * 10;
                    int thumbH = Math.max(pl(28), Math.round((float) viewH / (float) (totalGridH + 16) * sbH));
                    float frac = Math.max(0f, Math.min(1f, (float)(ry - sbY - thumbH / 2) / (float)(sbH - thumbH)));
                    scrollDetalle = frac * maxScrollDetalle;
                    return true;
                }
            }

            // Clic en Botón de Comprar / Reclamar
            var f = fichaDe(kitDetalleId);
            int btnY = PANT_Y + PANT_H - MARGEN - 50;
            int btnW = PANT_W - 2 * MARGEN;
            int btnH = 46;
            int libres = huecosLibres();
            int necesarios = f != null ? Math.min(itemsDelKit(f.id()).size(), 24) : 0;
            boolean espacioSuficiente = libres >= necesarios;

            if (pestana == 0) {
                if (f != null && f.disponible() && !esperando() && espacioSuficiente
                        && dentro(rx, ry, px(PANT_X + MARGEN), py(btnY), pl(btnW), pl(btnH))) {
                    pulsado = System.currentTimeMillis();
                    sonar();
                    ClientPlayNetworking.send(new Red.ReclamarKit(f.id()));
                    return true;
                }
            } else if (pestana == 1) {
                if (f != null && !f.propio() && !esperando()
                        && dentro(rx, ry, px(PANT_X + MARGEN), py(btnY), pl(btnW), pl(btnH))) {
                    var s = EstadoCliente.saldo();
                    long saldo = s == null ? 0 : s.reportcoins();
                    if (saldo < KitCatalog.PRECIO_KIT_EXCLUSIVO) {
                        abrirTiendaWeb();
                    } else if (f.disponible()) {
                        pulsado = System.currentTimeMillis();
                        sonar();
                        ClientPlayNetworking.send(new Red.ReclamarKit(f.id()));
                    }
                    return true;
                }
            } else if (pestana == 2) {
                boolean yaReclamado = f != null && ("reclamado".equals(f.espera()) || !f.disponible());
                if (f != null && f.propio() && !yaReclamado && !esperando() && espacioSuficiente
                        && dentro(rx, ry, px(PANT_X + MARGEN), py(btnY), pl(btnW), pl(btnH))) {
                    pulsado = System.currentTimeMillis();
                    sonar();
                    ClientPlayNetworking.send(new Red.ReclamarKit(f.id()));
                    return true;
                }
            }
            return true;
        } else if (pestana == 0) {
            var f = fichas();
            for (int i = 0; i < f.size(); i++) {
                if (dentro(rx, ry, px(listaX()), py(filaY(i)), pl(listaW()), pl(52))) {
                    elegido = i;
                    kitDetalleId = f.get(i).id();
                    scrollDetalle = 0f;
                    arrastrandoScroll = false;
                    rotando360 = false;
                    rotacionYaw = 0f;
                    rotacionPitch = 0f;
                    sonar();
                    return true;
                }
            }
            var sel = ficha(elegido);
            if (sel != null && dentro(rx, ry, px(listaX()), py(PANT_Y + PANT_H - MARGEN - 56),
                    pl(listaW()), pl(50))) {
                kitDetalleId = sel.id();
                scrollDetalle = 0f;
                arrastrandoScroll = false;
                rotando360 = false;
                rotacionYaw = 0f;
                rotacionPitch = 0f;
                sonar();
                return true;
            }
        } else {
            var lista = kitsVisibles();
            int totalPaginas = Math.max(1, (lista.size() + 2) / 3);
            int inicio = pestana == 2 ? paginaMisKits * 3 : 0;
            int fin = Math.min(lista.size(), inicio + 3);
            int cardW = (PANT_W - 2 * MARGEN - 24) / 3;

            for (int i = 0; i < (fin - inicio); i++) {
                int indice = inicio + i;
                int x = PANT_X + MARGEN + i * (cardW + 12), y = PANT_Y + 54;
                if (dentro(rx, ry, px(x), py(y), pl(cardW), pl(350))) {
                    kitElegido = indice;
                    kitDetalleId = lista.get(indice).id();
                    scrollDetalle = 0f;
                    arrastrandoScroll = false;
                    rotando360 = false;
                    rotacionYaw = 0f;
                    rotacionPitch = 0f;
                    sonar();
                    return true;
                }
            }

            // Mis Kits: flechas de paginación
            if (pestana == 2 && totalPaginas > 1) {
                int pagY = PANT_Y + PANT_H - 68;
                int btnAtrasX = PANT_X + PANT_W - MARGEN - 150;
                int btnSigX = PANT_X + PANT_W - MARGEN - 40;
                if (dentro(rx, ry, px(btnAtrasX), py(pagY), pl(40), pl(52))) {
                    if (paginaMisKits > 0) {
                        paginaMisKits--;
                        sonar();
                        return true;
                    }
                }
                if (dentro(rx, ry, px(btnSigX), py(pagY), pl(40), pl(52))) {
                    if (paginaMisKits < totalPaginas - 1) {
                        paginaMisKits++;
                        sonar();
                        return true;
                    }
                }
            }

            // Botón inferior
            if (pestana == 1 && kitElegido < lista.size()) {
                var f = lista.get(kitElegido);
                if (!f.propio() && !esperando()
                        && dentro(rx, ry, px(PANT_X + MARGEN), py(PANT_Y + PANT_H - 68), pl(PANT_W - 2 * MARGEN), pl(52))) {
                    var s = EstadoCliente.saldo();
                    long saldo = s == null ? 0 : s.reportcoins();
                    if (saldo < KitCatalog.PRECIO_KIT_EXCLUSIVO) {
                        abrirTiendaWeb();
                    } else if (f.disponible()) {
                        pulsado = System.currentTimeMillis();
                        sonar();
                        ClientPlayNetworking.send(new Red.ReclamarKit(f.id()));
                    }
                    return true;
                }
            } else if (pestana == 2 && kitElegido < lista.size()) {
                int btnW = (totalPaginas > 1) ? (PANT_W - 2 * MARGEN - 170) : (PANT_W - 2 * MARGEN);
                if (dentro(rx, ry, px(PANT_X + MARGEN), py(PANT_Y + PANT_H - 68), pl(btnW), pl(52))) {
                    kitDetalleId = lista.get(kitElegido).id();
                    scrollDetalle = 0f;
                    arrastrandoScroll = false;
                    rotando360 = false;
                    rotacionYaw = 0f;
                    rotacionPitch = 0f;
                    sonar();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, boton);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int boton, double dx, double dy) {
        if (arrastrandoScroll && maxScrollDetalle > 0 && kitDetalleId != null) {
            int cy = PANT_Y + 48;
            int ch = PANT_H - MARGEN - 48 - 56;
            int sbY = py(cy + 35);
            int sbH = pl(ch - 42);
            int viewH = ch - 39;
            int rows = Math.max(1, (itemsDelKit(kitDetalleId).size() + 5 - 1) / 5);
            int totalGridH = rows * 64 + (rows - 1) * 10;
            int thumbH = Math.max(pl(28), Math.round((float) viewH / (float) (totalGridH + 16) * sbH));
            float frac = Math.max(0f, Math.min(1f, (float)((int)my - sbY - thumbH / 2) / (float)(sbH - thumbH)));
            scrollDetalle = frac * maxScrollDetalle;
            return true;
        }
        if (kitDetalleId != null) {
            int vx = px(PANT_X + MARGEN);
            int vy = py(PANT_Y + 48);
            int vw = pl(240);
            int vh = pl(PANT_H - MARGEN - 48 - 56);
            if (dentro((int) mx, (int) my, vx, vy, vw, vh)) {
                rotacionYaw = (rotacionYaw + (float) dx * 1.5f) % 360f;
                rotacionPitch = Math.max(-35f, Math.min(35f, rotacionPitch + (float) dy * 1.0f));
                return true;
            }
        }
        return super.mouseDragged(mx, my, boton, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int boton) {
        arrastrandoScroll = false;
        return super.mouseReleased(mx, my, boton);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (kitDetalleId != null && maxScrollDetalle > 0) {
            int vx = PANT_X + MARGEN;
            int vw = 240;
            int cx = vx + vw + 14;
            int cy = PANT_Y + 48;
            int cw = PANT_W - 2 * MARGEN - vw - 14;
            int ch = PANT_H - MARGEN - 48 - 56;
            if (dentro((int) mx, (int) my, px(cx), py(cy), pl(cw), pl(ch))) {
                scrollDetalle = Math.max(0f, Math.min(scrollDetalle - (float) v * 28f, maxScrollDetalle));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        if (tecla == 256) {
            if (kitDetalleId != null) {
                kitDetalleId = null;
                scrollDetalle = 0f;
                arrastrandoScroll = false;
                rotando360 = false;
                rotacionYaw = 0f;
                rotacionPitch = 0f;
                sonar();
                return true;
            }
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

    private void abrirTiendaWeb() {
        sonar();
        net.pokereport.luna.client.Enlaces.abrirTienda(client, this);
    }
}
