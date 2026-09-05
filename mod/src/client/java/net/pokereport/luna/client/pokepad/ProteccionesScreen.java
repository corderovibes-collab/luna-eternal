package net.pokereport.luna.client.pokepad;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;

/**
 * PROTECCIONES: tus parcelas, y el botón para soltarlas.
 *
 * <h2>⚠⚠⚠ EXISTE PORQUE LA DEL MOD NO SE PUEDE ARREGLAR</h2>
 *
 * ClaimBlocks trae la suya, y tiene dos problemas que no se resuelven con
 * configuración: es un <b>menú de cofre</b> —lo que P9-bis prohíbe con todas
 * las letras— y <b>parte de su texto está en inglés a fuego</b>. Se decompiló
 * su manejador de comandos y ahí están, fuera de {@code texts.json}:
 * {@code "--- ClaimBlocks System Admin"}, {@code "You must be looking..."},
 * {@code "Toggle boundaries"}. Y el mod es <b>ARR</b>, así que no se puede
 * parchear como se hizo con CobblemonCards, que es CC0.
 *
 * <p>Así que esto no es un capricho estético: <b>es la única forma</b> de tener
 * las protecciones enteras en español y con nuestro arte.
 *
 * <h2>⚠⚠ LO QUE SE ENSEÑA LO DECIDE EL SERVIDOR, Y LO QUE SE HACE TAMBIÉN</h2>
 *
 * La lista llega ya filtrada a las tuyas, y borrar <b>vuelve a comprobar</b> que
 * la parcela es tuya. Que aquí solo salgan las tuyas es dibujo, no una regla
 * (P6).
 *
 * <h2>⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 */
public class ProteccionesScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier ICONO =
            Identifier.of("lunaeternal", "textures/gui/pokepad/protecciones.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    private static final int MARGEN = 14;

    private static final int FILA_ALTO = 72, FILA_AIRE = 8;

    /** La banda naranja del chasis, medida sobre el PNG. Igual que en Tienda. */
    private static final int PAG_Y = 698 + (745 - 698 - 40) / 2;
    private static final int PAG_SEP = 215;

    private static final int FILA_FONDO = 0xFFBFCBE8;
    private static final int FILA_BORDE = 0xFF7C89B4;
    private static final int FILA_ENCIMA = 0xFFFFF0DC;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int ROJO = 0xFFD8443A;
    private static final int AMBAR = 0xFFE0A845;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private int pagina;

    /**
     * Cuál está esperando confirmación.
     *
     * <h2>⚠⚠ BORRAR UNA PARCELA NO SE DESHACE, Y POR ESO SE PREGUNTA</h2>
     *
     * Un clic despistado en una lista de filas iguales te suelta el terreno
     * donde tienes la casa. El botón cambia a <b>¿SEGURO?</b> y hay que volver
     * a pulsar; mover el ratón fuera lo cancela.
     */
    private String confirmando = "";

    /**
     * Qué parcela está abierta, o cadena vacía si se está viendo la lista.
     *
     * <h2>⚠⚠ DOS VISTAS EN UNA PANTALLA Y NO DOS PANTALLAS</h2>
     *
     * Una pantalla aparte obligaría a duplicar el chasis, la navegación y el
     * escalado —que ya estuvo copiado en once sitios con seis variantes
     * distintas— para enseñar la misma parcela desde otro ángulo. Y el «atrás»
     * cobra sentido solo: desde el detalle vuelve a la lista, desde la lista
     * vuelve al Pad.
     */
    private String abierta = "";

    private net.minecraft.client.gui.widget.TextFieldWidget campoNombre;
    private net.minecraft.client.gui.widget.TextFieldWidget campoMiembro;

    public ProteccionesScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.protecciones"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        // ⚠ Se conserva lo escrito al recalcular (cambio de tamaño de ventana):
        //   `init` se vuelve a llamar, y perder el nombre a medio teclear
        //   parece que la pantalla se ha reiniciado sola.
        String n = campoNombre == null ? "" : campoNombre.getText();
        String m = campoMiembro == null ? "" : campoMiembro.getText();
        campoNombre = campo(24, n);
        campoMiembro = campo(16, m);
        addSelectableChild(campoNombre);
        addSelectableChild(campoMiembro);
        ClientPlayNetworking.send(new Red.PedirProtecciones());
    }

    private net.minecraft.client.gui.widget.TextFieldWidget campo(int max, String texto) {
        var c = new net.minecraft.client.gui.widget.TextFieldWidget(
                textRenderer, 0, 0, 10, 10, Text.literal(""));
        c.setMaxLength(max);
        c.setText(texto);
        return c;
    }

    /**
     * Coloca un campo en coordenadas de arte.
     *
     * <p>⚠ Se recolocan EN CADA FOTOGRAMA y no solo en {@code init}: la
     * pantalla se reescala sola con la ventana ({@code recalcular}), y un campo
     * que se quedara donde estaba antes se dibujaría fuera de su marco.
     */
    private void colocar(net.minecraft.client.gui.widget.TextFieldWidget c,
                         int ax, int ay, int aw, int ah) {
        c.setX(px(ax));
        c.setY(py(ay));
        c.setWidth(pl(aw));
        c.setHeight(Math.max(12, pl(ah)));
    }

    /** ⚠ Delegado en {@link Escalado}: era copia literal en once pantallas. */
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

    // ---- datos -------------------------------------------------------------

    private List<Red.Parcela> parcelas() {
        var e = EstadoCliente.protecciones();
        return e == null ? List.of() : e.parcelas();
    }

    /**
     * ⚠⚠ CUÁNTAS FILAS CABEN SE CALCULA. Es la quinta vez que este proyecto
     * tropieza con un número que cuadraba por casualidad: la rejilla del
     * PokePad, las paradas, La Liga, los cosméticos y el panel de la tienda.
     */
    private int filasCaben() {
        return Math.max(1, (PANT_H - 2 * MARGEN - 46) / (FILA_ALTO + FILA_AIRE));
    }

    private int paginas() {
        int n = parcelas().size();
        return Math.max(1, (n + filasCaben() - 1) / filasCaben());
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);

        // ⚠ Si la lista encoge --acabas de borrar la última de la página 3--
        //   habría que mirar una página que ya no existe: se recoloca.
        if (pagina >= paginas()) {
            pagina = Math.max(0, paginas() - 1);
        }

        // ⚠ Si la parcela abierta desaparece --la has borrado, o te la han
        //   renombrado-- se vuelve a la lista en vez de quedarse en blanco.
        if (!abierta.isEmpty() && detalle() == null && EstadoCliente.protecciones() != null) {
            boolean sigue = false;
            for (var x : parcelas()) {
                sigue |= x.nombre().equals(abierta);
            }
            if (!sigue) {
                volverALista();
            }
        }

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanel(ctx);

        // ⚠⚠ DOS PASADAS: todo el 2D primero, `ctx.draw()`, y solo entonces los
        //    modelos. Mezclarlos pinta el 2D ENCIMA de los objetos, porque van
        //    por lotes distintos. Regla 3 de dibujado.md.
        if (abierta.isEmpty()) {
            dibujarFilas(ctx, rx, ry, false);
            dibujarPie(ctx, rx, ry);
            ctx.draw();
            dibujarFilas(ctx, rx, ry, true);
        } else {
            dibujarDetalle(ctx, rx, ry, false);
            ctx.draw();
            dibujarDetalle(ctx, rx, ry, true);
        }
    }

    // ---- el detalle --------------------------------------------------------

    private Red.DetalleParcela detalle() {
        var d = EstadoCliente.parcela();
        return d != null && d.nombre().equals(abierta) ? d : null;
    }

    private Red.Parcela laAbierta() {
        for (var x : parcelas()) {
            if (x.nombre().equals(abierta)) {
                return x;
            }
        }
        return null;
    }

    private void volverALista() {
        abierta = "";
        confirmando = "";
        EstadoCliente.olvidarParcela();
        setFocused(null);
    }

    /** Las once banderas, en dos columnas. */
    private static final int PERM_ALTO = 30;

    private int permisoY(int i) {
        return PANT_Y + 150 + (i % 6) * PERM_ALTO;
    }

    private int permisoX(int i) {
        return PANT_X + MARGEN + 396 + (i / 6) * 190;
    }

    private void dibujarDetalle(DrawContext ctx, int rx, int ry, boolean objetos) {
        var p = laAbierta();
        var d = detalle();
        if (objetos) {
            if (p != null) {
                objeto(ctx, p.pila(), PANT_X + MARGEN + 8, PANT_Y + MARGEN + 6, 44);
            }
            return;
        }
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;

        // --- cabecera
        texto(ctx, Text.literal(abierta), ax + 66, PANT_Y + MARGEN + 4, 24,
                TEXTO_OSCURO, false, true);
        if (p != null) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.tamano",
                            p.lado() + "×" + p.lado(),
                            String.format("%,d", (long) p.lado() * p.lado())),
                    ax + 66, PANT_Y + MARGEN + 32, 15, TEXTO_SUAVE, false, false);
            texto(ctx, Text.literal(p.centro().getX() + ", " + p.centro().getY()
                            + ", " + p.centro().getZ()),
                    ax + 66, PANT_Y + MARGEN + 50, 14, TEXTO_SUAVE, false, false);
        }

        if (d == null) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 20, TEXTO_SUAVE, true, false);
            return;
        }

        // --- renombrar
        texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.nombre"),
                ax, PANT_Y + 82, 15, TEXTO_SUAVE, false, false);
        colocar(campoNombre, ax, PANT_Y + 100, 250, 26);
        campoNombre.render(ctx, rx, ry, 0);
        boton(ctx, rx, ry, ax + 262, PANT_Y + 100, 110, 28,
                Text.translatable("pokepad.lunaeternal.protecciones.guardar"),
                !campoNombre.getText().trim().isEmpty(), 0xFF2E9E56);

        // --- miembros
        texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.miembros_tit"),
                ax, PANT_Y + 150, 18, TEXTO_OSCURO, false, true);
        int my = PANT_Y + 178;
        if (d.miembros().isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.sin_miembros"),
                    ax, my + 4, 14, TEXTO_SUAVE, false, false);
        }
        for (int i = 0; i < d.miembros().size() && i < 6; i++) {
            var m = d.miembros().get(i);
            int y = my + i * 28;
            ctx.fill(px(ax), py(y), px(ax + 372), py(y + 24), FILA_FONDO);
            texto(ctx, Text.literal(m.nombre()), ax + 8, y + 5, 15, TEXTO_OSCURO, false, false);
            boolean encima = dentro(rx, ry, px(ax + 340), py(y + 2), pl(28), pl(20));
            texto(ctx, Text.literal("✕"), ax + 354, y + 5, 15,
                    encima ? BORDE_ENCIMA : ROJO, true, false);
        }
        if (d.miembros().size() > 6) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.y_mas",
                            d.miembros().size() - 6),
                    ax, my + 6 * 28 + 4, 13, TEXTO_SUAVE, false, false);
        }

        // --- añadir a alguien
        int ay2 = PANT_Y + PANT_H - MARGEN - 34;
        colocar(campoMiembro, ax, ay2, 250, 26);
        campoMiembro.render(ctx, rx, ry, 0);
        if (campoMiembro.getText().isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.quien"),
                    ax + 8, ay2 + 7, 13, 0xFF8892AC, false, false);
        }
        boton(ctx, rx, ry, ax + 262, ay2, 110, 28,
                Text.translatable("pokepad.lunaeternal.protecciones.anadir"),
                !campoMiembro.getText().trim().isEmpty(), 0xFF2E9E56);

        // --- permisos
        texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.permisos"),
                PANT_X + MARGEN + 396, PANT_Y + 120, 18, TEXTO_OSCURO, false, true);
        for (int i = 0; i < d.permisos().size(); i++) {
            var q = d.permisos().get(i);
            int x = permisoX(i), y = permisoY(i);
            boolean encima = dentro(rx, ry, px(x), py(y), pl(180), pl(26));
            if (encima) {
                ctx.fill(px(x), py(y), px(x + 180), py(y + 26), FILA_ENCIMA);
            }
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.flag."
                            + q.clave()),
                    x + 4, y + 5, 14, TEXTO_OSCURO, false, false);
            // ⚠ El interruptor dice SI/NO y no un icono: «permitido» y
            //   «prohibido» con un simbolo se confunden, y aqui equivocarse es
            //   dejar la casa abierta.
            int bx = x + 132;
            ctx.fill(px(bx), py(y + 2), px(bx + 44), py(y + 22),
                    q.valor() ? 0xFF2E9E56 : 0xFF9E3A32);
            texto(ctx, Text.translatable(q.valor()
                            ? "pokepad.lunaeternal.protecciones.si"
                            : "pokepad.lunaeternal.protecciones.no"),
                    bx + 22, y + 5, 14, 0xFFFFFFFF, true, false);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.no_miembros"),
                PANT_X + MARGEN + 396, PANT_Y + 150 + 6 * PERM_ALTO + 6, 12,
                TEXTO_SUAVE, false, false);
    }

    private void boton(DrawContext ctx, int rx, int ry, int ax, int ay, int aw, int ah,
                       Text etiqueta, boolean activo, int color) {
        boolean encima = activo && dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah),
                !activo ? 0xFF6E7899 : (encima ? 0xFF4FD07A : color));
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), 0xFF20283C, Math.max(1, pl(2)));
        texto(ctx, etiqueta, ax + aw / 2, ay + ah / 2 - 8, 17,
                activo ? 0xFFFFFFFF : 0xFFD8DEEA, true, false);
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 120, 96);
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2, pl(60) + 4, pl(48) + 4,
                    BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicio"),
                PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false, false);

        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4, BORDE_ENCIMA, 2);
        }
    }

    /** Izquierda: el icono, para qué sirve, y cuánto tienes protegido. */
    private void dibujarPanel(DrawContext ctx) {
        int cx = PANEL_X + PANEL_W / 2;
        dibujarTextura(ctx, ICONO, px(cx - 60), py(PANEL_Y + NAV_ALTO + 20),
                pl(120), pl(120), 100, 100);

        texto(ctx, Text.translatable("pokepad.lunaeternal.app.protecciones"),
                cx, PANEL_Y + NAV_ALTO + 156, 26, 0xFFFFFFFF, true, false);

        int y = PANEL_Y + NAV_ALTO + 192;
        for (String linea : partir(
                Text.translatable("pokepad.lunaeternal.protecciones.explica").getString(),
                PANEL_W - 60, 15)) {
            texto(ctx, Text.literal(linea), cx, y, 15, TEXTO_SUAVE, true, false);
            y += 18;
        }

        separador(ctx, y + 14);
        y += 34;

        var e = EstadoCliente.protecciones();
        if (e == null) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                    cx, y + 20, 18, TEXTO_SUAVE, true, false);
            return;
        }

        texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.cuantas"),
                cx, y, 16, TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(String.valueOf(e.parcelas().size())),
                cx, y + 22, 34, 0xFFFFFFFF, true, false);

        // ⚠ El total se dice EN BLOQUES y no en «parcelas»: cinco de 15×15 y una
        //   de 251×251 son seis parcelas y no se parecen en nada.
        long bloques = 0;
        for (var p : e.parcelas()) {
            bloques += (long) p.lado() * p.lado();
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.bloques",
                        String.format("%,d", bloques)),
                cx, y + 64, 15, TEXTO_SUAVE, true, false);
    }

    private int filaY(int n) {
        return PANT_Y + MARGEN + n * (FILA_ALTO + FILA_AIRE);
    }

    /**
     * @param objetos {@code false} dibuja el 2D, {@code true} solo los módulos.
     *                Las dos pasadas recorren lo mismo para que las medidas no
     *                puedan separarse.
     */
    private void dibujarFilas(DrawContext ctx, int rx, int ry, boolean objetos) {
        var e = EstadoCliente.protecciones();
        if (e == null) {
            if (!objetos) {
                texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                        PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 22, TEXTO_SUAVE, true, false);
            }
            return;
        }
        // ⚠⚠ «NO TIENES NINGUNA» Y «EL SISTEMA NO ESTÁ» SE DIBUJAN IGUAL Y
        //    SIGNIFICAN LO CONTRARIO. Por eso `hayMod` viaja aparte: sin él,
        //    un mod caído se leería como «aún no has protegido nada» y el
        //    jugador se pondría a buscar un módulo que nadie puede darle.
        if (!e.hayMod()) {
            if (!objetos) {
                texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.sin_mod"),
                        PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2 - 10, 20, ROJO, true, false);
            }
            return;
        }
        var lista = e.parcelas();
        if (lista.isEmpty()) {
            if (!objetos) {
                texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.ninguna"),
                        PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2 - 30, 22, TEXTO_SUAVE,
                        true, false);
                texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.como"),
                        PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2 + 4, 16, TEXTO_SUAVE,
                        true, false);
            }
            return;
        }

        int desde = pagina * filasCaben();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= lista.size()) {
                break;
            }
            var p = lista.get(i);
            int y = filaY(n);

            if (objetos) {
                objeto(ctx, p.pila(), ax + 16, y + FILA_ALTO / 2 - 18, 36);
                continue;
            }

            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA_ALTO));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA_ALTO),
                    encima ? FILA_ENCIMA : FILA_FONDO);
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA_ALTO), FILA_BORDE, Math.max(1, pl(2)));

            // El nombre del módulo ya dice cuál es (Poké Ball, Master Ball...),
            // y lo resuelve EL CLIENTE en su idioma: el servidor no tiene idioma.
            texto(ctx, p.pila().getName().copy(), ax + 66, y + 10, 21,
                    TEXTO_OSCURO, false, true);
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.tamano",
                            p.lado() + "×" + p.lado(),
                            String.format("%,d", (long) p.lado() * p.lado())),
                    ax + 66, y + 36, 15, TEXTO_SUAVE, false, false);
            texto(ctx, Text.literal(p.centro().getX() + ", " + p.centro().getY()
                            + ", " + p.centro().getZ()),
                    ax + 66, y + 54, 14, TEXTO_SUAVE, false, false);
            if (p.miembros() > 0) {
                texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.miembros",
                                p.miembros()),
                        ax + 330, y + 54, 14, TEXTO_SUAVE, false, false);
            }

            // ⚠ Se pregunta antes de borrar: un clic despistado en una lista de
            //   filas iguales te suelta el terreno donde tienes la casa.
            boolean pidiendo = p.nombre().equals(confirmando);
            int bx = ax + aw - 190;
            boolean sobreBoton = dentro(rx, ry, px(bx), py(y + 18), pl(174), pl(38));
            ctx.fill(px(bx), py(y + 18), px(bx + 174), py(y + 56),
                    pidiendo ? (sobreBoton ? 0xFFEE5B50 : ROJO)
                             : (sobreBoton ? 0xFFE8A0A0 : 0xFFC9A2A2));
            marco(ctx, px(bx), py(y + 18), pl(174), pl(38), 0xFF3A1010, Math.max(1, pl(2)));
            texto(ctx, Text.translatable(pidiendo
                            ? "pokepad.lunaeternal.protecciones.seguro"
                            : "pokepad.lunaeternal.protecciones.borrar"),
                    bx + 87, y + 27, 19, pidiendo ? 0xFFFFFFFF : 0xFF3A1010, true, false);
        }

        // ⚠ Salir de la fila cancela la confirmación: si se quedara puesta, el
        //   siguiente clic en esa fila borraría sin preguntar.
        if (!objetos && !confirmando.isEmpty() && !sobreLaFilaDe(rx, ry, confirmando)) {
            confirmando = "";
        }
    }

    private boolean sobreLaFilaDe(int rx, int ry, String nombre) {
        var lista = parcelas();
        int desde = pagina * filasCaben();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= lista.size()) {
                break;
            }
            if (lista.get(i).nombre().equals(nombre)) {
                return dentro(rx, ry, px(ax), py(filaY(n)), pl(aw), pl(FILA_ALTO));
            }
        }
        return false;
    }

    private void dibujarPie(DrawContext ctx, int rx, int ry) {
        if (paginas() <= 1) {
            return;
        }
        int cx = PANT_X + PANT_W / 2;
        flecha(ctx, rx, ry, cx - PAG_SEP, PAG_Y, false, pagina > 0);
        flecha(ctx, rx, ry, cx + PAG_SEP - 40, PAG_Y, true, pagina < paginas() - 1);
        texto(ctx, Text.literal((pagina + 1) + " / " + paginas()), cx, PAG_Y + 10, 20,
                0xFF3A2000, true, false);
    }

    private void flecha(DrawContext ctx, int rx, int ry, int ax, int ay,
                        boolean derecha, boolean activa) {
        boolean encima = activa && dentro(rx, ry, px(ax), py(ay), pl(40), pl(40));
        int color = !activa ? 0xFF8A6A4A : (encima ? 0xFFFFFFFF : 0xFF3A2000);
        texto(ctx, Text.literal(derecha ? ">" : "<"), ax + 20, ay + 8, 26, color, true, false);
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
            // ⚠ Desde el detalle vuelve a LA LISTA, no al Pad: es lo que espera
            //   cualquiera que haya entrado a mirar una parcela, y ahorra tener
            //   dos botones de volver que hacen cosas distintas.
            if (!abierta.isEmpty()) {
                volverALista();
                return true;
            }
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

        if (!abierta.isEmpty()) {
            return clicDetalle(rx, ry, mx, my, boton) || super.mouseClicked(mx, my, boton);
        }

        if (paginas() > 1) {
            int cx = PANT_X + PANT_W / 2;
            if (pagina > 0 && dentro(rx, ry, px(cx - PAG_SEP), py(PAG_Y), pl(40), pl(40))) {
                pagina--;
                confirmando = "";
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                return true;
            }
            if (pagina < paginas() - 1
                    && dentro(rx, ry, px(cx + PAG_SEP - 40), py(PAG_Y), pl(40), pl(40))) {
                pagina++;
                confirmando = "";
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                return true;
            }
        }

        var lista = parcelas();
        int desde = pagina * filasCaben();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= lista.size()) {
                break;
            }
            var p = lista.get(i);
            int y = filaY(n);
            int bx = ax + aw - 190;
            if (!dentro(rx, ry, px(bx), py(y + 18), pl(174), pl(38))) {
                // ⚠ Pulsar la fila (fuera del botón) ABRE la parcela. El botón
                //   se comprueba PRIMERO: si no, quitar sería imposible porque
                //   el clic lo capturaría siempre la fila que hay debajo.
                if (dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA_ALTO))) {
                    abierta = p.nombre();
                    confirmando = "";
                    EstadoCliente.olvidarParcela();
                    campoNombre.setText(p.nombre());
                    campoMiembro.setText("");
                    setFocused(null);
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                    ClientPlayNetworking.send(new Red.PedirParcela(p.nombre()));
                    return true;
                }
                continue;
            }
            if (!p.nombre().equals(confirmando)) {
                // Primer clic: preguntar.
                confirmando = p.nombre();
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f);
                return true;
            }
            // ⚠ No se quita de la lista aquí: se manda y se espera. El servidor
            //   reenvía las parcelas borre o no borre, así que la pantalla
            //   vuelve sola a la verdad. Adelantarse haría que un rechazo se
            //   viera como una fila que desaparece y reaparece.
            confirmando = "";
            sonar(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.9f);
            ClientPlayNetworking.send(new Red.BorrarProteccion(p.nombre()));
            return true;
        }
        return super.mouseClicked(mx, my, boton);
    }

    private boolean clicDetalle(int rx, int ry, double mx, double my, int boton) {
        var d = detalle();
        if (d == null) {
            return false;
        }
        int ax = PANT_X + MARGEN;

        if (campoNombre.mouseClicked(mx, my, boton)) {
            setFocused(campoNombre);
            return true;
        }
        if (campoMiembro.mouseClicked(mx, my, boton)) {
            setFocused(campoMiembro);
            return true;
        }

        // Renombrar
        if (!campoNombre.getText().trim().isEmpty()
                && dentro(rx, ry, px(ax + 262), py(PANT_Y + 100), pl(110), pl(28))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
            ClientPlayNetworking.send(
                    new Red.RenombrarParcela(abierta, campoNombre.getText().trim()));
            // ⚠⚠ SE APUNTA EL NOMBRE NUEVO YA. El servidor contesta con el
            //    detalle del nombre nuevo, y si aqui siguiera el viejo,
            //    `detalle()` lo descartaria por no coincidir y la pantalla se
            //    quedaria «cargando» para siempre despues de una operacion que
            //    fue bien.
            abierta = campoNombre.getText().trim();
            return true;
        }

        // Quitar a un miembro
        int my2 = PANT_Y + 178;
        for (int i = 0; i < d.miembros().size() && i < 6; i++) {
            int y = my2 + i * 28;
            if (dentro(rx, ry, px(ax + 340), py(y + 2), pl(28), pl(20))) {
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 0.9f);
                ClientPlayNetworking.send(new Red.TocarMiembro(
                        abierta, d.miembros().get(i).nombre(), false));
                return true;
            }
        }

        // Añadir a alguien
        int ay2 = PANT_Y + PANT_H - MARGEN - 34;
        if (!campoMiembro.getText().trim().isEmpty()
                && dentro(rx, ry, px(ax + 262), py(ay2), pl(110), pl(28))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
            ClientPlayNetworking.send(new Red.TocarMiembro(
                    abierta, campoMiembro.getText().trim(), true));
            campoMiembro.setText("");
            return true;
        }

        // Permisos
        for (int i = 0; i < d.permisos().size(); i++) {
            if (dentro(rx, ry, px(permisoX(i)), py(permisoY(i)), pl(180), pl(26))) {
                var q = d.permisos().get(i);
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), q.valor() ? 0.8f : 1.2f);
                // ⚠ No se pinta el cambio aqui: se manda y se espera. El
                //   servidor reenvia el detalle acepte o rechace, asi que la
                //   pantalla vuelve sola a la verdad.
                ClientPlayNetworking.send(
                        new Red.CambiarPermiso(abierta, q.clave(), !q.valor()));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        // ⚠ ESCAPE CON UN CAMPO ENFOCADO SUELTA EL FOCO en vez de cerrar la
        //   pantalla: cerrarlo todo por querer salir de un campo molesta.
        if (tecla == 256 && getFocused() != null) {
            setFocused(null);
            return true;
        }
        if (getFocused() == campoNombre && campoNombre.keyPressed(tecla, escaneo, mods)) {
            return true;
        }
        if (getFocused() == campoMiembro && campoMiembro.keyPressed(tecla, escaneo, mods)) {
            return true;
        }
        return super.keyPressed(tecla, escaneo, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (getFocused() == campoNombre) {
            return campoNombre.charTyped(c, mods);
        }
        if (getFocused() == campoMiembro) {
            return campoMiembro.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
    }

    private void sonar(net.minecraft.sound.SoundEvent sonido, float tono) {
        if (client != null && client.player != null) {
            client.player.playSound(sonido, 0.7f, tono);
        }
    }

    // ---- utilidades --------------------------------------------------------

    private void objeto(DrawContext ctx, ItemStack pila, int ax, int ay, int altoArte) {
        float escala = altoArte * k / 16f;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(px(ax), py(ay), 0);
        m.scale(escala, escala, 1f);
        ctx.drawItem(pila, 0, 0);
        m.pop();
    }

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
