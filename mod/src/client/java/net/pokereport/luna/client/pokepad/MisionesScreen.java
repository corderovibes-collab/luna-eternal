package net.pokereport.luna.client.pokepad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * MISIONES: el árbol, con sus ramas y sus candados.
 *
 * <h2>Por qué un árbol y no una lista</h2>
 *
 * Lo pidió el usuario comparándolo con FTB Quests: <i>«misiones progresivas y
 * libres pero que a su vez se conecten entre sí»</i>. Y esas dos cosas —libres y
 * conectadas— son justo lo que una lista no puede enseñar: una lista dice
 * <b>qué</b> hay que hacer, un árbol dice <b>a dónde lleva</b>.
 *
 * <p>Por eso se dibujan también <b>las bloqueadas</b>, con candado. Enseñar solo
 * lo disponible convertiría el árbol otra vez en una lista de una entrada.
 *
 * <h2>⚠ El reparto se CALCULA, no se escribe en el JSON</h2>
 *
 * La columna de cada misión es su <b>profundidad</b> —cuántos {@code requires}
 * hay que subir hasta la raíz— y la fila es su sitio entre las de su columna.
 * Escribir coordenadas a mano en el catálogo obligaría a recolocar media pestaña
 * cada vez que se añade una misión en medio, y nadie lo haría: acabarían
 * solapadas.
 *
 * <h2>⚠⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 *
 * Son ocho reglas y ninguna da error al compilar. Y la geometría
 * ({@code recalcular}) es <b>copia literal</b> de {@code CosmeticosScreen}: al
 * escribirla de cero en la pantalla de Trabajos salió al cuádruple por olvidar
 * dividir por el GUI Scale.
 */
public class MisionesScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier ADELANTE =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_adelante.png");
    private static final Identifier CANDADO =
            Identifier.of("lunaeternal", "textures/gui/pokepad/candado.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;

    // ---- medidas del arte, las mismas del resto del Pad --------------------
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    private static final int MARGEN = 12, PESTANA_ALTO = 52;

    /**
     * El nodo: un CUADRO CON ICONO, y el nombre debajo.
     *
     * <p>⚠⚠ ANTES EL NOMBRE IBA DENTRO, y era el problema. En 84 px caben dos
     * líneas de letra diminuta —«Un compañero» partido en dos— y el resultado son
     * cajas grises indistinguibles: hay que <i>leer</i> cada una para saber qué
     * es. Que es exactamente lo contrario de lo que un árbol tiene que conseguir.
     *
     * <p>FTB Quests lo resuelve con un icono grande por nodo, y funciona porque
     * <b>un icono se reconoce sin leerlo</b>. Aquí el icono sale del TIPO DE
     * OBJETIVO —un pico para minar, una caña para pescar, una Poké Ball para
     * capturar— así que dos misiones parecidas se parecen, y eso también informa.
     *
     * <p>El nombre va debajo, fuera del cuadro, donde hay ancho de sobra.
     */
    private static final int NODO_BASE = 72, ICONO_BASE = 40;
    private static final int SEP_X_BASE = 170, SEP_Y_BASE = 118;
    /** Lo que se reserva bajo cada nodo para su nombre: dos líneas. */
    private static final int NOMBRE_ALTO = 34;

    /**
     * ⚠⚠ EL ARBOL SE ENCOGE SOLO SI NO CABE, y hace falta de verdad.
     *
     * <p>Con el nodo a 72 y el nombre debajo, la cadena «oficios» —cuatro
     * misiones a la misma profundidad— pedía 754 px de alto en una pantalla de
     * 698. La primera versión no lo detectaba: dibujaba la cuarta fila fuera del
     * marco, y desde dentro del juego eso se ve como «faltan misiones».
     *
     * <p>Recortar el diseño hasta que quepa lo de hoy sería arreglar el caso y no
     * el problema: la cadena siguiente con cinco ramas volvería a salirse. Se
     * calcula un factor por pestaña y ya.
     *
     * <p>Con tope inferior: por debajo de 0,55 el icono deja de reconocerse, y un
     * árbol ilegible no es mejor que uno cortado. Si alguna vez se llega ahí, lo
     * que hay que hacer es partir la cadena en dos, no seguir encogiendo.
     */
    private float escalaArbol = 1f;

    private int nodo() {
        return Math.round(NODO_BASE * escalaArbol);
    }

    private int icono() {
        return Math.round(ICONO_BASE * escalaArbol);
    }

    private int sepX() {
        return Math.round(SEP_X_BASE * escalaArbol);
    }

    private int sepY() {
        return Math.round(SEP_Y_BASE * escalaArbol);
    }

    private int nombreAlto() {
        return Math.round(NOMBRE_ALTO * escalaArbol);
    }

    // ---- las flechas de pestaña, MEDIDAS sobre la banda naranja ------------
    private static final int PAG_W = 50, PAG_H = 40;
    private static final int PAG_Y = 698 + (745 - 698 - PAG_H) / 2;
    private static final int PAG_SEP = 215;

    // ---- paleta ------------------------------------------------------------
    private static final int NODO_ABIERTO = 0xFFBFCBE8;
    private static final int NODO_BLOQUEADO = 0xFF8E96AE;
    private static final int NODO_HECHO = 0xFF7FC98D;
    private static final int NODO_COBRABLE = 0xFFFFD65C;
    private static final int BORDE = 0xFF7C89B4;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    /** Azul pizarra oscuro: tiene que verse sobre el fondo claro de la pantalla. */
    private static final int LINEA = 0xFF495270;
    private static final int LINEA_HECHA = 0xFF4E9E63;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;
    private static final int SEPARADOR = 0xFF3C4250;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private List<Red.MisionEstado> todas = List.of();
    private List<String> cadenas = List.of("tutorial");
    private int pestana = 0;
    private Red.MisionEstado elegida;

    /** Dónde cae cada misión en la rejilla. Se recalcula al cambiar de pestaña. */
    private final Map<String, int[]> sitio = new HashMap<>();

    public MisionesScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.misiones"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirMisiones());
    }

    /** Copia literal de CosmeticosScreen. Ver el comentario de la clase. */
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

        boolean exacto = Math.round(ancho * gui) == NAT_ANCHO
                && Math.round(alto * gui) == NAT_ALTO;
        if (client != null) {
            for (Identifier t : new Identifier[] { CHASIS, ATRAS, CERRAR, ADELANTE, CANDADO }) {
                client.getTextureManager().getTexture(t).setFilter(!exacto, false);
            }
        }
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

    private void leerDelServidor() {
        Red.Misiones m = EstadoCliente.misiones();
        if (m == null || m.misiones() == todas) {
            return;
        }
        todas = m.misiones();

        // Las pestañas SALEN DE LOS DATOS, igual que en Cosméticos: una lista
        // escrita aquí sería una segunda verdad que se desincroniza.
        List<String> c = new ArrayList<>();
        for (var q : todas) {
            if (!c.contains(q.cadena())) {
                c.add(q.cadena());
            }
        }
        if (!c.isEmpty()) {
            cadenas = List.copyOf(c);
            if (pestana >= cadenas.size()) {
                pestana = 0;
            }
        }
        colocar();

        // Se reengancha por IDENTIFICADOR: la lista se reconstruye entera en cada
        // paquete, así que el objeto que tenía el panel ya no está en ella.
        if (elegida != null) {
            String id = elegida.id();
            elegida = todas.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
        }
        if (elegida == null) {
            elegida = visibles().stream().findFirst().orElse(null);
        }
    }

    private List<Red.MisionEstado> visibles() {
        String cadena = cadenas.get(Math.min(pestana, cadenas.size() - 1));
        return todas.stream().filter(q -> q.cadena().equals(cadena)).toList();
    }

    /**
     * Reparte las misiones de la pestaña en columnas y filas.
     *
     * <p>⚠ La columna es la <b>profundidad</b> y no el campo {@code orden}: dos
     * misiones con el mismo {@code orden} pueden colgar de padres distintos, y
     * usar el orden las pondría en la misma columna aunque una vaya mucho más
     * lejos en la cadena. La profundidad es la única que dice de verdad «cuánto
     * hay que recorrer para llegar aquí».
     *
     * <p>⚠ El recorrido lleva <b>tope de saltos</b>. El servidor no debería mandar
     * ciclos —el autotest lo comprueba— pero si algún día manda uno, esto se
     * colgaría en un bucle infinito <i>dentro del dibujado</i>, o sea que el juego
     * se quedaría congelado sin decir por qué.
     */
    private void colocar() {
        sitio.clear();
        var porId = new HashMap<String, Red.MisionEstado>();
        for (var q : todas) {
            porId.put(q.id(), q);
        }
        var lista = visibles();
        var porColumna = new HashMap<Integer, List<Red.MisionEstado>>();

        for (var q : lista) {
            int prof = 0;
            var actual = q;
            while (actual != null && !actual.requiere().isEmpty() && prof <= lista.size()) {
                actual = porId.get(actual.requiere());
                prof++;
            }
            porColumna.computeIfAbsent(prof, x -> new ArrayList<>()).add(q);
        }
        int maxCol = 0, maxFil = 0;
        for (var e : porColumna.entrySet()) {
            var col = e.getValue();
            col.sort(java.util.Comparator.comparingInt(Red.MisionEstado::orden));
            maxCol = Math.max(maxCol, e.getKey());
            maxFil = Math.max(maxFil, col.size());
            for (int i = 0; i < col.size(); i++) {
                sitio.put(col.get(i).id(), new int[] { e.getKey(), i });
            }
        }

        // Lo que hay, contra lo que pide. Ver el comentario de `escalaArbol`.
        int anchoUtil = PANT_W - 2 * MARGEN - 56;
        int altoUtil = PANT_H - MARGEN - PESTANA_ALTO - 26 - MARGEN;
        int pideAncho = maxCol * SEP_X_BASE + NODO_BASE;
        int pideAlto = Math.max(0, maxFil - 1) * SEP_Y_BASE + NODO_BASE + NOMBRE_ALTO;
        escalaArbol = (float) Math.max(0.55, Math.min(1.0,
                Math.min(anchoUtil / (double) pideAncho, altoUtil / (double) pideAlto)));
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        leerDelServidor();

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarDetalle(ctx, rx, ry);
        dibujarPestanas(ctx, rx, ry);
        dibujarArbol(ctx, rx, ry);
        dibujarFlechas(ctx, rx, ry);
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

    /**
     * El panel izquierdo: la misión que estás mirando, entera.
     *
     * <p>El árbol solo puede enseñar el nombre; todo lo demás —qué hay que hacer,
     * cuánto llevas, qué te llevas— vive aquí. Es la división que pidió el
     * usuario: <i>«a la izquierda el título o menú de cada misión»</i>.
     */
    private void dibujarDetalle(DrawContext ctx, int rx, int ry) {
        int cx = PANEL_X + PANEL_W / 2;
        if (elegida == null) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.sin_seleccion"),
                    cx, PANEL_Y + PANEL_H / 2, 22, TEXTO_SUAVE, true, false);
            return;
        }
        int y = PANEL_Y + NAV_ALTO + 24;
        for (String linea : partir(elegida.nombre(), PANEL_W - 40, 28)) {
            texto(ctx, Text.literal(linea), cx, y, 28, 0xFFFFFFFF, true, false);
            y += 32;
        }

        y += 10;
        separador(ctx, y);
        y += 16;
        for (String linea : partir(elegida.descripcion(), PANEL_W - 44, 19)) {
            texto(ctx, Text.literal(linea), cx, y, 19, 0xFFC9D2E6, true, false);
            y += 23;
        }

        // El progreso, en números. Un porcentaje no dice si falta una captura o
        // doscientas -- la misma decisión que en Trabajos.
        y += 14;
        separador(ctx, y);
        y += 16;
        texto(ctx, Text.translatable("pokepad.lunaeternal.progreso"),
                cx, y, 18, TEXTO_SUAVE, true, false);
        y += 24;
        long meta = Math.max(1, elegida.meta());
        double frac = Math.min(1.0, elegida.progreso() / (double) meta);
        int bx = PANEL_X + 28, bw = PANEL_W - 56, bh = 18;
        ctx.fill(px(bx), py(y), px(bx + bw), py(y) + pl(bh), 0xFF2B3240);
        if (frac > 0) {
            ctx.fill(px(bx), py(y), px(bx) + (int) Math.round(pl(bw) * frac), py(y) + pl(bh),
                    elegida.completada() ? NODO_HECHO : 0xFF4F7BD0);
        }
        marco(ctx, px(bx), py(y), pl(bw), pl(bh), SEPARADOR, Math.max(1, pl(2)));
        texto(ctx, Text.literal(String.format("%,d / %,d", elegida.progreso(), elegida.meta())),
                cx, y + 1, 15, 0xFFFFFFFF, true, false);

        // Recompensas
        y += 40;
        separador(ctx, y);
        y += 16;
        texto(ctx, Text.translatable("pokepad.lunaeternal.recompensa"),
                cx, y, 18, TEXTO_SUAVE, true, false);
        y += 24;
        if (elegida.plata() > 0) {
            texto(ctx, Text.literal(String.format("%,d Plata", elegida.plata())),
                    cx, y, 20, 0xFFE2E8F2, true, false);
            y += 24;
        }
        if (elegida.marcas() > 0) {
            texto(ctx, Text.literal(elegida.marcas() + " Marcas"), cx, y, 20,
                    0xFF9FD0F0, true, false);
            y += 24;
        }
        if (!elegida.via().isEmpty() && elegida.xp() > 0) {
            texto(ctx, Text.literal("+" + elegida.xp() + " XP de " + bonito(elegida.via())),
                    cx, y, 18, 0xFFB8C2DA, true, false);
        }

        dibujarBotonCobrar(ctx, rx, ry);
    }

    /** El botón de cobrar, abajo del panel. Solo aparece cuando se puede. */
    private void dibujarBotonCobrar(DrawContext ctx, int rx, int ry) {
        if (elegida == null || !elegida.cobrable()) {
            if (elegida != null && elegida.cobrada()) {
                texto(ctx, Text.translatable("pokepad.lunaeternal.ya_cobrada"),
                        PANEL_X + PANEL_W / 2, PANEL_Y + PANEL_H - 62, 20,
                        NODO_HECHO, true, false);
            }
            return;
        }
        int bx = PANEL_X + 40, by = PANEL_Y + PANEL_H - 76, bw = PANEL_W - 80, bh = 44;
        boolean encima = dentro(rx, ry, px(bx), py(by), pl(bw), pl(bh));
        ctx.fill(px(bx), py(by), px(bx + bw), py(by + bh), encima ? 0xFFFFD65C : 0xFFE8A317);
        marco(ctx, px(bx), py(by), pl(bw), pl(bh), 0xFF8A5C00, Math.max(1, pl(2)));
        texto(ctx, Text.translatable("pokepad.lunaeternal.cobrar"),
                PANEL_X + PANEL_W / 2, by + 12, 24, 0xFF2A1C00, true, false);
    }

    private void dibujarPestanas(DrawContext ctx, int rx, int ry) {
        int anchoUtil = PANT_W - 2 * MARGEN;
        int pw = anchoUtil / cadenas.size();
        for (int i = 0; i < cadenas.size(); i++) {
            int x = PANT_X + MARGEN + i * pw;
            boolean activa = i == pestana;
            boolean encima = dentro(rx, ry, px(x), py(PANT_Y + MARGEN), pl(pw - 6),
                    pl(PESTANA_ALTO));
            ctx.fill(px(x), py(PANT_Y + MARGEN), px(x + pw - 6), py(PANT_Y + MARGEN + PESTANA_ALTO),
                    activa ? 0xFFFFF0DC : (encima ? 0xFFD3DCF2 : NODO_ABIERTO));
            marco(ctx, px(x), py(PANT_Y + MARGEN), pl(pw - 6), pl(PESTANA_ALTO),
                    activa ? BORDE_ENCIMA : BORDE, Math.max(1, pl(activa ? 3 : 2)));
            // ⚠⚠ EL TEXTO SE ENCOGE HASTA CABER, y sin esto se salia. Con seis
            //   pestañas cada una mide 129 px, y «COLECCIONISTA» a cuerpo 20
            //   ocupa 160: se veian «ENTRENADO» y «COLECCIONIST» pegadas, sin
            //   que nada fallara. Un texto que no cabe no se recorta solo.
            Text nombre = Text.translatable("pokepad.lunaeternal.cadena." + cadenas.get(i));
            int alto = 20;
            while (alto > 11 && anchoArte(nombre.getString(), alto) > pw - 20) {
                alto--;
            }
            texto(ctx, nombre, x + (pw - 6) / 2,
                    PANT_Y + MARGEN + (PESTANA_ALTO - alto) / 2 - 2, alto,
                    TEXTO_OSCURO, true, false);
        }
    }

    /**
     * El árbol: primero las líneas, luego los nodos.
     *
     * <p>⚠ El orden importa. Si se dibujaran a la vez, cada línea taparía el nodo
     * anterior; con las líneas debajo, se ven salir de detrás de los cuadros, que
     * es como se lee un árbol.
     */
    private void dibujarArbol(DrawContext ctx, int rx, int ry) {
        var lista = visibles();
        if (lista.isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.cadena_vacia"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H / 2, 22, TEXTO_SUAVE, true, false);
            return;
        }
        var porId = new HashMap<String, Red.MisionEstado>();
        for (var q : lista) {
            porId.put(q.id(), q);
        }

        for (var q : lista) {
            if (q.requiere().isEmpty() || !sitio.containsKey(q.id())) {
                continue;
            }
            var padre = porId.get(q.requiere());
            if (padre == null || !sitio.containsKey(padre.id())) {
                continue;
            }
            int[] a = centro(sitio.get(padre.id()));
            int[] b = centro(sitio.get(q.id()));
            // En codo: horizontal hasta media distancia, vertical, y horizontal.
            // Una diagonal cruzaría por encima de otros nodos en cuanto la rama
            // tenga tres hijos.
            int medio = (a[0] + b[0]) / 2;
            // ⚠ Mas gruesa y mas oscura que antes: la anterior era 0xFF6F7B9E
            //   sobre un fondo azul claro, y practicamente no se veia. Un arbol
            //   sin aristas visibles es una cuadricula.
            int grosor = Math.max(2, pl(6));
            ctx.fill(px(Math.min(a[0], medio)), py(a[1]) - grosor / 2,
                    px(Math.max(a[0], medio)), py(a[1]) + grosor / 2,
                    padre.completada() ? LINEA_HECHA : LINEA);
            ctx.fill(px(medio) - grosor / 2, py(Math.min(a[1], b[1])),
                    px(medio) + grosor / 2, py(Math.max(a[1], b[1])),
                    padre.completada() ? LINEA_HECHA : LINEA);
            ctx.fill(px(Math.min(medio, b[0])), py(b[1]) - grosor / 2,
                    px(Math.max(medio, b[0])), py(b[1]) + grosor / 2,
                    padre.completada() ? LINEA_HECHA : LINEA);
        }

        for (var q : lista) {
            if (sitio.containsKey(q.id())) {
                dibujarNodo(ctx, q, sitio.get(q.id()), rx, ry);
            }
        }
    }

    /**
     * El centro de un nodo, en píxeles del arte.
     *
     * <p>⚠ El árbol va CENTRADO en el hueco que queda bajo las pestañas. Sin eso,
     * una cadena de una sola fila —«comercio»— se queda pegada arriba con 300 px
     * vacíos debajo, que es lo que se veía y lo que hacía que la pantalla
     * pareciera a medio hacer.
     */
    private int[] centro(int[] rejilla) {
        int arriba = PANT_Y + MARGEN + PESTANA_ALTO + 26;
        int altoUtil = PANT_H - MARGEN - PESTANA_ALTO - 26 - MARGEN;
        int filas = 1;
        for (int[] r : sitio.values()) {
            filas = Math.max(filas, r[1] + 1);
        }
        int usado = (filas - 1) * sepY() + nodo() + nombreAlto();
        int gy = arriba + Math.max(0, (altoUtil - usado) / 2) + rejilla[1] * sepY();
        int gx = PANT_X + MARGEN + 28 + rejilla[0] * sepX();
        return new int[] { gx + nodo() / 2, gy + nodo() / 2 };
    }

    private void dibujarNodo(DrawContext ctx, Red.MisionEstado q, int[] rejilla,
                             int rx, int ry) {
        int[] c = centro(rejilla);
        int lado = nodo();
        int ax = c[0] - lado / 2, ay = c[1] - lado / 2;
        int x = px(ax), y = py(ay), w = pl(lado), h = pl(lado);
        boolean encima = dentro(rx, ry, x, y, w, h);
        boolean puesta = elegida != null && elegida.id().equals(q.id());
        boolean resaltada = encima || puesta;

        int fondo = !q.desbloqueada() ? NODO_BLOQUEADO
                : q.cobrable() ? NODO_COBRABLE
                : q.completada() ? NODO_HECHO : NODO_ABIERTO;

        // ⚠ SOMBRA BAJO EL NODO. Sin ella los cuadros se funden con el fondo
        //   claro de la pantalla y el arbol parece una cuadricula plana. Dos
        //   pixeles bastan: es profundidad, no decoracion.
        ctx.fill(x + pl(3), y + pl(3), x + w + pl(3), y + h + pl(3), 0x40000000);
        ctx.fill(x, y, x + w, y + h, fondo);
        // Un brillo arriba y una sombra abajo, DENTRO del cuadro: es lo que hace
        // que se lea como un boton y no como un rectangulo pintado.
        ctx.fill(x, y, x + w, y + pl(3), 0x35FFFFFF);
        ctx.fill(x, y + h - pl(3), x + w, y + h, 0x30000000);
        marco(ctx, x, y, w, h, resaltada ? BORDE_ENCIMA : BORDE,
                Math.max(1, pl(resaltada ? 4 : 3)));

        if (!q.desbloqueada()) {
            int m = pl(34);
            ctx.setShaderColor(1f, 1f, 1f, 0.75f);
            dibujarTextura(ctx, CANDADO, x + (w - m) / 2, y + (h - m) / 2, m, m, 100, 100);
            ctx.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            // ⚠ EL ICONO EN LUGAR DEL NOMBRE. Ver el comentario de NODO: un icono
            //   se reconoce sin leerlo, y dos misiones del mismo tipo se parecen.
            dibujarObjeto(ctx, iconoDe(q.objetivo()),
                    ax + (lado - icono()) / 2, ay + (lado - icono()) / 2, icono());
        }

        // Marca de estado en la esquina: un tilde si esta hecha, una admiracion
        // si se puede cobrar. Se ve antes que el color de fondo.
        if (q.cobrable()) {
            texto(ctx, Text.literal("!"), ax + lado - 13, ay + 2, 24, 0xFFB03000, true, true);
        } else if (q.completada()) {
            texto(ctx, Text.literal("✔"), ax + lado - 14, ay + 4, 20,
                    0xFF1F6B36, true, true);
        }

        // ⚠ EL NOMBRE VA DEBAJO Y FUERA. Dentro solo cabia partido en dos lineas
        //   de letra diminuta; aqui hay SEP_X de ancho para el.
        var lineas = partir(limpiar(q.nombre()), sepX() - 16, 16);
        int ty = ay + lado + 6;
        for (int i = 0; i < lineas.size() && i < 2; i++) {
            texto(ctx, Text.literal(lineas.get(i)), c[0], ty, 16,
                    q.desbloqueada() ? TEXTO_OSCURO : TEXTO_SUAVE, true, true);
            ty += 18;
        }
    }

    private void dibujarFlechas(DrawContext ctx, int rx, int ry) {
        if (cadenas.size() <= 1) {
            return;
        }
        int cx = PANT_X + PANT_W / 2;
        flecha(ctx, ATRAS, cx - PAG_SEP - PAG_W / 2, rx, ry, pestana > 0);
        flecha(ctx, ADELANTE, cx + PAG_SEP - PAG_W / 2, rx, ry, pestana < cadenas.size() - 1);
        float escala = Math.max(1f, Math.round(18 * k / textRenderer.fontHeight));
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x0, y0, 0);
        m.scale(escala, escala, 1f);
        Text etiqueta = Text.literal((pestana + 1) + " / " + cadenas.size());
        ctx.drawText(textRenderer, etiqueta,
                Math.round(cx * k / escala) - textRenderer.getWidth(etiqueta) / 2,
                Math.round((PAG_Y + PAG_H / 2) * k / escala) - textRenderer.fontHeight / 2,
                0xFFFFFFFF, true);
        m.pop();
    }

    private void flecha(DrawContext ctx, Identifier tex, int ax, int rx, int ry, boolean viva) {
        if (viva && dentro(rx, ry, px(ax), py(PAG_Y), pl(PAG_W), pl(PAG_H))) {
            marco(ctx, px(ax) - 2, py(PAG_Y) - 2, pl(PAG_W) + 4, pl(PAG_H) + 4, BORDE_ENCIMA, 2);
        }
        ctx.setShaderColor(1f, 1f, 1f, viva ? 1f : 0.4f);
        dibujarTextura(ctx, tex, px(ax), py(PAG_Y), pl(PAG_W), pl(PAG_H), 120, 96);
        ctx.setShaderColor(1f, 1f, 1f, 1f);
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
            sonar(true);
            if (client != null) {
                client.setScreen(anterior);
            }
            return true;
        }
        if (dentro(rx, ry, px(PANEL_X + PANEL_W - 18) - pl(80), cy - pl(32), pl(80), pl(64))) {
            sonar(true);
            close();
            return true;
        }

        // Cobrar
        if (elegida != null && elegida.cobrable()) {
            int bx = PANEL_X + 40, by = PANEL_Y + PANEL_H - 76, bw = PANEL_W - 80, bh = 44;
            if (dentro(rx, ry, px(bx), py(by), pl(bw), pl(bh))) {
                sonar(true);
                // ⚠ NO se pinta el cobro aquí. El servidor cobra y reenvía el
                //   árbol entero; adelantarse haría que un rechazo se viera como
                //   una recompensa que desaparece al reabrir.
                ClientPlayNetworking.send(new Red.ReclamarMision(elegida.id()));
                return true;
            }
        }

        if (cadenas.size() > 1) {
            int pcx = PANT_X + PANT_W / 2;
            if (dentro(rx, ry, px(pcx - PAG_SEP - PAG_W / 2), py(PAG_Y), pl(PAG_W), pl(PAG_H))) {
                return cambiarPestana(-1);
            }
            if (dentro(rx, ry, px(pcx + PAG_SEP - PAG_W / 2), py(PAG_Y), pl(PAG_W), pl(PAG_H))) {
                return cambiarPestana(+1);
            }
        }

        int pw = (PANT_W - 2 * MARGEN) / cadenas.size();
        for (int i = 0; i < cadenas.size(); i++) {
            if (dentro(rx, ry, px(PANT_X + MARGEN + i * pw), py(PANT_Y + MARGEN),
                    pl(pw - 6), pl(PESTANA_ALTO))) {
                if (i != pestana) {
                    pestana = i;
                    colocar();
                    elegida = visibles().stream().findFirst().orElse(null);
                    sonar(true);
                }
                return true;
            }
        }

        for (var q : visibles()) {
            int[] r = sitio.get(q.id());
            if (r == null) {
                continue;
            }
            int[] c = centro(r);
            if (dentro(rx, ry, px(c[0] - nodo() / 2), py(c[1] - nodo() / 2),
                    pl(nodo()), pl(nodo()))) {
                // Una misión bloqueada TAMBIÉN se puede mirar: ver a dónde lleva
                // la cadena es la mitad de para qué existe el árbol.
                elegida = q;
                sonar(q.desbloqueada());
                return true;
            }
        }
        return super.mouseClicked(mx, my, boton);
    }

    private boolean cambiarPestana(int paso) {
        int destino = pestana + paso;
        if (destino < 0 || destino >= cadenas.size()) {
            return true;
        }
        pestana = destino;
        colocar();
        elegida = visibles().stream().findFirst().orElse(null);
        sonar(true);
        return true;
    }

    private void sonar(boolean lleva) {
        if (client != null && client.player != null) {
            client.player.playSound(lleva
                    ? SoundEvents.UI_BUTTON_CLICK.value()
                    : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 1.0f);
        }
    }

    /**
     * El objeto que representa un objetivo.
     *
     * <p>⚠ Se elige por TIPO y no por misión: dos misiones de picar tienen que
     * verse iguales. Es lo que convierte el árbol en algo que se lee de un
     * vistazo — «esta rama es de minar»— sin una sola palabra.
     */
    private static net.minecraft.item.ItemStack iconoDe(String objetivo) {
        var item = switch (objetivo) {
            case "STARTER" -> net.minecraft.registry.Registries.ITEM
                    .get(Identifier.of("cobblemon", "poke_ball"));
            case "CATCH" -> net.minecraft.registry.Registries.ITEM
                    .get(Identifier.of("cobblemon", "ultra_ball"));
            case "POKEDEX", "POKEDEX_NEW" -> net.minecraft.registry.Registries.ITEM
                    .get(Identifier.of("cobblemon", "pokedex_red"));
            case "SHOP_BUY" -> net.minecraft.item.Items.EMERALD;
            case "GTS_SELL" -> net.minecraft.item.Items.GOLD_INGOT;
            case "PATH_LEVEL" -> net.minecraft.item.Items.EXPERIENCE_BOTTLE;
            case "EARN" -> net.minecraft.item.Items.GOLD_NUGGET;
            case "MINE" -> net.minecraft.item.Items.IRON_PICKAXE;
            case "FISH" -> net.minecraft.item.Items.FISHING_ROD;
            case "HARVEST" -> net.minecraft.item.Items.WHEAT;
            case "HATCH" -> net.minecraft.item.Items.TURTLE_EGG;
            case "BATTLE_WIN" -> net.minecraft.item.Items.IRON_SWORD;
            default -> net.minecraft.item.Items.PAPER;
        };
        // ⚠ Un identificador que Cobblemon no tenga devuelve el AIRE, no null.
        //   Con aire `drawItem` no pinta nada y el nodo sale vacio -- que parece
        //   un fallo de arte y no lo es.
        return item == net.minecraft.item.Items.AIR
                ? new net.minecraft.item.ItemStack(net.minecraft.item.Items.PAPER)
                : new net.minecraft.item.ItemStack(item);
    }

    /** Un objeto a un tamaño distinto de 16: `drawItem` no escala por su cuenta. */
    private void dibujarObjeto(DrawContext ctx, net.minecraft.item.ItemStack pila,
                               int ax, int ay, int lado) {
        float escala = pl(lado) / 16f;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(px(ax), py(ay), 0);
        m.scale(escala, escala, 1f);
        ctx.drawItem(pila, 0, 0);
        m.pop();
    }

    // ---- utilidades --------------------------------------------------------

    /** Quita los códigos de color: dentro de un nodo estorban más que ayudan. */
    private static String limpiar(String s) {
        return s.replaceAll("§.", "");
    }

    private static String bonito(String s) {
        return s.isEmpty() ? s
                : s.charAt(0) + s.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private List<String> partir(String texto, int anchoArte, int altoArte) {
        List<String> salida = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : limpiar(texto).split(" ")) {
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

    /** ⚠ `enableBlend()` a mano: es la regla 1 de dibujado.md. */
    private static void dibujarTextura(DrawContext ctx, Identifier tex,
                                       int x, int y, int w, int h, int natW, int natH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
    }
}
