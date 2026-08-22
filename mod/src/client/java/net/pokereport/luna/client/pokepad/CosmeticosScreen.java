package net.pokereport.luna.client.pokepad;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * La tienda de cosméticos del PokePad.
 *
 * <p>Izquierda: el previsualizador 3D y el saldo de LunaCoins con su «+».
 * Derecha: cuatro pestañas y una rejilla de 4×2 con el cosmético en 3D, su
 * precio y su botón.
 *
 * <h2>Las medidas salen del arte, no de esta clase</h2>
 *
 * Los números de {@code PANEL_*} y {@code PANT_*} los <b>midió</b>
 * {@code tools/gen_cosmeticos.py} sobre {@code fondo_cosmeticos.png}, buscando
 * la mancha clara de la derecha y el rectángulo oscuro de la izquierda. Están
 * aquí copiados porque en tiempo de ejecución no se puede analizar el PNG, pero
 * <b>la fuente es el generador</b>: si el fondo cambia, se vuelve a ejecutar y
 * se traen los números nuevos. Escribirlos a ojo es lo que ya dejó al chasis
 * mintiendo cuatro veces.
 *
 * <h2>Lo que esta pantalla NO decide</h2>
 *
 * Nada. El catálogo, los precios, qué posees y qué llevas puesto vienen del
 * servidor (P6), y con {@code D-039} eso pasa de buena costumbre a invariante:
 * si los cosméticos <b>solo</b> se consiguen comprándolos o en un evento, un
 * cliente que pudiera concederse uno sería la única forma de saltárselo.
 *
 * <p>El catálogo llega en {@code Red.Cosmeticos} y se relee <b>en cada
 * fotograma</b> desde {@code EstadoCliente}: llega de forma asíncrona y además
 * el servidor lo reenvía entero tras cada compra, así que sin releerlo el botón
 * seguiría diciendo COMPRAR después de haber comprado.
 *
 * <p>⚠ Que el catálogo sea {@code null} <b>no es un error, es «todavía no»</b>.
 * Enseñar una tienda vacía mientras el paquete está en vuelo hace creer que no
 * hay nada a la venta.
 *
 * <h2>Antes de tocar el dibujado</h2>
 *
 * Lee {@code docs/ui/dibujado.md}. Son seis reglas y <b>ninguna da error al
 * compilar</b>: se pagan en horas depurando. La primera —encender la mezcla
 * alfa a mano— costó una noche entera.
 */
public class CosmeticosScreen extends Screen {

    private static final Identifier CHASIS = tex("pokepad_cosmeticos");
    private static final Identifier MONEDA = tex("lunacoin_oro");
    private static final Identifier MAS = tex("boton_mas_luna");
    private static final Identifier ATRAS = tex("boton_atras");
    private static final Identifier ADELANTE = tex("boton_adelante");
    private static final Identifier CERRAR = tex("boton_cerrar");

    /** El arte, a tamaño real. Ver la regla 2 de `dibujado.md`. */
    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;

    // ---- medidas del arte, MEDIDAS por tools/gen_cosmeticos.py -------------
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;

    private static final int NAV_ALTO = 72;
    private static final int SALDO_ALTO = 147;

    private static final int COLS = 4, FILAS = 2;
    private static final int AIRE = 14, MARGEN = 12, PESTANA_ALTO = 56;

    /**
     * El pie de la celda: precio y botón en la MISMA fila.
     *
     * <p>⚠ Esto solo cabe con 4 columnas, y ese es el motivo de que sean 4. Con
     * 5 la celda medía 144: el precio con su moneda se llevaba 80 y quedaban 60
     * para el botón, donde «COMPRAR» no entra. Había que apilarlos, y apilarlos
     * costaba 72 px de <b>alto</b> que salían del 3D, dejándolo en 124×90 — un
     * Charizard ahí no se distingue.
     */
    private static final int PIE = 38;

    // ---- las flechas de pagina, MEDIDAS sobre la banda naranja del chasis ---
    //
    // ⚠ Estas cuatro cifras salen de recorrer el PNG, no de mirarlo. La banda
    //   calida de abajo va de y=698 a y=745 y lleva adornos oscuros --las muescas
    //   en diagonal-- en x=732..744, 763..774, 936..947 y 966..978. Los huecos
    //   limpios que quedan son 437..731, 775..935 y 979..1273, asi que las
    //   flechas van en el primero y el tercero, a la misma distancia del centro
    //   de la pantalla (x=860). Escribirlas a ojo ya ha salido mal cuatro veces
    //   con este chasis.
    private static final int PAG_W = 50, PAG_H = 40;
    private static final int PAG_Y = 698 + (745 - 698 - PAG_H) / 2;
    private static final int PAG_SEP = 215;      // desde el centro de la pantalla

    // ---- paleta, la misma de la pantalla principal -------------------------
    private static final int CELDA_FONDO = 0xFFBFCBE8;
    private static final int CELDA_BORDE = 0xFF7C89B4;
    private static final int CELDA_ENCIMA = 0xFFFFF0DC;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int ORO = 0xFFFFD65C;
    private static final int VERDE_PUESTO = 0xFF157A3E;

    /**
     * Las pestañas SALEN DEL CATÁLOGO que manda el servidor, no de una lista de
     * aquí.
     *
     * <p>⚠⚠ ESTUVO ESCRITA A MANO Y ERA UN FALLO ESPERANDO. Eran dos listas en
     * dos lados —{@code Catalogo.categorias()} en el servidor y un array aquí— sin
     * nada que las atara. Si aquí sobraba una, salía una pestaña vacía para
     * siempre; si faltaba, <b>sus cosméticos eran inalcanzables aunque el servidor
     * los mandara</b>. Al retirar «Capas» hubo que acordarse de tocar las dos, y
     * acordarse no es un mecanismo.
     *
     * <p>Ahora se derivan de las piezas recibidas, en su orden de aparición —que
     * es el de {@code Catalogo.todas()}—. Una categoría sin piezas no sale, que es
     * exactamente lo que se quiere: una pestaña vacía no es información.
     *
     * <p>El respaldo solo cubre el hueco entre abrir la pantalla y que llegue el
     * catálogo, que es un fotograma o dos. Sin él, la fila de pestañas parpadea al
     * abrir.
     */
    private static final List<String> RESPALDO = List.of("mascotas", "sombreros", "auras");

    private List<String> categorias = RESPALDO;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private int pestana = 0;
    private int pagina = 0;

    /** Lo que se ve en el previsualizador. {@code null} = nada seleccionado. */
    private Cosmetico enfocado;

    private List<Cosmetico> catalogo = List.of();
    private int lunacoins = 0;

    public CosmeticosScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.cosmeticos"));
        this.anterior = anterior;
    }

    private static Identifier tex(String nombre) {
        return Identifier.of("lunaeternal", "textures/gui/pokepad/" + nombre + ".png");
    }

    @Override
    protected void init() {
        recalcular();
        // Se pide al abrir. Si ya habia catalogo de una visita anterior se
        // dibuja mientras llega el nuevo: enseñar lo de antes un instante es
        // mejor que enseñar una tienda vacia que luego se llena de golpe.
        leerDelServidor();
        ClientPlayNetworking.send(new Red.PedirCosmeticos());
    }

    /**
     * Trae lo ultimo que mando el servidor.
     *
     * <p>⚠ Se llama en cada fotograma, no solo al abrir. El catalogo llega de
     * forma asincrona y ademas se reenvia tras cada compra: sin releerlo, el
     * boton seguiria diciendo COMPRAR despues de haber comprado.
     */
    private void leerDelServidor() {
        Red.Cosmeticos c = EstadoCliente.cosmeticos();
        if (c == null) {
            return;
        }
        lunacoins = (int) Math.min(Integer.MAX_VALUE, c.lunacoins());
        List<Cosmetico> lista = new ArrayList<>(c.piezas().size());
        for (Red.PiezaCosmetica p : c.piezas()) {
            lista.add(new Cosmetico(
                    p.categoria(), p.id(), p.especie(), p.aspecto(), p.precio(),
                    (p.banderas() & Red.PiezaCosmetica.POSEIDO) != 0,
                    (p.banderas() & Red.PiezaCosmetica.EQUIPADO) != 0,
                    (p.banderas() & Red.PiezaCosmetica.EQUIPABLE) != 0));
        }
        catalogo = lista;

        // Las pestañas, en el orden en que las piezas llegan --que es el de
        // `Catalogo.todas()` en el servidor--. Ver el comentario de `RESPALDO`.
        List<String> cats = new ArrayList<>();
        for (Cosmetico x : lista) {
            if (!cats.contains(x.categoria())) {
                cats.add(x.categoria());
            }
        }
        if (!cats.isEmpty()) {
            categorias = List.copyOf(cats);
            // ⚠ La pestaña abierta puede quedarse fuera de rango si el servidor
            //   retira una categoria mientras la pantalla esta abierta. Sin esto
            //   seria un IndexOutOfBounds en el siguiente fotograma.
            if (pestana >= categorias.size()) {
                pestana = 0;
                pagina = 0;
            }
        }

        // Se reengancha el enfoque POR IDENTIFICADOR, no por referencia: la
        // lista se reconstruye entera en cada paquete, asi que el objeto que
        // tenia el previsualizador ya no esta en ella. Sin esto, comprar lo que
        // estabas mirando vaciaba el previsualizador.
        if (enfocado != null) {
            String id = enfocado.id();
            enfocado = lista.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
        }
        if (enfocado == null) {
            enfocado = lista.stream().filter(Cosmetico::equipado).findFirst().orElse(null);
        }
    }

    private void recalcular() {
        double gui = client != null ? client.getWindow().getScaleFactor() : 1;
        int ventanaW = client == null ? NAT_ANCHO : client.getWindow().getFramebufferWidth();
        int ventanaH = client == null ? NAT_ALTO : client.getWindow().getFramebufferHeight();
        double cabe = Math.min(ventanaW / (double) NAT_ANCHO, ventanaH / (double) NAT_ALTO);
        k = (float) (Math.min(1.0, cabe) / gui);
        ancho = Math.round(NAT_ANCHO * k);
        alto = Math.round(NAT_ALTO * k);
        x0 = (width - ancho) / 2;
        y0 = (height - alto) / 2;

        // ⚠ REGLA 3: encoger con vecino más próximo TIRA filas y columnas
        // enteras. Solo si el tamaño no sale exacto se pasa a filtrado lineal,
        // que reparte el error en vez de dejar rayas cruzando el chasis.
        boolean exacto = Math.round(ancho * gui) == NAT_ANCHO
                && Math.round(alto * gui) == NAT_ALTO;
        if (client != null) {
            for (Identifier t : new Identifier[] { CHASIS, MONEDA, MAS, ATRAS, CERRAR }) {
                client.getTextureManager().getTexture(t).setFilter(!exacto, false);
            }
        }
    }

    /** El juego sigue corriendo detrás: es un menú, no una pausa. */
    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- geometría, en coordenadas de pantalla -----------------------------

    private int px(int artX) {
        return x0 + Math.round(artX * k);
    }

    private int py(int artY) {
        return y0 + Math.round(artY * k);
    }

    private int pl(int largoArt) {
        return Math.round(largoArt * k);
    }

    /** Los cosméticos de la pestaña activa. */
    private List<Cosmetico> visibles() {
        String cat = categorias.get(Math.min(pestana, categorias.size() - 1));
        return catalogo.stream().filter(c -> c.categoria().equals(cat)).toList();
    }

    private int porPagina() {
        return COLS * FILAS;
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int ratonX, int ratonY, float delta) {
        super.render(ctx, ratonX, ratonY, delta);
        recalcular();
        leerDelServidor();

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);

        // ⚠⚠ DOS PASADAS, Y ESTE ES EL ARREGLO DEL TITILEO.
        //
        // `DrawContext` NO dibuja al momento: acumula rectangulos y texturas en
        // un bufer y los vuelca cuando le toca. `drawProfilePokemon`, en cambio,
        // dibuja YA, hablando con OpenGL directamente.
        //
        // Intercalados --una celda, su modelo, la siguiente celda...-- el orden
        // entre lo 2D y lo 3D cambia de un fotograma a otro segun cuando caiga
        // el volcado, y eso es lo que se ve como titileo. No es el modelo: es
        // quien pinta primero, y cambia solo.
        //
        // Se probaron dos parches antes y ninguno bastaba, porque los dos
        // seguian intercalando: vaciar el bufer alrededor de cada modelo, y
        // cambiar la profundidad. Lo que lo arregla es no intercalar.
        //
        // Primera pasada: TODO lo plano. Segunda: TODOS los modelos, ya con el
        // bufer vacio. Un solo orden posible, siempre el mismo.
        dibujarNavegacion(ctx, ratonX, ratonY);
        dibujarPreview(ctx);
        dibujarSaldo(ctx, ratonX, ratonY);
        dibujarPestanas(ctx, ratonX, ratonY);
        dibujarRejilla(ctx, ratonX, ratonY);
        dibujarPaginas(ctx, ratonX, ratonY);

        ctx.draw();

        dibujarModelos(ctx, ratonX, ratonY, delta);
    }

    /**
     * Segunda pasada: SOLO los modelos, con el bufer de la interfaz ya vaciado.
     *
     * <p>Ver el comentario de `render`. Aqui no se dibuja ni un rectangulo ni
     * una letra: en cuanto se mezcle algo plano, vuelve el titileo.
     */
    private void dibujarModelos(DrawContext ctx, int rx, int ry, float delta) {
        // El previsualizador. Su caja es alta y estrecha, asi que el origen va
        // mas abajo que en una celda: con 0,06 el modelo quedaria pegado al
        // techo del panel.
        if (enfocado != null) {
            int ax = PANEL_X + 8, ay = PANEL_Y + NAV_ALTO;
            int aw = PANEL_W - 16, ah = PANEL_H - NAV_ALTO - SALDO_ALTO - 8;
            Mascota3D.dibujar(ctx, enfocado, "preview", px(ax), py(ay), pl(aw), pl(ah),
                    0.34f, delta, true);
        }

        List<Cosmetico> lista = visibles();
        int anchoUtil = PANT_W - 2 * MARGEN;
        int gy0 = PANT_Y + MARGEN + PESTANA_ALTO + AIRE;
        int altoUtil = PANT_Y + PANT_H - gy0 - MARGEN;
        int cw = (anchoUtil - (COLS - 1) * AIRE) / COLS;
        int ch = (altoUtil - (FILAS - 1) * AIRE) / FILAS;
        int desde = pagina * porPagina();

        for (int n = 0; n < porPagina(); n++) {
            int idx = desde + n;
            if (idx >= lista.size()) {
                break;
            }
            int ax = PANT_X + MARGEN + (n % COLS) * (cw + AIRE);
            int ay = gy0 + (n / COLS) * (ch + AIRE);
            int x = px(ax), y = py(ay), w = pl(cw), h = pl(ch);
            boolean encima = dentro(rx, ry, x, y, w, h);
            // La caja del modelo es la celda menos el pie, que es donde van el
            // precio y el boton.
            Mascota3D.dibujar(ctx, lista.get(idx), "celda", x + pl(10), y + pl(6),
                    w - pl(20), h - pl(PIE + 6), 0.06f, delta, encima);
        }
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = py(PANEL_Y + NAV_ALTO / 2);
        int aw = pl(60), ah = pl(48);
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), cy - ah / 2, aw, ah, 60, 48);
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicio"),
                PANEL_X + 18 + 60 + 12, PANEL_Y + NAV_ALTO / 2 - 13, 26,
                0xFFD2D8E8, false, false);
        int cw = pl(80), chh = pl(64);
        dibujarTextura(ctx, CERRAR, px(PANEL_X + PANEL_W - 18) - cw, cy - chh / 2, cw, chh, 80, 64);
    }

    private void dibujarPreview(DrawContext ctx) {
        // Igual que en la celda: lo plano del aura va en esta pasada.
        if (enfocado != null) {
            Mascota3D.dibujarAura(ctx, enfocado,
                    px(PANEL_X + 8), py(PANEL_Y + NAV_ALTO),
                    pl(PANEL_W - 16), pl(PANEL_H - NAV_ALTO - SALDO_ALTO - 8));
        }
        int ax = PANEL_X + 8, ay = PANEL_Y + NAV_ALTO;
        int aw = PANEL_W - 16, ah = PANEL_H - NAV_ALTO - SALDO_ALTO - 8;

        if (enfocado == null) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.sin_seleccion"),
                    ax + aw / 2, ay + ah / 2, 24, TEXTO_SUAVE, true, false);
            return;
        }
        texto(ctx, Text.literal(nombreDe(enfocado)), ax + aw / 2, ay + ah - 34, 26,
                ORO, true, false);
        texto(ctx, Text.literal(subtituloDe(enfocado)), ax + aw / 2, ay + ah - 6, 20,
                TEXTO_SUAVE, true, false);
    }

    /**
     * «Charizard» a partir de «cobblemon:charizard».
     *
     * <p>⚠ Antes la celda solo enseñaba el ASPECTO —«knight», «chef»— y no la
     * especie, asi que la tienda era una lista de adjetivos sueltos: no se sabia
     * de que Pokemon era cada uno sin mirar el dibujo.
     */
    private static String nombreDe(Cosmetico c) {
        // ⚠ EN LOS COSMETICOS DEL JUGADOR EL NOMBRE VA EN `aspecto`, no en
        //   `especie` --que esta vacia, porque no dependen de ningun Pokemon--.
        //   Sin esto la tienda enseñaba el identificador crudo: «aura_neon_cian».
        //   El campo se reutiliza en vez de añadir uno sexto al paquete: en las
        //   mascotas `aspecto` ya es «lo que distingue a esta pieza», y en las
        //   del jugador tambien.
        if (!c.esMascota()) {
            return c.aspecto().isEmpty() ? c.id() : c.aspecto();
        }
        String s = c.especie();
        int i = s.indexOf(':');
        if (i >= 0) {
            s = s.substring(i + 1);
        }
        if (s.isEmpty()) {
            return c.id();
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** La linea pequeña bajo el nombre en el previsualizador. */
    private static String subtituloDe(Cosmetico c) {
        // En una mascota, el aspecto («knight») dice cual de los disfraces de esa
        // especie es. En un cosmetico del jugador, el aspecto YA ES el nombre, asi
        // que repetirlo no informa de nada: se pone la categoria.
        return c.esMascota() ? c.aspecto() : c.categoria();
    }

    private void dibujarSaldo(DrawContext ctx, int rx, int ry) {
        int acy = PANEL_Y + PANEL_H - SALDO_ALTO / 2;
        int m = pl(40);
        dibujarTextura(ctx, MONEDA, px(PANEL_X + 24), py(acy) - m / 2, m, m, 100, 100);
        texto(ctx, Text.literal(String.format("%,d", lunacoins)),
                PANEL_X + 24 + 40 + 14, acy - 17, 34, ORO, false, false);
        int mw = pl(58);
        dibujarTextura(ctx, MAS, px(PANEL_X + PANEL_W - 22) - mw, py(acy) - mw / 2, mw, mw, 58, 58);
    }

    private void dibujarPestanas(DrawContext ctx, int rx, int ry) {
        int anchoUtil = PANT_W - 2 * MARGEN;
        int pw = anchoUtil / categorias.size();
        for (int i = 0; i < categorias.size(); i++) {
            int ax = PANT_X + MARGEN + i * pw, ay = PANT_Y + MARGEN;
            int x = px(ax), y = py(ay), w = pl(pw - 6), h = pl(PESTANA_ALTO);
            boolean activa = i == pestana;
            boolean encima = dentro(rx, ry, x, y, w, h);

            ctx.fill(x, y, x + w, y + h, activa || encima ? CELDA_ENCIMA : CELDA_FONDO);
            marco(ctx, x, y, w, h, activa || encima ? BORDE_ENCIMA : CELDA_BORDE,
                    Math.max(1, pl(activa ? 4 : 2)));
            texto(ctx, Text.translatable("pokepad.lunaeternal.cat." + categorias.get(i)),
                    ax + (pw - 6) / 2, ay + PESTANA_ALTO / 2 - 13, 26,
                    TEXTO_OSCURO, true, true);
        }
    }

    /**
     * Las flechas de pagina, en la banda naranja de abajo.
     *
     * <p>⚠⚠ <b>SIN ESTO, 54 DE LOS 62 COSMETICOS ERAN INALCANZABLES.</b> El campo
     * {@code pagina} existia y se usaba en los tres sitios que tocaba —dibujar la
     * rejilla, dibujar los modelos y detectar el clic— pero <b>nada lo cambiaba
     * nunca</b>. La tienda enseñaba los ocho primeros y no habia forma de ver el
     * resto: ni flechas, ni rueda, ni teclas. Y no daba ningun error, claro.
     *
     * <p>Se dibujan apagadas en los extremos en vez de esconderse. Una flecha que
     * desaparece mueve la que queda y deja al jugador sin saber si ha llegado al
     * final o si ha dejado de funcionar algo.
     */
    private void dibujarPaginas(DrawContext ctx, int rx, int ry) {
        int total = paginas();
        if (total <= 1) {
            return;            // una sola pagina: las flechas solo estorbarian
        }
        int cx = PANT_X + PANT_W / 2;
        dibujarFlecha(ctx, ATRAS, cx - PAG_SEP - PAG_W / 2, rx, ry, pagina > 0);
        dibujarFlecha(ctx, ADELANTE, cx + PAG_SEP - PAG_W / 2, rx, ry, pagina < total - 1);

        // El contador va entre las dos, en el hueco central que los adornos
        // dejan libre (x=775..935). A escala ENTERA: ver `textoNitido`.
        textoNitido(ctx, Text.literal((pagina + 1) + " / " + total),
                cx, PAG_Y + PAG_H / 2, 18, 0xFFFFFFFF);
    }

    private void dibujarFlecha(DrawContext ctx, Identifier tex, int ax,
                               int rx, int ry, boolean viva) {
        boolean encima = viva && dentro(rx, ry, px(ax), py(PAG_Y), pl(PAG_W), pl(PAG_H));
        // El realce es un marco, no un cambio de color: la textura del boton ya
        // trae su propio tono y teñirla la ensucia.
        if (encima) {
            marco(ctx, px(ax) - 2, py(PAG_Y) - 2, pl(PAG_W) + 4, pl(PAG_H) + 4,
                    BORDE_ENCIMA, 2);
        }
        // Lo apagado se dibuja a media opacidad. Es lo que hace el resto del Pad
        // con las celdas bloqueadas, asi que se lee igual sin explicar nada.
        ctx.setShaderColor(1f, 1f, 1f, viva ? 1f : 0.4f);
        dibujarTextura(ctx, tex, px(ax), py(PAG_Y), pl(PAG_W), pl(PAG_H), 120, 96);
        ctx.setShaderColor(1f, 1f, 1f, 1f);
    }

    private int paginas() {
        int n = visibles().size();
        return Math.max(1, (n + porPagina() - 1) / porPagina());
    }

    private void dibujarRejilla(DrawContext ctx, int rx, int ry) {
        List<Cosmetico> lista = visibles();
        int anchoUtil = PANT_W - 2 * MARGEN;
        int gy0 = PANT_Y + MARGEN + PESTANA_ALTO + AIRE;
        int altoUtil = PANT_Y + PANT_H - gy0 - MARGEN;
        int cw = (anchoUtil - (COLS - 1) * AIRE) / COLS;
        int ch = (altoUtil - (FILAS - 1) * AIRE) / FILAS;

        int desde = pagina * porPagina();
        for (int n = 0; n < porPagina(); n++) {
            int idx = desde + n;
            if (idx >= lista.size()) {
                break;
            }
            Cosmetico c = lista.get(idx);
            int ax = PANT_X + MARGEN + (n % COLS) * (cw + AIRE);
            int ay = gy0 + (n / COLS) * (ch + AIRE);
            dibujarCelda(ctx, c, ax, ay, cw, ch, rx, ry);
        }
    }

    private void dibujarCelda(DrawContext ctx, Cosmetico c, int ax, int ay, int aw, int ah,
                              int rx, int ry) {
        int x = px(ax), y = py(ay), w = pl(aw), h = pl(ah);
        boolean encima = dentro(rx, ry, x, y, w, h);
        boolean elegido = enfocado != null && enfocado.id().equals(c.id());
        boolean marcado = encima || elegido || c.equipado();

        ctx.fill(x, y, x + w, y + h, encima ? CELDA_ENCIMA : CELDA_FONDO);
        marco(ctx, x, y, w, h, marcado ? BORDE_ENCIMA : CELDA_BORDE,
                Math.max(1, pl(marcado ? 4 : 2)));

        // Los puntos del aura. Van AQUI --pasada 2D-- y no con el modelo: ver
        // `Mascota3D.dibujarAura`. Quedan detras del personaje, que es la
        // consecuencia aceptada de no volver a mezclar plano y 3D.
        Mascota3D.dibujarAura(ctx, c, x, y, w, h - pl(PIE));

        // El 3D ocupa todo lo que no es el pie. Se dibuja ANTES que el nombre
        // para que el nombre quede por encima y siga leyendose.
        texto(ctx, Text.literal(nombreDe(c)), ax + aw / 2, ay + ah - PIE - 24, 22,
                TEXTO_OSCURO, true, true);

        dibujarPie(ctx, c, ax, ay + ah - PIE, aw, PIE);
    }

    private void dibujarPie(DrawContext ctx, Cosmetico c, int ax, int ay, int aw, int ah) {
        Cosmetico.Estado est = c.estado();
        Text etiqueta = Text.translatable("pokepad.lunaeternal." + clave(est));

        // ---- el precio primero, porque es el que NO se puede recortar
        int altoPrecio = 18;
        Text precio = etiquetaIzquierda(c);
        int finPrecio = est == Cosmetico.Estado.COMPRAR
                ? ax + 36 + anchoArte(precio, altoPrecio)
                : ax + 10 + anchoArte(precio, 16);

        // ---- y el boton se queda con lo que sobra
        //
        // ⚠⚠ ASI ES COMO SE ARREGLA DE VERDAD, Y NO RESERVANDO UN HUECO FIJO.
        //
        // Antes ponia `bw = min(aw - 74, ...)`: 74 pixeles de reserva a ojo. Un
        // precio de cuatro cifras necesita mas, asi que el boton se comia el
        // ultimo digito y la tienda enseñaba "250" donde ponia 2500 -- un precio
        // DIEZ VECES MENOR, otra vez, y otra vez sin dar ningun error.
        //
        // Ahora el hueco se MIDE del precio real, y si lo que queda no da para
        // "COMPRAR" a cuerpo 18, se encoge la LETRA del boton en vez de invadir
        // el precio. El precio siempre gana: es el dato que no se puede mentir.
        int bx = finPrecio + 10;
        int bw = ax + aw - 10 - bx;
        if (bw < 40) {
            return;                       // celda absurda: mejor sin boton que encima del precio
        }
        int altoBoton = 18;
        int necesita = anchoArte(etiqueta, altoBoton) + 14;
        if (necesita > bw) {
            altoBoton = Math.max(10, altoBoton * (bw - 14) / Math.max(1, anchoArte(etiqueta, altoBoton)));
        }

        if (est == Cosmetico.Estado.COMPRAR) {
            int m = pl(22);
            dibujarTextura(ctx, MONEDA, px(ax + 10), py(ay + ah / 2) - m / 2, m, m, 100, 100);
            texto(ctx, precio, ax + 36, ay + ah / 2 - altoPrecio / 2, altoPrecio,
                    TEXTO_OSCURO, false, true);
        } else {
            // PUESTO en verde y TUYO en gris. Es lo unico que separa "lo tienes"
            // de "lo tienes Y lo llevas", ahora que el boton dice la accion en
            // vez del estado.
            int tono = est == Cosmetico.Estado.EQUIPADO ? VERDE_PUESTO : TEXTO_SUAVE;
            texto(ctx, precio, ax + 10, ay + ah / 2 - 8, 16, tono, false, true);
        }

        int relleno = switch (est) {
            case COMPRAR -> BORDE_ENCIMA;
            case DE_EVENTO -> 0xFF6E7899;
            case EQUIPAR -> 0xFF567AC8;
            // Antes era casi el color de la celda --un boton apagado, porque no
            // hacia nada--. Ahora QUITAR se pulsa, asi que tiene que parecer
            // pulsable; en gris para que no compita con COMPRAR ni con EQUIPAR,
            // que son las acciones que queremos que se vean primero.
            case EQUIPADO -> 0xFF6E7899;
            case SIN_POKEMON -> 0xFF8A8FA3;
        };
        int tinta = 0xFFFFFFFF;
        ctx.fill(px(bx), py(ay + 4), px(bx + bw), py(ay + ah - 4), relleno);
        texto(ctx, etiqueta, bx + bw / 2, ay + ah / 2 - altoBoton / 2, altoBoton,
                tinta, true, false);
    }

    /**
     * Lo que pone en el BOTON, que es lo que hace al pulsarlo.
     *
     * <p>⚠ EQUIPADO dice «QUITAR», y no es un descuido. Un boton se etiqueta con
     * la accion, no con el estado: «EQUIPADO» describia la celda y dejaba al
     * jugador sin saber que iba a pasar al pulsarlo --de hecho no pasaba nada--.
     * Que esta puesto se sigue viendo, pero en la ETIQUETA de la izquierda, que
     * es donde va el estado: ahi pone PUESTO en vez de TUYO.
     */
    private static String clave(Cosmetico.Estado est) {
        return switch (est) {
            case COMPRAR -> "comprar";
            case DE_EVENTO -> "evento";
            case EQUIPAR -> "equipar";
            case EQUIPADO -> "quitar";
            case SIN_POKEMON -> "sin_pokemon";
        };
    }

    /** La etiqueta de la izquierda: el precio si esta a la venta, el ESTADO si es tuyo. */
    private static Text etiquetaIzquierda(Cosmetico c) {
        return switch (c.estado()) {
            case COMPRAR -> Text.literal(String.valueOf(c.precio()));
            case EQUIPADO -> Text.translatable("pokepad.lunaeternal.puesto");
            default -> Text.translatable("pokepad.lunaeternal.tuyo");
        };
    }

    /** El area del boton, CALCULADA IGUAL que al dibujarlo. Ver `dibujarPie`. */
    private int botonX(Cosmetico c, int ax, int aw) {
        Cosmetico.Estado est = c.estado();
        Text precio = etiquetaIzquierda(c);
        return (est == Cosmetico.Estado.COMPRAR
                ? ax + 36 + anchoArte(precio, 18)
                : ax + 10 + anchoArte(precio, 16)) + 10;
    }

    // ---- interacción -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) {
            return super.mouseClicked(mx, my, boton);
        }
        int rx = (int) mx, ry = (int) my;

        // Navegación
        int cy = py(PANEL_Y + NAV_ALTO / 2);
        if (dentro(rx, ry, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48))) {
            sonar(true);
            volver();
            return true;
        }
        if (dentro(rx, ry, px(PANEL_X + PANEL_W - 18) - pl(80), cy - pl(32), pl(80), pl(64))) {
            sonar(true);
            close();
            return true;
        }

        // Pestañas
        int pw = (PANT_W - 2 * MARGEN) / categorias.size();
        for (int i = 0; i < categorias.size(); i++) {
            if (dentro(rx, ry, px(PANT_X + MARGEN + i * pw), py(PANT_Y + MARGEN),
                    pl(pw - 6), pl(PESTANA_ALTO))) {
                if (i != pestana) {
                    pestana = i;
                    pagina = 0;
                    sonar(true);
                }
                return true;
            }
        }

        // Flechas de pagina. Van ANTES que las celdas porque estan fuera del
        // area de la rejilla y no compiten con ella; el orden es solo para que
        // el codigo se lea igual que se dibuja.
        int total = paginas();
        if (total > 1) {
            int pcx = PANT_X + PANT_W / 2;
            if (dentro(rx, ry, px(pcx - PAG_SEP - PAG_W / 2), py(PAG_Y), pl(PAG_W), pl(PAG_H))) {
                return cambiarPagina(-1);
            }
            if (dentro(rx, ry, px(pcx + PAG_SEP - PAG_W / 2), py(PAG_Y), pl(PAG_W), pl(PAG_H))) {
                return cambiarPagina(+1);
            }
        }

        // Celdas
        List<Cosmetico> lista = visibles();
        int anchoUtil = PANT_W - 2 * MARGEN;
        int gy0 = PANT_Y + MARGEN + PESTANA_ALTO + AIRE;
        int altoUtil = PANT_Y + PANT_H - gy0 - MARGEN;
        int cw = (anchoUtil - (COLS - 1) * AIRE) / COLS;
        int ch = (altoUtil - (FILAS - 1) * AIRE) / FILAS;
        int desde = pagina * porPagina();

        for (int n = 0; n < porPagina(); n++) {
            int idx = desde + n;
            if (idx >= lista.size()) {
                break;
            }
            int ax = PANT_X + MARGEN + (n % COLS) * (cw + AIRE);
            int ay = gy0 + (n / COLS) * (ch + AIRE);
            if (!dentro(rx, ry, px(ax), py(ay), pl(cw), pl(ch))) {
                continue;
            }
            Cosmetico c = lista.get(idx);

            // ⚠ EL CLIC EN LA CELDA SOLO PREVISUALIZA. Comprar y equipar son el
            // botón del pie, y a propósito: en una tienda, un clic en cualquier
            // parte del artículo que además COBRA es cómo se gasta dinero sin
            // querer.
            enfocado = c;
            // El area del boton se calcula IGUAL que al dibujarlo, con la
            // misma funcion. Si las dos formulas se separan, el jugador pulsa
            // donde ve el boton y no pasa nada -- o peor, compra pulsando al
            // lado del precio.
            int bx = botonX(c, ax, cw);
            int bw = ax + cw - 10 - bx;
            if (bw >= 40 && dentro(rx, ry, px(bx), py(ay + ch - PIE + 4), pl(bw), pl(PIE - 8))) {
                accion(c);
            } else {
                sonar(true);
            }
            return true;
        }
        return super.mouseClicked(mx, my, boton);
    }

    /**
     * Pulsar el botón del pie.
     *
     * <p>⚠ <b>Aquí no se compra nada todavía, y es correcto que así sea.</b> La
     * compra la decide el servidor: descuenta, comprueba saldo y anota la
     * posesión, con clave de idempotencia (R4). Un cliente que se marcara el
     * cosmético como comprado estaría mintiéndose — y con D-039, donde la única
     * fuente es el servidor, sería además la única forma de saltárselo.
     *
     * <p>Falta el paquete. Hasta que exista, esto suena y no hace nada, que es
     * preferible a fingir una compra que no ha ocurrido.
     */
    private void accion(Cosmetico c) {
        Cosmetico.Estado est = c.estado();

        // ⚠ SIN_POKEMON suena a bloqueado y dice POR QUE. Es el caso que este
        //   estado existe para evitar: sin el, el boton diria EQUIPAR, el
        //   servidor lo rechazaria con razon, y el jugador --que ya ha pagado--
        //   no sabria si el fallo es suyo o nuestro.
        if (est == Cosmetico.Estado.SIN_POKEMON) {
            sonar(false);
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.translatable(
                        "pokepad.lunaeternal.necesitas_pokemon",
                        nombreDe(c)), true);
            }
            return;
        }
        // DE_EVENTO no se compra: solo sale en eventos (D-039). Suena a
        // bloqueado porque lo esta, y no hay accion que ofrecer.
        if (est == Cosmetico.Estado.DE_EVENTO) {
            sonar(false);
            return;
        }
        sonar(true);

        // ⚠ NO SE CAMBIA NADA EN PANTALLA AQUI. Se manda y se espera: el
        // servidor cobra, comprueba y responde con el catalogo entero, y
        // `leerDelServidor` lo recoge en el siguiente fotograma.
        //
        // Pintar la compra antes de que ocurra es lo que hace que un fallo de
        // saldo se vea como un cosmetico comprado que desaparece al reabrir.
        //
        // ⚠ AL EQUIPAR VIAJA LA RANURA, y se elige la PRIMERA del equipo cuya
        //   especie encaje. No hay selector, y es por lo que el propio cosmetico
        //   impone: un disfraz es de UNA especie, asi que en un equipo normal
        //   hay como mucho un Pokemon valido. Un selector de seis casillas con
        //   cinco invalidas es una pregunta cuya respuesta ya sabemos.
        //
        //   El unico caso que quedaria fuera es llevar DOS de la misma especie y
        //   querer elegir cual. Si eso llega a importar, aqui es donde se abre
        //   la tira de eleccion.
        int ranura = switch (est) {
            case COMPRAR -> Red.AccionCosmetico.COMPRAR;
            // ⚠ QUITAR TAMPOCO LLEVA RANURA. Podria mandarse la que se dibujo,
            //   pero el equipo puede haber cambiado desde entonces --basta con
            //   reordenarlo-- y se le quitaria el disfraz al Pokemon equivocado.
            //   El servidor busca cual lo lleva en el momento de quitarlo.
            case EQUIPADO -> Red.AccionCosmetico.QUITAR;
            default -> Red.AccionCosmetico.AUTOMATICA;
        };
        ClientPlayNetworking.send(new Red.AccionCosmetico(c.id(), ranura));
    }

    /**
     * Cambiar de pagina.
     *
     * <p>⚠ <b>NO da la vuelta al llegar al final</b>, y se dibuja apagada en vez
     * de esconderse: las dos cosas son la misma decision. Una flecha que salta de
     * la ultima pagina a la primera hace que el jugador no sepa cuantas hay, y
     * una que desaparece mueve a la otra de sitio.
     *
     * <p>El enfocado NO se borra al cambiar de pagina: el previsualizador sigue
     * enseñando lo ultimo que se miro, que es util al comparar dos cosmeticos que
     * han quedado en paginas distintas.
     */
    private boolean cambiarPagina(int paso) {
        int destino = pagina + paso;
        if (destino < 0 || destino >= paginas()) {
            sonar(false);
            return true;
        }
        pagina = destino;
        sonar(true);
        return true;
    }

    private void volver() {
        if (client != null) {
            client.setScreen(anterior);
        }
    }

    /** Lo bloqueado suena DISTINTO, no en silencio: sin sonido parece que no ha llegado el clic. */
    private void sonar(boolean lleva) {
        if (client != null && client.player != null) {
            client.player.playSound(lleva
                    ? SoundEvents.UI_BUTTON_CLICK.value()
                    : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 1.0f);
        }
    }

    // ---- utilidades --------------------------------------------------------


    /**
     * Escribe una linea MEDIDA EN PIXELES DEL ARTE.
     *
     * <p>⚠⚠ ESTA ES LA REGLA QUE SE ME OLVIDO Y QUE HIZO QUE LA TIENDA SE VIERA
     * ROTA EN EL JUEGO.
     *
     * La fuente de Minecraft mide 9 y se dibuja en unidades de interfaz, no en
     * pixeles del arte. Dibujando con {@code drawText} a secas, el chasis
     * encoge con {@code k} y <b>el texto no</b>: a tamaño real se ve bien, y en
     * cuanto la ventana obliga a reducir, las letras se quedan enormes, se
     * salen de su hueco y el precio se monta encima del boton -- que fue
     * exactamente lo que paso ("3.COMPRAR" donde ponia 2500).
     *
     * <p>Escalando la matriz, el texto ocupa siempre {@code alto} pixeles del
     * arte, y encaja pase lo que pase con el GUI Scale del jugador.
     *
     * <p>Contorno y no sombra: la sombra de Minecraft es una copia desplazada
     * en diagonal, que sobre estas celdas CLARAS queda gris sobre claro e
     * ilegible. En cruz y no en las ocho direcciones: cierra igual la letra y
     * pesa la mitad.
     */
    private void texto(DrawContext ctx, Text linea, int cx, int arriba, int alto,
                       int color, boolean centrado, boolean contorno) {
        float escala = alto * k / textRenderer.fontHeight;
        if (escala <= 0) {
            return;
        }
        var m = ctx.getMatrices();
        m.push();
        m.translate(x0, y0, 0);
        m.scale(escala, escala, 1f);

        int ancho = textRenderer.getWidth(linea);
        int px = Math.round(cx * k / escala) - (centrado ? ancho / 2 : 0);
        int py = Math.round(arriba * k / escala);

        if (contorno) {
            ctx.drawText(textRenderer, linea, px - 1, py, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, px + 1, py, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, px, py - 1, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, px, py + 1, TEXTO_CONTORNO, false);
        }
        ctx.drawText(textRenderer, linea, px, py, color, false);
        m.pop();
    }

    /**
     * Texto NITIDO: se dibuja a escala ENTERA, sin contorno.
     *
     * <p>⚠⚠ POR QUE HACE FALTA, HABIENDO YA UN {@code texto()}.
     *
     * <p>{@code texto()} escala por {@code alto * k / fontHeight}, y {@code k}
     * casi nunca es redondo: con {@code alto=18} sale 1,26 o 2,4 segun la
     * ventana. La fuente de Minecraft es un mapa de bits, asi que a escala
     * fraccionaria cada pixel de letra cae a caballo entre dos de pantalla y el
     * juego los promedia. <b>Y el contorno lo multiplica:</b> son cuatro copias
     * desplazadas ±1 <i>en coordenadas ya escaladas</i>, o sea ±1,26 px reales,
     * que no es un borde sino una mancha.
     *
     * <p>Se ve poco en «COMPRAR» --texto pequeño y sobre color plano-- y se ve
     * muchisimo en el contador de paginas, que es el mas grande de la pantalla.
     * El usuario lo reporto asi: «esos numeros se ven muy pixeleados».
     *
     * <p>Aqui la escala se REDONDEA a entero (minimo 1), que es donde la fuente
     * cae pixel sobre pixel. El tamaño final no es exactamente el pedido, y para
     * una etiqueta suelta da igual; para algo que tiene que caber en un hueco
     * medido, NO -- por eso esto no sustituye a {@code texto()}, convive con el.
     */
    private void textoNitido(DrawContext ctx, Text linea, int cx, int centroY,
                             int altoDeseado, int color) {
        float escala = Math.max(1f, Math.round(altoDeseado * k / textRenderer.fontHeight));
        var m = ctx.getMatrices();
        m.push();
        m.translate(x0, y0, 0);
        m.scale(escala, escala, 1f);
        int ancho = textRenderer.getWidth(linea);
        int px = Math.round(cx * k / escala) - ancho / 2;
        int py = Math.round(centroY * k / escala) - textRenderer.fontHeight / 2;
        // Sombra en vez de contorno: la dibuja el propio drawText a UN pixel de
        // pantalla, asi que no se emborrona, y sobre naranja basta para separar.
        ctx.drawText(textRenderer, linea, px, py, color, true);
        m.pop();
    }

    /** Ancho de un texto EN PIXELES DEL ARTE, para poder comprobar que cabe. */
    private int anchoArte(Text linea, int alto) {
        return Math.round(textRenderer.getWidth(linea) * alto / (float) textRenderer.fontHeight);
    }

    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;

    private static boolean dentro(int rx, int ry, int x, int y, int w, int h) {
        return rx >= x && rx < x + w && ry >= y && ry < y + h;
    }

    /** Marco de N px por dentro de la caja. */
    private static void marco(DrawContext ctx, int x, int y, int w, int h, int color, int g) {
        ctx.fill(x, y, x + w, y + g, color);
        ctx.fill(x, y + h - g, x + w, y + h, color);
        ctx.fill(x, y, x + g, y + h, color);
        ctx.fill(x + w - g, y, x + w, y + h, color);
    }

    /**
     * ⚠ REGLA 1 de `dibujado.md`: <b>la mezcla alfa hay que encenderla a mano</b>.
     *
     * <p>Sin {@code enableBlend()} el juego trata cualquier alfa mayor que cero
     * como opaco: un píxel con alfa 1 sale a todo color. Se ve como motas de
     * colores o como cerco negro alrededor de cada icono según lo que el arte
     * guarde debajo — parecen dos fallos y es uno. Y no está en el arte: se
     * perdieron tres diagnósticos buscándolo ahí.
     */
    private static void dibujarTextura(DrawContext ctx, Identifier textura,
                                       int x, int y, int w, int h, int natW, int natH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(textura, x, y, w, h, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
    }
}
