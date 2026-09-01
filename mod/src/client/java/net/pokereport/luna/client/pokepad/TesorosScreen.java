package net.pokereport.luna.client.pokepad;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.crate.Cofre;
import net.pokereport.luna.net.Red;

/**
 * TESOROS: los cofres, sus llaves y sus probabilidades.
 *
 * <p>Decisión: D-020. Diseño: {@code docs/economy/treasures.md}.
 *
 * <pre>
 *   panel izquierdo   el cofre elegido: llave, cuántas tienes, precio y --si es
 *                     el diario-- cuánto te falta de juego activo
 *   rejilla           los cuatro cofres
 *   pestaña PREMIOS   la tabla completa con su porcentaje
 *   ruleta            al abrir, encima de todo
 * </pre>
 *
 * <h2>⚠⚠⚠ LOS PORCENTAJES SALEN DE LA MISMA TABLA QUE SORTEA</h2>
 *
 * D-020 hizo obligatorias las probabilidades públicas, y eso solo significa
 * algo si el número que se enseña sale <b>del sitio del que sortea el
 * servidor</b>. {@link Cofre} vive en {@code main} y lo leen los dos lados.
 *
 * <p>Con una tabla para sortear y otra para enseñar, el sistema seguiría
 * funcionando y el número de la pantalla sería una decoración: <b>nadie lo
 * notaría jamás</b>. Es la misma decisión que las medallas.
 *
 * <h2>⚠⚠⚠ LA RULETA NO DECIDE NADA</h2>
 *
 * Cuando empieza a girar, el servidor <b>ya</b> sorteó, gastó la llave y lo
 * anotó en la base. Lo que llega es un hecho y la animación solo lo enseña.
 *
 * <p>Si la animación decidiera, el premio de la pantalla y el de la base
 * podrían no coincidir — y el jugador vería una cosa y recibiría otra.
 *
 * <h2>⚠⚠ Y NO HAY «CASI LO CONSIGUES»</h2>
 *
 * La ruleta frena y para donde salió. No se detiene al lado del premio gordo
 * para luego moverse un hueco. Eso es manipulación, está prohibido en
 * {@code treasures.md} §4.5, y no hace falta.
 *
 * <h2>⚠⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 */
public class TesorosScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 110;
    private static final int MARGEN = 12;

    private static final int TINTA = 0xFF16203A;
    private static final int TINTA_SUAVE = 0xFF5A6478;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int CONTORNO_OSCURO = 0xFF0A0E18;
    private static final int VERDE = 0xFF2E7A4E;
    private static final int GRIS = 0xFF3C4356;
    private static final int ORO = 0xFFF2C14E;

    /** El color de cada cofre, en el orden de {@link Cofre#TODOS}. */
    private static final int[] COLOR = {
        0xFF2E7A4E,   // gacha diario: verde, es el gratis
        0xFF8C5A2E,   // gachapon: madera
        0xFF6E3A9E,   // legendario: morado
        0xFFC79A2E,   // legendario shiny: oro
    };

    /**
     * LA ILUSTRACIÓN DE CADA COFRE.
     *
     * <h2>⚠⚠ ANTES ERAN OBJETOS DEL JUEGO, Y SE VEÍAN CUADRICULADOS</h2>
     *
     * Un objeto de Minecraft es una textura de <b>16×16</b>, y aquí se dibujaba
     * a 56 y a 72 píxeles del chasis — o sea ampliada tres y cuatro veces y
     * media. Eso no es un fallo de dibujado: es lo que pasa al ampliar un
     * sprite pequeño, y se ve exactamente como lo que es.
     *
     * <p>Es el mismo camino que ya recorrieron los iconos de Viajes: formas
     * dibujadas a mano, después objetos del juego, y hoy arte propio.
     *
     * <p>⚠ El color de {@link #COLOR} <b>se queda</b> aunque ya no pinte el
     * fondo: es el borde al pasar por encima, y es el tono que se le pidió al
     * arte, así que los cuatro pegan por construcción y no porque alguien los
     * emparejara a ojo. Igual que en Viajes.
     *
     * <p>⚠⚠ Y los cuatro son de NOCHE con la misma luna que las siete de
     * Viajes, a propósito: se ven en el mismo PokePad y tienen que leerse como
     * una familia, no como cuatro dibujos sueltos.
     */
    private static final int ARTE_W = 1024, ARTE_H = 512;

    private static final java.util.Map<String, Identifier> ARTE =
            new java.util.HashMap<>();

    static {
        for (var c : Cofre.TODOS) {
            ARTE.put(c.id(), Identifier.of("lunaeternal",
                    "textures/gui/tesoros/" + c.id() + ".png"));
        }
    }

    private static final int COLS = 2;

    // ---- la ruleta ---------------------------------------------------------

    /** Cuánto dura el giro. */
    private static final long GIRO_MS = 2600;

    /** Cuántas fichas caben a la vista en la tira. */
    private static final int VISIBLES = 5;

    /** El alto de cada ficha de la tira, en píxeles del chasis. */
    private static final int FICHA = 84;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private int elegido = 0;
    private boolean verPremios = false;
    private long pulsado = 0;

    /** Lo que se está enseñando, o {@code null}. */
    private Red.ResultadoCofre resultado;
    private long giroDesde = 0;

    public TesorosScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.tesoros"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirTesoros());
    }

    /**
     * Llega el resultado: empieza el giro.
     *
     * <p>⚠⚠ Lo llama {@code LunaCliente} al recibir el paquete, y no se lee del
     * estado en {@code render}: si se leyera ahí, un resultado igual al anterior
     * —abrir dos veces y que salga lo mismo, que pasa— <b>no se distinguiría</b>
     * y la segunda ruleta no arrancaría.
     */
    public void alLlegarResultado(Red.ResultadoCofre r) {
        this.resultado = r;
        this.giroDesde = System.currentTimeMillis();
        this.pulsado = 0;
        if (client != null) {
            client.getSoundManager().play(
                net.minecraft.client.sound.PositionedSoundInstance.master(
                    SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.0f));
        }
    }

    private boolean girando() {
        return resultado != null
                && System.currentTimeMillis() - giroDesde < GIRO_MS;
    }

    private boolean enRuleta() {
        return resultado != null;
    }

    // ---- geometría ---------------------------------------------------------

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

    private boolean esperando() {
        // ⚠ CON SALIDA a los 5 s. Sin ella, un paquete perdido deja el boton
        //   muerto y hay que reabrir la pantalla -- la leccion del inicial.
        return pulsado > 0 && System.currentTimeMillis() - pulsado < 5000;
    }

    // ---- datos -------------------------------------------------------------

    private Red.EstadoTesoros estado() {
        return EstadoCliente.tesoros();
    }

    private Cofre.Cofre_ cofre() {
        var todos = Cofre.TODOS;
        return todos.get(Math.max(0, Math.min(elegido, todos.size() - 1)));
    }

    private int llavesDe(int i) {
        var e = estado();
        return e == null || i >= e.llaves().size() ? 0 : e.llaves().get(i);
    }

    private int piedadDe(int i) {
        var e = estado();
        return e == null || i >= e.piedad().size() ? 0 : e.piedad().get(i);
    }

    /**
     * La pila que representa un premio.
     *
     * <p>⚠ Un Pokémon se dibuja con su Poké Ball y su NOMBRE, no con su modelo
     * 3D. En una tira que se mueve, dibujar veinte modelos por fotograma es
     * caro; y el nombre es lo que de verdad se lee cuando algo pasa deprisa.
     */
    private ItemStack pilaDe(Cofre.Premio p) {
        if (p.tipo() == Cofre.Tipo.POKEMON) {
            var ball = Registries.ITEM.get(Identifier.of(
                    p.shiny() ? "cobblemon:master_ball" : "cobblemon:ultra_ball"));
            return new ItemStack(ball == Items.AIR ? Items.SLIME_BALL : ball);
        }
        if (p.tipo() == Cofre.Tipo.PLATA) {
            return new ItemStack(Items.GOLD_NUGGET, Math.min(64, p.cantidad()));
        }
        var item = Registries.ITEM.get(Identifier.of(p.id()));
        return new ItemStack(item == Items.AIR ? Items.PAPER : item,
                Math.max(1, Math.min(64, p.cantidad())));
    }

    /** Cómo se llama un premio, en el idioma del jugador. */
    private Text nombreDe(Cofre.Premio p) {
        if (p.tipo() == Cofre.Tipo.PLATA) {
            return Text.literal(p.cantidad() + " de Plata");
        }
        if (p.tipo() == Cofre.Tipo.POKEMON) {
            // ⚠ La clave de especie la resuelve el CLIENTE: un servidor no tiene
            //   idioma. Es la regla del 25-ago.
            var nombre = Text.translatable("cobblemon.species." + p.id() + ".name");
            return p.shiny() ? Text.literal("✦ ").append(nombre) : nombre;
        }
        var pila = pilaDe(p);
        return p.cantidad() > 1
                ? Text.literal(p.cantidad() + "x ").append(pila.getName())
                : pila.getName();
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanel(ctx, rx, ry);

        if (verPremios) {
            dibujarPremios(ctx, rx, ry);
        } else {
            dibujarRejilla(ctx, rx, ry);
        }
        // ⚠ La ruleta va la ULTIMA: se dibuja encima de todo, y mientras esté
        //   nada de abajo responde al ratón (ver mouseClicked).
        if (enRuleta()) {
            dibujarRuleta(ctx);
        }
        // ⚠⚠⚠ NO SE LLAMA A `super.render` AQUI, Y ESTO COSTO UNA PANTALLA
        //    ENTERA BORROSA (2026-09-01).
        //
        //    `Screen.render` PINTA EL FONDO, y desde 1.20.5 ese fondo es un
        //    DESENFOQUE del juego (`menuBackgroundBlurriness`). Llamarlo al
        //    FINAL se lo aplica ENCIMA de todo lo ya dibujado: el chasis, los
        //    cofres y el texto salen emborronados.
        //
        //    ⚠ Y no da ningun error: se ve "como con blur", que es justo como
        //      lo describio el usuario. Se diagnostico COMPARANDO: las otras
        //      pantallas del Pad o no lo llaman, o lo llaman AL PRINCIPIO
        //      (CosmeticosScreen). Esta era la unica que lo llamaba al final.
        //
        //    ⚠ Aqui no hace falta para nada: esta pantalla no usa widgets de
        //      vanilla, dibuja todo a mano.
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24),
                pl(60), pl(48), 120, 96);
        texto(ctx, Text.translatable("pokepad.lunaeternal.app.tesoros"),
                PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false, 0);
        int cx = PANEL_X + PANEL_W - 18 - 80;
        dibujarTextura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
    }

    /** El panel de la izquierda: el cofre elegido y su botón. */
    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        var c = cofre();
        int y = PANEL_Y + NAV_ALTO + 16;

        // ⚠ El arte, de banda: el hueco del panel es APAISADO (255x110) y el
        //   PNG también, así que apenas se recorta. Se dibuja dentro del marco
        //   con 3 px de holgura para que el borde no quede pisado.
        int bandaW = PANEL_W - 60, bandaH = 110;
        ctx.fill(px(PANEL_X + 30), py(y), px(PANEL_X + 30 + bandaW),
                py(y + bandaH), 0x33000000);
        arte(ctx, c.id(), px(PANEL_X + 33), py(y + 3),
                pl(bandaW - 6), pl(bandaH - 6), bandaW - 6, bandaH - 6);
        marco(ctx, px(PANEL_X + 30), py(y), pl(bandaW), pl(bandaH),
                COLOR[Math.min(elegido, COLOR.length - 1)], Math.max(1, pl(2)));
        y += bandaH + 16;

        texto(ctx, Text.translatable("tesoros.lunaeternal.cofre." + c.id()),
                PANEL_X + PANEL_W / 2, y, 22, 0xFFFFFFFF, true, CONTORNO_OSCURO);
        y += 32;

        // cuántas llaves tienes
        texto(ctx, Text.translatable("tesoros.lunaeternal.tus_llaves",
                        llavesDe(elegido)),
                PANEL_X + PANEL_W / 2, y, 18, ORO, true, CONTORNO_OSCURO);
        y += 30;

        var e = estado();
        if (c.llave() == Cofre.Llave.JUEGO) {
            // ⚠⚠ EL DIARIO ENSEÑA CUÁNTO FALTA, y eso es la mitad del diseño:
            //    «juega una hora» sin contador es una promesa; con contador es
            //    una barra de progreso, y la gente vuelve a mirarla.
            int seg = e == null ? 0 : e.segundosHoy();
            boolean listo = seg >= net.pokereport.luna.crate.Actividad.SEGUNDOS_LLAVE;
            boolean ya = e != null && e.reclamadaHoy();
            if (ya) {
                texto(ctx, Text.translatable("tesoros.lunaeternal.ya_reclamada"),
                        PANEL_X + PANEL_W / 2, y, 15, TINTA_SUAVE, true, 0);
            } else if (listo) {
                texto(ctx, Text.translatable("tesoros.lunaeternal.llave_lista"),
                        PANEL_X + PANEL_W / 2, y, 15, 0xFF5CD68A, true, 0);
            } else {
                int falta = net.pokereport.luna.crate.Actividad.SEGUNDOS_LLAVE - seg;
                for (String l : partir(Text.translatable(
                        "tesoros.lunaeternal.falta_juego").getString(),
                        PANEL_W - 70, 13)) {
                    texto(ctx, Text.literal(l), PANEL_X + PANEL_W / 2, y, 13,
                            TINTA_SUAVE, true, 0);
                    y += 17;
                }
                texto(ctx, Text.literal((falta / 60) + "m " + (falta % 60) + "s"),
                        PANEL_X + PANEL_W / 2, y, 20, ORO, true, CONTORNO_OSCURO);
            }
        } else {
            texto(ctx, Text.translatable("tesoros.lunaeternal.precio", c.precio()),
                    PANEL_X + PANEL_W / 2, y, 16, ORO, true, CONTORNO_OSCURO);
            y += 26;
            // ⚠⚠ LA PIEDAD SE ENSEÑA, y no por transparencia decorativa: es lo
            //    que ACOTA el gasto máximo, y un jugador que no la ve no puede
            //    tomarla en cuenta. Es la condición de D-020 hecha pantalla.
            if (c.tieneMayor() && c.piedad() > 0) {
                int faltan = Math.max(0, c.piedad() - piedadDe(elegido));
                texto(ctx, Text.translatable("tesoros.lunaeternal.piedad", faltan),
                        PANEL_X + PANEL_W / 2, y, 13, TINTA_SUAVE, true, 0);
            }
        }

        // ---- los dos botones -----------------------------------------------
        int by = PANEL_Y + PANEL_H - 132;
        boolean puedeAbrir = llavesDe(elegido) > 0 && !esperando() && !enRuleta();
        boton(ctx, rx, ry, PANEL_X + 30, by, PANEL_W - 60, 46,
                Text.translatable("tesoros.lunaeternal.abrir"), puedeAbrir, VERDE);

        by += 56;
        if (c.llave() == Cofre.Llave.JUEGO) {
            boolean puede = e != null && !e.reclamadaHoy() && !enRuleta()
                    && e.segundosHoy() >= net.pokereport.luna.crate.Actividad.SEGUNDOS_LLAVE;
            boton(ctx, rx, ry, PANEL_X + 30, by, PANEL_W - 60, 46,
                    Text.translatable("tesoros.lunaeternal.reclamar"), puede, ORO);
        } else {
            long saldo = e == null ? 0 : e.saldo();
            boolean puede = saldo >= c.precio() && !esperando() && !enRuleta();
            boton(ctx, rx, ry, PANEL_X + 30, by, PANEL_W - 60, 46,
                    Text.translatable("tesoros.lunaeternal.comprar"), puede, ORO);
        }
    }

    /** Los cuatro cofres. */
    private void dibujarRejilla(DrawContext ctx, int rx, int ry) {
        texto(ctx, Text.translatable("tesoros.lunaeternal.titulo"),
                PANT_X + MARGEN, PANT_Y + MARGEN + 6, 22, TINTA, false, 0);
        textoDer(ctx, Text.translatable("tesoros.lunaeternal.ver_premios"),
                PANT_X + PANT_W - MARGEN, PANT_Y + MARGEN + 10, 14, 0xFFD98A2B);

        int w = celdaW(), h = celdaH();
        var todos = Cofre.TODOS;
        for (int i = 0; i < todos.size(); i++) {
            var c = todos.get(i);
            int cx = celdaX(i), cy = celdaY(i);
            boolean sel = i == elegido;
            boolean enc = dentro(rx, ry, px(cx), py(cy), pl(w), pl(h));

            // ⚠⚠ EL ARTE LLENA LA FICHA, RECORTADO Y NO ESTIRADO. La ficha es
            //    383x207 y el PNG 1024x512: casi la misma proporción, así que
            //    el recorte es de unos pocos píxeles por los lados. Se CALCULA
            //    de w y h -- escrito a mano dejaría de cuadrar el día que la
            //    rejilla cambie de tamaño, y nadie lo notaría.
            arte(ctx, c.id(), px(cx), py(cy), pl(w), pl(h), w, h);
            if (enc) {
                ctx.fill(px(cx), py(cy), px(cx + w), py(cy + h), 0x22FFFFFF);
            }
            // La banda del nombre: sobre una ilustración el texto se pierde.
            ctx.fill(px(cx), py(cy + h - 40), px(cx + w), py(cy + h), 0x99000000);
            marco(ctx, px(cx), py(cy), pl(w), pl(h),
                    sel ? BORDE_ENCIMA : aclarar(COLOR[Math.min(i, COLOR.length - 1)]),
                    Math.max(2, pl(sel ? 4 : 2)));

            texto(ctx, Text.translatable("tesoros.lunaeternal.cofre." + c.id()),
                    cx + w / 2, cy + h - 34, 16, 0xFFFFFFFF, true, CONTORNO_OSCURO);

            // Cuántas llaves tienes, arriba a la derecha de la ficha.
            int n = llavesDe(i);
            textoDer(ctx, Text.literal("x" + n), cx + w - 10, cy + 8, 16,
                    n > 0 ? ORO : 0xFFB8C0D0);
        }
    }

    /**
     * LA TABLA DE PREMIOS con su porcentaje.
     *
     * <p>⚠⚠ Este es el requisito de D-020 hecho pantalla, así que el porcentaje
     * se <b>calcula</b> de los pesos de {@link Cofre} y no se escribe: ver el
     * javadoc de la clase.
     */
    private void dibujarPremios(DrawContext ctx, int rx, int ry) {
        var c = cofre();
        texto(ctx, Text.translatable("tesoros.lunaeternal.cofre." + c.id()),
                PANT_X + MARGEN, PANT_Y + MARGEN + 6, 22, TINTA, false, 0);
        textoDer(ctx, Text.translatable("tesoros.lunaeternal.volver_cofres"),
                PANT_X + PANT_W - MARGEN, PANT_Y + MARGEN + 10, 14, 0xFFD98A2B);

        var lista = c.premios();
        int cols = 5;
        int w = (PANT_W - 2 * MARGEN - (cols - 1) * 8) / cols;
        int filas = Math.max(1, (lista.size() + cols - 1) / cols);
        int h = Math.min(96, (PANT_H - MARGEN - 46 - MARGEN - (filas - 1) * 8) / filas);

        for (int i = 0; i < lista.size(); i++) {
            var p = lista.get(i);
            int cx = PANT_X + MARGEN + (i % cols) * (w + 8);
            int cy = PANT_Y + MARGEN + 46 + (i / cols) * (h + 8);
            ctx.fill(px(cx), py(cy), px(cx + w), py(cy + h),
                    p.mayor() ? 0x33F2C14E : 0x1A000000);
            if (p.mayor()) {
                marco(ctx, px(cx), py(cy), pl(w), pl(h), ORO, Math.max(1, pl(2)));
            }
            icono(ctx, pilaDe(p), px(cx + w / 2), py(cy + 26), pl(32));
            // El porcentaje, con dos decimales: por debajo del 0,01 % ya no
            // dice nada y por encima de dos sobra precisión.
            double pct = c.probabilidad(p) * 100.0;
            texto(ctx, Text.literal(String.format(java.util.Locale.ROOT,
                            "%.2f%%", pct)),
                    cx + w / 2, cy + h - 34, 13,
                    p.mayor() ? ORO : 0xFF5CD68A, true, CONTORNO_OSCURO);
            var nom = nombreDe(p);
            var lineas = partir(nom.getString(), w - 8, 11);
            texto(ctx, Text.literal(lineas.get(0)), cx + w / 2, cy + h - 18, 11,
                    TINTA, true, 0);
        }
    }

    /**
     * LA RULETA VERTICAL.
     *
     * <h2>Cómo cae exactamente donde tiene que caer</h2>
     *
     * La tira es la lista de premios repetida. Se elige un desplazamiento final
     * que deja el premio ganador <b>en el centro</b> tras varias vueltas, y se
     * interpola de 0 a ese desplazamiento con una curva que frena
     * ({@code 1-(1-t)³}).
     *
     * <p>⚠⚠ El desplazamiento final se calcula <b>del índice que mandó el
     * servidor</b>, no de dónde acabe la animación. Si se leyera al revés
     * —dejar que pare donde caiga y decir que eso es el premio— la pantalla y
     * la base dirían cosas distintas.
     *
     * <p>⚠ Y no hay «casi lo consigues»: la curva es monótona y frena hasta
     * parar. No se pasa de largo para volver.
     */
    private void dibujarRuleta(DrawContext ctx) {
        var c = Cofre.de(resultado.cofre());
        if (c == null || c.premios().isEmpty()) {
            resultado = null;
            return;
        }
        var lista = c.premios();
        int n = lista.size();

        // el velo: apaga lo de debajo y deja claro que ahora manda esto
        ctx.fill(px(PANT_X), py(PANT_Y), px(PANT_X + PANT_W), py(PANT_Y + PANT_H),
                0xE6101828);

        long t = System.currentTimeMillis() - giroDesde;
        double avance = Math.min(1.0, (double) t / GIRO_MS);
        double suave = 1.0 - Math.pow(1.0 - avance, 3);   // frena al final

        // ⚠ Cuatro vueltas enteras más lo que haga falta para dejar al ganador
        //   en el centro. Menos vueltas y no se lee como una ruleta; más y se
        //   hace larga -- 2,6 s es lo que dura.
        double destino = (4.0 * n + resultado.indice()) * FICHA;
        double offset = destino * suave;

        int centroY = PANT_Y + PANT_H / 2;
        int tiraX = PANT_X + PANT_W / 2 - 180;
        int tiraW = 360;

        // la ventana
        ctx.fill(px(tiraX - 6), py(centroY - VISIBLES * FICHA / 2 - 6),
                px(tiraX + tiraW + 6), py(centroY + VISIBLES * FICHA / 2 + 6),
                0xFF0A0E18);

        for (int i = -VISIBLES; i <= VISIBLES; i++) {
            int hueco = (int) Math.floor(offset / FICHA) + i;
            int idx = ((hueco % n) + n) % n;
            var p = lista.get(idx);
            int dy = (int) (i * FICHA - (offset % FICHA));
            int cy = centroY + dy - FICHA / 2;
            if (cy + FICHA < centroY - VISIBLES * FICHA / 2
                    || cy > centroY + VISIBLES * FICHA / 2) {
                continue;
            }
            boolean centrado = Math.abs(dy) < FICHA / 2;
            ctx.fill(px(tiraX), py(cy), px(tiraX + tiraW), py(cy + FICHA - 4),
                    p.mayor() ? 0x66F2C14E : (centrado ? 0x44FFFFFF : 0x22FFFFFF));
            icono(ctx, pilaDe(p), px(tiraX + 44), py(cy + FICHA / 2 - 2), pl(40));
            texto(ctx, nombreDe(p), tiraX + 80, cy + FICHA / 2 - 12, 17,
                    centrado ? 0xFFFFFFFF : 0xFFB8C0D0, false, CONTORNO_OSCURO);
        }

        // la marca del centro
        marco(ctx, px(tiraX - 6), py(centroY - FICHA / 2 - 2),
                pl(tiraW + 12), pl(FICHA), BORDE_ENCIMA, Math.max(2, pl(3)));

        if (!girando()) {
            var p = lista.get(resultado.indice());
            texto(ctx, Text.translatable("tesoros.lunaeternal.te_toco"),
                    PANT_X + PANT_W / 2, PANT_Y + 24, 20, ORO, true, CONTORNO_OSCURO);
            texto(ctx, nombreDe(p), PANT_X + PANT_W / 2,
                    centroY + VISIBLES * FICHA / 2 + 20, 22,
                    0xFFFFFFFF, true, CONTORNO_OSCURO);
            if (resultado.porPiedad()) {
                // ⚠ Se DICE que salió por piedad. Callarlo haría creer que fue
                //   suerte, y la piedad solo sirve si el jugador sabe que está.
                texto(ctx, Text.translatable("tesoros.lunaeternal.por_piedad"),
                        PANT_X + PANT_W / 2, centroY + VISIBLES * FICHA / 2 + 48,
                        14, 0xFF5CD68A, true, 0);
            }
            texto(ctx, Text.translatable("tesoros.lunaeternal.pulsa_seguir"),
                    PANT_X + PANT_W / 2, PANT_Y + PANT_H - 30, 14,
                    0xFFB8C0D0, true, 0);
        }
    }

    // ---- la rejilla --------------------------------------------------------

    private int celdaW() {
        return (PANT_W - 2 * MARGEN - (COLS - 1) * 10) / COLS;
    }

    private int celdaH() {
        return (PANT_H - MARGEN - 46 - MARGEN - 10) / 2;
    }

    private int celdaX(int i) {
        return PANT_X + MARGEN + (i % COLS) * (celdaW() + 10);
    }

    private int celdaY(int i) {
        return PANT_Y + MARGEN + 46 + (i / COLS) * (celdaH() + 10);
    }

    // ---- interacción -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        int rx = (int) mx, ry = (int) my;

        // ⚠⚠ MIENTRAS HAY RULETA, NADA DE ABAJO RESPONDE. Sin esto se puede
        //    pulsar «abrir» otra vez por debajo de la animación, y aunque el
        //    servidor lo corte, el jugador ve una ruleta que se reinicia sola.
        if (enRuleta()) {
            if (!girando()) {
                resultado = null;
                sonar(true);
            }
            return true;
        }

        int cy = PANEL_Y + NAV_ALTO / 2;
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            sonar(true);
            if (client != null) {
                client.setScreen(anterior);
            }
            return true;
        }
        int cerrarX = PANEL_X + PANEL_W - 18 - 80;
        if (dentro(rx, ry, px(cerrarX), py(cy) - pl(32), pl(80), pl(64))) {
            sonar(true);
            close();
            return true;
        }

        // la cabecera de la derecha alterna entre cofres y premios
        if (dentro(rx, ry, px(PANT_X + PANT_W - 220), py(PANT_Y + MARGEN + 4),
                pl(210), pl(28))) {
            verPremios = !verPremios;
            sonar(true);
            return true;
        }

        if (!verPremios) {
            int w = celdaW(), h = celdaH();
            for (int i = 0; i < Cofre.TODOS.size(); i++) {
                if (dentro(rx, ry, px(celdaX(i)), py(celdaY(i)), pl(w), pl(h))) {
                    elegido = i;
                    sonar(true);
                    return true;
                }
            }
        }

        // los dos botones
        var c = cofre();
        int by = PANEL_Y + PANEL_H - 132;
        if (dentro(rx, ry, px(PANEL_X + 30), py(by), pl(PANEL_W - 60), pl(46))) {
            boolean puede = llavesDe(elegido) > 0 && !esperando();
            sonar(puede);
            if (puede) {
                pulsado = System.currentTimeMillis();
                ClientPlayNetworking.send(
                        new Red.AccionTesoro("abrir", c.id(), 1));
            }
            return true;
        }
        by += 56;
        if (dentro(rx, ry, px(PANEL_X + 30), py(by), pl(PANEL_W - 60), pl(46))) {
            var e = estado();
            if (c.llave() == Cofre.Llave.JUEGO) {
                boolean puede = e != null && !e.reclamadaHoy()
                        && e.segundosHoy()
                           >= net.pokereport.luna.crate.Actividad.SEGUNDOS_LLAVE;
                sonar(puede);
                if (puede) {
                    ClientPlayNetworking.send(
                            new Red.AccionTesoro("diaria", c.id(), 1));
                }
            } else {
                boolean puede = e != null && e.saldo() >= c.precio() && !esperando();
                sonar(puede);
                if (puede) {
                    pulsado = System.currentTimeMillis();
                    ClientPlayNetworking.send(
                            new Red.AccionTesoro("comprar", c.id(), 1));
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, boton);
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        if (tecla == 256) {
            // ⚠ Escape cierra la ruleta si ya paró, y si no, no hace nada: una
            //   animación de 2,6 s que se puede saltar a medias deja al jugador
            //   sin ver lo que le tocó.
            if (enRuleta()) {
                if (!girando()) {
                    resultado = null;
                }
                return true;
            }
            if (client != null) {
                client.setScreen(anterior);
            }
            return true;
        }
        return super.keyPressed(tecla, escaneo, mods);
    }

    private void sonar(boolean si) {
        if (client == null) {
            return;
        }
        client.getSoundManager().play(
            net.minecraft.client.sound.PositionedSoundInstance.master(
                si ? net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value()
                   : net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                si ? 1.0f : 0.7f));
    }

    // ---- dibujo de bajo nivel ---------------------------------------------

    private void boton(DrawContext ctx, int rx, int ry, int bx, int by,
                       int bw, int bh, Text etiqueta, boolean activo, int color) {
        boolean enc = activo && dentro(rx, ry, px(bx), py(by), pl(bw), pl(bh));
        ctx.fill(px(bx), py(by), px(bx + bw), py(by + bh),
                activo ? (enc ? aclarar(color) : color) : GRIS);
        marco(ctx, px(bx), py(by), pl(bw), pl(bh),
                activo ? 0x66FFFFFF : 0x33FFFFFF, Math.max(1, pl(2)));
        texto(ctx, etiqueta, bx + bw / 2, by + bh / 2 - 9, 18,
                activo ? 0xFFFFFFFF : 0xFF8892AC, true, CONTORNO_OSCURO);
    }

    private static int aclarar(int c) {
        int a = (c >>> 24) & 0xFF, r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (a << 24) | (Math.min(255, r + 28) << 16)
                | (Math.min(255, g + 28) << 8) | Math.min(255, b + 28);
    }

    /**
     * EL ARTE DE UN COFRE, recortado para llenar un hueco sin deformarse.
     *
     * <p>Se coge <b>el trozo centrado</b> del PNG con la misma proporción que
     * el hueco. Estirar sería una línea menos y se notaría: la luna saldría
     * ovalada, que es justo lo que ya pasó en Viajes.
     *
     * @param destW,destH el hueco en píxeles del CHASIS, que es de donde sale
     *                    la proporción. Los otros dos son ya de pantalla
     */
    private void arte(DrawContext ctx, String cofre, int x, int y, int w, int h,
                      int destW, int destH) {
        Identifier tex = ARTE.get(cofre);
        if (tex == null) {
            return;
        }
        int regW = ARTE_W, regH = ARTE_H;
        // ⚠ Se compara en cruz para no dividir: `destW/destH > ARTE_W/ARTE_H`
        //   con enteros truncaría y daría el recorte equivocado en los casos
        //   ajustados, que son justo estos --1,85 contra 2,00--.
        if (destW * ARTE_H > destH * ARTE_W) {
            regH = Math.max(1, ARTE_W * destH / destW);
        } else {
            regW = Math.max(1, ARTE_H * destW / destH);
        }
        int u = (ARTE_W - regW) / 2, v = (ARTE_H - regH) / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, u, v, regW, regH, ARTE_W, ARTE_H);
        RenderSystem.disableBlend();
    }

    /**
     * Un objeto, escalado.
     *
     * <p>⚠⚠ Se escala con la MATRIZ: {@code drawItem} dibuja siempre a 16×16.
     * Y se devuelve con {@code pop} pase lo que pase: dejar la matriz escalada
     * se lleva por delante todo lo que se dibuje después.
     */
    private void icono(DrawContext ctx, ItemStack pila, int cx, int cy, int lado) {
        if (pila == null || pila.isEmpty()) {
            return;
        }
        float escala = lado / 16f;
        MatrixStack m = ctx.getMatrices();
        m.push();
        try {
            m.translate(cx - lado / 2f, cy - lado / 2f, 0);
            m.scale(escala, escala, 1f);
            ctx.drawItem(pila, 0, 0);
        } finally {
            m.pop();
        }
    }

    private List<String> partir(String texto, int anchoMax, int alto) {
        var salida = new java.util.ArrayList<String>();
        var actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (anchoArte(prueba, alto) > anchoMax && !actual.isEmpty()) {
                salida.add(actual.toString());
                actual = new StringBuilder(palabra);
            } else {
                actual = new StringBuilder(prueba);
            }
        }
        if (!actual.isEmpty() || salida.isEmpty()) {
            salida.add(actual.toString());
        }
        return salida;
    }

    private int anchoArte(String s, int alto) {
        return Math.round(textRenderer.getWidth(s)
                * (alto / (float) textRenderer.fontHeight));
    }

    private void textoDer(DrawContext ctx, Text linea, int derecha, int arriba,
                          int alto, int color) {
        int w = anchoArte(linea.getString(), alto);
        texto(ctx, linea, derecha - w, arriba, alto, color, false, 0);
    }

    private void texto(DrawContext ctx, Text linea, int cx, int arriba, int alto,
                       int color, boolean centrado, int contorno) {
        float escala = alto * k / textRenderer.fontHeight;
        if (escala <= 0) {
            return;
        }
        MatrixStack m = ctx.getMatrices();
        m.push();
        try {
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
        } finally {
            m.pop();
        }
    }

    private static boolean dentro(int rx, int ry, int x, int y, int w, int h) {
        return rx >= x && rx < x + w && ry >= y && ry < y + h;
    }

    private static void marco(DrawContext ctx, int x, int y, int w, int h,
                              int color, int g) {
        ctx.fill(x, y, x + w, y + g, color);
        ctx.fill(x, y + h - g, x + w, y + h, color);
        ctx.fill(x, y, x + g, y + h, color);
        ctx.fill(x + w - g, y, x + w, y + h, color);
    }

    /** ⚠ {@code enableBlend()} a mano: regla 1 de dibujado.md. */
    private static void dibujarTextura(DrawContext ctx, Identifier tex,
                                       int x, int y, int w, int h,
                                       int natW, int natH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
    }
}
