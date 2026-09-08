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
import net.pokereport.luna.pase.PaseXp;
import net.pokereport.luna.pase.Recompensa;

/**
 * EL PASE DE BATALLA LUNA: cien niveles, una sola via, de pago.
 *
 * <p>Chasis compartido ({@code pokepad_cosmeticos.png}) y {@link Escalado},
 * como todas las sub-pantallas desde el 2026-08-22.
 *
 * <h2>&#9888;&#9888;&#9888; NI LA CURVA NI LOS PREMIOS VIAJAN POR LA RED</h2>
 *
 * Esta pantalla lee {@link PaseNivel} y {@link PaseCatalogo} <b>directamente</b>,
 * porque los dos viven en {@code main} y {@code main} corre en los dos lados.
 * Es la misma decision que {@code CatalogoPad}: mandar el nivel y la lista de
 * cien premios seria un SEGUNDO sitio donde vive la misma verdad, y este
 * proyecto ya sabe como acaba eso &mdash; las tres listas de medallas, los dos
 * ordenes del PokePad. Aqui no puede desincronizarse porque no hay dos copias.
 *
 * <p>Lo que si viaja es el ESTADO. Y P6 sigue intacto: pulsar RECLAMAR manda
 * <b>el nivel</b>, nunca el premio.
 *
 * <h2>Por que esta version se ve distinta a la primera</h2>
 *
 * La primera tenia dos vias y por tanto <b>dos filas de tarjetas apretadas</b>:
 * seis columnas de 120 px con texto de 11-12 px. El usuario lo dijo con todas
 * las letras &mdash;<i>«hay letras y numeros que no se ven muy claros»</i>&mdash;
 * y tenia razon: a 12 px de arte, sobre un chasis que se encoge en pantallas
 * pequeñas, eso son seis pixeles de alto.
 *
 * <p>Al quedar UNA sola via (D-046) se libera media pantalla, y eso es lo que
 * arregla la legibilidad de verdad: <b>cuatro tarjetas de 180x336</b> con todo
 * el texto entre 13 y 22 px. No es que se haya «subido la fuente»: es que hay
 * sitio.
 *
 * <h2>&#9888;&#9888; LA REGLA DE LAS 2 PASADAS DE {@code dibujado.md}</h2>
 *
 * Primero TODO el 2D, luego {@code ctx.draw()}, luego los modelos &mdash;objetos
 * y Pokemon&mdash;, y al final los tooltips. Mezclarlos corrompe lotes de OpenGL
 * o esconde sombras.
 *
 * <p>&#9888; Y con la ayuda abierta <b>la segunda pasada no se hace</b>: el 3D
 * no respeta el orden de dibujado de la interfaz, asi que un Charizard se
 * dibujaria por encima del panel de ayuda.
 */
public class PaseScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier LUNACOIN =
            Identifier.of("lunaeternal", "textures/gui/pokepad/lunacoin_oro.png");

    // ---- medidas del arte (1380 x 828) -------------------------------------
    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;

    // ---- el carril ---------------------------------------------------------
    private static final int MARGEN = 16;
    private static final int GAP = 12;
    private static final int CARD_W = 180;
    private static final int CARD_H = 336;
    private static final int CARDS_Y = 272;
    private static final int MAPA_Y = 624;

    /**
     * Cuantas tarjetas caben. <b>SE CALCULA, NO SE ESCRIBE.</b>
     *
     * <p>&#9888;&#9888;&#9888; ES LA QUINTA VEZ QUE ESTE PROYECTO TROPIEZA CON
     * UNA REJILLA QUE «CABIA POR CASUALIDAD»: los quince iconos del Pad que eran
     * 5x3 justos, los 62 cosmeticos de los que 54 eran inalcanzables, las 8
     * paradas de Viajes, las 23 medallas de la Liga y las 8 categorias de la
     * tienda. Ninguna dio un error &mdash; todas dieron cosas dibujadas fuera
     * del marco o imposibles de alcanzar.
     */
    private static final int COLS =
            Math.max(1, (PANT_W - 2 * MARGEN + GAP) / (CARD_W + GAP));

    /** Donde empieza la fila, centrada en la pantalla del chasis. */
    private static final int FILA_X =
            PANT_X + (PANT_W - (COLS * CARD_W + (COLS - 1) * GAP)) / 2;

    // ---- colores -----------------------------------------------------------
    private static final int FONDO_TARJETA = 0xFF1B2333;
    private static final int FONDO_TARJETA_ENCIMA = 0xFF26314A;
    private static final int FONDO_COBRADO = 0xFF141A26;
    private static final int FONDO_HUNDIDO = 0xFF0A0E16;
    /** El fondo de una barra vacia. Mas claro que el hundido: si no, no se ve. */
    private static final int FONDO_BARRA = 0xFF1C2536;
    private static final int BORDE_HUNDIDO = 0xFF35496A;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int TEXTO = 0xFFF2F6FF;
    private static final int TEXTO_SUAVE = 0xFF9FB0D4;
    private static final int GRIS = 0xFF5A6678;
    private static final int ORO = 0xFFFFD65C;
    private static final int VERDE = 0xFF2E9E56;
    private static final int VERDE_CLARO = 0xFF5BE08C;
    private static final int NARANJA = 0xFFF35C0C;
    private static final int LUNA = 0xFFB98CFF;

    private final Screen anterior;
    private float k;
    private int ancho, alto, x0, y0;

    /** Primer nivel visible. Entre 1 y {@code MAX - COLS + 1}. */
    private int desde = 1;

    /**
     * Lo mismo, pero interpolado.
     *
     * <p>&#9888; El carril <b>se desliza</b> en vez de saltar. Con cien niveles y
     * cuatro a la vista, saltar de golpe hace que el jugador pierda el sitio: no
     * sabe si ha avanzado uno o veinte. El deslizamiento es lo que le dice
     * cuanto se ha movido.
     */
    private double desdeSuave = 1;

    private final long abierta = System.currentTimeMillis();
    private boolean colocado;

    /** Con SALIDA a 1,5 s: un paquete perdido no puede dejar el boton muerto. */
    private long pulsado;

    private boolean ayuda;
    private ItemStack bajoElRaton;

    private final Efectos.Chispas chispas = new Efectos.Chispas();

    public PaseScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.pase"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirPase());
    }

    @Override
    public void removed() {
        chispas.limpiar();
        super.removed();
    }

    private void recalcular() {
        var m = Escalado.aplicar(client, width, height, CHASIS, ATRAS, CERRAR, LUNACOIN);
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
    private int pxd(double a) { return x0 + (int) Math.round(a * k); }
    private int pl(int a) { return Math.max(1, Math.round(a * k)); }

    private Red.EstadoPase estado() {
        return EstadoCliente.pase();
    }

    private int nivel() {
        var e = estado();
        return e == null ? 0 : PaseNivel.nivelDe(e.xp());
    }

    private boolean tienePase() {
        var e = estado();
        return e != null && e.premium();
    }

    private boolean esperando() {
        return pulsado > 0 && System.currentTimeMillis() - pulsado < 1500;
    }

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

        if (!colocado && estado() != null) {
            colocado = true;
            desde = Math.max(1, Math.min(maxDesde(), nivel() - 1));
            desdeSuave = desde;
        }
        // Interpolacion exponencial: se acerca deprisa y frena sola. Con un paso
        // fijo el carril llegaria de golpe y volveria a ser un salto.
        desdeSuave += (desde - desdeSuave) * Math.min(1, delta * 0.35);
        if (Math.abs(desde - desdeSuave) < 0.004) {
            desdeSuave = desde;
        }

        // ---- PASADA 1: todo el 2D ----------------------------------------
        textura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanel(ctx, rx, ry);
        dibujarCarril(ctx, rx, ry);
        chispas.dibujar(ctx);
        if (ayuda) {
            // ⚠⚠⚠ `ctx.draw()` ANTES DE LA AYUDA, Y NO ES UN ADORNO.
            //
            //    `DrawContext` NO dibuja en el orden en que se le pide: agrupa
            //    por capas y el TEXTO va en una capa que se vuelca LA ULTIMA.
            //    Asi que un panel opaco pintado con `fill` despues de las
            //    tarjetas se dibuja debajo de SU TEXTO: el resultado es la
            //    captura que mando el usuario -- la ayuda encima de la rejilla y
            //    los dos textos superpuestos, ilegibles.
            //
            //    No era transparencia: el relleno es del 95 % y tapaba de
            //    verdad. Lo que se colaba era el texto de abajo, que se pinta
            //    despues. Vaciar el lote aqui obliga a que todo lo anterior
            //    --texto incluido-- quede ya en pantalla antes de tapar.
            ctx.draw();
            dibujarAyuda(ctx, rx, ry);
        }

        // ---- PASADA 2: los modelos ---------------------------------------
        //
        // ⚠ `ctx.draw()` VACIA EL BUFFER antes de meter geometria 3D. Sin esto,
        //   los `fill` de arriba y los modelos de abajo se mezclan en el mismo
        //   lote y salen sombras perdidas o cuadros negros.
        if (!ayuda) {
            ctx.draw();
            dibujarModelos(ctx, rx, ry, delta);
        }

        // ---- PASADA 3: el tooltip nativo ---------------------------------
        if (!ayuda && bajoElRaton != null && !bajoElRaton.isEmpty()) {
            ctx.drawItemTooltip(textRenderer, bajoElRaton, rx, ry);
        }
    }

    private void dibujarNavegacion(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;

        textura(ctx, ATRAS, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48), 120, 96);
        if (dentro(rx, ry, px(PANEL_X + 18), py(cy) - pl(24), pl(60), pl(48))) {
            marco(ctx, px(PANEL_X + 18) - 2, py(cy) - pl(24) - 2,
                    pl(60) + 4, pl(48) + 4, NARANJA, 2);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicio"),
                PANEL_X + 92, cy - 14, 28, TEXTO, false);

        int cx = PANEL_X + PANEL_W - 18 - 80;
        textura(ctx, CERRAR, px(cx), py(cy) - pl(32), pl(80), pl(64), 120, 96);
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4,
                    NARANJA, 2);
        }
    }

    // ---- el panel ----------------------------------------------------------

    //
    // ⚠⚠ ESTOS NUMEROS SALEN DE LA MAQUETA, NO DE LA CABEZA. En la primera
    //    pasada de `tools/gen_maqueta_pase.py` sobre esta pantalla salieron dos
    //    cosas que NO dan ningun error y solo se ven mirando:
    //      1) el boton de COMPRAR acababa en y+86 y el saldo empezaba en y+78,
    //         asi que el precio se comia el «Tienes N LunaCoins»;
    //      2) «NIVEL 47 / 100» iba DENTRO del anillo, y a 26 px del centro un
    //         circulo de radio 62 solo tiene 112 px de ancho -- el texto medía
    //         149 y se salia por los dos lados.
    //    Las dos son «numeros que dejaron de cuadrar», que es exactamente la
    //    familia de fallos para la que existe la maqueta.
    private static final int ANILLO_Y = 272;
    private static final int ANILLO_R = 58;
    private static final int CAJA_PASE_Y = 492;
    private static final int CAJA_PASE_H = 98;
    private static final int BOTON_COMPRAR_DY = 34;
    private static final int BOTON_COMPRAR_H = 42;
    private static final int BOTON_RECLAMAR_Y = 602;
    private static final int BOTON_RECLAMAR_H = 54;
    private static final int AYUDA_X = PANEL_X + PANEL_W - 52;
    private static final int AYUDA_Y = 414;

    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        var e = estado();
        int cx = PANEL_X + PANEL_W / 2;
        int nivel = nivel();

        texto(ctx, Text.literal("PASE DE BATALLA"), cx, PANEL_Y + NAV_ALTO + 8,
                26, ORO, true);
        texto(ctx, Text.literal(e == null ? "Cargando..."
                        : "TEMPORADA " + e.temporada() + "  ·  "
                          + (e.diasRestantes() > 0
                             ? e.diasRestantes() + " dias" : "terminada")),
                cx, PANEL_Y + NAV_ALTO + 40, 15,
                e != null && e.diasRestantes() == 0 ? NARANJA : TEXTO_SUAVE, true);

        // ---- el anillo ----------------------------------------------------
        double fraccion;
        if (e == null) {
            fraccion = 0;
        } else if (nivel >= PaseNivel.MAX) {
            fraccion = 1;
        } else {
            long cuesta = PaseNivel.coste(nivel);
            fraccion = cuesta <= 0 ? 0
                    : Math.min(1.0, PaseNivel.enNivel(e.xp()) / (double) cuesta);
        }
        double entrada = Efectos.suave((System.currentTimeMillis() - abierta) / 700.0);
        boolean completo = nivel >= PaseNivel.MAX;
        int colorAnillo = completo ? VERDE_CLARO : ORO;

        // Halo que respira: es lo que hace que el numero sea LO PRIMERO que se
        // mira al abrir la pantalla.
        Efectos.halo(ctx, px(cx) - pl(ANILLO_R), py(ANILLO_Y) - pl(ANILLO_R),
                pl(ANILLO_R) * 2, pl(ANILLO_R) * 2, pl(9), colorAnillo,
                0.16f + 0.14f * Efectos.pulso(2400));
        Iconos.disco(ctx, px(cx), py(ANILLO_Y), pl(ANILLO_R), FONDO_HUNDIDO);
        Iconos.aro(ctx, px(cx), py(ANILLO_Y), pl(ANILLO_R), pl(8), 0xFF35496A);
        Efectos.arco(ctx, px(cx), py(ANILLO_Y), pl(ANILLO_R) - pl(1), pl(8),
                fraccion * entrada, colorAnillo);

        texto(ctx, Text.literal(String.valueOf(nivel)), cx, ANILLO_Y - 24, 46,
                TEXTO, true);
        // ⚠ «NIVEL» a secas cabe dentro del aro; el «47 / 100» NO -- ver el
        //   aviso de las constantes. Va debajo, donde hay panel entero.
        texto(ctx, Text.literal("NIVEL"), cx, ANILLO_Y + 20, 13, TEXTO_SUAVE, true);
        texto(ctx, Text.literal(nivel + "  /  " + PaseNivel.MAX), cx,
                ANILLO_Y + ANILLO_R + 10, 16, TEXTO_SUAVE, true);

        // ---- la XP --------------------------------------------------------
        int y = 364;
        if (e != null && !completo) {
            texto(ctx, Text.literal(String.format("%,d / %,d XP",
                            PaseNivel.enNivel(e.xp()), PaseNivel.coste(nivel))),
                    cx, y, 17, TEXTO, true);
        } else if (e != null) {
            texto(ctx, Text.literal("PASE COMPLETADO"), cx, y, 17, VERDE_CLARO, true);
        }
        Efectos.barra(ctx, px(PANEL_X + 26), py(386), pl(PANEL_W - 52), pl(15),
                fraccion * entrada, FONDO_BARRA, colorAnillo, !completo);
        marco(ctx, px(PANEL_X + 26), py(386), pl(PANEL_W - 52), pl(15),
                BORDE_HUNDIDO, Math.max(1, pl(1)));

        // ---- el tope del dia ---------------------------------------------
        //
        // ⚠ SE ENSEÑA SIEMPRE, no solo al llegar. Un jugador que pica menas
        //   media hora sin ver subir la barra tiene que poder saber POR QUE, y
        //   «ya has hecho tu tope de hoy» solo sirve si se lee ANTES de llegar.
        int xpHoy = e == null ? 0 : e.xpHoy();
        int tope = e == null ? PaseNivel.TOPE_DIARIO : Math.max(1, e.topeHoy());
        boolean topado = xpHoy >= tope;
        texto(ctx, Text.literal("XP DE HOY"), PANEL_X + 26, AYUDA_Y, 15,
                TEXTO_SUAVE, false);
        texto(ctx, Text.literal(String.format("%,d / %,d", xpHoy, tope)),
                AYUDA_X - 12, AYUDA_Y, 15, topado ? NARANJA : TEXTO, false, true);
        // El «?» abre la lista entera de fuentes de XP.
        boolean sobreAyuda = dentro(rx, ry, px(AYUDA_X), py(AYUDA_Y - 4),
                pl(26), pl(26));
        Iconos.disco(ctx, px(AYUDA_X + 13), py(AYUDA_Y + 9), pl(12),
                sobreAyuda ? LUNA : BORDE_HUNDIDO);
        texto(ctx, Text.literal("?"), AYUDA_X + 13, AYUDA_Y + 2, 15,
                sobreAyuda ? 0xFF16203A : TEXTO, true);

        Efectos.barra(ctx, px(PANEL_X + 26), py(438), pl(PANEL_W - 52), pl(14),
                xpHoy / (double) tope, FONDO_BARRA,
                topado ? NARANJA : VERDE_CLARO, !topado && xpHoy > 0);
        marco(ctx, px(PANEL_X + 26), py(438), pl(PANEL_W - 52), pl(14),
                BORDE_HUNDIDO, Math.max(1, pl(1)));

        String pie;
        int colorPie;
        if (topado) {
            pie = "Tope alcanzado. Vuelve mañana.";
            colorPie = NARANJA;
        } else if (tope > PaseNivel.TOPE_DIARIO) {
            pie = "Descanso acumulado: tope x" + (tope / PaseNivel.TOPE_DIARIO);
            colorPie = LUNA;
        } else {
            pie = pistaRotatoria();
            colorPie = TEXTO_SUAVE;
        }
        texto(ctx, Text.literal(pie), cx, 460, 13, colorPie, true);

        separador(ctx, 480);

        // ---- el pase ------------------------------------------------------
        dibujarCajaPase(ctx, rx, ry, e);

        // ---- reclamar todo -------------------------------------------------
        int pendientes = pendientes();
        boolean hay = pendientes > 0 && !esperando();
        boolean sobre = dentro(rx, ry, px(PANEL_X + 22), py(BOTON_RECLAMAR_Y),
                pl(PANEL_W - 44), pl(BOTON_RECLAMAR_H));
        int fondo = !hay ? 0xFF222835
                : (sobre ? VERDE_CLARO
                         : Efectos.mezclar(VERDE, VERDE_CLARO, Efectos.pulso(1100)));
        if (hay) {
            Efectos.halo(ctx, px(PANEL_X + 22), py(BOTON_RECLAMAR_Y),
                    pl(PANEL_W - 44), pl(BOTON_RECLAMAR_H), pl(7), VERDE_CLARO,
                    0.20f + 0.16f * Efectos.pulso(1100));
        }
        ctx.fill(px(PANEL_X + 22), py(BOTON_RECLAMAR_Y),
                px(PANEL_X + PANEL_W - 22), py(BOTON_RECLAMAR_Y + BOTON_RECLAMAR_H),
                fondo);
        if (hay) {
            Efectos.brillo(ctx, px(PANEL_X + 22), py(BOTON_RECLAMAR_Y),
                    pl(PANEL_W - 44), pl(BOTON_RECLAMAR_H), Efectos.rampa(2200),
                    0xFFFFFFFF, 0.30f);
        }
        marco(ctx, px(PANEL_X + 22), py(BOTON_RECLAMAR_Y), pl(PANEL_W - 44),
                pl(BOTON_RECLAMAR_H), hay ? 0xFF0F3320 : BORDE_HUNDIDO,
                Math.max(1, pl(2)));
        texto(ctx, Text.literal(pendientes > 0
                        ? "RECLAMAR TODO  (" + pendientes + ")"
                        : (tienePase() ? "NADA QUE RECLAMAR" : "PASE NO ACTIVO")),
                PANEL_X + PANEL_W / 2, BOTON_RECLAMAR_Y + 18, 20,
                hay ? 0xFFFFFFFF : GRIS, true);

        texto(ctx, Text.literal("Sube el pase capturando, minando,"),
                PANEL_X + PANEL_W / 2, 672, 13, TEXTO_SUAVE, true);
        texto(ctx, Text.literal("pescando, criando y en la Torre"),
                PANEL_X + PANEL_W / 2, 690, 13, TEXTO_SUAVE, true);
    }

    /** La caja del pase: comprarlo, o el sello de que ya lo tienes. */
    private void dibujarCajaPase(DrawContext ctx, int rx, int ry, Red.EstadoPase e) {
        int cx = PANEL_X + PANEL_W / 2;
        int y = CAJA_PASE_Y;
        boolean tiene = e != null && e.premium();

        ctx.fill(px(PANEL_X + 22), py(y), px(PANEL_X + PANEL_W - 22),
                py(y + CAJA_PASE_H), tiene ? 0xFF241B3B : FONDO_HUNDIDO);

        if (tiene) {
            // ⚠ El sello se anima despacio (4 s): es un estado, no una llamada a
            //   la accion. Con el mismo ritmo que el boton de reclamar los dos
            //   competirian y ninguno diria nada.
            Efectos.marcoVivo(ctx, px(PANEL_X + 22), py(y), pl(PANEL_W - 44),
                    pl(CAJA_PASE_H), Math.max(1, pl(2)), 0xFF4A3A6E, LUNA,
                    Efectos.rampa(4000));
            texto(ctx, Text.literal("PASE ACTIVO"), cx, y + 16, 22, LUNA, true);
            texto(ctx, Text.literal("Todos los premios son tuyos"),
                    cx, y + 46, 14, TEXTO_SUAVE, true);
            texto(ctx, Text.literal("hasta el nivel " + PaseNivel.MAX),
                    cx, y + 68, 14, TEXTO_SUAVE, true);
            return;
        }

        marco(ctx, px(PANEL_X + 22), py(y), pl(PANEL_W - 44), pl(CAJA_PASE_H),
                BORDE_HUNDIDO, Math.max(1, pl(2)));
        texto(ctx, Text.literal("DESBLOQUEA EL PASE"), cx, y + 12, 16, TEXTO, true);

        boolean puede = e != null && e.saldoLuna() >= PaseCatalogo.PRECIO;
        int by = y + BOTON_COMPRAR_DY;
        boolean sobre = dentro(rx, ry, px(PANEL_X + 28), py(by),
                pl(PANEL_W - 56), pl(BOTON_COMPRAR_H));
        if (puede) {
            Efectos.halo(ctx, px(PANEL_X + 28), py(by), pl(PANEL_W - 56),
                    pl(BOTON_COMPRAR_H), pl(6), LUNA,
                    0.18f + 0.14f * Efectos.pulso(1600));
        }
        ctx.fill(px(PANEL_X + 28), py(by), px(PANEL_X + PANEL_W - 28),
                py(by + BOTON_COMPRAR_H),
                !puede ? 0xFF262B38 : (sobre ? 0xFF3A2C63 : 0xFF2B2050));
        if (puede) {
            Efectos.brillo(ctx, px(PANEL_X + 28), py(by), pl(PANEL_W - 56),
                    pl(BOTON_COMPRAR_H), Efectos.rampa(2600), 0xFFFFFFFF, 0.28f);
        }
        marco(ctx, px(PANEL_X + 28), py(by), pl(PANEL_W - 56), pl(BOTON_COMPRAR_H),
                !puede ? GRIS : (sobre ? 0xFFFFFFFF : LUNA), Math.max(1, pl(2)));

        textura(ctx, LUNACOIN, px(cx - 60), py(by + 10), pl(22), pl(22), 40, 40);
        texto(ctx, Text.literal(String.format("%,d", PaseCatalogo.PRECIO)),
                cx + 16, by + 10, 22, puede ? ORO : GRIS, true);
        // ⚠ 82 + 13 = 95, y la caja mide 98. El boton acaba en 76, asi que hay
        //   seis pixeles de aire entre los dos: es lo que la maqueta cazo.
        texto(ctx, Text.literal(e == null ? "Cargando saldo..."
                        : String.format("Tienes %,d LunaCoins", e.saldoLuna())),
                cx, y + 82, 13, puede ? TEXTO_SUAVE : GRIS, true);
    }

    /**
     * Una fuente de XP cada tres segundos.
     *
     * <p>&#9888;&#9888; LOS NUMEROS SALEN DE {@code PaseXp}, NO ESTAN ESCRITOS
     * AQUI. Escritos a mano serian una SEGUNDA lista que nada obliga a coincidir
     * con la que paga el servidor: el dia que se recalibre la pesca, la pantalla
     * seguiria prometiendo lo de antes &mdash; sin ningun error, y con el jugador
     * convencido de que le pagamos de menos.
     */
    private static final String[][] FUENTES = {
        {"Capturar un Pokemon", "+" + PaseXp.CAPTURA},
        {"Especie nueva en la Pokedex", "+" + PaseXp.ESPECIE_NUEVA},
        {"Eclosionar un huevo", "+" + PaseXp.ECLOSION},
        {"Pescar con la caña Poke", "+" + PaseXp.PESCA},
        {"Picar una mena", "+" + PaseXp.MENA},
        {"Picar diamante o esmeralda", "+" + PaseXp.MENA_RARA},
        {"Cosechar", "+" + PaseXp.COSECHA},
        {"Ganar un combate", "+" + PaseXp.COMBATE},
        {"Ronda de la Torre", "+" + PaseXp.torre(1) + ".." + PaseXp.torre(100_000)},
        {"Completar una mision", "+" + PaseXp.MISION},
        {"Subir un nivel de Via", "+" + PaseXp.NIVEL_VIA},
        {"Ganar una medalla", "+" + PaseXp.MEDALLA},
    };

    private String pistaRotatoria() {
        int i = (int) ((System.currentTimeMillis() / 3000) % FUENTES.length);
        return FUENTES[i][0] + "   " + FUENTES[i][1];
    }

    /** Cuantos premios hay disponibles y sin cobrar. */
    private int pendientes() {
        var e = estado();
        if (e == null || !e.premium()) {
            return 0;
        }
        int n = 0;
        for (int lvl = 1; lvl <= nivel(); lvl++) {
            if (PaseCatalogo.de(lvl) != null && !e.cobrado(lvl)) {
                n++;
            }
        }
        return n;
    }

    // ---- el carril ---------------------------------------------------------

    private void dibujarCarril(DrawContext ctx, int rx, int ry) {
        var tramo = PaseCatalogo.tramoDe(desde + COLS / 2);

        // Cabecera: el tramo manda, porque es lo que dice QUE clase de premios
        // hay delante. Con cien niveles, «nivel 63» no dice nada; «COMBATE» si.
        ctx.fill(px(PANT_X + MARGEN), py(PANT_Y + 14),
                px(PANT_X + MARGEN + 8), py(PANT_Y + 54), tramo.color());
        texto(ctx, Text.literal(tramo.nombre()), PANT_X + MARGEN + 20,
                PANT_Y + 12, 24, tramo.color(), false);
        texto(ctx, Text.literal(tramo.lema()), PANT_X + MARGEN + 20,
                PANT_Y + 40, 14, 0xFF41506E, false);

        boolean puedeIzq = desde > 1;
        boolean puedeDer = desde < maxDesde();
        flecha(ctx, rx, ry, PANT_X + PANT_W - MARGEN - 104, PANT_Y + 14, false, puedeIzq);
        flecha(ctx, rx, ry, PANT_X + PANT_W - MARGEN - 46, PANT_Y + 14, true, puedeDer);
        texto(ctx, Text.literal("NIVELES " + desde + " - "
                        + Math.min(PaseNivel.MAX, desde + COLS - 1)),
                PANT_X + PANT_W - MARGEN - 116, PANT_Y + 20, 15, 0xFF41506E,
                false, true);

        // ---- las tarjetas, recortadas para que el deslizamiento no se salga
        int cx0 = px(PANT_X + 6);
        int cy0 = py(CARDS_Y - 6);
        int cx1 = px(PANT_X + PANT_W - 6);
        int cy1 = py(CARDS_Y + CARD_H + 6);
        ctx.enableScissor(cx0, cy0, cx1, cy1);
        int base = (int) Math.floor(desdeSuave);
        double desfase = desdeSuave - base;
        for (int i = -1; i <= COLS; i++) {
            int lvl = base + i;
            if (lvl < 1 || lvl > PaseNivel.MAX) {
                continue;
            }
            double tx = FILA_X + (i - desfase) * (CARD_W + GAP);
            tarjeta(ctx, rx, ry, tx, lvl);
        }
        ctx.disableScissor();

        dibujarMapa(ctx, rx, ry);
    }

    /**
     * EL MINI-MAPA DE LOS CIEN NIVELES.
     *
     * <p>&#9888;&#9888; Con cuatro tarjetas a la vista, el carril enseña el 4 %
     * del pase. Sin esta barra el jugador no tiene forma de saber <b>donde
     * esta</b> ni cuanto le queda &mdash; y con veinticinco pantallazos de
     * flecha, se pierde. Aqui se ve de un vistazo: los cinco tramos por color,
     * lo conseguido en oro, y la ventana que estas mirando.
     *
     * <p>&#9888; Y se puede PULSAR para saltar. Un mapa que solo informa obliga a
     * seguir dando a la flecha veinte veces.
     */
    private void dibujarMapa(DrawContext ctx, int rx, int ry) {
        int x = PANT_X + MARGEN;
        int w = PANT_W - 2 * MARGEN;
        int h = 22;
        ctx.fill(px(x), py(MAPA_Y), px(x + w), py(MAPA_Y + h), FONDO_HUNDIDO);

        // Los cinco tramos, cada uno de su color y apagados.
        for (var t : PaseCatalogo.TRAMOS) {
            int a = x + (t.desde() - 1) * w / PaseNivel.MAX;
            int b = x + t.hasta() * w / PaseNivel.MAX;
            ctx.fill(px(a), py(MAPA_Y), px(b) - Math.max(1, pl(1)), py(MAPA_Y + h),
                    Efectos.conAlfa(t.color(), 0.22f));
        }
        // Lo conseguido.
        int nivel = nivel();
        if (nivel > 0) {
            int fin = x + nivel * w / PaseNivel.MAX;
            ctx.fill(px(x), py(MAPA_Y), px(fin), py(MAPA_Y + h),
                    Efectos.conAlfa(ORO, 0.55f));
            Efectos.brillo(ctx, px(x), py(MAPA_Y), px(fin) - px(x), pl(h),
                    Efectos.rampa(3400), 0xFFFFFFFF, 0.35f);
        }
        // La ventana que se esta mirando.
        int va = x + (desde - 1) * w / PaseNivel.MAX;
        int vb = x + Math.min(PaseNivel.MAX, desde + COLS - 1) * w / PaseNivel.MAX;
        ctx.fill(px(va), py(MAPA_Y - 3), px(vb), py(MAPA_Y + h + 3),
                Efectos.conAlfa(0xFFFFFFFF, 0.18f));
        marco(ctx, px(va), py(MAPA_Y - 3), px(vb) - px(va), pl(h + 6),
                0xFFFFFFFF, Math.max(1, pl(2)));

        marco(ctx, px(x), py(MAPA_Y), pl(w), pl(h), BORDE_HUNDIDO,
                Math.max(1, pl(1)));

        // Las marcas de los Pokemon: donde estan los cinco hitos.
        for (int lvl = 1; lvl <= PaseNivel.MAX; lvl++) {
            var r = PaseCatalogo.de(lvl);
            if (r == null || r.tipo() != Recompensa.Tipo.POKEMON) {
                continue;
            }
            int mx = x + (lvl - 1) * w / PaseNivel.MAX + 2;
            Efectos.destello(ctx, px(mx), py(MAPA_Y + h / 2), pl(7),
                    lvl == PaseNivel.MAX ? ORO : 0xFFFFFFFF);
        }

        texto(ctx, Text.literal("Pulsa la barra para saltar de tramo"),
                PANT_X + PANT_W / 2, MAPA_Y + h + 8, 13, 0xFF41506E, true);
    }

    /** Una tarjeta de premio. Solo el 2D: el modelo va en la segunda pasada. */
    private void tarjeta(DrawContext ctx, int rx, int ry, double tx, int lvl) {
        var e = estado();
        var r = PaseCatalogo.de(lvl);
        if (r == null) {
            return;
        }
        var tramo = PaseCatalogo.tramoDe(lvl);
        boolean cobrado = e != null && e.cobrado(lvl);
        boolean alcanzado = lvl <= nivel();
        boolean cobrable = alcanzado && !cobrado && tienePase();
        boolean sobre = dentro(rx, ry, pxd(tx), py(CARDS_Y), pl(CARD_W), pl(CARD_H));

        int x = pxd(tx);
        int y = py(CARDS_Y);
        int w = pl(CARD_W);
        int h = pl(CARD_H);
        // El «levantar» al pasar por encima: dos pixeles de arte. Poco es
        // suficiente -- lo que se busca es que el ojo note cual esta activa.
        if (sobre) {
            y -= pl(3);
        }

        int rareza = r.rareza().color();

        // Halo: solo lo cobrable y lo legendario. Un halo en las cien tarjetas
        // seria una pantalla brillando entera, o sea ninguna destacando.
        if (cobrable) {
            Efectos.halo(ctx, x, y, w, h, pl(8), rareza,
                    0.22f + 0.18f * Efectos.pulso(1200));
        } else if (r.rareza() == Recompensa.Rareza.LEGENDARIA && alcanzado) {
            Efectos.halo(ctx, x, y, w, h, pl(6), rareza, 0.14f);
        }

        int fondo = cobrado ? FONDO_COBRADO
                : (sobre ? FONDO_TARJETA_ENCIMA : FONDO_TARJETA);
        ctx.fill(x, y, x + w, y + h, fondo);

        // La cinta del nivel, del color de su tramo.
        int cinta = pl(36);
        ctx.fill(x, y, x + w, y + cinta,
                cobrado ? 0xFF1E2A22 : Efectos.conAlfa(tramo.color(), 0.92f));
        textoP(ctx, Text.literal("NIVEL " + lvl), tx + CARD_W / 2.0, CARDS_Y + 9,
                19, cobrado ? 0xFF7FB894 : 0xFF0D121B, true, sobre);

        // La rareza, en su color y con su nombre. Es lo que un jugador mira
        // primero en cualquier pase.
        textoP(ctx, Text.literal(r.rareza().nombre()), tx + CARD_W / 2.0,
                CARDS_Y + 46, 13, cobrado ? GRIS : rareza, true, sobre);

        // El casillero del premio.
        int hueco = 108;
        int hx = (int) Math.round(tx + (CARD_W - hueco) / 2.0);
        int hy = CARDS_Y + 66;
        ctx.fill(pxd(hx), py(hy) + (sobre ? -pl(3) : 0), pxd(hx + hueco),
                py(hy + hueco) + (sobre ? -pl(3) : 0), FONDO_HUNDIDO);
        marco(ctx, pxd(hx), py(hy) + (sobre ? -pl(3) : 0), pl(hueco), pl(hueco),
                cobrado ? BORDE_HUNDIDO : Efectos.conAlfa(rareza, 0.6f),
                Math.max(1, pl(1)));

        // El nombre, hasta dos lineas y a 15 px: es lo que el usuario decia que
        // no se leia.
        int ty = CARDS_Y + 186;
        for (String linea : partir(nombre(r), CARD_W - 18, 15, 2)) {
            textoP(ctx, Text.literal(linea), tx + CARD_W / 2.0, ty, 15,
                    cobrado ? TEXTO_SUAVE : TEXTO, true, sobre);
            ty += 19;
        }
        if (r.cantidad() > 1) {
            textoP(ctx, Text.literal("x" + r.cantidad()), tx + CARD_W / 2.0,
                    CARDS_Y + 228, 17, ORO, true, sobre);
        } else if (r.tipo() == Recompensa.Tipo.POKEMON) {
            textoP(ctx, Text.literal((r.shiny() ? "✦ VARIOCOLOR  ·  " : "")
                            + "Nivel " + r.nivel()),
                    tx + CARD_W / 2.0, CARDS_Y + 228, 15,
                    r.shiny() ? ORO : TEXTO_SUAVE, true, sobre);
        } else {
            // ⚠ Un premio de UNO dejaba este renglon vacio y la tarjeta se veia
            //   a medias. Con la categoria, el hueco dice algo: PARA QUE sirve
            //   lo que hay dentro.
            textoP(ctx, Text.literal(tramo.nombre()), tx + CARD_W / 2.0,
                    CARDS_Y + 228, 14, Efectos.conAlfa(tramo.color(), 0.85f),
                    true, sobre);
        }

        // El pie: el estado, que es lo que se pulsa.
        int pieH = 46;
        int pieY = CARDS_Y + CARD_H - pieH - 14;
        int fpy = py(pieY) + (sobre ? -pl(3) : 0);
        int fondoPie;
        int colorPie;
        String rotulo;
        if (cobrado) {
            fondoPie = 0xFF14301E;
            colorPie = 0xFF6FCF97;
            rotulo = "RECOGIDO";
        } else if (cobrable) {
            fondoPie = sobre ? VERDE_CLARO : VERDE;
            colorPie = 0xFFFFFFFF;
            rotulo = "RECLAMAR";
        } else if (!tienePase()) {
            fondoPie = 0xFF241B3B;
            colorPie = LUNA;
            rotulo = "REQUIERE PASE";
        } else {
            // ⚠ Decia «NIVEL 3» y la cinta de arriba YA lo dice: el pie repetia
            //   el numero en vez de decir el estado, que es lo unico que un
            //   boton tiene que decir.
            fondoPie = 0xFF171D2A;
            colorPie = GRIS;
            rotulo = "BLOQUEADO";
        }
        ctx.fill(pxd(tx + 12), fpy, pxd(tx + CARD_W - 12), fpy + pl(pieH), fondoPie);
        if (cobrable) {
            Efectos.brillo(ctx, pxd(tx + 12), fpy, pl(CARD_W - 24), pl(pieH),
                    Efectos.rampa(1800), 0xFFFFFFFF, 0.35f);
        }
        marco(ctx, pxd(tx + 12), fpy, pl(CARD_W - 24), pl(pieH),
                cobrable ? 0xFF0F3320 : BORDE_HUNDIDO, Math.max(1, pl(2)));
        textoP(ctx, Text.literal(rotulo), tx + CARD_W / 2.0, pieY + 15, 18,
                colorPie, true, sobre);

        // El marco. El de un premio cobrable GIRA; el resto es fijo.
        if (cobrable) {
            Efectos.marcoVivo(ctx, x, y, w, h, Math.max(1, pl(3)),
                    Efectos.conAlfa(rareza, 0.55f), 0xFFFFFFFF,
                    Efectos.rampa(2000));
        } else {
            marco(ctx, x, y, w, h,
                    cobrado ? 0xFF2A5E3C
                            : (sobre ? NARANJA : Efectos.conAlfa(rareza, 0.75f)),
                    Math.max(1, pl(sobre ? 3 : 2)));
        }

        // Los destellos de un legendario alcanzado: cuatro puntos girando.
        if (r.rareza() == Recompensa.Rareza.LEGENDARIA && alcanzado && !cobrado) {
            double g = Efectos.rampa(5000) * Math.PI * 2;
            for (int i = 0; i < 4; i++) {
                double a = g + i * (Math.PI / 2);
                int sx = x + w / 2 + (int) (Math.cos(a) * w * 0.42);
                int sy = y + pl(120) + (int) (Math.sin(a) * pl(58));
                Efectos.destello(ctx, sx, sy, pl(5), Efectos.conAlfa(ORO, 0.85f));
            }
        }
    }

    /** Los modelos 3D: objetos y Pokemon. Segunda pasada. */
    private void dibujarModelos(DrawContext ctx, int rx, int ry, float delta) {
        int cx0 = px(PANT_X + 6);
        int cy0 = py(CARDS_Y - 6);
        int cx1 = px(PANT_X + PANT_W - 6);
        int cy1 = py(CARDS_Y + CARD_H + 6);
        ctx.enableScissor(cx0, cy0, cx1, cy1);
        int base = (int) Math.floor(desdeSuave);
        double desfase = desdeSuave - base;
        for (int i = -1; i <= COLS; i++) {
            int lvl = base + i;
            if (lvl < 1 || lvl > PaseNivel.MAX) {
                continue;
            }
            double tx = FILA_X + (i - desfase) * (CARD_W + GAP);
            modeloDe(ctx, rx, ry, tx, lvl, delta);
        }
        ctx.disableScissor();
    }

    private void modeloDe(DrawContext ctx, int rx, int ry, double tx, int lvl,
                          float delta) {
        var r = PaseCatalogo.de(lvl);
        if (r == null) {
            return;
        }
        boolean sobre = dentro(rx, ry, pxd(tx), py(CARDS_Y), pl(CARD_W), pl(CARD_H));
        int hueco = 108;
        int hx = (int) Math.round(tx + (CARD_W - hueco) / 2.0);
        int hy = CARDS_Y + 66 - (sobre ? 3 : 0);

        if (r.tipo() == Recompensa.Tipo.POKEMON) {
            // ⚠ La CLAVE tiene que ser distinta por tarjeta: dos sitios que
            //   dibujen la misma especie con la misma clave comparten el estado
            //   de animacion y se pisan la orientacion. Aqui hay DOS Charizard
            //   (niveles 1 y 100), asi que la clave lleva el nivel.
            Mascota3D.dibujarEspecie(ctx,
                    Identifier.of("cobblemon", r.id()), "pase:" + lvl,
                    r.shiny() ? "shiny" : "",
                    pxd(hx), py(hy), pl(hueco), pl(hueco), 0.16f, delta, true);
            return;
        }
        var item = Registries.ITEM.get(Identifier.of(r.iconoObjeto()));
        if (item == null) {
            return;
        }
        ItemStack pila = new ItemStack(item, Math.max(1, r.cantidad()));
        int lado = 56;
        int ix = hx + (hueco - lado) / 2;
        int iy = hy + (hueco - lado) / 2;
        float escala = pl(lado) / 16f;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(pxd(ix), py(iy), 0);
        m.scale(escala, escala, 1f);
        ctx.drawItem(pila, 0, 0);
        m.pop();
        if (dentro(rx, ry, pxd(ix), py(iy), pl(lado), pl(lado))) {
            bajoElRaton = pila;
        }
    }

    /** La ayuda: de que se saca XP, entera y a tamaño legible. */
    private void dibujarAyuda(DrawContext ctx, int rx, int ry) {
        // Opaco del todo: un velo deja ver el carril por debajo y el ojo
        // intenta leer las dos cosas a la vez.
        ctx.fill(px(PANT_X), py(PANT_Y), px(PANT_X + PANT_W), py(PANT_Y + PANT_H),
                0xFF0A0E16);
        marco(ctx, px(PANT_X), py(PANT_Y), pl(PANT_W), pl(PANT_H), LUNA,
                Math.max(1, pl(2)));
        texto(ctx, Text.literal("DE QUE SE SACA XP DEL PASE"),
                PANT_X + PANT_W / 2, PANT_Y + 18, 22, ORO, true);
        texto(ctx, Text.literal("Tope de " + String.format("%,d", PaseNivel.TOPE_DIARIO)
                        + " XP al dia. Sin jugar, el tope se acumula hasta x"
                        + PaseNivel.DIAS_DESCANSO + "."),
                PANT_X + PANT_W / 2, PANT_Y + 48, 14, TEXTO_SUAVE, true);

        int y = PANT_Y + 84;
        int col = PANT_W / 2 - 20;
        for (int i = 0; i < FUENTES.length; i++) {
            int cx = PANT_X + 24 + (i % 2) * col;
            int fy = y + (i / 2) * 34;
            ctx.fill(px(cx), py(fy - 4), px(cx + col - 16), py(fy + 24),
                    0xFF141A26);
            texto(ctx, Text.literal(FUENTES[i][0]), cx + 12, fy, 15, TEXTO, false);
            texto(ctx, Text.literal(FUENTES[i][1]), cx + col - 28, fy, 15, ORO,
                    false, true);
        }

        int cerrar = PANT_Y + PANT_H - 54;
        boolean sobre = dentro(rx, ry, px(PANT_X + PANT_W / 2 - 90), py(cerrar),
                pl(180), pl(40));
        ctx.fill(px(PANT_X + PANT_W / 2 - 90), py(cerrar),
                px(PANT_X + PANT_W / 2 + 90), py(cerrar + 40),
                sobre ? 0xFF3A2C63 : 0xFF241B3B);
        marco(ctx, px(PANT_X + PANT_W / 2 - 90), py(cerrar), pl(180), pl(40),
                LUNA, Math.max(1, pl(2)));
        texto(ctx, Text.literal("ENTENDIDO"), PANT_X + PANT_W / 2, cerrar + 12,
                18, TEXTO, true);
    }

    /** Como se llama un premio en la tarjeta. */
    private String nombre(Recompensa r) {
        if (r.tipo() == Recompensa.Tipo.POKEMON) {
            // ⚠ El nombre lo pone el CLIENTE con su clave de traduccion: un
            //   servidor no tiene idioma, y `getString()` en el servidor lo
            //   congelaria en ingles para todo el mundo.
            return Text.translatable("cobblemon.species." + r.id() + ".name")
                    .getString();
        }
        var item = Registries.ITEM.get(Identifier.of(r.id()));
        return item == null ? r.id() : item.getName().getString();
    }

    // =======================================================================
    // ENTRADA
    // =======================================================================

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (!ayuda && dentro((int) mx, (int) my, px(PANT_X), py(PANT_Y),
                pl(PANT_W), pl(PANT_H))) {
            mover(v > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(int tecla, int scan, int mod) {
        if (ayuda && (tecla == 256 || tecla == 257)) {
            ayuda = false;
            return true;
        }
        if (tecla == 263) {          // izquierda
            mover(-COLS);
            return true;
        }
        if (tecla == 262) {          // derecha
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

        if (ayuda) {
            ayuda = false;
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            return true;
        }

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
        if (dentro(rx, ry, px(PANEL_X + PANEL_W - 18) - pl(80), cy - pl(32),
                pl(80), pl(64))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            close();
            return true;
        }

        // El «?»
        if (dentro(rx, ry, px(AYUDA_X), py(AYUDA_Y - 4), pl(26), pl(26))) {
            ayuda = true;
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f);
            return true;
        }

        // Las flechas: una PAGINA entera. Con cien niveles y cuatro a la vista,
        // avanzar de uno en uno son noventa y seis clics.
        if (dentro(rx, ry, px(PANT_X + PANT_W - MARGEN - 104), py(PANT_Y + 14),
                pl(44), pl(34))) {
            mover(-COLS);
            return true;
        }
        if (dentro(rx, ry, px(PANT_X + PANT_W - MARGEN - 46), py(PANT_Y + 14),
                pl(44), pl(34))) {
            mover(COLS);
            return true;
        }

        // El mini-mapa: saltar donde se pulse.
        int mapaX = PANT_X + MARGEN;
        int mapaW = PANT_W - 2 * MARGEN;
        if (dentro(rx, ry, px(mapaX), py(MAPA_Y - 4), pl(mapaW), pl(30))) {
            double f = (rx - px(mapaX)) / (double) pl(mapaW);
            int destino = (int) Math.round(f * PaseNivel.MAX) - COLS / 2;
            int nuevo = Math.max(1, Math.min(maxDesde(), destino));
            if (nuevo != desde) {
                desde = nuevo;
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f);
            }
            return true;
        }

        var e = estado();
        if (e == null || esperando()) {
            return super.mouseClicked(mx, my, boton);
        }

        // Comprar el pase.
        if (!e.premium() && dentro(rx, ry, px(PANEL_X + 28),
                py(CAJA_PASE_Y + BOTON_COMPRAR_DY), pl(PANEL_W - 56),
                pl(BOTON_COMPRAR_H))) {
            if (e.saldoLuna() < PaseCatalogo.PRECIO) {
                sonar(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
                return true;
            }
            pulsado = System.currentTimeMillis();
            sonar(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
            chispas.soltar(px(PANEL_X + PANEL_W / 2), py(CAJA_PASE_Y + 50), 40,
                    LUNA, 2.2);
            ClientPlayNetworking.send(new Red.AccionPase("comprar", 0));
            return true;
        }

        // Reclamar todo.
        if (dentro(rx, ry, px(PANEL_X + 22), py(BOTON_RECLAMAR_Y),
                pl(PANEL_W - 44), pl(BOTON_RECLAMAR_H))) {
            if (pendientes() == 0) {
                sonar(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
                return true;
            }
            pulsado = System.currentTimeMillis();
            sonar(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.3f);
            chispas.soltar(px(PANEL_X + PANEL_W / 2),
                    py(BOTON_RECLAMAR_Y + 20), 60, ORO, 2.6);
            ClientPlayNetworking.send(new Red.AccionPase("reclamar_todo", 0));
            return true;
        }

        // Una tarjeta suelta. Se recorre igual que al dibujar --misma cuenta,
        // mismas constantes-- porque si cada uno la hiciera a su manera, pulsar
        // aqui abriria la de al lado. Es la leccion de la rejilla del PokePad.
        int base = (int) Math.floor(desdeSuave);
        double desfase = desdeSuave - base;
        for (int i = -1; i <= COLS; i++) {
            int lvl = base + i;
            if (lvl < 1 || lvl > PaseNivel.MAX) {
                continue;
            }
            double tx = FILA_X + (i - desfase) * (CARD_W + GAP);
            if (dentro(rx, ry, pxd(tx), py(CARDS_Y), pl(CARD_W), pl(CARD_H))) {
                reclamar(lvl, pxd(tx) + pl(CARD_W) / 2, py(CARDS_Y) + pl(140));
                return true;
            }
        }
        return super.mouseClicked(mx, my, boton);
    }

    private void reclamar(int lvl, int cx, int cy) {
        var e = estado();
        if (e == null || PaseCatalogo.de(lvl) == null) {
            return;
        }
        if (!e.premium()) {
            sonar(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
            return;
        }
        if (lvl > nivel() || e.cobrado(lvl)) {
            sonar(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
            return;
        }
        pulsado = System.currentTimeMillis();
        var r = PaseCatalogo.de(lvl);
        chispas.soltar(cx, cy, r.rareza() == Recompensa.Rareza.LEGENDARIA ? 70 : 30,
                r.rareza().color(), 2.4);
        sonar(r.rareza() == Recompensa.Rareza.LEGENDARIA
                ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE
                : SoundEvents.ENTITY_ITEM_PICKUP, 1.3f);
        ClientPlayNetworking.send(new Red.AccionPase("reclamar", lvl));
    }

    private void sonar(net.minecraft.sound.SoundEvent sonido, float tono) {
        if (client != null && client.player != null) {
            client.player.playSound(sonido, 0.7f, tono);
        }
    }

    // =======================================================================
    // UTILIDADES DE DIBUJADO
    // =======================================================================

    private void separador(DrawContext ctx, int artY) {
        ctx.fill(px(PANEL_X + 26), py(artY), px(PANEL_X + PANEL_W - 26),
                py(artY) + Math.max(1, pl(2)), SEPARADOR);
    }

    private void flecha(DrawContext ctx, int rx, int ry, int x, int y,
                        boolean derecha, boolean activa) {
        boolean sobre = activa && dentro(rx, ry, px(x), py(y), pl(44), pl(34));
        ctx.fill(px(x), py(y), px(x + 44), py(y + 34),
                sobre ? 0xFF2A3550 : FONDO_HUNDIDO);
        marco(ctx, px(x), py(y), pl(44), pl(34),
                activa ? (sobre ? NARANJA : BORDE_HUNDIDO) : 0xFF232A38,
                Math.max(1, pl(2)));
        int cx = px(x + 22);
        int cy = py(y + 17);
        int lado = pl(9);
        for (int i = 0; i < lado; i++) {
            int semi = lado - i;
            int dx = derecha ? cx - lado / 2 + i : cx + lado / 2 - i;
            ctx.fill(dx, cy - semi, dx + Math.max(1, pl(2)), cy + semi,
                    activa ? TEXTO : GRIS);
        }
    }

    /** Parte un texto en como mucho {@code maxLineas}. */
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
                    return salida;
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
     * deja una mancha que parece un fallo de renderizado &mdash; ya se pago en
     * {@code TorreRecompensasScreen}.
     */
    private void texto(DrawContext ctx, Text linea, int cx, int arriba, int alto,
                       int color, boolean centrado, boolean derecha) {
        textoEn(ctx, linea, cx, arriba, alto, color, centrado, derecha, 0);
    }

    /** Igual, pero con la X en coma flotante (el carril se desliza). */
    private void textoP(DrawContext ctx, Text linea, double cx, int arriba,
                        int alto, int color, boolean centrado, boolean subido) {
        textoEn(ctx, linea, cx, arriba, alto, color, centrado, false,
                subido ? -3 : 0);
    }

    private void textoEn(DrawContext ctx, Text linea, double cx, int arriba,
                         int alto, int color, boolean centrado, boolean derecha,
                         int dy) {
        float escala = alto * k / textRenderer.fontHeight;
        if (escala <= 0) {
            return;
        }
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x0, y0, 0);
        m.scale(escala, escala, 1f);
        int anchoTexto = textRenderer.getWidth(linea);
        int tx = (int) Math.round(cx * k / escala);
        if (centrado) {
            tx -= anchoTexto / 2;
        } else if (derecha) {
            tx -= anchoTexto;
        }
        // ⚠⚠⚠ SIN SOMBRA, Y ESTO CORRIGE LA LECCION DE LA TORRE.
        //
        //    `TorreRecompensasScreen` dice --y es cierto-- que la sombra NATIVA
        //    es mejor que dibujar cuatro copias desplazadas en blanco. Lo que
        //    aquella nota no dice es que ESO VALE A ESCALA 1.
        //
        //    Minecraft dibuja la sombra desplazada UNA UNIDAD DE FUENTE, y aqui
        //    la matriz esta escalada: un texto de 19 px de arte se dibuja con
        //    `escala = 19 / 9 = 2,1`, asi que esa unidad se convierte en DOS
        //    PIXELES Y MEDIO de pantalla... y en un 4K, en cinco. Deja de ser
        //    una sombra y pasa a ser un CONTORNO NEGRO GRUESO pegado a cada
        //    letra: es justo lo que reporto el usuario --«tiene como un contorno
        //    negro y no se ve bien»-- con la captura delante.
        //
        //    Y aqui no hace falta ninguna: todo el texto de esta pantalla cae
        //    sobre un relleno SOLIDO --tarjetas oscuras, panel oscuro, la
        //    pantalla clara del chasis--, asi que el contraste ya lo da el
        //    fondo. La sombra solo existe para leer sobre algo que se mueve.
        ctx.drawText(textRenderer, linea, tx,
                Math.round((arriba + dy) * k / escala), color, false);
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
