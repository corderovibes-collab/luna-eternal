package net.pokereport.luna.client.pokepad;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;

/**
 * PROTECCIONES: tus parcelas, y todo lo que se hace con ellas.
 *
 * <h2>⚠⚠⚠ EXISTE PORQUE LA DEL MOD NO SE PUEDE ARREGLAR</h2>
 *
 * ClaimBlocks trae la suya y tiene dos problemas que no se resuelven con
 * configuración: es un <b>menú de cofre</b> —lo que P9-bis prohíbe con todas
 * las letras— y parte de su texto está <b>en inglés a fuego</b>, fuera de su
 * {@code texts.json}. Y el mod es <b>ARR</b>: no se puede parchear como se hizo
 * con CobblemonCards, que es CC0.
 *
 * <h2>⚠⚠ TRES PESTAÑAS, Y NO UNA PANTALLA APRETADA</h2>
 *
 * La primera versión metía nombre, once permisos, miembros y el mensaje de
 * entrada <b>en la misma vista</b>, y el usuario lo dijo mirándola: <i>«el
 * texto no se aprecia muy bien y hay textos sobreexpuestos»</i>. Con todo junto
 * la única salida era encoger la letra, y una letra que no se lee no informa de
 * nada. <b>Repartirlo en pestañas sale gratis</b> —una pestaña es un clic— y
 * deja escribir grande.
 *
 * <h2>⚠⚠ EL CONTORNO DE TEXTO ES PARA LO CLARO SOBRE OSCURO, Y AQUÍ ERA AL REVÉS</h2>
 *
 * El nombre de la fila se dibujaba con contorno claro <b>y en negrita</b> sobre
 * un panel claro: las cuatro copias desplazadas del contorno emborronaban las
 * letras y parecían texto duplicado. Sobre fondo claro, el texto oscuro va
 * <b>sin contorno</b>. Y sin los códigos de color del módulo, que además lo
 * ponían en negrita a dos colores.
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

    /**
     * ⚠⚠ LA FILA CRECE DE 72 A 92 Y SE PIERDE UNA POR PÁGINA, a propósito. Con
     * 72, para que cupieran tres líneas la letra tenía que bajar a 21; con 92
     * cabe a 26 y se lee de un vistazo. Cuatro por página, y pagina — que es
     * justo lo que pidió el usuario: «no importa si hay que crear otra página».
     */
    private static final int FILA_ALTO = 92, FILA_AIRE = 10;

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
    private static final int SEPARADOR = 0xFF9AA6C4;
    private static final int ROJO = 0xFFD8443A;
    private static final int VERDE = 0xFF2E9E56;

    // ---- las tres pestañas del detalle
    private static final int GENERAL = 0, PERMISOS = 1, MIEMBROS = 2;
    private static final String[] PESTANAS = {"general", "permisos", "miembros"};
    private static final int PEST_Y = PANT_Y + 78, PEST_ALTO = 38, PEST_ANCHO = 250;
    /** Donde empieza el contenido de la pestaña, debajo de su barra. */
    private static final int CONT_Y = PEST_Y + PEST_ALTO + 16;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private int pagina;

    /**
     * Cuál está esperando confirmación.
     *
     * <p>⚠⚠ QUITAR UNA PARCELA NO SE DESHACE, Y POR ESO SE PREGUNTA. Un clic
     * despistado en una lista de filas iguales te suelta el terreno donde
     * tienes la casa. Salir de la fila lo cancela.
     */
    private String confirmando = "";

    /**
     * Qué parcela está abierta, o cadena vacía si se ve la lista.
     *
     * <p>⚠⚠ DOS VISTAS EN UNA PANTALLA Y NO DOS PANTALLAS: una aparte obligaría
     * a duplicar chasis, navegación y escalado —que ya estuvo copiado en once
     * sitios con seis variantes distintas—. Y el «atrás» cobra sentido solo:
     * del detalle a la lista, de la lista al Pad.
     */
    private String abierta = "";
    private int pestana = GENERAL;

    private TextFieldWidget campoNombre;
    private TextFieldWidget campoMiembro;
    private TextFieldWidget campoTitulo;
    private TextFieldWidget campoSubtitulo;

    /**
     * De qué parcela se han rellenado ya los campos con lo que dice el servidor.
     *
     * <p>⚠⚠ HACE FALTA PORQUE EL DETALLE LLEGA TARDE. Rellenarlos en cada
     * fotograma borraría lo que se está tecleando; solo al abrir los dejaría
     * vacíos, porque al abrir el servidor aún no ha contestado.
     */
    private String rellenado = "";

    public ProteccionesScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.protecciones"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        // ⚠ Se conserva lo escrito: `init` se vuelve a llamar al cambiar el
        //   tamaño de la ventana, y perder lo tecleado parece un reinicio.
        campoNombre = campo(24, textoDe(campoNombre));
        campoMiembro = campo(16, textoDe(campoMiembro));
        campoTitulo = campo(32, textoDe(campoTitulo));
        campoSubtitulo = campo(32, textoDe(campoSubtitulo));
        for (var c : campos()) {
            addSelectableChild(c);
        }
        ClientPlayNetworking.send(new Red.PedirProtecciones());
    }

    private TextFieldWidget[] campos() {
        return new TextFieldWidget[] {campoNombre, campoMiembro, campoTitulo, campoSubtitulo};
    }

    private static String textoDe(TextFieldWidget c) {
        return c == null ? "" : c.getText();
    }

    private TextFieldWidget campo(int max, String valor) {
        var c = new TextFieldWidget(textRenderer, 0, 0, 10, 10, Text.literal(""));
        c.setMaxLength(max);
        c.setText(valor);
        return c;
    }

    /**
     * Coloca un campo en coordenadas de arte.
     *
     * <p>⚠ Se recolocan EN CADA FOTOGRAMA: la pantalla se reescala sola con la
     * ventana, y un campo que se quedara donde estaba se dibujaría fuera.
     */
    private void colocar(TextFieldWidget c, int ax, int ay, int aw, int ah) {
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

    /**
     * ⚠⚠ CUÁNTAS FILAS CABEN SE CALCULA. Es la quinta vez que este proyecto
     * tropieza con un número que cuadraba por casualidad: la rejilla del
     * PokePad, las paradas, La Liga, los cosméticos y el panel de la tienda.
     */
    private int filasCaben() {
        return Math.max(1, (PANT_H - 2 * MARGEN - 46) / (FILA_ALTO + FILA_AIRE));
    }

    private int paginas() {
        return Math.max(1, (parcelas().size() + filasCaben() - 1) / filasCaben());
    }

    private void volverALista() {
        abierta = "";
        rellenado = "";
        confirmando = "";
        pestana = GENERAL;
        EstadoCliente.olvidarParcela();
        setFocused(null);
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);

        if (pagina >= paginas()) {
            pagina = Math.max(0, paginas() - 1);
        }
        // ⚠ Si la parcela abierta desaparece --la has quitado-- se vuelve a la
        //   lista en vez de quedarse en blanco.
        if (!abierta.isEmpty() && EstadoCliente.protecciones() != null) {
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
        //    modelos. Mezclarlos pinta el 2D ENCIMA de los objetos. Regla 3.
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

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 120, 96);
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2, pl(60) + 4, pl(48) + 4,
                    BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.translatable(abierta.isEmpty()
                        ? "pokepad.lunaeternal.inicio"
                        : "pokepad.lunaeternal.protecciones.volver"),
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
        dibujarTextura(ctx, ICONO, px(cx - 62), py(PANEL_Y + NAV_ALTO + 18),
                pl(124), pl(124), 100, 100);

        texto(ctx, Text.translatable("pokepad.lunaeternal.app.protecciones"),
                cx, PANEL_Y + NAV_ALTO + 158, 30, 0xFFFFFFFF, true, false);

        int y = PANEL_Y + NAV_ALTO + 202;
        for (String linea : partir(
                Text.translatable("pokepad.lunaeternal.protecciones.explica").getString(),
                PANEL_W - 56, 17)) {
            texto(ctx, Text.literal(linea), cx, y, 17, TEXTO_SUAVE, true, false);
            y += 21;
        }

        separador(ctx, y + 16);
        y += 40;

        var e = EstadoCliente.protecciones();
        if (e == null) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                    cx, y + 20, 20, TEXTO_SUAVE, true, false);
            return;
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.cuantas"),
                cx, y, 18, TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(String.valueOf(e.parcelas().size())),
                cx, y + 26, 40, 0xFFFFFFFF, true, false);

        // ⚠ El total va EN BLOQUES y no en «parcelas»: cinco de 15×15 y una de
        //   251×251 son seis parcelas y no se parecen en nada.
        long bloques = 0;
        for (var p : e.parcelas()) {
            bloques += (long) p.lado() * p.lado();
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.bloques",
                        String.format("%,d", bloques)),
                cx, y + 78, 17, TEXTO_SUAVE, true, false);
    }

    // ---- la lista ----------------------------------------------------------

    private int filaY(int n) {
        return PANT_Y + MARGEN + n * (FILA_ALTO + FILA_AIRE);
    }

    private void dibujarFilas(DrawContext ctx, int rx, int ry, boolean objetos) {
        var e = EstadoCliente.protecciones();
        if (e == null) {
            if (!objetos) {
                centrado(ctx, "pokepad.lunaeternal.cargando", 24, TEXTO_SUAVE, 0);
            }
            return;
        }
        // ⚠⚠ «NO TIENES NINGUNA» Y «EL SISTEMA NO ESTÁ» SE DIBUJAN IGUAL Y
        //    SIGNIFICAN LO CONTRARIO. Por eso `hayMod` viaja aparte: sin él, un
        //    mod caído se leería como «aún no has protegido nada» y el jugador
        //    se pondría a buscar un módulo que nadie puede darle.
        if (!e.hayMod()) {
            if (!objetos) {
                centrado(ctx, "pokepad.lunaeternal.protecciones.sin_mod", 22, ROJO, 0);
            }
            return;
        }
        var lista = e.parcelas();
        if (lista.isEmpty()) {
            if (!objetos) {
                centrado(ctx, "pokepad.lunaeternal.protecciones.ninguna", 26, TEXTO_OSCURO, -26);
                centrado(ctx, "pokepad.lunaeternal.protecciones.como", 18, TEXTO_SUAVE, 14);
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
                objeto(ctx, p.pila(), ax + 18, y + FILA_ALTO / 2 - 22, 44);
                continue;
            }

            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA_ALTO));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA_ALTO),
                    encima ? FILA_ENCIMA : FILA_FONDO);
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA_ALTO), FILA_BORDE, Math.max(1, pl(2)));

            // ⚠⚠ SIN CÓDIGOS DE COLOR Y SIN CONTORNO. El nombre del módulo trae
            //    «§8§l…§c§l…» de su configuración: dibujado tal cual sale en
            //    negrita a dos colores, y con el contorno claro encima se
            //    emborrona y parece texto duplicado. Es lo que se vio en el
            //    juego. Aquí manda la legibilidad, no el color del módulo.
            texto(ctx, Text.literal(limpio(p.pila().getName().getString())),
                    ax + 78, y + 12, 26, TEXTO_OSCURO, false, false);
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.bloques",
                            String.format("%,d", (long) p.lado() * p.lado())),
                    ax + 78, y + 46, 18, TEXTO_SUAVE, false, false);
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.en",
                            p.centro().getX() + ", " + p.centro().getY()
                                    + ", " + p.centro().getZ()),
                    ax + 78, y + 68, 16, TEXTO_SUAVE, false, false);

            boolean pidiendo = p.nombre().equals(confirmando);
            int bx = ax + aw - 210;
            boton(ctx, rx, ry, bx, y + 26, 194, 42, Text.translatable(pidiendo
                            ? "pokepad.lunaeternal.protecciones.seguro"
                            : "pokepad.lunaeternal.protecciones.borrar"),
                    true, pidiendo ? ROJO : 0xFFA9707A);
        }

        // ⚠ Salir de la fila cancela: si se quedara puesta, el siguiente clic
        //   en esa fila quitaría sin preguntar.
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
        texto(ctx, Text.literal((pagina + 1) + " / " + paginas()), cx, PAG_Y + 10, 22,
                0xFF3A2000, true, false);
    }

    private void flecha(DrawContext ctx, int rx, int ry, int ax, int ay,
                        boolean derecha, boolean activa) {
        boolean encima = activa && dentro(rx, ry, px(ax), py(ay), pl(40), pl(40));
        int color = !activa ? 0xFF8A6A4A : (encima ? 0xFFFFFFFF : 0xFF3A2000);
        texto(ctx, Text.literal(derecha ? ">" : "<"), ax + 20, ay + 8, 28, color, true, false);
    }

    // ---- el detalle --------------------------------------------------------

    private int pestanaX(int i) {
        return PANT_X + MARGEN + i * (PEST_ANCHO + 8);
    }

    private void dibujarDetalle(DrawContext ctx, int rx, int ry, boolean objetos) {
        var p = laAbierta();
        var d = detalle();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;

        if (objetos) {
            if (p != null) {
                objeto(ctx, p.pila(), ax + 6, PANT_Y + MARGEN + 2, 48);
            }
            return;
        }

        texto(ctx, Text.literal(abierta), ax + 66, PANT_Y + MARGEN, 28,
                TEXTO_OSCURO, false, false);
        if (p != null) {
            texto(ctx, Text.literal(p.lado() + " × " + p.lado() + "   ·   "
                            + String.format("%,d", (long) p.lado() * p.lado()) + " bloques"),
                    ax + 66, PANT_Y + MARGEN + 34, 18, TEXTO_SUAVE, false, false);
        }

        if (d == null) {
            centrado(ctx, "pokepad.lunaeternal.cargando", 24, TEXTO_SUAVE, 0);
            return;
        }
        // ⚠ La primera vez que llega el detalle se rellenan los campos. Después
        //   no: se borraría lo que el jugador esté tecleando.
        if (!abierta.equals(rellenado)) {
            rellenado = abierta;
            campoNombre.setText(abierta);
            campoTitulo.setText(d.titulo());
            campoSubtitulo.setText(d.subtitulo());
        }

        for (int i = 0; i < PESTANAS.length; i++) {
            boolean activa = i == pestana;
            int bx = pestanaX(i);
            boolean encima = dentro(rx, ry, px(bx), py(PEST_Y), pl(PEST_ANCHO), pl(PEST_ALTO));
            ctx.fill(px(bx), py(PEST_Y), px(bx + PEST_ANCHO), py(PEST_Y + PEST_ALTO),
                    activa ? 0xFFFFF0DC : (encima ? 0xFFD3DCF2 : FILA_FONDO));
            marco(ctx, px(bx), py(PEST_Y), pl(PEST_ANCHO), pl(PEST_ALTO),
                    activa ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(activa ? 3 : 2)));
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.pes." + PESTANAS[i]),
                    bx + PEST_ANCHO / 2, PEST_Y + 10, 20, TEXTO_OSCURO, true, false);
        }

        switch (pestana) {
            case PERMISOS -> pestanaPermisos(ctx, rx, ry, d);
            case MIEMBROS -> pestanaMiembros(ctx, rx, ry, d, ax);
            default -> pestanaGeneral(ctx, rx, ry, d, ax, aw);
        }
    }

    /** GENERAL: el nombre, el módulo y el mensaje al entrar. */
    private void pestanaGeneral(DrawContext ctx, int rx, int ry, Red.DetalleParcela d,
                                int ax, int aw) {
        int y = CONT_Y;
        rotulo(ctx, "pokepad.lunaeternal.protecciones.nombre", ax, y);
        colocar(campoNombre, ax, y + 28, 320, 32);
        campoNombre.render(ctx, rx, ry, 0);
        boton(ctx, rx, ry, ax + 334, y + 28, 150, 34,
                Text.translatable("pokepad.lunaeternal.protecciones.guardar"),
                !campoNombre.getText().trim().isEmpty(), VERDE);

        int ym = y + 104;
        separador2(ctx, ax, aw, ym - 20);
        rotulo(ctx, "pokepad.lunaeternal.protecciones.el_modulo", ax, ym);
        texto(ctx, Text.translatable(d.visible()
                        ? "pokepad.lunaeternal.protecciones.esta_puesto"
                        : "pokepad.lunaeternal.protecciones.esta_escondido"),
                ax, ym + 28, 17, TEXTO_SUAVE, false, false);
        boton(ctx, rx, ry, ax, ym + 50, 300, 40, Text.translatable(d.visible()
                        ? "pokepad.lunaeternal.protecciones.esconder"
                        : "pokepad.lunaeternal.protecciones.ensenar"),
                true, d.visible() ? 0xFF6E5A9E : 0xFF2E7A9E);

        int yg = ym + 120;
        separador2(ctx, ax, aw, yg - 20);
        rotulo(ctx, "pokepad.lunaeternal.protecciones.mensaje", ax, yg);
        // ⚠ Se dice DE QUÉ DEPENDE: el mensaje no sale si «Avisar al entrar»
        //   está en NO, y un campo que se rellena y no hace nada parece roto.
        texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.mensaje_pista"),
                ax, yg + 24, 15, TEXTO_SUAVE, false, false);
        colocar(campoTitulo, ax, yg + 44, 320, 30);
        campoTitulo.render(ctx, rx, ry, 0);
        colocar(campoSubtitulo, ax + 334, yg + 44, 320, 30);
        campoSubtitulo.render(ctx, rx, ry, 0);
        if (campoSubtitulo.getText().isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.subtitulo"),
                    ax + 342, yg + 52, 14, 0xFF8892AC, false, false);
        }
        boton(ctx, rx, ry, ax, yg + 78, 200, 34,
                Text.translatable("pokepad.lunaeternal.protecciones.guardar"),
                true, VERDE);
    }

    private int permisoY(int i) {
        return CONT_Y + 30 + (i % 6) * 46;
    }

    private int permisoX(int i) {
        return PANT_X + MARGEN + (i / 6) * 388;
    }

    /** PERMISOS: los once, grandes, en dos columnas. */
    private void pestanaPermisos(DrawContext ctx, int rx, int ry, Red.DetalleParcela d) {
        texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.no_miembros"),
                PANT_X + MARGEN, CONT_Y, 16, TEXTO_SUAVE, false, false);
        for (int i = 0; i < d.permisos().size(); i++) {
            var q = d.permisos().get(i);
            int x = permisoX(i), y = permisoY(i);
            boolean encima = dentro(rx, ry, px(x), py(y), pl(370), pl(40));
            ctx.fill(px(x), py(y), px(x + 370), py(y + 40),
                    encima ? FILA_ENCIMA : FILA_FONDO);
            marco(ctx, px(x), py(y), pl(370), pl(40), FILA_BORDE, Math.max(1, pl(2)));
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.flag." + q.clave()),
                    x + 12, y + 11, 19, TEXTO_OSCURO, false, false);
            // ⚠ El interruptor dice SÍ/NO y no un icono: «permitido» y
            //   «prohibido» con un símbolo se confunden, y aquí equivocarse es
            //   dejar la casa abierta.
            int bx = x + 282;
            ctx.fill(px(bx), py(y + 5), px(bx + 76), py(y + 35),
                    q.valor() ? VERDE : 0xFF9E3A32);
            texto(ctx, Text.translatable(q.valor()
                            ? "pokepad.lunaeternal.protecciones.si"
                            : "pokepad.lunaeternal.protecciones.no"),
                    bx + 38, y + 11, 19, 0xFFFFFFFF, true, false);
        }
    }

    private int miembroY(int i) {
        return CONT_Y + 34 + i * 44;
    }

    /** MIEMBROS: quién puede construir. */
    private void pestanaMiembros(DrawContext ctx, int rx, int ry, Red.DetalleParcela d,
                                 int ax) {
        rotulo(ctx, "pokepad.lunaeternal.protecciones.miembros_tit", ax, CONT_Y);
        if (d.miembros().isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.sin_miembros"),
                    ax, miembroY(0) + 8, 18, TEXTO_SUAVE, false, false);
        }
        for (int i = 0; i < d.miembros().size() && i < 5; i++) {
            var m = d.miembros().get(i);
            int y = miembroY(i);
            ctx.fill(px(ax), py(y), px(ax + 470), py(y + 38), FILA_FONDO);
            marco(ctx, px(ax), py(y), pl(470), pl(38), FILA_BORDE, Math.max(1, pl(2)));
            texto(ctx, Text.literal(m.nombre()), ax + 14, y + 10, 20,
                    TEXTO_OSCURO, false, false);
            boton(ctx, rx, ry, ax + 366, y + 4, 96, 30,
                    Text.translatable("pokepad.lunaeternal.protecciones.quitar_m"),
                    true, 0xFFA9707A);
        }
        if (d.miembros().size() > 5) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.y_mas",
                            d.miembros().size() - 5),
                    ax, miembroY(5) + 6, 16, TEXTO_SUAVE, false, false);
        }

        int ay = PANT_Y + PANT_H - MARGEN - 42;
        colocar(campoMiembro, ax, ay, 320, 32);
        campoMiembro.render(ctx, rx, ry, 0);
        if (campoMiembro.getText().isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.protecciones.quien"),
                    ax + 10, ay + 9, 15, 0xFF8892AC, false, false);
        }
        boton(ctx, rx, ry, ax + 334, ay, 150, 34,
                Text.translatable("pokepad.lunaeternal.protecciones.anadir"),
                !campoMiembro.getText().trim().isEmpty(), VERDE);
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
            //   cualquiera, y ahorra tener dos botones de volver.
            if (!abierta.isEmpty()) {
                volverALista();
            } else if (client != null) {
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
        return clicLista(rx, ry) || super.mouseClicked(mx, my, boton);
    }

    private boolean clicLista(int rx, int ry) {
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
            int bx = ax + aw - 210;
            // ⚠ El botón se comprueba ANTES que la fila: al revés, quitar sería
            //   imposible porque el clic lo capturaría siempre la fila.
            if (dentro(rx, ry, px(bx), py(y + 26), pl(194), pl(42))) {
                if (!p.nombre().equals(confirmando)) {
                    confirmando = p.nombre();
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f);
                    return true;
                }
                // ⚠ No se quita de la lista aquí: se manda y se espera. El
                //   servidor reenvía las parcelas quite o no quite.
                confirmando = "";
                sonar(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.9f);
                ClientPlayNetworking.send(new Red.BorrarProteccion(p.nombre()));
                return true;
            }
            if (dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA_ALTO))) {
                abierta = p.nombre();
                rellenado = "";
                confirmando = "";
                pestana = GENERAL;
                EstadoCliente.olvidarParcela();
                campoMiembro.setText("");
                setFocused(null);
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                ClientPlayNetworking.send(new Red.PedirParcela(p.nombre()));
                return true;
            }
        }
        return false;
    }

    private boolean clicDetalle(int rx, int ry, double mx, double my, int boton) {
        var d = detalle();
        if (d == null) {
            return false;
        }
        int ax = PANT_X + MARGEN;

        for (int i = 0; i < PESTANAS.length; i++) {
            if (dentro(rx, ry, px(pestanaX(i)), py(PEST_Y), pl(PEST_ANCHO), pl(PEST_ALTO))) {
                pestana = i;
                setFocused(null);
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                return true;
            }
        }
        for (var c : campos()) {
            if (c.mouseClicked(mx, my, boton)) {
                setFocused(c);
                return true;
            }
        }

        if (pestana == GENERAL) {
            int y = CONT_Y;
            if (!campoNombre.getText().trim().isEmpty()
                    && dentro(rx, ry, px(ax + 334), py(y + 28), pl(150), pl(34))) {
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
                ClientPlayNetworking.send(
                        new Red.RenombrarParcela(abierta, campoNombre.getText().trim()));
                // ⚠⚠ SE APUNTA EL NOMBRE NUEVO YA: el servidor contesta con el
                //    detalle del nombre nuevo, y con el viejo puesto
                //    `detalle()` lo descartaría y la pantalla se quedaría
                //    «cargando» para siempre tras una operación que fue bien.
                abierta = campoNombre.getText().trim();
                rellenado = abierta;
                return true;
            }
            int ym = y + 104;
            if (dentro(rx, ry, px(ax), py(ym + 50), pl(300), pl(40))) {
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), d.visible() ? 0.8f : 1.2f);
                ClientPlayNetworking.send(new Red.VerModulo(abierta, !d.visible()));
                return true;
            }
            int yg = ym + 120;
            if (dentro(rx, ry, px(ax), py(yg + 78), pl(200), pl(34))) {
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
                ClientPlayNetworking.send(new Red.MensajeParcela(abierta,
                        campoTitulo.getText().trim(), campoSubtitulo.getText().trim()));
                return true;
            }
            return false;
        }

        if (pestana == PERMISOS) {
            for (int i = 0; i < d.permisos().size(); i++) {
                if (dentro(rx, ry, px(permisoX(i)), py(permisoY(i)), pl(370), pl(40))) {
                    var q = d.permisos().get(i);
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), q.valor() ? 0.8f : 1.2f);
                    // ⚠ No se pinta el cambio: se manda y se espera. El servidor
                    //   reenvía el detalle acepte o rechace.
                    ClientPlayNetworking.send(
                            new Red.CambiarPermiso(abierta, q.clave(), !q.valor()));
                    return true;
                }
            }
            return false;
        }

        for (int i = 0; i < d.miembros().size() && i < 5; i++) {
            if (dentro(rx, ry, px(ax + 366), py(miembroY(i) + 4), pl(96), pl(30))) {
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 0.9f);
                ClientPlayNetworking.send(new Red.TocarMiembro(
                        abierta, d.miembros().get(i).nombre(), false));
                return true;
            }
        }
        int ay = PANT_Y + PANT_H - MARGEN - 42;
        if (!campoMiembro.getText().trim().isEmpty()
                && dentro(rx, ry, px(ax + 334), py(ay), pl(150), pl(34))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
            ClientPlayNetworking.send(new Red.TocarMiembro(
                    abierta, campoMiembro.getText().trim(), true));
            campoMiembro.setText("");
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        // ⚠ Escape con un campo enfocado SUELTA EL FOCO en vez de cerrar la
        //   pantalla: cerrarlo todo por querer salir de un campo molesta.
        if (tecla == 256 && getFocused() != null) {
            setFocused(null);
            return true;
        }
        for (var c : campos()) {
            if (getFocused() == c && c.keyPressed(tecla, escaneo, mods)) {
                return true;
            }
        }
        return super.keyPressed(tecla, escaneo, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        for (var f : campos()) {
            if (getFocused() == f) {
                return f.charTyped(c, mods);
            }
        }
        return super.charTyped(c, mods);
    }

    private void sonar(net.minecraft.sound.SoundEvent sonido, float tono) {
        if (client != null && client.player != null) {
            client.player.playSound(sonido, 0.7f, tono);
        }
    }

    // ---- utilidades --------------------------------------------------------

    private void rotulo(DrawContext ctx, String clave, int ax, int ay) {
        texto(ctx, Text.translatable(clave), ax, ay, 21, TEXTO_OSCURO, false, false);
    }

    private void centrado(DrawContext ctx, String clave, int alto, int color, int dy) {
        texto(ctx, Text.translatable(clave), PANT_X + PANT_W / 2,
                PANT_Y + PANT_H / 2 + dy, alto, color, true, false);
    }

    private void boton(DrawContext ctx, int rx, int ry, int ax, int ay, int aw, int ah,
                       Text etiqueta, boolean activo, int color) {
        boolean encima = activo && dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah),
                !activo ? 0xFF6E7899 : (encima ? aclarar(color) : color));
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), 0xFF20283C, Math.max(1, pl(2)));
        // ⚠ La letra se encoge SOLO si no cabe. Escrita a un tamaño fijo, la
        //   etiqueta más larga se saldría del botón en cuanto se traduzca.
        int t = 19;
        while (t > 12 && anchoArte(etiqueta.getString(), t) > aw - 16) {
            t--;
        }
        texto(ctx, etiqueta, ax + aw / 2, ay + ah / 2 - t / 2 - 1, t,
                activo ? 0xFFFFFFFF : 0xFFD8DEEA, true, false);
    }

    private static int aclarar(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 40);
        int b = Math.min(255, (color & 0xFF) + 40);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static String limpio(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

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

    private void separador2(DrawContext ctx, int ax, int aw, int artY) {
        ctx.fill(px(ax), py(artY), px(ax + aw), py(artY) + Math.max(1, pl(2)), SEPARADOR);
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
