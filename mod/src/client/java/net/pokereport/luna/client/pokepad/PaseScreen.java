package net.pokereport.luna.client.pokepad;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;
import net.pokereport.luna.pase.PaseCatalogo;
import net.pokereport.luna.pase.PaseNivel;
import net.pokereport.luna.pase.Recompensa;

/**
 * EL PASE DE BATALLA LUNA.
 *
 * <p>Chasis compartido ({@code pokepad_cosmeticos.png}) y {@link Escalado},
 * como todas las sub-pantallas desde el 2026-08-22.
 *
 * <h2>&#9888;&#9888;&#9888; NI LA CURVA NI LOS PREMIOS VIAJAN POR LA RED</h2>
 *
 * Esta pantalla lee {@link PaseNivel} y {@link PaseCatalogo} <b>directamente</b>,
 * porque los dos viven en {@code main} y {@code main} corre en los dos lados.
 * Es la misma decision que {@code CatalogoPad}, y por el mismo motivo: mandar el
 * nivel y la lista de premios seria un SEGUNDO sitio donde vive la misma
 * verdad, y este proyecto ya sabe como acaba eso &mdash; las tres listas de
 * medallas, los dos ordenes del PokePad. Aqui no puede desincronizarse porque
 * no hay dos copias.
 *
 * <p>Lo que si viaja es el ESTADO: cuanta XP llevas, si tienes la via Luna, lo
 * cobrado y el tope de hoy. Eso solo lo sabe el servidor, y P6 sigue intacto:
 * pulsar RECLAMAR manda el nivel y la via, nunca el premio.
 *
 * <h2>Las tres animaciones, y por que cada una</h2>
 *
 * <ol>
 *   <li><b>El anillo del nivel</b> barre al abrirse (700 ms). Es lo que dice de
 *       un vistazo cuanto falta para el siguiente nivel sin leer un numero.</li>
 *   <li><b>Los premios cobrables laten</b> en oro. Un premio disponible que se
 *       dibuja igual que uno bloqueado <b>no se ve</b>: el usuario abre, no
 *       encuentra nada y cierra.</li>
 *   <li><b>El carril se llena</b> hasta el nivel alcanzado, con un brillo que
 *       recorre lo conseguido. Es el hilo que une las cincuenta columnas.</li>
 * </ol>
 *
 * <p>&#9888; Todas van con {@code System.currentTimeMillis()} y no con el
 * tiempo del mundo: la ciudadela tiene la hora CONGELADA (noche permanente), asi
 * que cualquier animacion colgada del reloj del mundo se queda clavada. Es la
 * leccion que ya pago {@code Auras}.
 *
 * <h2>&#9888;&#9888; LA REGLA DE LAS 2 PASADAS DE {@code dibujado.md}</h2>
 *
 * Primero TODO el 2D, luego {@code ctx.draw()}, luego los modelos de objeto, y
 * al final los tooltips. Mezclarlos corrompe lotes de OpenGL o esconde sombras.
 */
public class PaseScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier ICONO =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pase.png");
    private static final Identifier PLATA =
            Identifier.of("lunaeternal", "textures/gui/pokepad/plata.png");
    private static final Identifier LUNACOIN =
            Identifier.of("lunaeternal", "textures/gui/pokepad/lunacoin_oro.png");

    // ---- medidas del arte (1380 x 828) -------------------------------------
    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;

    // ---- el carril ---------------------------------------------------------
    private static final int MARGEN = 15;
    private static final int GAP = 10;
    private static final int CARD_W = 120;
    private static final int CARD_H = 160;

    /**
     * Cuantas columnas caben. <b>SE CALCULA, NO SE ESCRIBE.</b>
     *
     * <p>&#9888;&#9888;&#9888; ES LA QUINTA VEZ QUE ESTE PROYECTO TROPIEZA CON
     * UNA REJILLA QUE "CABIA POR CASUALIDAD": los quince iconos del Pad que
     * eran 5x3 justos y se rompieron con el decimosexto; los 62 cosmeticos de
     * los que 54 eran inalcanzables; las 8 paradas de Viajes; las 23 medallas
     * de la Liga. Ninguna dio un error &mdash; todas dieron cosas dibujadas
     * fuera del marco o imposibles de alcanzar.
     *
     * <p>Escrito a mano, un 7 aqui pintaria la septima tarjeta fuera de la
     * pantalla del chasis. Calculado, no puede: si alguien ensancha las
     * tarjetas, caben menos y ya esta.
     */
    private static final int COLS =
            Math.max(1, (PANT_W - 2 * MARGEN + GAP) / (CARD_W + GAP));
    private static final int TRACK_Y = 258;
    private static final int RAIL_H = 44;
    private static final int RAIL_Y = TRACK_Y + CARD_H + 8;
    private static final int LUNA_Y = RAIL_Y + RAIL_H + 8;

    // ---- colores -----------------------------------------------------------
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int BORDE_BASE = 0xFF7C89B4;
    private static final int FONDO_TARJETA = 0xFF222B3D;
    private static final int FONDO_TARJETA_ENCIMA = 0xFF2D3950;
    private static final int FONDO_HUNDIDO = 0xFF0D121B;
    private static final int BORDE_HUNDIDO = 0xFF28364D;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int TEXTO_SUAVE = 0xFF8FA0C8;
    private static final int ORO = 0xFFFFD65C;
    private static final int ORO_OSCURO = 0xFF8A6A16;
    private static final int VERDE = 0xFF2E9E56;
    private static final int VERDE_CLARO = 0xFF4FD07A;
    private static final int GRIS = 0xFF4A5468;
    /** El violeta de la via Luna. No compite con el naranja del chasis. */
    private static final int LUNA_BORDE = 0xFFB98CFF;
    private static final int LUNA_FONDO = 0xFF231B3B;
    private static final int LUNA_FONDO_ENCIMA = 0xFF2F2450;

    private final Screen anterior;
    private float k;
    private int ancho, alto, x0, y0;

    /** Primer nivel visible del carril. Entre 1 y {@code MAX - COLS + 1}. */
    private int desde = 1;

    /** Cuando se abrio, para el barrido del anillo. */
    private final long abierta = System.currentTimeMillis();

    /** Para colocar el carril en el nivel del jugador UNA sola vez. */
    private boolean colocado;

    /**
     * Cuando se pulso algo que espera respuesta del servidor.
     *
     * <p>&#9888; Con SALIDA a 1,5 s. Sin ella, un paquete perdido deja el boton
     * muerto y hay que reabrir la pantalla: es la leccion de la pantalla del
     * inicial y de la de Curar.
     */
    private long pulsado;

    /** Lo que hay bajo el raton, para el tooltip de la tercera pasada. */
    private ItemStack bajoElRaton;

    public PaseScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.pase"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirPase());
    }

    private void recalcular() {
        var m = Escalado.aplicar(client, width, height, CHASIS, ATRAS, CERRAR,
                ICONO, PLATA, LUNACOIN);
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

    private int px(int a) { return x0 + Math.round(a * k); }
    private int py(int a) { return y0 + Math.round(a * k); }
    private int pl(int a) { return Math.max(1, Math.round(a * k)); }

    private Red.EstadoPase estado() {
        return EstadoCliente.pase();
    }

    private int nivel() {
        var e = estado();
        return e == null ? 0 : PaseNivel.nivelDe(e.xp());
    }

    private boolean esperando() {
        return pulsado > 0 && System.currentTimeMillis() - pulsado < 1500;
    }

    /** El maximo desplazamiento posible. Se calcula, nunca se escribe. */
    private static int maxDesde() {
        return Math.max(1, PaseNivel.MAX - COLS + 1);
    }

    // =======================================================================
    // DIBUJADO
    // =======================================================================

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        bajoElRaton = null;

        // La primera vez que llega el estado, el carril se coloca donde esta el
        // jugador. Abrirlo siempre en el nivel 1 obliga a buscarse a uno mismo.
        if (!colocado && estado() != null) {
            colocado = true;
            desde = Math.max(1, Math.min(maxDesde(), nivel() - 1));
        }

        // ---- PASADA 1: todo el 2D ----------------------------------------
        textura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanel(ctx, rx, ry);
        dibujarCarril(ctx, rx, ry);

        // ---- PASADA 2: los modelos de objeto -----------------------------
        //
        // ⚠ `ctx.draw()` VACIA EL BUFFER antes de meter geometria 3D. Sin esto,
        //   los `fill` de arriba y los modelos de abajo se mezclan en el mismo
        //   lote y salen sombras perdidas o cuadros negros. Regla de dibujado.md.
        ctx.draw();
        dibujarObjetos(ctx, rx, ry);

        // ---- PASADA 3: el tooltip nativo ---------------------------------
        if (bajoElRaton != null && !bajoElRaton.isEmpty()) {
            ctx.drawItemTooltip(textRenderer, bajoElRaton, rx, ry);
        }
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;

        textura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 120, 96);
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2,
                    pl(60) + 4, pl(48) + 4, BORDE_ENCIMA, 2);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicio"),
                PANEL_X + 92, cy - 14, 28, 0xFFFFFFFF, false);

        int cx = PANEL_X + PANEL_W - 18 - 80;
        textura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4,
                    BORDE_ENCIMA, 2);
        }
    }

    /** El panel izquierdo: quien eres en el pase. */
    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        var e = estado();
        int cx = PANEL_X + PANEL_W / 2;

        texto(ctx, Text.literal("PASE DE BATALLA"), cx, PANEL_Y + NAV_ALTO + 6,
                24, ORO, true);
        texto(ctx, Text.literal(e == null
                        ? "Cargando..."
                        : "TEMPORADA " + e.temporada()),
                cx, PANEL_Y + NAV_ALTO + 32, 15, TEXTO_SUAVE, true);

        // ---- el anillo del nivel -----------------------------------------
        int anilloY = PANEL_Y + NAV_ALTO + 118;
        int r = 56;
        int nivel = nivel();
        double fraccion;
        if (e == null) {
            fraccion = 0;
        } else if (nivel >= PaseNivel.MAX) {
            fraccion = 1;
        } else {
            long dentroDelNivel = PaseNivel.enNivel(e.xp());
            long cuesta = PaseNivel.coste(nivel);
            fraccion = cuesta <= 0 ? 0 : Math.min(1.0, dentroDelNivel / (double) cuesta);
        }
        // El barrido de apertura: 700 ms de 0 a lo que toque, con frenada.
        long t = System.currentTimeMillis() - abierta;
        double entrada = t >= 700 ? 1.0 : 1 - Math.pow(1 - t / 700.0, 3);

        disco(ctx, px(cx), py(anilloY), pl(r), FONDO_HUNDIDO);
        aro(ctx, px(cx), py(anilloY), pl(r), pl(7), BORDE_HUNDIDO);
        arco(ctx, px(cx), py(anilloY), pl(r) - pl(1), pl(7),
                fraccion * entrada, nivel >= PaseNivel.MAX ? VERDE_CLARO : ORO);

        texto(ctx, Text.literal(String.valueOf(nivel)), cx, anilloY - 22, 42,
                0xFFFFFFFF, true);
        texto(ctx, Text.literal("NIVEL"), cx, anilloY + 20, 13, TEXTO_SUAVE, true);

        // ---- la XP --------------------------------------------------------
        int y = anilloY + r + 16;
        if (e != null && nivel < PaseNivel.MAX) {
            texto(ctx, Text.literal(String.format("%,d / %,d XP",
                            PaseNivel.enNivel(e.xp()), PaseNivel.coste(nivel))),
                    cx, y, 15, 0xFFD8DEEA, true);
        } else if (e != null) {
            texto(ctx, Text.literal("PASE COMPLETADO"), cx, y, 15, VERDE_CLARO, true);
        }
        y += 22;

        // ---- el tope del dia ---------------------------------------------
        //
        // ⚠ SE ENSEÑA SIEMPRE, no solo al llegar. Un jugador que pica menas
        //   media hora sin ver subir la barra tiene que poder saber POR QUE, y
        //   la respuesta «ya has hecho tu tope de hoy» solo sirve si se puede
        //   leer ANTES de llegar a ella.
        separador(ctx, y);
        y += 12;
        int xpHoy = e == null ? 0 : e.xpHoy();
        int tope = e == null ? PaseNivel.TOPE_DIARIO : Math.max(1, e.topeHoy());
        boolean topado = xpHoy >= tope;
        texto(ctx, Text.literal("XP DE HOY"), PANEL_X + 26, y, 14, TEXTO_SUAVE, false);
        texto(ctx, Text.literal(String.format("%,d / %,d", xpHoy, tope)),
                PANEL_X + PANEL_W - 26, y, 14,
                topado ? BORDE_ENCIMA : 0xFFD8DEEA, false, true);
        y += 18;
        barra(ctx, PANEL_X + 26, y, PANEL_W - 52, 12,
                xpHoy / (double) tope, topado ? BORDE_ENCIMA : VERDE_CLARO);
        y += 20;
        if (topado) {
            texto(ctx, Text.literal("Tope alcanzado. Vuelve mañana."),
                    cx, y, 12, BORDE_ENCIMA, true);
        } else if (tope > PaseNivel.TOPE_DIARIO) {
            // El descanso acumulado, dicho. Un numero que sube solo y sin
            // explicacion parece un fallo.
            texto(ctx, Text.literal("Descanso acumulado: tope x"
                            + (tope / PaseNivel.TOPE_DIARIO)),
                    cx, y, 12, LUNA_BORDE, true);
        } else {
            texto(ctx, Text.literal("Sube capturando, minando, pescando..."),
                    cx, y, 12, TEXTO_SUAVE, true);
        }
        y += 20;

        separador(ctx, y);

        // ---- la via Luna --------------------------------------------------
        //
        // ⚠⚠ LA Y SALE DE `yCajaLuna()` Y NO DE LA CUENTA DE ARRIBA, y no es
        //    un adorno: el CLIC usa esa misma funcion. Si el dibujado siguiera
        //    su propio acumulador, mover una linea del panel dejaria el boton
        //    pintado en un sitio y respondiendo en otro, SIN DAR NINGUN ERROR.
        //    Es la leccion de la rejilla del PokePad: el dibujado y el clic
        //    calculan la ranura IGUAL o acaban abriendo la celda de al lado.
        y = yCajaLuna();
        boolean premium = e != null && e.premium();
        int cajaH = CAJA_LUNA_H;
        ctx.fill(px(PANEL_X + 22), py(y), px(PANEL_X + PANEL_W - 22), py(y + cajaH),
                premium ? LUNA_FONDO : FONDO_HUNDIDO);
        marco(ctx, px(PANEL_X + 22), py(y), pl(PANEL_W - 44), pl(cajaH),
                premium ? LUNA_BORDE : BORDE_HUNDIDO, Math.max(1, pl(2)));
        if (premium) {
            texto(ctx, Text.literal("VIA LUNA ACTIVA"), cx, y + 12, 17,
                    LUNA_BORDE, true);
            texto(ctx, Text.literal("Temporada " + (e == null ? 1 : e.temporada())),
                    cx, y + 34, 13, TEXTO_SUAVE, true);
            texto(ctx, Text.literal("Los premios violeta son tuyos"),
                    cx, y + 52, 12, TEXTO_SUAVE, true);
        } else {
            boolean puede = e != null && e.saldoLuna() >= PaseCatalogo.PRECIO_LUNA;
            boolean sobre = dentro(rx, ry, px(PANEL_X + 26), py(y + BOTON_LUNA_DY),
                    pl(PANEL_W - 52), pl(BOTON_LUNA_H));
            texto(ctx, Text.literal("DESBLOQUEA LA VIA LUNA"), cx, y + 7, 13,
                    0xFFD8DEEA, true);
            int fondoBtn = !puede ? 0xFF2A2F3C
                    : (sobre ? LUNA_FONDO_ENCIMA : LUNA_FONDO);
            ctx.fill(px(PANEL_X + 26), py(y + BOTON_LUNA_DY),
                    px(PANEL_X + PANEL_W - 26), py(y + BOTON_LUNA_DY + BOTON_LUNA_H),
                    fondoBtn);
            marco(ctx, px(PANEL_X + 26), py(y + BOTON_LUNA_DY), pl(PANEL_W - 52),
                    pl(BOTON_LUNA_H),
                    !puede ? GRIS : (sobre ? 0xFFFFFFFF : LUNA_BORDE),
                    Math.max(1, pl(2)));
            texto(ctx, Text.literal(String.format("%,d", PaseCatalogo.PRECIO_LUNA)),
                    cx + 12, y + BOTON_LUNA_DY + 8, 17, puede ? ORO : GRIS, true);
            textura(ctx, LUNACOIN, px(cx - 42), py(y + BOTON_LUNA_DY + 7),
                    pl(18), pl(18), 40, 40);
            // ⚠ Cabe DENTRO de la caja: 62 + 11 = 73, y la caja mide 76. Un
            //   renglón que se sale del cuadro no da ningún error, se dibuja
            //   encima del chasis.
            texto(ctx, Text.literal(e == null ? "Cargando saldo..."
                            : String.format("Tienes %,d LunaCoins", e.saldoLuna())),
                    cx, y + 62, 11, puede ? TEXTO_SUAVE : GRIS, true);
        }
        y = yBotonReclamar();

        // ---- reclamar todo -------------------------------------------------
        int pendientes = pendientes();
        int btnH = BOTON_RECLAMAR_H;
        boolean hay = pendientes > 0 && !esperando();
        boolean sobreBtn = dentro(rx, ry, px(PANEL_X + 22), py(y),
                pl(PANEL_W - 44), pl(btnH));
        // El boton late cuando hay algo que cobrar. Un boton verde apagado y uno
        // encendido se distinguen; uno encendido y quieto entre nueve cajas, no.
        int fondo = !hay ? 0xFF232833
                : (sobreBtn ? VERDE_CLARO : mezclar(VERDE, VERDE_CLARO, latido(900)));
        ctx.fill(px(PANEL_X + 22), py(y), px(PANEL_X + PANEL_W - 22), py(y + btnH),
                fondo);
        marco(ctx, px(PANEL_X + 22), py(y), pl(PANEL_W - 44), pl(btnH),
                hay ? 0xFF10331E : GRIS, Math.max(1, pl(2)));
        texto(ctx, Text.literal(pendientes > 0
                        ? "RECLAMAR TODO (" + pendientes + ")"
                        : "NADA QUE RECLAMAR"),
                PANEL_X + PANEL_W / 2, y + 14, 17,
                hay ? 0xFFFFFFFF : GRIS, true);
        y += btnH + 10;

        if (e != null) {
            texto(ctx, Text.literal(e.diasRestantes() > 0
                            ? "Quedan " + e.diasRestantes() + " dias de temporada"
                            : "Temporada terminada - reclama lo tuyo"),
                    PANEL_X + PANEL_W / 2, y, 12,
                    e.diasRestantes() > 0 ? TEXTO_SUAVE : BORDE_ENCIMA, true);
        }
        // ⚠ La leyenda de abajo llega hasta y=743 y el panel acaba en 762.
        //   Los numeros se midieron: con 22 de hueco y filas de 13 se salia por
        //   el borde, y una fila fuera del marco NO da ningun error -- se
        //   dibuja encima del chasis. Es el mismo fallo que la cadena `oficios`
        //   del arbol de misiones y la sexta categoria de la tienda.
        y += 16;

        separador(ctx, y);
        y += 12;
        dibujarComoSubir(ctx, y);
    }

    /**
     * COMO SE SUBE EL PASE, con las cifras de verdad.
     *
     * <p>&#9888;&#9888; LOS NUMEROS SALEN DE {@code PaseXp}, NO ESTAN ESCRITOS
     * AQUI. Escritos a mano serian una SEGUNDA lista de valores que nada obliga
     * a coincidir con la que paga el servidor: el dia que se recalibre la pesca,
     * la pantalla seguiria prometiendo lo de antes &mdash; sin dar ningun error,
     * y con el jugador convencido de que le estamos pagando de menos.
     *
     * <p>&#9888; Y esta en la pantalla y no en un documento porque es <b>la
     * pregunta que se hace todo el mundo al abrir un pase</b>: vale, &#191;y
     * como subo? Mandarla a un wiki es mandarla a ningun sitio.
     */
    private void dibujarComoSubir(DrawContext ctx, int y) {
        texto(ctx, Text.literal("COMO SUBE EL PASE"), PANEL_X + PANEL_W / 2, y,
                13, ORO, true);
        y += 18;
        String[][] filas = {
            {"Capturar un Pokemon", "+" + net.pokereport.luna.pase.PaseXp.CAPTURA},
            {"Especie nueva en la Pokedex",
                    "+" + net.pokereport.luna.pase.PaseXp.ESPECIE_NUEVA},
            {"Eclosionar un huevo", "+" + net.pokereport.luna.pase.PaseXp.ECLOSION},
            {"Pescar", "+" + net.pokereport.luna.pase.PaseXp.PESCA},
            {"Picar una mena", "+" + net.pokereport.luna.pase.PaseXp.MENA},
            {"Cosechar", "+" + net.pokereport.luna.pase.PaseXp.COSECHA},
            {"Ganar un combate", "+" + net.pokereport.luna.pase.PaseXp.COMBATE},
            {"Ronda de la Torre",
                    "+" + net.pokereport.luna.pase.PaseXp.torre(1)
                        + ".." + net.pokereport.luna.pase.PaseXp.torre(100_000)},
            {"Completar una mision", "+" + net.pokereport.luna.pase.PaseXp.MISION},
            {"Ganar una medalla", "+" + net.pokereport.luna.pase.PaseXp.MEDALLA},
        };
        for (String[] fila : filas) {
            texto(ctx, Text.literal(fila[0]), PANEL_X + 26, y, 11,
                    0xFFB8C2D8, false);
            texto(ctx, Text.literal(fila[1]), PANEL_X + PANEL_W - 26, y, 11,
                    ORO, false, true);
            y += 12;
        }
    }

    /** Cuantos premios hay disponibles y sin cobrar. */
    private int pendientes() {
        var e = estado();
        if (e == null) {
            return 0;
        }
        int n = 0;
        for (int lvl = 1; lvl <= nivel(); lvl++) {
            if (PaseCatalogo.libre(lvl) != null && !e.cobrado(lvl, PaseCatalogo.LIBRE)) {
                n++;
            }
            if (e.premium() && PaseCatalogo.luna(lvl) != null
                    && !e.cobrado(lvl, PaseCatalogo.LUNA)) {
                n++;
            }
        }
        return n;
    }

    // ---- el carril ---------------------------------------------------------

    private static int colX(int i) {
        return PANT_X + MARGEN + i * (CARD_W + GAP);
    }

    private void dibujarCarril(DrawContext ctx, int rx, int ry) {
        var e = estado();

        // Cabecera: la leyenda de las dos vias y las flechas.
        //
        // ⚠ EN UNA SOLA LINEA Y CON PASTILLA DE COLOR. Apiladas se leen como
        //   dos titulos sueltos y no como la leyenda de las dos filas: el
        //   cuadrito del color es lo que las ata a su fila, porque es el mismo
        //   color del borde de sus tarjetas.
        int lx = PANT_X + MARGEN;
        ctx.fill(px(lx), py(PANT_Y + 18), px(lx + 12), py(PANT_Y + 30), ORO);
        texto(ctx, Text.literal("VIA LIBRE"), lx + 18, PANT_Y + 18, 15, ORO, false);
        int lx2 = lx + 24 + anchoArte("VIA LIBRE", 15);
        boolean tieneLuna = e != null && e.premium();
        ctx.fill(px(lx2), py(PANT_Y + 18), px(lx2 + 12), py(PANT_Y + 30),
                tieneLuna ? LUNA_BORDE : GRIS);
        texto(ctx, Text.literal(tieneLuna ? "VIA LUNA" : "VIA LUNA (bloqueada)"),
                lx2 + 18, PANT_Y + 18, 15, tieneLuna ? LUNA_BORDE : GRIS, false);

        boolean puedeIzq = desde > 1;
        boolean puedeDer = desde < maxDesde();
        flecha(ctx, rx, ry, PANT_X + PANT_W - MARGEN - 96, PANT_Y + 14, false, puedeIzq);
        flecha(ctx, rx, ry, PANT_X + PANT_W - MARGEN - 42, PANT_Y + 14, true, puedeDer);
        texto(ctx, Text.literal("Niveles " + desde + "-"
                        + Math.min(PaseNivel.MAX, desde + COLS - 1)
                        + "  de  " + PaseNivel.MAX),
                PANT_X + PANT_W - MARGEN - 110, PANT_Y + 22, 13, TEXTO_SUAVE,
                false, true);

        // El carril de fondo, de lado a lado, y la parte conseguida encima.
        int railCentro = RAIL_Y + RAIL_H / 2;
        ctx.fill(px(PANT_X + MARGEN), py(railCentro - 3),
                px(PANT_X + PANT_W - MARGEN), py(railCentro + 3), FONDO_HUNDIDO);
        int nivel = nivel();
        // Hasta donde llega lo conseguido DENTRO de lo que se ve ahora.
        int hasta = Math.min(COLS, Math.max(0, nivel - desde + 1));
        if (hasta > 0) {
            int fin = colX(hasta - 1) + CARD_W;
            ctx.fill(px(PANT_X + MARGEN), py(railCentro - 3), px(fin),
                    py(railCentro + 3), ORO_OSCURO);
            // El brillo que recorre lo conseguido: 2,4 s de ida, y solo si hay
            // algo que recorrer.
            int largo = fin - (PANT_X + MARGEN);
            if (largo > 40) {
                double f = (System.currentTimeMillis() % 2400) / 2400.0;
                int bx = PANT_X + MARGEN + (int) (f * largo);
                ctx.fill(px(Math.max(PANT_X + MARGEN, bx - 26)), py(railCentro - 3),
                        px(Math.min(fin, bx + 26)), py(railCentro + 3), ORO);
            }
        }

        for (int i = 0; i < COLS; i++) {
            int lvl = desde + i;
            if (lvl > PaseNivel.MAX) {
                break;
            }
            dibujarColumna(ctx, rx, ry, i, lvl);
        }
    }

    private void dibujarColumna(DrawContext ctx, int rx, int ry, int i, int lvl) {
        var e = estado();
        int x = colX(i);
        int nivel = nivel();
        boolean alcanzado = lvl <= nivel;

        tarjeta(ctx, rx, ry, x, TRACK_Y, lvl, PaseCatalogo.LIBRE,
                PaseCatalogo.libre(lvl), alcanzado);

        // ---- el nodo del carril ------------------------------------------
        int cx = x + CARD_W / 2;
        int cy = RAIL_Y + RAIL_H / 2;
        int r = 17;
        disco(ctx, px(cx), py(cy), pl(r), alcanzado ? ORO : FONDO_HUNDIDO);
        aro(ctx, px(cx), py(cy), pl(r), pl(3),
                alcanzado ? 0xFFFFFFFF : BORDE_HUNDIDO);
        // El nivel EN CURSO late: es donde esta el jugador y tiene que
        // encontrarse sin buscar.
        if (lvl == nivel + 1 && nivel < PaseNivel.MAX) {
            aro(ctx, px(cx), py(cy), pl(r) + pl(4), pl(2),
                    mezclar(BORDE_HUNDIDO, ORO, latido(1200)));
        }
        texto(ctx, Text.literal(String.valueOf(lvl)), cx, cy - 7, 15,
                alcanzado ? 0xFF16203A : TEXTO_SUAVE, true);

        var premio = PaseCatalogo.luna(lvl);
        if (premio == null) {
            // Un hueco de la via Luna se dibuja HUNDIDO y no vacio: un hueco
            // vacio parece una tarjeta que no ha cargado.
            ctx.fill(px(x), py(LUNA_Y + CARD_H / 2 - 16), px(x + CARD_W),
                    py(LUNA_Y + CARD_H / 2 + 16), FONDO_HUNDIDO);
            marco(ctx, px(x), py(LUNA_Y + CARD_H / 2 - 16), pl(CARD_W), pl(32),
                    BORDE_HUNDIDO, Math.max(1, pl(1)));
            texto(ctx, Text.literal("-"), x + CARD_W / 2,
                    LUNA_Y + CARD_H / 2 - 8, 14, GRIS, true);
            return;
        }
        tarjeta(ctx, rx, ry, x, LUNA_Y, lvl, PaseCatalogo.LUNA, premio,
                alcanzado && e != null && e.premium());
    }

    /**
     * Una tarjeta de premio.
     *
     * <p>&#9888; Solo dibuja el 2D. El icono del objeto es 3D y va en la
     * segunda pasada ({@link #dibujarObjetos}), que es la regla de
     * {@code dibujado.md}.
     */
    private void tarjeta(DrawContext ctx, int rx, int ry, int x, int y, int lvl,
                         String via, Recompensa r, boolean disponible) {
        var e = estado();
        boolean luna = PaseCatalogo.LUNA.equals(via);
        boolean cobrado = e != null && e.cobrado(lvl, via);
        boolean cobrable = disponible && !cobrado;
        boolean sobre = dentro(rx, ry, px(x), py(y), pl(CARD_W), pl(CARD_H));

        int fondo;
        if (cobrado) {
            fondo = 0xFF1A2130;
        } else if (luna) {
            fondo = sobre ? LUNA_FONDO_ENCIMA : LUNA_FONDO;
        } else {
            fondo = sobre ? FONDO_TARJETA_ENCIMA : FONDO_TARJETA;
        }
        ctx.fill(px(x), py(y), px(x + CARD_W), py(y + CARD_H), fondo);

        int borde;
        int grosor = 2;
        if (cobrable) {
            // ⚠ EL LATIDO ES LO QUE HACE QUE SE VEA. Un premio disponible
            //   dibujado igual que uno bloqueado no lo encuentra nadie.
            borde = mezclar(luna ? LUNA_BORDE : ORO_OSCURO, ORO, latido(800));
            grosor = 3;
        } else if (cobrado) {
            borde = 0xFF2A5E3C;
        } else if (sobre) {
            borde = BORDE_ENCIMA;
        } else {
            borde = luna ? 0xFF4A3A6E : BORDE_BASE;
        }
        marco(ctx, px(x), py(y), pl(CARD_W), pl(CARD_H), borde, Math.max(1, pl(grosor)));

        // El casillero hundido donde va el icono.
        int hueco = 56;
        int hx = x + (CARD_W - hueco) / 2;
        int hy = y + 14;
        ctx.fill(px(hx), py(hy), px(hx + hueco), py(hy + hueco), FONDO_HUNDIDO);
        marco(ctx, px(hx), py(hy), pl(hueco), pl(hueco), BORDE_HUNDIDO,
                Math.max(1, pl(1)));

        // Los cosmeticos no son objetos del juego: llevan glifo propio.
        if (r.tipo() == Recompensa.Tipo.COSMETICO) {
            glifoCosmetico(ctx, r.id(), hx + hueco / 2, hy + hueco / 2);
        } else if (r.tipo() == Recompensa.Tipo.PLATA) {
            textura(ctx, PLATA, px(hx + 12), py(hy + 12), pl(32), pl(32), 40, 40);
        }

        // La cantidad, en pastilla. Solo si es mas de una.
        if (r.cantidad() > 1 && r.tipo() != Recompensa.Tipo.PLATA) {
            texto(ctx, Text.literal("x" + r.cantidad()), hx + hueco - 4, hy + hueco - 14,
                    13, 0xFFFFFFFF, false, true);
        }

        // El nombre, hasta dos lineas.
        int ty = y + hueco + 22;
        for (String linea : partir(nombre(r), CARD_W - 14, 12, 2)) {
            texto(ctx, Text.literal(linea), x + CARD_W / 2, ty, 12,
                    cobrado ? TEXTO_SUAVE : 0xFFE6EBF5, true);
            ty += 14;
        }

        // El pie: el estado, que es lo que se puede pulsar.
        int pieH = 24;
        int pieY = y + CARD_H - pieH - 8;
        int fondoPie;
        int colorPie;
        String rotulo;
        if (cobrado) {
            fondoPie = 0xFF16301F;
            colorPie = 0xFF6FCF97;
            rotulo = "RECOGIDO";
        } else if (cobrable) {
            fondoPie = sobre ? VERDE_CLARO : VERDE;
            colorPie = 0xFFFFFFFF;
            rotulo = "RECLAMAR";
        } else if (luna && (e == null || !e.premium())) {
            fondoPie = 0xFF241B3B;
            colorPie = LUNA_BORDE;
            rotulo = "VIA LUNA";
        } else {
            fondoPie = 0xFF1B2030;
            colorPie = GRIS;
            rotulo = "NIVEL " + lvl;
        }
        ctx.fill(px(x + 8), py(pieY), px(x + CARD_W - 8), py(pieY + pieH), fondoPie);
        marco(ctx, px(x + 8), py(pieY), pl(CARD_W - 16), pl(pieH),
                cobrable ? 0xFF10331E : BORDE_HUNDIDO, Math.max(1, pl(1)));
        texto(ctx, Text.literal(rotulo), x + CARD_W / 2, pieY + 6, 13, colorPie, true);
    }

    /**
     * Los iconos 3D, en su propia pasada.
     *
     * <p>Aqui tambien se apunta lo que hay bajo el raton, porque el tooltip
     * nativo necesita el {@link ItemStack} y este es el sitio donde ya existe.
     */
    private void dibujarObjetos(DrawContext ctx, int rx, int ry) {
        for (int i = 0; i < COLS; i++) {
            int lvl = desde + i;
            if (lvl > PaseNivel.MAX) {
                break;
            }
            int x = colX(i);
            objetoDe(ctx, rx, ry, PaseCatalogo.libre(lvl), x, TRACK_Y);
            var luna = PaseCatalogo.luna(lvl);
            if (luna != null) {
                objetoDe(ctx, rx, ry, luna, x, LUNA_Y);
            }
        }
    }

    private void objetoDe(DrawContext ctx, int rx, int ry, Recompensa r, int x, int y) {
        if (r == null || r.tipo() == Recompensa.Tipo.COSMETICO
                || r.tipo() == Recompensa.Tipo.PLATA) {
            return;
        }
        var item = Registries.ITEM.get(Identifier.of(r.iconoObjeto()));
        if (item == null) {
            return;
        }
        ItemStack pila = new ItemStack(item, Math.max(1, r.cantidad()));
        int hueco = 56;
        int hx = x + (CARD_W - hueco) / 2 + 12;
        int hy = y + 14 + 12;
        float escala = pl(32) / 16f;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(px(hx), py(hy), 0);
        m.scale(escala, escala, 1f);
        ctx.drawItem(pila, 0, 0);
        m.pop();
        if (dentro(rx, ry, px(hx), py(hy), pl(32), pl(32))) {
            bajoElRaton = pila;
        }
    }

    /**
     * El glifo de un cosmetico, dibujado con codigo.
     *
     * <p>&#9888; Un cosmetico NO ES UN OBJETO en este servidor &mdash;esa es la
     * decision de D-039 y de {@code Sombreros}&mdash; asi que no hay
     * {@code ItemStack} que dibujar. Se dibuja su FAMILIA, que es lo que el
     * jugador necesita saber de un vistazo: sombrero, aura o disfraz.
     */
    private void glifoCosmetico(DrawContext ctx, String id, int cx, int cy) {
        int c = px(cx);
        int y = py(cy);
        if (id.startsWith("aura_")) {
            // Un aro con tres motas girando: es lo que un aura hace.
            aro(ctx, c, y, pl(15), pl(3), LUNA_BORDE);
            double g = (System.currentTimeMillis() % 3000) / 3000.0 * Math.PI * 2;
            for (int i = 0; i < 3; i++) {
                double a = g + i * (Math.PI * 2 / 3);
                disco(ctx, c + (int) (Math.cos(a) * pl(15)),
                        y + (int) (Math.sin(a) * pl(15)), pl(3), 0xFFFFFFFF);
            }
        } else if (id.startsWith("sombrero_")) {
            // Copa y ala: la silueta minima que se lee como sombrero.
            ctx.fill(c - pl(9), y - pl(11), c + pl(9), y + pl(2), LUNA_BORDE);
            ctx.fill(c - pl(16), y + pl(2), c + pl(16), y + pl(6), 0xFFFFFFFF);
        } else {
            // Una mascota es un disfraz de Pokemon: su icono es una Poke Ball.
            Iconos.pokeball(ctx, c, y, pl(30));
        }
    }

    /** Como se llama un premio en la tarjeta. */
    private String nombre(Recompensa r) {
        return switch (r.tipo()) {
            case PLATA -> String.format("%,d Plata", r.cantidad());
            case LLAVE -> "gachapon".equals(r.id())
                    ? "Llave de Gachapon" : "Llave de Gacha";
            case COSMETICO -> {
                var pieza = net.pokereport.luna.cosmetics.Catalogo.de(r.id());
                yield pieza == null ? r.id() : pieza.aspecto();
            }
            case OBJETO -> {
                var item = Registries.ITEM.get(Identifier.of(r.id()));
                // ⚠ El nombre lo pone el CLIENTE, no el servidor: `getName`
                //   devuelve un Text traducible, y resolverlo en el servidor lo
                //   congelaria en ingles. Es la regla del bloque Idioma.
                yield item == null ? r.id() : item.getName().getString();
            }
        };
    }

    // =======================================================================
    // ENTRADA
    // =======================================================================

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (dentro((int) mx, (int) my, px(PANT_X), py(PANT_Y), pl(PANT_W), pl(PANT_H))) {
            mover(v > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(int tecla, int scan, int mod) {
        if (tecla == 263) {          // flecha izquierda
            mover(-COLS);
            return true;
        }
        if (tecla == 262) {          // flecha derecha
            mover(COLS);
            return true;
        }
        return super.keyPressed(tecla, scan, mod);
    }

    private void mover(int pasos) {
        int nuevo = Math.max(1, Math.min(maxDesde(), desde + pasos));
        if (nuevo != desde) {
            desde = nuevo;
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) {
            return super.mouseClicked(mx, my, boton);
        }
        int rx = (int) mx, ry = (int) my;

        int cy = py(PANEL_Y + NAV_ALTO / 2);
        if (dentro(rx, ry, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            if (anterior != null && client != null) {
                client.setScreen(anterior);
            } else {
                close();
            }
            return true;
        }
        int cerrarX = px(PANEL_X + PANEL_W - 18) - pl(80);
        if (dentro(rx, ry, cerrarX, cy - pl(32), pl(80), pl(64))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            close();
            return true;
        }

        // Las flechas del carril.
        //
        // ⚠ LAS FLECHAS SALTAN UNA PAGINA ENTERA Y LA RUEDA VA DE UNO EN UNO.
        //   Con cincuenta niveles y seis columnas, saltar de uno en uno son
        //   CUARENTA Y CINCO clics para llegar al final: eso no es navegar, es
        //   un castigo. La rueda se queda fina porque es lo que se usa para
        //   mirar de cerca.
        if (dentro(rx, ry, px(PANT_X + PANT_W - MARGEN - 96), py(PANT_Y + 14),
                pl(40), pl(30))) {
            mover(-COLS);
            return true;
        }
        if (dentro(rx, ry, px(PANT_X + PANT_W - MARGEN - 42), py(PANT_Y + 14),
                pl(40), pl(30))) {
            mover(COLS);
            return true;
        }

        var e = estado();
        if (e == null || esperando()) {
            return super.mouseClicked(mx, my, boton);
        }

        // Comprar la via Luna. El rectangulo se calcula IGUAL que al dibujar
        // --misma cuenta, mismas constantes-- porque si cada uno la hiciera a su
        // manera, pulsar aqui haria otra cosa. Es la leccion de la rejilla.
        int botonLunaY = yCajaLuna();
        if (!e.premium() && dentro(rx, ry, px(PANEL_X + 26),
                py(botonLunaY + BOTON_LUNA_DY), pl(PANEL_W - 52), pl(BOTON_LUNA_H))) {
            if (e.saldoLuna() < PaseCatalogo.PRECIO_LUNA) {
                sonar(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
                return true;
            }
            pulsado = System.currentTimeMillis();
            sonar(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
            ClientPlayNetworking.send(new Red.AccionPase("comprar", 0, ""));
            return true;
        }

        // Reclamar todo.
        int btnY = yBotonReclamar();
        if (dentro(rx, ry, px(PANEL_X + 22), py(btnY), pl(PANEL_W - 44),
                pl(BOTON_RECLAMAR_H))) {
            if (pendientes() == 0) {
                sonar(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
                return true;
            }
            pulsado = System.currentTimeMillis();
            sonar(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.3f);
            ClientPlayNetworking.send(new Red.AccionPase("reclamar_todo", 0, ""));
            return true;
        }

        // Una tarjeta suelta.
        for (int i = 0; i < COLS; i++) {
            int lvl = desde + i;
            if (lvl > PaseNivel.MAX) {
                break;
            }
            int x = colX(i);
            if (dentro(rx, ry, px(x), py(TRACK_Y), pl(CARD_W), pl(CARD_H))) {
                reclamar(lvl, PaseCatalogo.LIBRE);
                return true;
            }
            if (PaseCatalogo.luna(lvl) != null
                    && dentro(rx, ry, px(x), py(LUNA_Y), pl(CARD_W), pl(CARD_H))) {
                reclamar(lvl, PaseCatalogo.LUNA);
                return true;
            }
        }
        return super.mouseClicked(mx, my, boton);
    }

    /**
     * Donde empieza la caja de la via Luna del panel.
     *
     * <p>&#9888;&#9888; ES UNA CUENTA Y NO UN NUMERO, y la usan el dibujado Y el
     * clic. Escrita dos veces, el dia que se mueva una linea del panel el boton
     * se dibujaria en un sitio y respondería en otro &mdash; sin dar ningun
     * error, que es como se descubre tarde.
     */
    private static int yCajaLuna() {
        int anilloY = PANEL_Y + NAV_ALTO + 118;
        int y = anilloY + 56 + 16;   // radio del anillo + hueco
        y += 22;                     // la linea de XP
        y += 12;                     // separador
        y += 18 + 20 + 20;           // rotulo, barra y pie del tope diario
        y += 12;                     // separador
        return y;
    }

    /** Alto de la caja de la via Luna. Lo usan el dibujado y {@link #yBotonReclamar}. */
    private static final int CAJA_LUNA_H = 76;

    /**
     * El boton de COMPRAR, dentro de esa caja.
     *
     * <p>&#9888;&#9888; SON CONSTANTES Y NO NUMEROS SUELTOS porque el dibujado y
     * el clic los usan LOS DOS. Estaban escritos dos veces &mdash;{@code y + 30}
     * y {@code 38}&mdash; y esa es exactamente la forma del fallo de la rejilla
     * del PokePad: mover el boton un pixel al dibujarlo y no al pulsarlo deja
     * una franja que se ve y no responde, y otra que responde y no se ve.
     */
    private static final int BOTON_LUNA_DY = 26;

    /** Alto del boton de comprar la via Luna. */
    private static final int BOTON_LUNA_H = 32;

    /** Alto del boton de reclamar todo. */
    private static final int BOTON_RECLAMAR_H = 44;

    /** Donde empieza el boton de RECLAMAR TODO. Misma regla que arriba. */
    private static int yBotonReclamar() {
        return yCajaLuna() + CAJA_LUNA_H + 12;
    }

    private void reclamar(int nivel, String via) {
        var e = estado();
        if (e == null || nivel > nivel() || e.cobrado(nivel, via)
                || PaseCatalogo.de(nivel, via) == null) {
            sonar(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
            return;
        }
        if (PaseCatalogo.LUNA.equals(via) && !e.premium()) {
            sonar(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
            return;
        }
        pulsado = System.currentTimeMillis();
        sonar(SoundEvents.ENTITY_ITEM_PICKUP, 1.4f);
        ClientPlayNetworking.send(new Red.AccionPase("reclamar", nivel, via));
    }

    private void sonar(net.minecraft.sound.SoundEvent sonido, float tono) {
        if (client != null && client.player != null) {
            client.player.playSound(sonido, 0.7f, tono);
        }
    }

    // =======================================================================
    // UTILIDADES DE DIBUJADO
    // =======================================================================

    /** Entre 0 y 1 y vuelta, con el periodo en milisegundos. */
    private static float latido(int periodoMs) {
        double f = (System.currentTimeMillis() % periodoMs) / (double) periodoMs;
        return (float) ((Math.sin(f * Math.PI * 2) + 1) / 2);
    }

    private static int mezclar(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int z = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | z;
    }

    private void barra(DrawContext ctx, int x, int y, int w, int h,
                       double fraccion, int color) {
        ctx.fill(px(x), py(y), px(x + w), py(y + h), FONDO_HUNDIDO);
        int lleno = (int) Math.round(Math.max(0, Math.min(1, fraccion)) * w);
        if (lleno > 0) {
            ctx.fill(px(x), py(y), px(x + lleno), py(y + h), color);
        }
        marco(ctx, px(x), py(y), pl(w), pl(h), BORDE_HUNDIDO, Math.max(1, pl(1)));
    }

    /**
     * Un arco, dibujado por segmentos.
     *
     * <p>&#9888; Por segmentos y no pixel a pixel: un anillo de radio 56 tiene
     * ~10.000 pixeles dentro de su caja, y eso serian 10.000 {@code fill} por
     * fotograma. Con 140 segmentos que se solapan sale igual de continuo y
     * cuesta 140.
     */
    private static void arco(DrawContext ctx, int cx, int cy, int r, int grosor,
                             double fraccion, int color) {
        double f = Math.max(0, Math.min(1, fraccion));
        // ⚠ Con 0 no se dibuja NADA. Sin esta línea el bucle daría una vuelta y
        //   dejaría un punto suelto a las doce, que parece un píxel muerto.
        if (f <= 0) {
            return;
        }
        int pasos = (int) (140 * f);
        int radio = r - grosor / 2;
        for (int i = 0; i <= pasos; i++) {
            double a = -Math.PI / 2 + (i / 140.0) * Math.PI * 2;
            int x = cx + (int) Math.round(Math.cos(a) * radio);
            int y = cy + (int) Math.round(Math.sin(a) * radio);
            ctx.fill(x - grosor / 2, y - grosor / 2,
                     x + (grosor + 1) / 2, y + (grosor + 1) / 2, color);
        }
    }

    private static void disco(DrawContext ctx, int cx, int cy, int r, int color) {
        Iconos.disco(ctx, cx, cy, r, color);
    }

    private static void aro(DrawContext ctx, int cx, int cy, int r, int grosor,
                            int color) {
        Iconos.aro(ctx, cx, cy, r, grosor, color);
    }

    private void flecha(DrawContext ctx, int rx, int ry, int x, int y,
                        boolean derecha, boolean activa) {
        boolean sobre = activa && dentro(rx, ry, px(x), py(y), pl(40), pl(30));
        ctx.fill(px(x), py(y), px(x + 40), py(y + 30),
                sobre ? FONDO_TARJETA_ENCIMA : FONDO_HUNDIDO);
        marco(ctx, px(x), py(y), pl(40), pl(30),
                activa ? (sobre ? BORDE_ENCIMA : BORDE_BASE) : GRIS,
                Math.max(1, pl(1)));
        int cx = px(x + 20);
        int cy = py(y + 15);
        int lado = pl(8);
        for (int i = 0; i < lado; i++) {
            int semi = lado - i;
            int dx = derecha ? cx - lado / 2 + i : cx + lado / 2 - i;
            ctx.fill(dx, cy - semi, dx + Math.max(1, pl(2)), cy + semi,
                    activa ? 0xFFFFFFFF : GRIS);
        }
    }

    private void separador(DrawContext ctx, int artY) {
        ctx.fill(px(PANEL_X + 26), py(artY), px(PANEL_X + PANEL_W - 26),
                py(artY) + Math.max(1, pl(2)), SEPARADOR);
    }

    /** Parte un texto en como mucho {@code maxLineas}, con puntos suspensivos. */
    private List<String> partir(String texto, int anchoArte, int altoArte,
                                int maxLineas) {
        var salida = new ArrayList<String>();
        var actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (anchoArte(prueba, altoArte) > anchoArte && !actual.isEmpty()) {
                salida.add(actual.toString());
                actual = new StringBuilder(palabra);
                if (salida.size() == maxLineas) {
                    break;
                }
            } else {
                actual = new StringBuilder(prueba);
            }
        }
        if (salida.size() < maxLineas && !actual.isEmpty()) {
            salida.add(actual.toString());
        }
        return salida;
    }

    private int anchoArte(String linea, int alto) {
        return Math.round(textRenderer.getWidth(linea) * alto
                / (float) textRenderer.fontHeight);
    }

    private void texto(DrawContext ctx, Text linea, int cx, int arriba, int alto,
                       int color, boolean centrado) {
        texto(ctx, linea, cx, arriba, alto, color, centrado, false);
    }

    /**
     * Texto escalado con el chasis.
     *
     * <p>&#9888;&#9888; LA SOMBRA ES LA NATIVA DE MINECRAFT, no cuatro copias
     * desplazadas en blanco. El contorno manual empasta en textos pequeños y
     * deja una mancha que parece un fallo de renderizado &mdash; esta ya se
     * pago en {@code TorreRecompensasScreen}.
     */
    private void texto(DrawContext ctx, Text linea, int cx, int arriba, int alto,
                       int color, boolean centrado, boolean derecha) {
        float escala = alto * k / textRenderer.fontHeight;
        if (escala <= 0) {
            return;
        }
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x0, y0, 0);
        m.scale(escala, escala, 1f);
        int anchoTexto = textRenderer.getWidth(linea);
        int tx = Math.round(cx * k / escala);
        if (centrado) {
            tx -= anchoTexto / 2;
        } else if (derecha) {
            tx -= anchoTexto;
        }
        ctx.drawText(textRenderer, linea, tx, Math.round(arriba * k / escala),
                color, true);
        m.pop();
    }

    private static boolean dentro(int rx, int ry, int x, int y, int w, int h) {
        return rx >= x && rx < x + w && ry >= y && ry < y + h;
    }

    /**
     * Un marco, SIN rellenar el interior.
     *
     * <p>&#9888;&#9888;&#9888; EL BORDE IZQUIERDO ES {@code x + g}, NO
     * {@code x + w}. Con {@code x + w} la funcion rellena el rectangulo ENTERO
     * del color del borde: pedir un filo dorado de 2 px pinta la tarjeta de
     * amarillo y el texto de encima desaparece. Paso de verdad en la Torre el
     * 2026-09-07, y el sintoma parecia «la pantalla tiene fondo amarillo».
     */
    private static void marco(DrawContext ctx, int x, int y, int w, int h,
                              int color, int g) {
        ctx.fill(x, y, x + w, y + g, color);
        ctx.fill(x, y + h - g, x + w, y + h, color);
        ctx.fill(x, y, x + g, y + h, color);
        ctx.fill(x + w - g, y, x + w, y + h, color);
    }

    /**
     * Dibuja una textura con la mezcla alfa ENCENDIDA.
     *
     * <p>&#9888;&#9888;&#9888; Sin {@code enableBlend}, el juego trata cualquier
     * alfa mayor que cero como opaco y salen motas de colores o un cerco negro
     * alrededor del arte. Es la regla 1 de {@code dibujado.md} y costo una noche.
     */
    private static void textura(DrawContext ctx, Identifier tex,
                                int x, int y, int w, int h, int natW, int natH) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
    }
}
