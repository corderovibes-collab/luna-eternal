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
import net.pokereport.luna.net.Red;

/**
 * ELEGIR EL PRIMER COMPAÑERO, DELANTE DEL PROFESOR OAK.
 *
 * <h2>&#9888;&#9888;&#9888; LA PANTALLA YA NO SE ABRE SOLA, Y ESO CAMBIA SU
 * DISEÑO ENTERO</h2>
 *
 * Hasta el 2026-09-08 esta pantalla <b>aparecia al entrar al servidor</b> y no se
 * podia cerrar. Las dos cosas eran correctas entonces y estaban razonadas: <i>«un
 * icono mas en el PokePad no habria servido, quien acaba de entrar no sabe que el
 * PokePad existe»</i>, y <i>«cerrarla deja al jugador donde estaba el problema,
 * dentro del servidor y sin nada que hacer»</i>.
 *
 * <p>Hoy la abre {@code OakNpc} con un clic derecho, y eso <b>deshace las dos</b>:
 *
 * <ul>
 *   <li>Ya no hace falta que se abra sola, porque <b>hay algo que mirar</b>: un
 *       señor con bata, un cartel encima y un mensaje al entrar.</li>
 *   <li>&#9888;&#9888; Y <b>YA SE PUEDE CERRAR</b>. Aquella pantalla no se podia
 *       cerrar porque <b>no habia forma de volver</b>; hoy la hay --Oak sigue
 *       ahi-- asi que atrapar al jugador ha dejado de proteger a nadie. Con eso
 *       se va de golpe toda la familia de fallos de «me quede encerrado».</li>
 * </ul>
 *
 * <h2>&#9888;&#9888; LA REJILLA SIGNIFICA ALGO: FILA = REGION, COLUMNA = TIPO</h2>
 *
 * Seis tarjetas puestas en fila serian seis tarjetas. Puestas asi, la pantalla
 * <b>ya ha explicado el juego</b> antes de que nadie lea una palabra: arriba
 * Kanto, abajo Johto, y las tres columnas son siempre Planta, Fuego y Agua. Es
 * la misma razon por la que cada parada de Viajes tiene su color.
 *
 * <h2>&#9888;&#9888; Y ELEGIR PIDE CONFIRMACION</h2>
 *
 * Peticion del usuario, y ademas es la unica decision permanente que toma un
 * jugador en su primer minuto de partida. El velo de confirmacion se dibuja
 * DESPUES de {@code ctx.draw()} y es OPACO: las dos cosas hacen falta, y la
 * primera no es evidente --{@code DrawContext} amontona el texto en una capa que
 * se vuelca la ultima, asi que un relleno pedido despues acaba DEBAJO de las
 * letras de las tarjetas--. Es la leccion del panel de ayuda del Pase.
 */
public class InicialScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;

    private static final int COLS = 3, FILAS = 2;
    private static final int MARGEN = 14, AIRE = 12;

    // ---- la paleta ---------------------------------------------------------
    //
    // ⚠⚠ ES OSCURA, Y ES UN CAMBIO DELIBERADO. La version anterior pintaba
    //    celdas CLARAS sobre el chasis claro y le ponia al texto un contorno de
    //    cuatro copias desplazadas para que se leyera. Eso es justo lo que
    //    CLAUDE.md tiene escrito que EMPASTA en textos pequeños. Sobre un fondo
    //    oscuro el contorno sobra: el contraste lo da el fondo.

    private static final int FONDO = 0xFF0E1526;
    private static final int CARTA = 0xFF17223C;
    private static final int CARTA_ENCIMA = 0xFF1F2E52;
    private static final int BORDE = 0xFF35496A;
    private static final int TEXTO = 0xFFE8EEFA;
    private static final int TEXTO_SUAVE = 0xFF8FA0C8;
    private static final int ORO = 0xFFFFD65C;
    private static final int VERDE = 0xFF57C56A;

    /** Los tres tipos de un inicial, y nada mas. Ver {@link #colorTipo}. */
    private static final int T_PLANTA = 0xFF57C56A;
    private static final int T_FUEGO = 0xFFF2703A;
    private static final int T_AGUA = 0xFF4FA8FF;

    private float k;
    private int ancho, alto, x0, y0;
    private List<Red.OpcionInicial> opciones = List.of();
    private Red.OpcionInicial elegida;

    /** Se pone al pulsar ELEGIR: el velo de «¿seguro?». */
    private boolean confirmando;
    /** Se pone al confirmar: impide mandar dos veces por doble clic. */
    private boolean enviado;
    /** Cuando se pulso, para no esperar eternamente. */
    private long pulsadoEn;
    private boolean falloEntrega;

    /** Para las animaciones. Se pone en {@link #init}. */
    private long abiertoEn;

    /**
     * Cuando el servidor confirmo la entrega.
     *
     * <p>&#9888;&#9888; SIRVE PARA NO CERRAR DE GOLPE. El servidor contesta en
     * decimas, asi que sin esto la celebracion --el aro, los destellos y el
     * «¡ES TUYO!»-- duraba literalmente un fotograma y la pantalla se evaporaba.
     * Elegir el primer Pokemon es el unico momento memorable del primer minuto
     * de partida: merece segundo y medio.
     */
    private long confirmadoEn;

    /** Cuanto dura la celebracion antes de cerrar. */
    private static final long CELEBRACION_MS = 1800;

    /** Separacion entre VOLVER y CONFIRMAR. */
    private static final int SEP_BOTON = 40;

    private final Efectos.Chispas chispas = new Efectos.Chispas();

    /**
     * Lo que se espera a una respuesta antes de rendirse.
     *
     * <p>&#9888;&#9888; ESTO EXISTE PORQUE LA PANTALLA DEJO A UN JUGADOR
     * ATRAPADO: {@code conceder} es asincrono, se le preguntaba antes de tiempo y
     * aqui se quedaba «ENTREGANDO…» para siempre.
     *
     * <p>La causa esta arreglada y aun asi esto se queda, aunque hoy la pantalla
     * ya se pueda cerrar: <b>un boton que no responde y no dice por que sigue
     * siendo un callejon</b>. El fallo de entonces era mio, el siguiente puede ser
     * un corte de red, y desde dentro se ven igual.
     */
    private static final long ESPERA_MAX_MS = 6000;

    public InicialScreen() {
        super(Text.translatable("pokepad.lunaeternal.inicial.titulo"));
    }

    @Override
    protected void init() {
        recalcular();
        abiertoEn = System.currentTimeMillis();
        ClientPlayNetworking.send(new Red.PedirInicial());
    }

    /**
     * &#9888; Delegado en {@link Escalado}: esto era una copia literal en once
     * pantallas y ya habia envejecido en seis variantes distintas.
     */
    private void recalcular() {
        var m = Escalado.aplicar(client, width, height, CHASIS);
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

    /**
     * SE PUEDE CERRAR, y eso es nuevo.
     *
     * <p>&#9888;&#9888;&#9888; Antes devolvia {@code false} salvo si la entrega
     * fallaba, y estaba bien: sin inicial no habia partida <b>y no habia forma de
     * volver a la pantalla</b>. Desde que la puerta es Oak, cerrarla no te deja
     * sin nada -- te deja delante de un señor al que puedes volver a hacer clic.
     * Encerrar a alguien cuando ya existe una salida no protege: molesta.
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return !enviado;
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

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        leerDelServidor();

        // ⚠ Si se pulso y no llega respuesta, se suelta el boton y se dice. El
        //   servidor contesta en los tres caminos --entregado, ya elegido o
        //   fallo-- asi que pasar de aqui significa que algo se perdio.
        if (enviado && !falloEntrega
                && System.currentTimeMillis() - pulsadoEn > ESPERA_MAX_MS) {
            enviado = false;
            falloEntrega = true;
        }

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);

        // ⚠ PRIMERA PASADA: TODO lo plano. Mezclar 2D y 3D deja el orden al azar
        //   y los modelos titilan. Es la regla de las 2 pasadas de dibujado.md.
        // ⚠⚠⚠ LA REJILLA NO SE TAPA: SE DEJA DE DIBUJAR. La primera version
        //    pintaba un velo opaco encima de TODA la pantalla, y la captura del
        //    usuario lo enseño como lo que era: el PokePad desaparecido, el panel
        //    flotando en negro y LOS SEIS POKEMON DIBUJADOS POR ENCIMA del velo
        //    y del texto.
        //    ⚠⚠ Y no era el orden de mis llamadas. Los modelos de Cobblemon NO
        //       van en el lote de `DrawContext`: salen por su propio
        //       `VertexConsumerProvider`, que se vuelca AL FINAL del fotograma.
        //       TAPAR UN MODELO CON UN `fill` ES IMPOSIBLE por definicion, se
        //       pida cuando se pida -- la unica forma de que no se vea es NO
        //       PINTARLO.
        boolean tapado = confirmando || enviado;

        dibujarPanel(ctx, rx, ry);
        if (tapado) {
            dibujarTapa(ctx, rx, ry);
        } else {
            dibujarRejilla(ctx, rx, ry, delta);
        }

        ctx.draw();

        // SEGUNDA PASADA: solo modelos.
        dibujarModelos(ctx, delta, rx, ry, tapado);
    }

    private void leerDelServidor() {
        Red.Iniciales i = EstadoCliente.iniciales();
        if (i == null) {
            return;
        }
        // ⚠ SI YA ELIGIO, SE CIERRA SOLA. Es lo que hace que al pulsar no haga
        //   falta adivinar si funciono: se espera a que el servidor lo confirme.
        if (i.yaEligio()) {
            // ⚠⚠ NO SE CIERRA EN EL ACTO: se apunta el instante y se deja correr
            //    la celebracion. El servidor contesta en decimas, y cerrar ahi
            //    mismo se lleva por delante lo unico que hace que parezca que ha
            //    pasado algo.
            if (confirmadoEn == 0) {
                confirmadoEn = System.currentTimeMillis();
                celebrar();
            }
            if (client != null
                    && System.currentTimeMillis() - confirmadoEn > CELEBRACION_MS) {
                client.setScreen(null);
            }
            return;
        }
        if (opciones != i.opciones()) {
            opciones = i.opciones();
            if (elegida == null) {
                elegida = opciones.isEmpty() ? null : opciones.get(0);
            }
        }
    }

    // ---- el panel de la izquierda ------------------------------------------

    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        ctx.fill(px(PANEL_X), py(PANEL_Y),
                px(PANEL_X + PANEL_W), py(PANEL_Y + PANEL_H), FONDO);
        marco(ctx, px(PANEL_X), py(PANEL_Y), pl(PANEL_W), pl(PANEL_H),
                BORDE, Math.max(1, pl(2)));

        int cx = PANEL_X + PANEL_W / 2;

        texto(ctx, Text.literal("PROFESOR OAK"), cx, PANEL_Y + 22, 20, ORO, true);
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicial.titulo"),
                cx, PANEL_Y + 48, 27, TEXTO, true);

        // La frase de Oak, o el aviso si algo fallo.
        texto(ctx, Text.translatable(falloEntrega
                        ? "pokepad.lunaeternal.inicial.fallo"
                        : "pokepad.lunaeternal.inicial.aviso"),
                cx, PANEL_Y + 84, 16,
                falloEntrega ? 0xFFE06060 : TEXTO_SUAVE, true);

        if (elegida == null) {
            return;
        }

        // El hueco del modelo grande: entre el titulo y la ficha. Se reserva
        // aqui y se pinta en la segunda pasada.
        int hueco = PANEL_Y + 112;
        ctx.fill(px(PANEL_X + 18), py(hueco),
                px(PANEL_X + PANEL_W - 18), py(hueco + 250), 0xFF0A1020);
        int tipo = colorTipo(elegida.tipo());
        Efectos.marcoVivo(ctx, px(PANEL_X + 18), py(hueco),
                pl(PANEL_W - 36), pl(250), Math.max(1, pl(2)),
                BORDE, tipo, Efectos.rampa(4200));

        int y = PANEL_Y + 386;
        texto(ctx, Text.literal(elegida.nombre()), cx, y, 34, ORO, true);
        texto(ctx, Text.literal(elegida.tipo()), cx, y + 42, 20, tipo, true);
        texto(ctx, Text.literal(elegida.region()), cx, y + 68, 17, TEXTO_SUAVE, true);

        y += 100;
        separador(ctx, y - 12);
        for (String linea : partir(elegida.consejo(), PANEL_W - 44, 17)) {
            texto(ctx, Text.literal(linea), cx, y, 17, 0xFFC9D2E6, true);
            y += 21;
        }

        dibujarBoton(ctx, rx, ry);
    }

    private void dibujarBoton(DrawContext ctx, int rx, int ry) {
        int bx = PANEL_X + 34, by = PANEL_Y + PANEL_H - 78, bw = PANEL_W - 68, bh = 50;
        boolean encima = !enviado && dentro(rx, ry, px(bx), py(by), pl(bw), pl(bh));

        if (!enviado) {
            // Un latido lento: es la accion de la pantalla y tiene que llamar.
            Efectos.halo(ctx, px(bx), py(by), pl(bw), pl(bh),
                    Math.max(2, pl(6)), ORO, 0.25f + 0.25f * Efectos.pulso(2000));
        }
        ctx.fill(px(bx), py(by), px(bx + bw), py(by + bh),
                enviado ? 0xFF3A4358 : (encima ? 0xFFFFE08A : 0xFFE8A317));
        marco(ctx, px(bx), py(by), pl(bw), pl(bh), 0xFF8A5C00, Math.max(1, pl(2)));
        texto(ctx, Text.translatable(enviado
                        ? "pokepad.lunaeternal.inicial.enviando"
                        : "pokepad.lunaeternal.inicial.elegir"),
                PANEL_X + PANEL_W / 2, by + 15, 24,
                enviado ? 0xFFAAB4C8 : 0xFF2A1C00, true);
    }

    // ---- la rejilla --------------------------------------------------------

    private void dibujarRejilla(DrawContext ctx, int rx, int ry, float delta) {
        int[] c = celda();
        // La entrada: las tarjetas caen una detras de otra. `rebote` da el
        // pequeño rebase del final, que es lo que lo hace parecer vivo.
        long t = System.currentTimeMillis() - abiertoEn;

        for (int i = 0; i < opciones.size() && i < COLS * FILAS; i++) {
            double avance = Math.min(1.0, Math.max(0.0, (t - i * 70) / 420.0));
            int caida = (int) Math.round((1 - Efectos.rebote(avance)) * 40);

            int ax = c[0] + (i % COLS) * (c[2] + AIRE);
            int ay = c[1] + (i / COLS) * (c[3] + AIRE) - caida;
            var op = opciones.get(i);
            boolean encima = dentro(rx, ry, px(ax), py(ay), pl(c[2]), pl(c[3]));
            boolean puesta = elegida != null && elegida.especie().equals(op.especie());
            int tipo = colorTipo(op.tipo());

            if (puesta) {
                Efectos.halo(ctx, px(ax), py(ay), pl(c[2]), pl(c[3]),
                        Math.max(2, pl(7)), tipo, 0.55f);
            }
            ctx.fill(px(ax), py(ay), px(ax + c[2]), py(ay + c[3]),
                    encima || puesta ? CARTA_ENCIMA : CARTA);

            if (puesta) {
                Efectos.marcoVivo(ctx, px(ax), py(ay), pl(c[2]), pl(c[3]),
                        Math.max(1, pl(3)), BORDE, tipo, Efectos.rampa(2600));
            } else {
                marco(ctx, px(ax), py(ay), pl(c[2]), pl(c[3]),
                        encima ? tipo : BORDE, Math.max(1, pl(encima ? 3 : 2)));
            }

            // La cinta del tipo, arriba: es lo que hace que la columna se lea
            // como una columna sin tener que leer los nombres.
            ctx.fill(px(ax), py(ay), px(ax + c[2]), py(ay) + pl(5), tipo);

            texto(ctx, Text.literal(op.nombre()), ax + c[2] / 2, ay + c[3] - 50, 22,
                    puesta ? ORO : TEXTO, true);
            texto(ctx, Text.literal(op.tipo()), ax + c[2] / 2, ay + c[3] - 26, 16,
                    tipo, true);
        }
    }

    /**
     * Segunda pasada: solo modelos. Ni un rectangulo ni una letra aqui.
     *
     * @param tapado si la rejilla esta cubierta. Entonces sus seis modelos
     *               <b>no se dibujan</b>, porque taparlos es imposible
     */
    private void dibujarModelos(DrawContext ctx, float delta, int rx, int ry,
                                boolean tapado) {
        if (elegida != null) {
            // ⚠ La clave lleva el prefijo `panel:` para que NO comparta estado de
            //   animacion con la celda del mismo Pokemon: `drawProfilePokemon`
            //   MUTA el cuaternion, asi que dos sitios con la misma clave se
            //   pisan la orientacion y titilan. Es la regla 6 de dibujado.md.
            var id = Identifier.tryParse("cobblemon:" + elegida.especie());
            if (id != null) {
                // ⚠⚠ EL 0,30 DEJABA AL POKEMON SENTADO EN EL SUELO DE LA CAJA.
                //    `origenY` es la fraccion de la caja donde cae el ORIGEN del
                //    modelo, y el modelo CRECE HACIA ABAJO desde ahi: con 0,30 y
                //    una caja de 232 el origen caia a 70, y los ciento y pico de
                //    Squirtle acababan pegados al borde de abajo. Calibrado
                //    contra la captura del usuario, no deducido.
                Mascota3D.dibujarEspecie(ctx, id, "panel:" + elegida.especie(), "",
                        px(PANEL_X + 26), py(PANEL_Y + 116),
                        pl(PANEL_W - 52), pl(242), 0.12f, delta, true);
            }
        }
        if (tapado) {
            return;
        }
        int[] c = celda();
        long t = System.currentTimeMillis() - abiertoEn;
        for (int i = 0; i < opciones.size() && i < COLS * FILAS; i++) {
            double avance = Math.min(1.0, Math.max(0.0, (t - i * 70) / 420.0));
            int caida = (int) Math.round((1 - Efectos.rebote(avance)) * 40);
            int ax = c[0] + (i % COLS) * (c[2] + AIRE);
            int ay = c[1] + (i / COLS) * (c[3] + AIRE) - caida;
            var op = opciones.get(i);
            var id = Identifier.tryParse("cobblemon:" + op.especie());
            if (id == null) {
                continue;
            }
            boolean encima = dentro(rx, ry, px(ax), py(ay), pl(c[2]), pl(c[3]));
            boolean puesta = elegida != null && elegida.especie().equals(op.especie());
            Mascota3D.dibujarEspecie(ctx, id, "celda:" + op.especie(), "",
                    px(ax + 8), py(ay + 10), pl(c[2] - 16), pl(c[3] - 68),
                    0.06f, delta, encima || puesta);
        }
    }

    // ---- lo que ocupa el hueco de la rejilla -------------------------------

    /**
     * LA CAJA DE LOS DOS BOTONES, en un solo sitio.
     *
     * <p>&#9888;&#9888; La dibuja {@link #dibujarTapa} y la lee
     * {@link #mouseClicked}. Escrita dos veces, mover el panel deja el boton
     * <b>pintado en un sitio y respondiendo en otro</b> -- que es exactamente el
     * fallo de la rejilla del PokePad, y no da ningun error.
     *
     * @return {x, y, ancho, alto} del boton IZQUIERDO. El derecho va a
     *         {@code x + ancho + SEP_BOTON}
     */
    private static int[] botonesTapa() {
        int bw = 260, bh = 58;
        int cx = PANT_X + PANT_W / 2;
        return new int[] { cx - SEP_BOTON / 2 - bw, PANT_Y + PANT_H - 112, bw, bh };
    }

    /**
     * TAPA LA REJILLA Y PREGUNTA, DENTRO DEL CHASIS.
     *
     * <p>&#9888;&#9888;&#9888; LA PRIMERA VERSION TAPABA LA PANTALLA ENTERA, y en
     * la captura del usuario se veia como una averia: el PokePad desaparecido, un
     * panel flotando en negro y <b>los seis Pokemon dibujados por encima</b>. Hoy
     * el velo ocupa <b>solo el hueco de la rejilla</b>, y eso ademas es mejor
     * diseño que lo de antes: <b>el panel de la izquierda sigue enseñando al
     * elegido en 3D</b>, asi que ves lo que confirmas mientras lo confirmas -- y
     * la confirmacion no tiene que repetir el nombre en grande.
     */
    private void dibujarTapa(DrawContext ctx, int rx, int ry) {
        ctx.fill(px(PANT_X), py(PANT_Y), px(PANT_X + PANT_W), py(PANT_Y + PANT_H),
                FONDO);
        int tipo = elegida == null ? ORO : colorTipo(elegida.tipo());
        Efectos.marcoVivo(ctx, px(PANT_X), py(PANT_Y), pl(PANT_W), pl(PANT_H),
                Math.max(1, pl(3)), BORDE, enviado ? VERDE : tipo,
                Efectos.rampa(enviado ? 1200 : 3400));

        int cx = PANT_X + PANT_W / 2;

        if (enviado) {
            dibujarCelebracion(ctx, cx, tipo);
            return;
        }

        texto(ctx, Text.translatable("pokepad.lunaeternal.inicial.confirmar_titulo"),
                cx, PANT_Y + 96, 38, ORO, true);
        if (elegida != null) {
            texto(ctx, Text.literal(elegida.nombre()), cx, PANT_Y + 160, 50,
                    TEXTO, true);
            texto(ctx, Text.literal(elegida.tipo()), cx, PANT_Y + 224, 24, tipo, true);
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicial.confirmar_aviso"),
                cx, PANT_Y + 280, 19, TEXTO_SUAVE, true);

        int[] b = botonesTapa();
        int ax1 = b[0], by = b[1], bw = b[2], bh = b[3];
        int ax2 = ax1 + bw + SEP_BOTON;

        boolean e1 = dentro(rx, ry, px(ax1), py(by), pl(bw), pl(bh));
        ctx.fill(px(ax1), py(by), px(ax1 + bw), py(by + bh),
                e1 ? 0xFF2A3550 : 0xFF1B2438);
        marco(ctx, px(ax1), py(by), pl(bw), pl(bh), BORDE, Math.max(1, pl(2)));
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicial.volver"),
                ax1 + bw / 2, by + 18, 24, TEXTO_SUAVE, true);

        boolean e2 = dentro(rx, ry, px(ax2), py(by), pl(bw), pl(bh));
        Efectos.halo(ctx, px(ax2), py(by), pl(bw), pl(bh), Math.max(2, pl(6)),
                VERDE, 0.30f + 0.22f * Efectos.pulso(1500));
        ctx.fill(px(ax2), py(by), px(ax2 + bw), py(by + bh),
                e2 ? 0xFF6FE08A : 0xFF3FA85C);
        marco(ctx, px(ax2), py(by), pl(bw), pl(bh), 0xFF1E5E33, Math.max(1, pl(2)));
        texto(ctx, Text.translatable("pokepad.lunaeternal.inicial.confirmar"),
                ax2 + bw / 2, by + 18, 24, 0xFF06210F, true);
    }

    /**
     * EL MOMENTO. Dura {@link #CELEBRACION_MS} y despues la pantalla se cierra.
     *
     * <p>&#9888;&#9888; Existe porque <b>elegir el primer Pokemon es el unico
     * momento memorable del primer minuto de partida</b> y se pasaba en un
     * fotograma. Un segundo y medio no molesta a nadie y es lo unico que hace
     * que parezca que ha pasado algo.
     *
     * <p>&#9888; Todo sale de {@code Efectos} -- aro, destellos y chispas -- y
     * ni un {@code fill} a mano: es la misma caja de herramientas del Pase, que
     * es lo que hace que las dos pantallas se parezcan sin copiarse codigo.
     */
    private void dibujarCelebracion(DrawContext ctx, int cx, int tipo) {
        long t = System.currentTimeMillis() - pulsadoEn;
        float abre = (float) Efectos.suave(Math.min(1.0, t / 520.0));

        int cy = PANT_Y + 200;
        int r = Math.round(130 * abre);

        // ⚠ `arco` recibe QUE FRACCION del circulo pinta, no una fase: se anima
        //   haciendo crecer la fraccion, no girandola. Dos radios a dos
        //   velocidades se leen como energia; uno solo parece un cargador.
        Efectos.arco(ctx, px(cx), py(cy), pl(r), Math.max(2, pl(6)),
                abre, Efectos.conAlfa(tipo, 0.9f));
        Efectos.arco(ctx, px(cx), py(cy), pl(r + 26), Math.max(1, pl(2)),
                Efectos.rampa(2400), Efectos.conAlfa(ORO, 0.5f));

        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i + Efectos.rampa(3200) * Math.PI * 2;
            int dx = (int) Math.round(Math.cos(a) * (r + 44));
            int dy = (int) Math.round(Math.sin(a) * (r + 44));
            Efectos.destello(ctx, px(cx) + pl(dx), py(cy) + pl(dy),
                    Math.max(3, pl(11)), Efectos.conAlfa(ORO, 0.9f * abre));
        }

        texto(ctx, Text.translatable("pokepad.lunaeternal.inicial.tuyo"),
                cx, PANT_Y + 336, 46, ORO, true);
        if (elegida != null) {
            texto(ctx, Text.literal(elegida.nombre()), cx, PANT_Y + 396, 30,
                    TEXTO, true);
        }
        chispas.dibujar(ctx);
    }

    /**
     * El sonido y las chispas del momento de la entrega.
     *
     * <p>&#9888; La fanfarria es la de los LOGROS de vainilla. Es la que el
     * jugador ya asocia con «has conseguido algo», asi que no hay que inventarse
     * ninguna -- y llega a todo el mundo, tenga o no el resource pack.
     */
    private void celebrar() {
        chispas.soltar(width / 2, height / 2, 90, ORO, 1.6);
        if (client != null && client.player != null) {
            client.player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    net.minecraft.sound.SoundCategory.MASTER, 0.9f, 1.0f);
            client.player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP,
                    net.minecraft.sound.SoundCategory.MASTER, 0.6f, 1.2f);
        }
    }

    /** {x, y, ancho, alto} de la primera celda. Todas miden lo mismo. */
    private int[] celda() {
        int anchoUtil = PANT_W - 2 * MARGEN;
        int altoUtil = PANT_H - 2 * MARGEN;
        int cw = (anchoUtil - (COLS - 1) * AIRE) / COLS;
        int ch = (altoUtil - (FILAS - 1) * AIRE) / FILAS;
        return new int[] { PANT_X + MARGEN, PANT_Y + MARGEN, cw, ch };
    }

    /**
     * EL COLOR DE UN TIPO.
     *
     * <p>&#9888; Se mira el texto que manda el servidor y no una tabla de
     * especies: los seis iniciales son siempre Planta, Fuego y Agua, y una tabla
     * por especie seria una lista paralela que hay que mantener. Lo que no
     * reconozca se pinta con el borde de siempre, que es un color valido y no un
     * hueco.
     */
    private static int colorTipo(String tipo) {
        if (tipo == null) {
            return BORDE;
        }
        String t = tipo.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("planta") || t.contains("grass")) {
            return T_PLANTA;
        }
        if (t.contains("fuego") || t.contains("fire")) {
            return T_FUEGO;
        }
        if (t.contains("agua") || t.contains("water")) {
            return T_AGUA;
        }
        return BORDE;
    }

    // ---- interaccion -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0 || enviado) {
            return super.mouseClicked(mx, my, boton);
        }
        int rx = (int) mx, ry = (int) my;

        // ⚠ La tapa se atiende PRIMERO y se traga el clic pase lo que pase: si
        //   los botones de debajo siguieran respondiendo, pulsar «¿seguro?»
        //   encima de una tarjeta cambiaria la eleccion sin que se vea.
        if (confirmando) {
            int[] b = botonesTapa();
            int ax1 = b[0], by = b[1], bw = b[2], bh = b[3];
            int ax2 = ax1 + bw + SEP_BOTON;

            if (dentro(rx, ry, px(ax1), py(by), pl(bw), pl(bh))) {
                confirmando = false;
                sonar(false);
            } else if (dentro(rx, ry, px(ax2), py(by), pl(bw), pl(bh))
                    && elegida != null) {
                // ⚠ NO SE CIERRA AQUI. Se manda y se espera: el servidor entrega
                //   y contesta «ya elegiste», y `leerDelServidor` cierra al
                //   verlo -- despues de la celebracion. Cerrar al pulsar dejaria
                //   sin Pokemon y sin pantalla a quien se encuentre un fallo.
                enviado = true;
                confirmando = false;
                falloEntrega = false;
                pulsadoEn = System.currentTimeMillis();
                sonar(true);
                ClientPlayNetworking.send(new Red.ElegirInicial(elegida.especie()));
            }
            return true;
        }

        int bx = PANEL_X + 34, by = PANEL_Y + PANEL_H - 78, bw = PANEL_W - 68, bh = 50;
        if (elegida != null && dentro(rx, ry, px(bx), py(by), pl(bw), pl(bh))) {
            confirmando = true;
            sonar(true);
            return true;
        }

        int[] c = celda();
        for (int i = 0; i < opciones.size() && i < COLS * FILAS; i++) {
            int ax = c[0] + (i % COLS) * (c[2] + AIRE);
            int ay = c[1] + (i / COLS) * (c[3] + AIRE);
            if (dentro(rx, ry, px(ax), py(ay), pl(c[2]), pl(c[3]))) {
                elegida = opciones.get(i);
                // ⚠ El tono sube con la COLUMNA: planta grave, fuego medio, agua
                //   aguda. No es adorno -- recorrer una fila suena a escala, y eso
                //   dice sin palabras que las tres columnas son la misma familia.
                sonarNota(0.85f + 0.15f * (i % COLS));
                return true;
            }
        }
        return super.mouseClicked(mx, my, boton);
    }

    /** Una nota de campana, para recorrer la rejilla. */
    private void sonarNota(float tono) {
        if (client != null && client.player != null) {
            client.player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                    net.minecraft.sound.SoundCategory.MASTER, 0.5f, tono);
        }
    }

    private void sonar(boolean lleva) {
        if (client != null && client.player != null) {
            // ⚠ `playSoundToPlayer` y no `playSound`: en un jugador, el primer
            //   parametro de `playSound` es `except` -- o sea que el unico que NO
            //   lo oiria seria el que pulsa.
            client.player.playSoundToPlayer(lleva
                    ? SoundEvents.UI_BUTTON_CLICK.value()
                    : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                    net.minecraft.sound.SoundCategory.MASTER, 0.6f, 1.0f);
        }
    }

    // ---- utilidades --------------------------------------------------------

    private List<String> partir(String texto, int anchoArte, int altoArte) {
        List<String> salida = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (Math.round(textRenderer.getWidth(prueba) * altoArte
                    / (float) textRenderer.fontHeight) > anchoArte && !actual.isEmpty()) {
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

    private void separador(DrawContext ctx, int artY) {
        ctx.fill(px(PANEL_X + 28), py(artY), px(PANEL_X + PANEL_W - 28),
                py(artY) + Math.max(1, pl(2)), BORDE);
    }

    /**
     * &#9888;&#9888;&#9888; LA SOMBRA VA APAGADA, Y AQUI ES DONDE SE VE POR QUE.
     *
     * <p>La matriz esta escalada: un texto de 24 px de arte se dibuja con
     * {@code escala} de 2 o mas, y Minecraft desplaza la sombra <b>una unidad de
     * fuente</b> -- que a esa escala son dos o tres pixeles de pantalla. Deja de
     * ser sombra y pasa a ser un contorno negro pegado a cada letra.
     *
     * <p>&#9888;&#9888; Y por eso tampoco hay contorno manual: el de cuatro
     * copias desplazadas que tenia la version anterior <b>empastaba</b> en los
     * textos pequeños. Sobre este fondo oscuro no hace falta ninguno de los dos.
     */
    private void texto(DrawContext ctx, Text linea, int cx, int arriba, int alto,
                       int color, boolean centrado) {
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

    /** &#9888; `enableBlend()` a mano: regla 1 de dibujado.md. */
    private static void dibujarTextura(DrawContext ctx, Identifier tex,
                                       int x, int y, int w, int h, int natW, int natH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
    }
}
