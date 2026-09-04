package net.pokereport.luna.client.pokepad;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;

/**
 * CLANES: fundar, entrar, dirigir.
 *
 * <h2>Dos pantallas en una, y es lo correcto</h2>
 *
 * Lo que ve alguien <b>sin clan</b> y alguien <b>con clan</b> no se parece en
 * nada: uno tiene que elegir entre fundar o entrar en uno; el otro tiene una
 * lista de gente y un tesoro. Meterlo todo con la mitad en gris sería enseñarle a
 * cada uno la pantalla del otro a medio apagar.
 *
 * <p>Por eso las pestañas <b>cambian según el caso</b> — y salen de los datos,
 * como en Cosméticos y Misiones, no de una lista escrita aquí.
 *
 * <h2>⚠ El cliente no decide NADA</h2>
 *
 * Los botones que se dibujan dependen de {@code miRol}, y eso es <b>dibujado</b>,
 * no permiso: esconder «echar» a un miembro raso es cortesía, y el servidor lo
 * rechazaría igual si lo mandara un cliente modificado. En un sistema social los
 * permisos <i>son</i> la funcionalidad, así que viven en {@code ClanService} y en
 * ningún otro sitio.
 *
 * <h2>⚠⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 *
 * Y la geometría ({@code recalcular}) es <b>copia literal</b> de
 * {@code CosmeticosScreen}: escribirla de cero en Trabajos la sacó al cuádruple
 * por olvidar dividir por el GUI Scale.
 */
public class ClanScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    private static final int MARGEN = 12, PESTANA_ALTO = 52, FILA_ALTO = 46, AIRE = 6;

    private static final int FILA_FONDO = 0xFFBFCBE8;
    private static final int FILA_BORDE = 0xFF7C89B4;
    private static final int FILA_ENCIMA = 0xFFFFF0DC;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int ORO = 0xFFFFD65C;
    private static final int VERDE = 0xFF2E7D46;
    private static final int ROJO = 0xFFB03A2E;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private Red.EstadoClan estado;
    private List<String> pestanas = List.of();
    private int pestana = 0;

    /**
     * Página de la lista de la pestaña actual.
     *
     * <h2>⚠⚠⚠ SIN ESTO SE VEÍAN 6 MIEMBROS DE 30</h2>
     *
     * Las cinco listas de esta pantalla dibujaban {@code i < filasCaben()} y se
     * paraban ahí, <b>sin flechas y sin arrastre</b>. Con el aforo en 30, eso
     * dejaba <b>24 miembros invisibles</b> — y el líder no podía echarlos ni
     * ascenderlos, porque su fila no existía. Lo mismo con el tesoro y el
     * registro, que traen hasta 200 apuntes, y con la lista de clanes, hasta 100.
     *
     * <p>Y no daba ningún error: la pantalla se dibujaba perfecta. Es la quinta
     * vez que este proyecto tropieza con lo mismo —la rejilla del PokePad, los
     * cosméticos, las categorías de la tienda, las paradas de viajes— y siempre
     * por el mismo motivo: <b>una lista que cabía por casualidad el día que se
     * escribió</b>.
     */
    private int pagina = 0;

    // ---- las flechas de pagina, en la banda naranja de abajo ---------------
    //
    // ⚠ Las mismas medidas que en Trabajos y Cosmeticos, y el mismo chasis: se
    //   midieron recorriendo el PNG (banda y=698..745) y volver a hacerlo seria
    //   repetir el trabajo para llegar al mismo sitio.
    private static final int PAG_W = 50, PAG_H = 40;
    private static final int PAG_Y = 698 + (745 - 698 - PAG_H) / 2;
    private static final int PAG_SEP = 215;
    private static final Identifier PAG_ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier PAG_ADELANTE =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_adelante.png");
    private String aviso = "";

    /**
     * Los campos de texto.
     *
     * <p>⚠ Se crean en {@code init()} y no en el constructor: {@code init} vuelve
     * a correr al cambiar el tamaño de la ventana, y un campo colocado con las
     * medidas viejas se queda donde no está su hueco.
     */
    private TextFieldWidget campoNombre;
    private TextFieldWidget campoEtiqueta;
    private TextFieldWidget campoJugador;
    private TextFieldWidget campoCantidad;

    public ClanScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.clan"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirClan());
        // ⚠ El saldo hace falta AQUI: fundar cuesta 5.000 y aportar sale de tu
        //   bolsillo. Sin el numero delante, la decisión se toma a ciegas y el
        //   único aviso llega en forma de rechazo.
        ClientPlayNetworking.send(new Red.PedirSaldo());

        // ⚠ Los cuatro se crean SIEMPRE, aunque solo se usen dos a la vez. Lo
        //   que cambia es cuáles se dibujan: crearlos según el caso obligaría a
        //   recrearlos cada vez que llega un paquete, y el jugador perdería lo
        //   que estuviera escribiendo a mitad de palabra.
        campoNombre = campo(PANEL_X + 28, PANEL_Y + 190, PANEL_W - 56, 24, "Nombre");
        campoEtiqueta = campo(PANEL_X + 28, PANEL_Y + 256, PANEL_W - 56, 5, "TAG");
        campoJugador = campo(PANT_X + MARGEN, PANT_Y + PANT_H - 52, 360, 16, "Jugador");
        campoCantidad = campo(PANEL_X + 28, PANEL_Y + 470, PANEL_W - 56, 9, "0");
        for (var c : new TextFieldWidget[] { campoNombre, campoEtiqueta,
                campoJugador, campoCantidad }) {
            addSelectableChild(c);
        }
    }

    private TextFieldWidget campo(int ax, int ay, int aw, int max, String pista) {
        var c = new TextFieldWidget(textRenderer, px(ax), py(ay),
                pl(aw), Math.max(12, pl(30)), Text.literal(pista));
        c.setMaxLength(max);
        c.setPlaceholder(Text.literal(pista));
        return c;
    }

    /** Copia literal de CosmeticosScreen. Ver el comentario de la clase. */
    /**
     * ⚠ Delegado en {@link Escalado} (2026-08-26). Esto era una copia
     *   literal en ONCE pantallas, y para entonces ya había seis
     *   variantes distintas: cada una había envejecido por su lado sin
     *   dar ningún error.
     */
    private void recalcular() {
        var m = Escalado.aplicar(client, width, height, CHASIS, ATRAS, CERRAR);
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

    private boolean tengoClan() {
        return estado != null && estado.mio() != null;
    }

    private boolean mando() {
        return estado != null && ("LIDER".equals(estado.miRol())
                || "OFICIAL".equals(estado.miRol()));
    }

    private boolean soyLider() {
        return estado != null && "LIDER".equals(estado.miRol());
    }

    // ---- datos -------------------------------------------------------------

    private void leerDelServidor() {
        Red.EstadoClan e = EstadoCliente.clan();
        if (e == null || e == estado) {
            return;
        }
        estado = e;

        // ⚠ Las pestañas cambian según tengas clan o no. Se recalculan aquí y no
        //   se escriben arriba: al fundar o al salir, la pantalla tiene que pasar
        //   de una cara a la otra sin que el jugador la reabra.
        var p = new ArrayList<String>();
        if (tengoClan()) {
            p.add("miembros");
            p.add("tesoro");
            // ⚠ EL REGISTRO LO VE TODO EL CLAN, no solo quien manda. Un registro
            //   que solo pueden leer los que podrían robar no vigila a nadie:
            //   lo que lo hace útil es que lo vean los demás.
            p.add("registro");
        } else {
            p.add("invitaciones");
            p.add("clanes");
        }
        pestanas = List.copyOf(p);
        if (pestana >= pestanas.size()) {
            pestana = 0;
            pagina = 0;
        }
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        leerDelServidor();

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);

        if (tengoClan()) {
            dibujarPanelClan(ctx, rx, ry);
        } else {
            dibujarPanelFundar(ctx, rx, ry);
        }

        dibujarPestanas(ctx, rx, ry);
        String cual = pestanas.isEmpty() ? "" : pestanas.get(pestana);
        switch (cual) {
            case "miembros" -> dibujarMiembros(ctx, rx, ry);
            case "tesoro" -> dibujarTesoro(ctx, rx, ry);
            case "registro" -> dibujarRegistro(ctx, rx, ry);
            case "invitaciones" -> dibujarInvitaciones(ctx, rx, ry);
            case "clanes" -> dibujarClanes(ctx, rx, ry);
            default -> { }
        }
        dibujarPaginas(ctx, rx, ry);

        if (!aviso.isEmpty()) {
            texto(ctx, Text.literal(aviso), PANT_X + PANT_W / 2, PANT_Y + PANT_H - 22,
                    16, ROJO, true, true);
        }
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

    /** Sin clan: el formulario de fundar. */
    private void dibujarPanelFundar(DrawContext ctx, int rx, int ry) {
        int cx = PANEL_X + PANEL_W / 2;
        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.fundar"),
                cx, PANEL_Y + NAV_ALTO + 16, 26, 0xFFFFFFFF, true, false);

        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.nombre"),
                PANEL_X + 28, PANEL_Y + 166, 17, TEXTO_SUAVE, false, false);
        campoNombre.render(ctx, rx, ry, 0);

        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.etiqueta"),
                PANEL_X + 28, PANEL_Y + 232, 17, TEXTO_SUAVE, false, false);
        campoEtiqueta.render(ctx, rx, ry, 0);
        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.etiqueta_ayuda"),
                PANEL_X + 28, PANEL_Y + 296, 15, TEXTO_SUAVE, false, false);

        separador(ctx, PANEL_Y + 340);
        long coste = estado == null ? 5000 : estado.costeFundar();
        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.coste"),
                cx, PANEL_Y + 356, 17, TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(String.format("%,d", coste)),
                cx, PANEL_Y + 380, 30, ORO, true, false);

        // Tu Plata, justo debajo del coste. Los dos números juntos contestan
        // la única pregunta que se hace aquí: ¿me llega?
        var s = EstadoCliente.saldo();
        long tengo = s == null ? -1 : s.pokedolares();
        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.tienes_tu"),
                cx, PANEL_Y + 420, 15, TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(tengo < 0 ? "—" : String.format("%,d", tengo)),
                cx, PANEL_Y + 440, 22,
                tengo >= 0 && tengo < coste ? ROJO : 0xFFFFFFFF, true, false);

        boton(ctx, rx, ry, PANEL_X + 40, PANEL_Y + PANEL_H - 76, PANEL_W - 80, 46,
                Text.translatable("pokepad.lunaeternal.clan.fundar_btn"),
                tengo < 0 || tengo >= coste);
    }

    /** Con clan: quién eres dentro y qué puedes hacer. */
    private void dibujarPanelClan(DrawContext ctx, int rx, int ry) {
        var c = estado.mio();
        int cx = PANEL_X + PANEL_W / 2;

        texto(ctx, Text.literal("[" + c.etiqueta() + "]"),
                cx, PANEL_Y + NAV_ALTO + 10, 24, colorDe(c.color()), true, false);
        int y = PANEL_Y + NAV_ALTO + 42;
        for (String linea : partir(c.nombre(), PANEL_W - 40, 30)) {
            texto(ctx, Text.literal(linea), cx, y, 30, 0xFFFFFFFF, true, false);
            y += 34;
        }

        y += 8;
        separador(ctx, y);
        y += 16;
        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.lider"),
                cx, y, 16, TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(c.lider()), cx, y + 20, 20, 0xFFC9D2E6, true, false);

        y += 56;
        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.miembros"),
                cx, y, 16, TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(c.miembros() + " / 30"), cx, y + 20, 20, 0xFFC9D2E6, true, false);

        y += 56;
        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.tesoro"),
                cx, y, 16, TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(String.format("%,d", c.tesoro())), cx, y + 20, 24, ORO,
                true, false);

        y += 56;
        separador(ctx, y);
        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.rol." + estado.miRol()),
                cx, y + 14, 20, soyLider() ? ORO : 0xFF9FD0F0, true, false);

        var saldo = EstadoCliente.saldo();
        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.tienes_tu"),
                cx, y + 44, 15, TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(saldo == null ? "—"
                        : String.format("%,d", saldo.pokedolares())),
                cx, y + 62, 20, 0xFFFFFFFF, true, false);

        // ⚠ El líder ve DISOLVER y no SALIR, y no es un detalle: salir le está
        //   prohibido —dejaría el clan sin quien lo dirija— así que enseñárselo
        //   sería ofrecer un botón que siempre falla.
        boton(ctx, rx, ry, PANEL_X + 40, PANEL_Y + PANEL_H - 76, PANEL_W - 80, 46,
                Text.translatable(soyLider()
                        ? "pokepad.lunaeternal.clan.disolver"
                        : "pokepad.lunaeternal.clan.salir"),
                !soyLider() || c.miembros() == 1);
    }

    private void dibujarPestanas(DrawContext ctx, int rx, int ry) {
        if (pestanas.isEmpty()) {
            return;
        }
        int anchoUtil = PANT_W - 2 * MARGEN;
        int pw = anchoUtil / pestanas.size();
        for (int i = 0; i < pestanas.size(); i++) {
            int x = PANT_X + MARGEN + i * pw;
            boolean activa = i == pestana;
            boolean encima = dentro(rx, ry, px(x), py(PANT_Y + MARGEN), pl(pw - 6),
                    pl(PESTANA_ALTO));
            ctx.fill(px(x), py(PANT_Y + MARGEN), px(x + pw - 6),
                    py(PANT_Y + MARGEN + PESTANA_ALTO),
                    activa ? 0xFFFFF0DC : (encima ? 0xFFD3DCF2 : FILA_FONDO));
            marco(ctx, px(x), py(PANT_Y + MARGEN), pl(pw - 6), pl(PESTANA_ALTO),
                    activa ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(activa ? 3 : 2)));
            // El texto se encoge hasta caber: la lección de Misiones.
            Text n = Text.translatable("pokepad.lunaeternal.clan.tab." + pestanas.get(i));
            int alto = 22;
            while (alto > 11 && anchoArte(n.getString(), alto) > pw - 20) {
                alto--;
            }
            texto(ctx, n, x + (pw - 6) / 2,
                    PANT_Y + MARGEN + (PESTANA_ALTO - alto) / 2 - 2, alto,
                    TEXTO_OSCURO, true, false);
        }
    }

    private int primeraFilaY() {
        return PANT_Y + MARGEN + PESTANA_ALTO + 12;
    }

    private int filasCaben() {
        return (PANT_Y + PANT_H - MARGEN - 60 - primeraFilaY()) / (FILA_ALTO + AIRE);
    }

    /** Cómo se llama la pestaña abierta, o cadena vacía. */
    private String pestanaActual() {
        return pestanas.isEmpty() ? "" : pestanas.get(pestana);
    }

    /**
     * Cuántas filas caben en la pestaña abierta.
     *
     * <p>⚠ No es la misma para todas: las de fila gorda (miembros, invitaciones,
     * clanes) usan {@code filasCaben()}; el tesoro y el registro son renglones
     * de 22 y 24 px. Con un único número, una de las dos listas se cortaría o se
     * saldría del marco.
     */
    private int capacidadPagina() {
        return switch (pestanaActual()) {
            case "tesoro" -> Math.max(1,
                    (PANT_Y + PANT_H - MARGEN - (primeraFilaY() + 150)) / 22);
            case "registro" -> Math.max(1,
                    (PANT_Y + PANT_H - MARGEN - primeraFilaY()) / 24);
            default -> Math.max(1, filasCaben());
        };
    }

    /** Cuántos elementos tiene la lista de la pestaña abierta. */
    private int totalPagina() {
        if (estado == null) {
            return 0;
        }
        return switch (pestanaActual()) {
            case "miembros" -> estado.miembros().size();
            case "tesoro" -> estado.movimientos().size();
            case "registro" -> estado.registro().size();
            case "invitaciones" -> estado.invitaciones().size();
            case "clanes" -> estado.otros().size();
            default -> 0;
        };
    }

    /**
     * Cuántas páginas hacen falta. <b>Se calcula, no se escribe.</b>
     *
     * <p>⚠ Un número a mano aquí es exactamente lo que dejó 54 cosméticos de 62
     * fuera de alcance: cuadraba el día que se escribió.
     */
    private int paginas() {
        int cap = capacidadPagina();
        return Math.max(1, (totalPagina() + cap - 1) / cap);
    }

    /**
     * El primer elemento de la página actual.
     *
     * <p>⚠⚠ ACOTA LA PÁGINA ADEMÁS DE DEVOLVER EL ÍNDICE, y hace falta: la lista
     * encoge sola —alguien sale del clan, caduca una invitación— y quedarse en
     * una página que ya no existe deja la pestaña <b>en blanco</b>, que se lee
     * como «se ha roto» y no como «ya no hay nada aquí».
     */
    private int desde() {
        if (pagina >= paginas()) {
            pagina = paginas() - 1;
        }
        if (pagina < 0) {
            pagina = 0;
        }
        return pagina * capacidadPagina();
    }

    private void dibujarMiembros(DrawContext ctx, int rx, int ry) {
        var lista = estado.miembros();
        int y = primeraFilaY();
        int desde = desde();
        for (int n = 0; n < capacidadPagina() && desde + n < lista.size(); n++) {
            var m = lista.get(desde + n);
            int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA_ALTO));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA_ALTO),
                    encima ? FILA_ENCIMA : FILA_FONDO);
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA_ALTO), FILA_BORDE, Math.max(1, pl(2)));

            // Un punto verde si está conectado. Es lo primero que se mira en una
            // lista de clan: con quién puedes contar ahora.
            ctx.fill(px(ax + 12), py(y + FILA_ALTO / 2 - 5), px(ax + 22),
                    py(y + FILA_ALTO / 2 + 5), m.conectado() ? 0xFF3FBF5F : 0xFF8A93A8);

            texto(ctx, Text.literal(m.nombre()), ax + 34, y + 12, 22, TEXTO_OSCURO,
                    false, true);
            texto(ctx, Text.translatable("pokepad.lunaeternal.clan.rol." + m.rol()),
                    ax + 300, y + 14, 18,
                    "LIDER".equals(m.rol()) ? 0xFFA07800 : TEXTO_SUAVE, false, true);

            // Los botones solo si mandas y no es el líder. Ver el comentario de
            // la clase: esto es dibujado, el permiso lo decide el servidor.
            if (mando() && !"LIDER".equals(m.rol())) {
                int bx = ax + aw - 250;
                if (soyLider()) {
                    botonPeq(ctx, rx, ry, bx, y + 8, 110, 30,
                            Text.translatable("MIEMBRO".equals(m.rol())
                                    ? "pokepad.lunaeternal.clan.ascender"
                                    : "pokepad.lunaeternal.clan.degradar"));
                }
                botonPeq(ctx, rx, ry, ax + aw - 130, y + 8, 118, 30,
                        Text.translatable("pokepad.lunaeternal.clan.echar"));
            }
            y += FILA_ALTO + AIRE;
        }

        // Invitar, abajo. Solo si mandas.
        if (mando()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.clan.invitar_a"),
                    PANT_X + MARGEN, PANT_Y + PANT_H - 76, 16, TEXTO_SUAVE, false, true);
            campoJugador.render(ctx, rx, ry, 0);
            botonPeq(ctx, rx, ry, PANT_X + MARGEN + 376, PANT_Y + PANT_H - 54, 150, 34,
                    Text.translatable("pokepad.lunaeternal.clan.invitar"));
        }
    }

    /**
     * El tesoro: cuánto hay, mover dinero y <b>quién lo ha movido</b>.
     *
     * <p>⚠ El historial va en la MISMA pestaña que los botones, y a propósito:
     * es lo que se mira justo antes de sacar y justo después de que falte algo.
     * En una pestaña aparte se consultaría cuando ya es tarde.
     */
    private void dibujarTesoro(DrawContext ctx, int rx, int ry) {
        int cx = PANT_X + PANT_W / 2;
        int y = primeraFilaY();

        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.tesoro"),
                cx, y, 18, TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(String.format("%,d", estado.mio().tesoro())),
                cx, y + 24, 40, ORO, true, false);

        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.cantidad"),
                PANEL_X + 28, PANEL_Y + 446, 16, TEXTO_SUAVE, false, false);
        campoCantidad.render(ctx, rx, ry, 0);

        int by = y + 76;
        botonPeq(ctx, rx, ry, cx - 250, by, 230, 40,
                Text.translatable("pokepad.lunaeternal.clan.aportar"));
        if (mando()) {
            botonPeq(ctx, rx, ry, cx + 20, by, 230, 40,
                    Text.translatable("pokepad.lunaeternal.clan.sacar"));
        }

        // ---- EL TOPE. Es la pieza de seguridad, así que se enseña siempre,
        //      también a los miembros: saber que existe es la mitad de para qué
        //      sirve.
        int ty = by + 48;
        separadorPantalla(ctx, ty);
        if (soyLider()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.clan.tope",
                            String.format("%,d", estado.topeOficial())),
                    PANT_X + MARGEN, ty + 12, 16, TEXTO_SUAVE, false, true);
            botonPeq(ctx, rx, ry, PANT_X + PANT_W - MARGEN - 190, ty + 8, 178, 30,
                    Text.translatable("pokepad.lunaeternal.clan.cambiar_tope"));
        } else if (mando()) {
            // Un oficial ve lo que le queda hoy. Enterarte del tope cuando te lo
            // rechazan es la peor forma de enterarte.
            long libre = Math.max(0, estado.topeOficial() - estado.sacadoHoy());
            texto(ctx, Text.translatable("pokepad.lunaeternal.clan.te_queda",
                            String.format("%,d", libre),
                            String.format("%,d", estado.topeOficial())),
                    PANT_X + MARGEN, ty + 14, 16,
                    libre <= 0 ? ROJO : TEXTO_SUAVE, false, true);
        } else {
            texto(ctx, Text.translatable("pokepad.lunaeternal.clan.tesoro_ayuda"),
                    PANT_X + MARGEN, ty + 14, 16, TEXTO_SUAVE, false, true);
        }

        // ---- EL HISTORIAL
        int hy = ty + 44;
        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.historial"),
                PANT_X + MARGEN, hy, 17, TEXTO_SUAVE, false, true);
        hy += 22;
        var movs = estado.movimientos();
        if (movs.isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.clan.sin_movimientos"),
                    cx, hy + 20, 16, TEXTO_SUAVE, true, false);
            return;
        }
        int caben = (PANT_Y + PANT_H - MARGEN - hy) / 22;
        int desde = desde();
        for (int n = 0; n < caben && desde + n < movs.size(); n++) {
            int i = desde + n;
            var m = movs.get(i);
            int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
            if (i % 2 == 0) {
                ctx.fill(px(ax), py(hy - 2), px(ax + aw), py(hy + 18), 0x22FFFFFF);
            }
            boolean entra = m.delta() > 0;
            // ⚠ El signo va DELANTE y con color. Una lista de cantidades sin
            //   signo obliga a leer el motivo para saber si el dinero entró o
            //   salió, y esta lista se lee buscando justo eso.
            texto(ctx, Text.literal((entra ? "+" : "-")
                            + String.format("%,d", Math.abs(m.delta()))),
                    ax + 4, hy, 16, entra ? VERDE : ROJO, false, true);
            texto(ctx, Text.literal(m.quien()), ax + 130, hy, 16, TEXTO_OSCURO,
                    false, true);
            texto(ctx, Text.literal(hace(m.cuando())), ax + aw - 150, hy, 15,
                    TEXTO_SUAVE, false, true);
            hy += 22;
        }
    }

    /**
     * El registro: quién entró, quién echó a quién, quién ascendió.
     *
     * <p>Es lo que convierte «me han echado y no sé por qué» en una línea con
     * nombre y hora.
     */
    private void dibujarRegistro(DrawContext ctx, int rx, int ry) {
        var lineas = estado.registro();
        if (lineas.isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.clan.sin_registro"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 20, TEXTO_SUAVE,
                    true, false);
            return;
        }
        int y = primeraFilaY();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        int caben = (PANT_Y + PANT_H - MARGEN - y) / 24;
        int desde = desde();
        for (int n = 0; n < caben && desde + n < lineas.size(); n++) {
            int i = desde + n;
            var l = lineas.get(i);
            if (i % 2 == 0) {
                ctx.fill(px(ax), py(y - 2), px(ax + aw), py(y + 20), 0x22FFFFFF);
            }
            texto(ctx, Text.translatable("pokepad.lunaeternal.clan.log." + l.accion()),
                    ax + 4, y, 16, colorAccion(l.accion()), false, true);
            String quien = l.aQuien().isEmpty()
                    ? l.quien()
                    : l.quien() + " \u2192 " + l.aQuien();
            texto(ctx, Text.literal(quien), ax + 190, y, 16, TEXTO_OSCURO, false, true);
            texto(ctx, Text.literal(hace(l.cuando())), ax + aw - 150, y, 15,
                    TEXTO_SUAVE, false, true);
            y += 24;
        }
    }

    /** Rojo lo que quita, verde lo que suma, ámbar lo que cambia el mando. */
    private static int colorAccion(String accion) {
        return switch (accion) {
            case "ECHAR", "SALIR", "DISOLVER" -> ROJO;
            case "FUNDAR", "ENTRAR", "ASCENDER" -> VERDE;
            case "TRASPASAR", "TOPE", "DEGRADAR" -> 0xFFA07800;
            default -> TEXTO_SUAVE;
        };
    }

    /**
     * «hace 5 min». No una fecha.
     *
     * <p>⚠ Una fecha obliga a restar mentalmente para saber si algo pasó antes o
     * después de que te fueras a dormir, que es la única pregunta que se le hace
     * a un registro de clan. Y evita el lío de zonas horarias entre el servidor y
     * quien mira.
     */
    private static String hace(long cuando) {
        long s = Math.max(0, (System.currentTimeMillis() - cuando) / 1000);
        if (s < 60) {
            return "ahora";
        }
        if (s < 3600) {
            return "hace " + (s / 60) + " min";
        }
        if (s < 86400) {
            return "hace " + (s / 3600) + " h";
        }
        return "hace " + (s / 86400) + " d";
    }

    private void separadorPantalla(DrawContext ctx, int artY) {
        ctx.fill(px(PANT_X + MARGEN), py(artY), px(PANT_X + PANT_W - MARGEN),
                py(artY) + Math.max(1, pl(2)), 0x44000000);
    }

    private void dibujarInvitaciones(DrawContext ctx, int rx, int ry) {
        var lista = estado.invitaciones();
        if (lista.isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.clan.sin_invitaciones"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 20, TEXTO_SUAVE, true, false);
            return;
        }
        int y = primeraFilaY();
        int desde = desde();
        for (int n = 0; n < capacidadPagina() && desde + n < lista.size(); n++) {
            var inv = lista.get(desde + n);
            int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA_ALTO), FILA_FONDO);
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA_ALTO), FILA_BORDE, Math.max(1, pl(2)));
            texto(ctx, Text.literal("[" + inv.etiqueta() + "] " + inv.nombre()),
                    ax + 14, y + 8, 20, TEXTO_OSCURO, false, true);
            texto(ctx, Text.translatable("pokepad.lunaeternal.clan.invitado_por",
                            inv.invitadoPor()),
                    ax + 14, y + 28, 14, TEXTO_SUAVE, false, true);
            botonPeq(ctx, rx, ry, ax + aw - 250, y + 8, 118, 30,
                    Text.translatable("pokepad.lunaeternal.clan.aceptar"));
            botonPeq(ctx, rx, ry, ax + aw - 126, y + 8, 114, 30,
                    Text.translatable("pokepad.lunaeternal.clan.rechazar"));
            y += FILA_ALTO + AIRE;
        }
    }

    private void dibujarClanes(DrawContext ctx, int rx, int ry) {
        var lista = estado.otros();
        if (lista.isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.clan.sin_clanes"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 20, TEXTO_SUAVE, true, false);
            return;
        }
        int y = primeraFilaY();
        int desde = desde();
        for (int n = 0; n < capacidadPagina() && desde + n < lista.size(); n++) {
            var c = lista.get(desde + n);
            int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA_ALTO), FILA_FONDO);
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA_ALTO), FILA_BORDE, Math.max(1, pl(2)));
            texto(ctx, Text.literal("[" + c.etiqueta() + "]"), ax + 14, y + 14, 20,
                    colorDe(c.color()), false, true);
            texto(ctx, Text.literal(c.nombre()), ax + 110, y + 14, 22, TEXTO_OSCURO,
                    false, true);
            texto(ctx, Text.literal(c.miembros() + " / 30"), ax + aw - 90, y + 15, 18,
                    TEXTO_SUAVE, false, true);
            y += FILA_ALTO + AIRE;
        }
        // ⚠ NO hay botón de «pedir entrar»: hoy solo se entra POR INVITACIÓN. La
        //   lista está para saber a quién buscar, y decirlo es mejor que poner un
        //   botón que no hace nada.
        texto(ctx, Text.translatable("pokepad.lunaeternal.clan.solo_invitacion"),
                PANT_X + PANT_W / 2, PANT_Y + PANT_H - 46, 16, TEXTO_SUAVE, true, false);
    }

    /**
     * Las flechas de página, en la banda naranja de abajo.
     *
     * <p>⚠ Se dibujan <b>apagadas en los extremos, no escondidas</b>: una flecha
     * que desaparece mueve la que queda y deja al jugador sin saber si ha llegado
     * al final o si algo ha dejado de funcionar. Es la misma decisión que en
     * Trabajos y en Cosméticos.
     */
    private void dibujarPaginas(DrawContext ctx, int rx, int ry) {
        int total = paginas();
        if (total <= 1) {
            return;
        }
        int cx = PANT_X + PANT_W / 2;
        dibujarFlechaPag(ctx, rx, ry, PAG_ATRAS, cx - PAG_SEP - PAG_W / 2, pagina > 0);
        dibujarFlechaPag(ctx, rx, ry, PAG_ADELANTE, cx + PAG_SEP - PAG_W / 2,
                pagina < total - 1);
        texto(ctx, Text.literal((pagina + 1) + " / " + total),
                cx, PAG_Y + PAG_H / 2 - 8, 18, TEXTO_SUAVE, true, false);
    }

    private void dibujarFlechaPag(DrawContext ctx, int rx, int ry, Identifier arte,
                                  int x, boolean viva) {
        boolean encima = viva && dentro(rx, ry, px(x), py(PAG_Y), pl(PAG_W), pl(PAG_H));
        // ⚠ El apagado se hace con transparencia y no dibujando otra textura:
        //   una flecha gris aparte seria arte nuevo que mantener.
        ctx.setShaderColor(1f, 1f, 1f, viva ? (encima ? 1f : 0.85f) : 0.35f);
        dibujarTextura(ctx, arte, px(x), py(PAG_Y), pl(PAG_W), pl(PAG_H), 120, 96);
        ctx.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * ⚠ NO da la vuelta al llegar al final: saltar de la última a la primera hace
     * que el jugador no sepa cuántas hay.
     */
    private boolean cambiarPagina(int paso) {
        int destino = pagina + paso;
        if (destino < 0 || destino >= paginas()) {
            return true;
        }
        pagina = destino;
        sonar();
        return true;
    }

    // ---- interacción -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) {
            return super.mouseClicked(mx, my, boton);
        }
        int rx = (int) mx, ry = (int) my;
        aviso = "";

        for (var c : new TextFieldWidget[] { campoNombre, campoEtiqueta,
                campoJugador, campoCantidad }) {
            if (c != null && c.mouseClicked(mx, my, boton)) {
                setFocused(c);
                return true;
            }
        }

        int cy = py(PANEL_Y + NAV_ALTO / 2);
        if (dentro(rx, ry, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48))) {
            sonar();
            if (client != null) {
                client.setScreen(anterior);
            }
            return true;
        }
        if (dentro(rx, ry, px(PANEL_X + PANEL_W - 18) - pl(80), cy - pl(32), pl(80), pl(64))) {
            sonar();
            close();
            return true;
        }

        // Las flechas van ANTES que las pestañas: comparten banda con nada, pero
        // el orden deja claro que la paginación manda sobre lo que haya debajo.
        if (paginas() > 1) {
            int pcx = PANT_X + PANT_W / 2;
            if (dentro(rx, ry, px(pcx - PAG_SEP - PAG_W / 2), py(PAG_Y),
                    pl(PAG_W), pl(PAG_H))) {
                return cambiarPagina(-1);
            }
            if (dentro(rx, ry, px(pcx + PAG_SEP - PAG_W / 2), py(PAG_Y),
                    pl(PAG_W), pl(PAG_H))) {
                return cambiarPagina(+1);
            }
        }

        int pw = pestanas.isEmpty() ? 0 : (PANT_W - 2 * MARGEN) / pestanas.size();
        for (int i = 0; i < pestanas.size(); i++) {
            if (dentro(rx, ry, px(PANT_X + MARGEN + i * pw), py(PANT_Y + MARGEN),
                    pl(pw - 6), pl(PESTANA_ALTO))) {
                pestana = i;
                // ⚠ Cambiar de pestaña vuelve a la página 1. Sin esto, pasar de
                //   una lista larga a una corta deja la pantalla EN BLANCO.
                pagina = 0;
                sonar();
                return true;
            }
        }

        // El botón grande del panel
        if (dentro(rx, ry, px(PANEL_X + 40), py(PANEL_Y + PANEL_H - 76),
                pl(PANEL_W - 80), pl(46))) {
            if (!tengoClan()) {
                mandar("fundar", campoNombre.getText(), campoEtiqueta.getText(), 0, 0);
            } else if (soyLider()) {
                mandar("disolver", "", "", 0, 0);
            } else {
                mandar("salir", "", "", 0, 0);
            }
            return true;
        }

        String cual = pestanas.isEmpty() ? "" : pestanas.get(pestana);
        if ("miembros".equals(cual)) {
            return clicMiembros(rx, ry);
        }
        if ("tesoro".equals(cual)) {
            return clicTesoro(rx, ry);
        }
        if ("invitaciones".equals(cual)) {
            return clicInvitaciones(rx, ry);
        }
        return super.mouseClicked(mx, my, boton);
    }

    private boolean clicMiembros(int rx, int ry) {
        var lista = estado.miembros();
        int y = primeraFilaY();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        // ⚠⚠⚠ EL MISMO DESPLAZAMIENTO QUE EL DIBUJADO. Sin el, pulsar en la
        //    primera fila de la pagina 2 actua sobre el PRIMERO de la lista --
        //    y no da ningun error.
        int desde = desde();
        for (int n = 0; n < capacidadPagina() && desde + n < lista.size(); n++) {
            var m = lista.get(desde + n);
            if (mando() && !"LIDER".equals(m.rol())) {
                if (soyLider() && dentro(rx, ry, px(ax + aw - 250), py(y + 8),
                        pl(110), pl(30))) {
                    mandar("MIEMBRO".equals(m.rol()) ? "ascender" : "degradar",
                            "", "", m.playerId(), 0);
                    return true;
                }
                if (dentro(rx, ry, px(ax + aw - 130), py(y + 8), pl(118), pl(30))) {
                    mandar("echar", "", "", m.playerId(), 0);
                    return true;
                }
            }
            y += FILA_ALTO + AIRE;
        }
        if (mando() && dentro(rx, ry, px(PANT_X + MARGEN + 376), py(PANT_Y + PANT_H - 54),
                pl(150), pl(34))) {
            mandar("invitar", campoJugador.getText(), "", 0, 0);
            campoJugador.setText("");
            return true;
        }
        return false;
    }

    private boolean clicTesoro(int rx, int ry) {
        int cx = PANT_X + PANT_W / 2;
        int by = primeraFilaY() + 76;
        long cantidad = leerCantidad();
        if (dentro(rx, ry, px(cx - 250), py(by), pl(230), pl(40))) {
            if (cantidad <= 0) {
                aviso = "Escribe una cantidad.";
                sonar();
                return true;
            }
            mandar("aportar", "", "", 0, cantidad);
            return true;
        }
        if (mando() && dentro(rx, ry, px(cx + 20), py(by), pl(230), pl(40))) {
            if (cantidad <= 0) {
                aviso = "Escribe una cantidad.";
                sonar();
                return true;
            }
            mandar("sacar", "", "", 0, cantidad);
            return true;
        }
        // El tope: solo el líder, y usa el mismo campo de cantidad.
        int ty = by + 48;
        if (soyLider() && dentro(rx, ry, px(PANT_X + PANT_W - MARGEN - 190),
                py(ty + 8), pl(178), pl(30))) {
            // ⚠ Aquí el 0 SÍ es válido: significa «que no saquen nada». Por eso
            //   no se rechaza como en aportar y sacar -- y por eso el texto del
            //   botón lo dice.
            if (campoCantidad.getText().trim().isEmpty()) {
                aviso = "Escribe el tope en el campo de cantidad.";
                sonar();
                return true;
            }
            mandar("tope", "", "", 0, cantidad);
            return true;
        }
        return false;
    }

    /**
     * ⚠ Se acota y se comprueba aquí <b>además</b> de en el servidor. No es
     * duplicar la regla: es no mandar un paquete que se sabe que va a fallar, y
     * poder decir «escribe una cantidad» en vez de esperar un rechazo.
     */
    private long leerCantidad() {
        try {
            return Math.max(0, Long.parseLong(campoCantidad.getText().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean clicInvitaciones(int rx, int ry) {
        var lista = estado.invitaciones();
        int y = primeraFilaY();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        // ⚠⚠⚠ EL MISMO DESPLAZAMIENTO QUE EL DIBUJADO. Sin el, pulsar en la
        //    primera fila de la pagina 2 actua sobre el PRIMERO de la lista --
        //    y no da ningun error.
        int desde = desde();
        for (int n = 0; n < capacidadPagina() && desde + n < lista.size(); n++) {
            var inv = lista.get(desde + n);
            if (dentro(rx, ry, px(ax + aw - 250), py(y + 8), pl(118), pl(30))) {
                mandar("aceptar", "", "", inv.clanId(), 0);
                return true;
            }
            if (dentro(rx, ry, px(ax + aw - 126), py(y + 8), pl(114), pl(30))) {
                mandar("rechazar", "", "", inv.clanId(), 0);
                return true;
            }
            y += FILA_ALTO + AIRE;
        }
        return false;
    }

    /**
     * ⚠ No se pinta el resultado: se manda y se espera. El servidor contesta con
     * el estado entero y la pantalla se redibuja sola. Adelantarse haría que un
     * rechazo se viera como un cambio que desaparece al reabrir.
     */
    private void mandar(String accion, String texto, String texto2,
                        long objetivo, long cantidad) {
        sonar();
        ClientPlayNetworking.send(new Red.AccionClan(accion, texto, texto2,
                objetivo, cantidad));
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        // ⚠ Los campos de texto se quedan con la tecla ANTES que la pantalla. Sin
        //   esto, escribir «e» en el nombre del clan abriría el inventario.
        for (var c : new TextFieldWidget[] { campoNombre, campoEtiqueta,
                campoJugador, campoCantidad }) {
            if (c != null && c.isFocused() && c.keyPressed(tecla, escaneo, mods)) {
                return true;
            }
        }
        return super.keyPressed(tecla, escaneo, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        for (var campo : new TextFieldWidget[] { campoNombre, campoEtiqueta,
                campoJugador, campoCantidad }) {
            if (campo != null && campo.isFocused() && campo.charTyped(c, mods)) {
                return true;
            }
        }
        return super.charTyped(c, mods);
    }

    private void sonar() {
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.0f);
        }
    }

    // ---- utilidades --------------------------------------------------------

    private void boton(DrawContext ctx, int rx, int ry, int ax, int ay, int aw, int ah,
                       Text etiqueta, boolean activo) {
        boolean encima = activo && dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah),
                !activo ? 0xFF6E7899 : (encima ? 0xFFFFD65C : 0xFFE8A317));
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), 0xFF8A5C00, Math.max(1, pl(2)));
        texto(ctx, etiqueta, ax + aw / 2, ay + ah / 2 - 12, 24,
                activo ? 0xFF2A1C00 : 0xFFD8DEEA, true, false);
    }

    private void botonPeq(DrawContext ctx, int rx, int ry, int ax, int ay, int aw, int ah,
                          Text etiqueta) {
        boolean encima = dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah), encima ? 0xFF5E86D8 : 0xFF4F6FB0);
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), 0xFF2E4270, Math.max(1, pl(2)));
        int alto = 17;
        while (alto > 10 && anchoArte(etiqueta.getString(), alto) > aw - 12) {
            alto--;
        }
        texto(ctx, etiqueta, ax + aw / 2, ay + (ah - alto) / 2 - 1, alto,
                0xFFFFFFFF, true, false);
    }

    private static int colorDe(String codigo) {
        char c = codigo == null || codigo.isEmpty() ? 'b' : codigo.charAt(0);
        return switch (c) {
            case 'a' -> 0xFF55E06A;
            case 'c' -> 0xFFE05555;
            case 'b' -> 0xFF55D6E0;
            case '6' -> 0xFFE0A845;
            case 'd' -> 0xFFE067C8;
            case 'e' -> 0xFFF0E060;
            default -> 0xFFB8C2DA;
        };
    }

    private List<String> partir(String texto, int anchoArte, int altoArte) {
        List<String> salida = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
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
