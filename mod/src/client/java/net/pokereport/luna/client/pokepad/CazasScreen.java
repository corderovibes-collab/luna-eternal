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

/**
 * CAZAS Y CRIANZA.
 *
 * <h2>Dos pestañas y tres objetivos en cada una</h2>
 *
 * <pre>
 *   CAZA     captura N de una especie        ★ ★★ ★★★
 *   CRIANZA  haz eclosionar un huevo suyo    ★ ★★ ★★★
 * </pre>
 *
 * Las estrellas <b>no son decoración</b>: son la rareza, y de ella salen tanto
 * cuántos hacen falta como cuánto paga. Y no son al azar — cada ciclo tiene
 * siempre uno fácil, uno medio y uno difícil, porque un ciclo de tres difíciles
 * no lo completaría nadie.
 *
 * <h2>⚠ Son las MISMAS para todo el servidor, y rotan cada 24 h</h2>
 *
 * Eso es lo que hace que la gente hable de lo mismo a la vez. Con 12 h —como
 * estaba— cambiaban dos veces al día y quien juega por la tarde nunca veía la
 * del ciclo de noche.
 *
 * <h2>⚠ Solo Kanto y Johto, y sin legendarios</h2>
 *
 * Lo decide el servidor ({@code Especies}), no esta pantalla. Un legendario
 * como objetivo sería una caza imposible, y lo único que enseñaría es a
 * ignorar las cazas.
 *
 * <h2>⚠⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 *
 * La geometría es <b>copia literal</b> de {@code CosmeticosScreen}.
 */
public class CazasScreen extends Screen {

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
    private static final int MARGEN = 12;

    private static final int PEST_W = 130, PEST_H = 36;

    /**
     * ⚠ Tres objetivos por pestaña, así que la fila puede ser GRANDE. Es lo que
     * permite que el Pokémon se vea de verdad en la lista y no como un sello.
     */
    private static final int FILA = 140;

    /**
     * Las cuatro columnas de una fila. Se declaran juntas <b>porque tienen que
     * cuadrar entre ellas</b>: el ancho de cada una es la distancia a la
     * siguiente, así que moverlas de una en una es como se solapan.
     */
    private static final int COL_MODELO = 8, COL_TEXTO = 136;
    private static final int COL_PREMIO = 470, ANCHO_PREMIO = 170;
    private static final int ANCHO_BOTON = 119;

    private static final int FILA_FONDO = 0xFFBFCBE8;
    private static final int FILA_BORDE = 0xFF7C89B4;
    private static final int FILA_SEL = 0xFFFFF0DC;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;
    private static final int TEXTO_OSCURO = 0xFF16203A;
    private static final int TEXTO_SUAVE = 0xFF5A668C;
    /**
     * ⚠⚠⚠ DOS CONTORNOS, UNO POR FONDO.
     *
     * <p>Un contorno sirve para <b>despegar el texto de su fondo</b>, así que
     * tiene que ir en contra de ese fondo. Con una sola constante clara:
     *
     * <pre>
     *   fila CLARA + texto oscuro + halo CLARO   ->  se despega. Bien.
     *   panel OSCURO + texto dorado + halo CLARO ->  BORRÓN. Mal.
     * </pre>
     *
     * <p>Es la lección del chasis v4, otra vez: la decisión correcta —«que se
     * lean»— aplicada sobre un fondo invertido da el resultado contrario.
     */
    private static final int CONTORNO_CLARO = 0xFFF2F6FF;
    private static final int CONTORNO_OSCURO = 0xFF080B12;
    private static final int TEXTO_CONTORNO = CONTORNO_CLARO;
    private static final int SEPARADOR = 0xFF3C4250;
    private static final int ORO = 0xFFFFD65C;
    private static final int VERDE = 0xFF2E9E56;
    private static final int APAGADO = 0xFF6E7899;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private Red.EstadoCazas estado;
    private boolean crianza;
    private int elegido = 0;
    private long pulsado;

    /** Sobre qué fila está el ratón, para el cartelito de la recompensa. */
    private int encima = -1;

    public CazasScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.cazas"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        ClientPlayNetworking.send(new Red.PedirCazas());
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

    private boolean esperando() {
        return pulsado > 0 && System.currentTimeMillis() - pulsado < 1500;
    }

    // ---- datos -------------------------------------------------------------

    /** Los tres de la pestaña puesta, en orden de estrellas. */
    private List<Red.ObjetivoCaza> lista() {
        if (estado == null) {
            return List.of();
        }
        var salida = new ArrayList<Red.ObjetivoCaza>();
        for (var o : estado.objetivos()) {
            if (o.tipo().equals(crianza ? "CRIANZA" : "CAPTURA")) {
                salida.add(o);
            }
        }
        // ⚠ Se ordena AQUI por rareza. El servidor ya los manda ordenados, pero
        //   la estrella es lo que promete la posicion: si un dia cambiara el
        //   ORDER BY, la lista diria ★★★ en la primera fila y ★ en la ultima.
        salida.sort(java.util.Comparator.comparingInt(Red.ObjetivoCaza::rareza));
        return salida;
    }

    private Red.ObjetivoCaza seleccionado() {
        var l = lista();
        return elegido >= 0 && elegido < l.size() ? l.get(elegido) : null;
    }

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        recalcular();
        renderBackground(ctx, rx, ry, delta);
        var nuevo = EstadoCliente.cazas();
        if (nuevo != null && nuevo != estado) {
            estado = nuevo;
            pulsado = 0;
            if (elegido >= lista().size()) {
                elegido = 0;
            }
        }

        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNavegacion(ctx, rx, ry);
        dibujarPanel(ctx, rx, ry);
        dibujarPestanas(ctx, rx, ry);
        dibujarLista(ctx, rx, ry, false);

        // ⚠ DOS PASADAS: todo el 2D, `ctx.draw()`, y solo entonces el 3D.
        //   Mezclados van por lotes distintos y el 2D se pinta ENCIMA de los
        //   modelos. Regla 3 de dibujado.md.
        ctx.draw();
        dibujarLista(ctx, rx, ry, true);
        dibujarRetrato(ctx, delta);
        // El cartelito va EL ULTIMO: tiene que taparlo todo, 3D incluido, y
        // el 3D se pinta después del 2D pase lo que pase.
        dibujarCartel(ctx, rx, ry);
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
            marco(ctx, px(cx) - 2, py(cy) - pl(32) - 2, pl(80) + 4, pl(64) + 4,
                    BORDE_ENCIMA, 2);
        }
    }

    // ---- el panel de la izquierda -----------------------------------------

    private static final int RET_X = PANEL_X + 24, RET_Y = PANEL_Y + NAV_ALTO + 4;
    private static final int RET_W = PANEL_W - 48, RET_H = 226;

    private void dibujarRetrato(DrawContext ctx, float delta) {
        var o = seleccionado();
        if (o == null || o.especie().isEmpty()) {
            return;
        }
        var id = Identifier.tryParse(
                "cobblemon:" + o.especie().toLowerCase(java.util.Locale.ROOT));
        if (id == null) {
            return;
        }
        Mascota3D.dibujarEspecie(ctx, id, "caza:" + o.especie(), "",
                px(RET_X), py(RET_Y), pl(RET_W), pl(RET_H), 0.10f, delta, true);
    }

    private void dibujarPanel(DrawContext ctx, int rx, int ry) {
        ctx.fill(px(RET_X), py(RET_Y), px(RET_X + RET_W), py(RET_Y + RET_H), 0xFF12161F);
        marco(ctx, px(RET_X), py(RET_Y), pl(RET_W), pl(RET_H), 0xFF39415C,
                Math.max(1, pl(2)));

        // --- LA CUENTA ATRAS, justo debajo del retrato (lo pidió el usuario)
        //
        // ⚠ La lleva EL CLIENTE, restando de un instante absoluto. Si el
        //   servidor mandara «faltan N horas», el número se quedaría viejo en
        //   cuanto pasara un minuto y el reloj mentiría hasta reabrir.
        int cy = RET_Y + RET_H + 6;
        ctx.fill(px(RET_X), py(cy), px(RET_X + RET_W), py(cy + 58), 0xFF10141F);
        marco(ctx, px(RET_X), py(cy), pl(RET_W), pl(58), 0xFF4A5578, Math.max(1, pl(2)));
        // ⚠ El rótulo va CLARO, no en `TEXTO_SUAVE`: ese gris azulado está
        //   pensado para las filas claras y sobre casi negro desaparece.
        texto(ctx, Text.translatable("pokepad.lunaeternal.caza.rotan"),
                PANEL_X + PANEL_W / 2, cy + 6, 13, 0xFFA8B4D0, true, 0);
        // ⚠ 30 px y SIN halo. Es el número más importante de la pantalla y era
        //   el que peor se leía: 22 px con halo claro sobre negro es un borrón.
        texto(ctx, Text.literal(queda(estado == null ? 0 : estado.terminaEn())),
                PANEL_X + PANEL_W / 2, cy + 24, 30, ORO, true, CONTORNO_OSCURO);

        var o = seleccionado();
        if (o == null) {
            for (String linea : partir(
                    Text.translatable("pokepad.lunaeternal.caza.elige").getString(),
                    RET_W, 15)) {
                texto(ctx, Text.literal(linea), PANEL_X + PANEL_W / 2, cy + 80, 15,
                        TEXTO_SUAVE, true, false);
            }
            return;
        }

        int y = cy + 74;
        for (Text linea : partirLim(especieEs(o.especie()).getString(), RET_W, 26, 2)) {
            texto(ctx, linea, PANEL_X + PANEL_W / 2, y, 26, 0xFFFFFFFF, true,
                    CONTORNO_OSCURO);
            y += 30;
        }
        // ⚠ 22 y no 16: en el panel las estrellas son lo que dice de qué nivel
        //   es el objetivo, y a 16 sobre negro no se distinguía cuáles estaban
        //   encendidas.
        estrellas(ctx, PANEL_X + PANEL_W / 2, y + 2, o.rareza(), 22, true);
        y += 34;

        // --- progreso
        progreso(ctx, PANEL_X + 30, y, PANEL_W - 60, 20, o);
        y += 32;

        separador(ctx, y);
        y += 12;
        texto(ctx, Text.translatable("pokepad.lunaeternal.caza.recompensa"),
                PANEL_X + PANEL_W / 2, y, 14, 0xFFA8B4D0, true, 0);
        y += 24;
        y = premio(ctx, o, PANEL_X + 30, y, PANEL_W - 60, false);

        boolean puede = o.completo() && !o.cobrado() && !esperando();
        boton(ctx, rx, ry, PANEL_X + 30, PANEL_Y + PANEL_H - 72, PANEL_W - 60, 56,
                Text.translatable(o.cobrado()
                        ? "pokepad.lunaeternal.caza.cobrado"
                        : "pokepad.lunaeternal.caza.cobrar"),
                puede, VERDE);
    }

    /**
     * El premio, en una o varias líneas.
     *
     * <p>⚠ El objeto se dibuja con su icono y <b>su nombre lo pone el cliente</b>
     * a partir del identificador: el servidor solo tiene {@code en_us}. Es la
     * regla de idioma de CLAUDE.md.
     */
    /**
     * El premio, en varias líneas.
     *
     * <p>⚠ Va SIEMPRE sobre fondo oscuro (el panel o el cartelito), así que el
     * contorno es oscuro. Y los colores son los vivos, no los del texto de las
     * filas: un gris azulado pensado para fondo claro sobre negro desaparece.
     */
    private int premio(DrawContext ctx, Red.ObjetivoCaza o, int ax, int y, int aw,
                       boolean compacto) {
        int alto = compacto ? 15 : 18;
        if (o.dolar() > 0) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.caza.plata",
                            String.format("%,d", o.dolar())),
                    ax, y, alto, ORO, false, CONTORNO_OSCURO);
            y += alto + 7;
        }
        if (o.marca() > 0) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.caza.marcas",
                            String.format("%,d", o.marca())),
                    ax, y, alto, 0xFF7FD4FF, false, CONTORNO_OSCURO);
            y += alto + 7;
        }
        for (var e : objetos(o)) {
            texto(ctx, recortar(nombreDe(e.id()).getString() + " x" + e.cantidad(),
                            aw - 26, alto),
                    ax + 26, y, alto, 0xFFFFFFFF, false, CONTORNO_OSCURO);
            y += alto + 7;
        }
        return y;
    }

    /** Un objeto del premio y cuántos. */
    private record Suelto(String id, int cantidad) {}

    /**
     * Los objetos de un premio, ya filtrados.
     *
     * <p>⚠ Existe para que dibujar y contar miren <b>la misma lista</b>. Con la
     * comprobación repetida en cada sitio, basta que uno olvide el segundo
     * objeto para que se enseñe uno y se entreguen dos — o al revés.
     */
    private List<Suelto> objetos(Red.ObjetivoCaza o) {
        var salida = new ArrayList<Suelto>(2);
        if (!o.objeto().isEmpty() && o.cantidad() > 0) {
            salida.add(new Suelto(o.objeto(), o.cantidad()));
        }
        if (!o.objeto2().isEmpty() && o.cantidad2() > 0) {
            salida.add(new Suelto(o.objeto2(), o.cantidad2()));
        }
        return salida;
    }

    /** Los iconos del premio. Van en la pasada de después de `ctx.draw()`. */
    private void premioIconos(DrawContext ctx, Red.ObjetivoCaza o, int ax, int y,
                              int paso) {
        int n = 0;
        for (var e : objetos(o)) {
            objeto(ctx, pila(e.id()), ax, y + n * paso, 18);
            n++;
        }
    }

    private void progreso(DrawContext ctx, int ax, int y, int aw, int ah,
                          Red.ObjetivoCaza o) {
        ctx.fill(px(ax), py(y), px(ax + aw), py(y + ah), 0xFF20283C);
        int lleno = o.necesarios() <= 0 ? 0
                : Math.min(aw, aw * o.hechos() / o.necesarios());
        if (lleno > 0) {
            ctx.fill(px(ax), py(y), px(ax + lleno), py(y + ah),
                    o.completo() ? VERDE : 0xFF4F6FB0);
        }
        marco(ctx, px(ax), py(y), pl(aw), pl(ah), 0xFF39415C, Math.max(1, pl(2)));
        texto(ctx, Text.literal(Math.min(o.hechos(), o.necesarios())
                        + " / " + o.necesarios()),
                ax + aw / 2, y + (ah - 13) / 2, 13, 0xFFFFFFFF, true, false);
    }

    // ---- las pestañas ------------------------------------------------------

    private void dibujarPestanas(DrawContext ctx, int rx, int ry) {
        for (int i = 0; i < 2; i++) {
            boolean act = (i == 1) == crianza;
            int bx = PANT_X + MARGEN + i * (PEST_W + 6);
            boolean enc = dentro(rx, ry, px(bx), py(PANT_Y + MARGEN),
                    pl(PEST_W), pl(PEST_H));
            ctx.fill(px(bx), py(PANT_Y + MARGEN), px(bx + PEST_W),
                    py(PANT_Y + MARGEN + PEST_H),
                    act ? BORDE_ENCIMA : (enc ? 0xFF4F6FB0 : 0xFF2A3145));
            marco(ctx, px(bx), py(PANT_Y + MARGEN), pl(PEST_W), pl(PEST_H),
                    act ? 0xFFFFC46B : 0xFF20283C, Math.max(1, pl(2)));
            texto(ctx, Text.translatable(i == 0
                            ? "pokepad.lunaeternal.caza.p_caza"
                            : "pokepad.lunaeternal.caza.p_crianza"),
                    bx + PEST_W / 2, PANT_Y + MARGEN + 11, 15,
                    act ? 0xFF2A1C00 : 0xFFC9D2E6, true, false);
        }
        // Una frase que diga qué hay que hacer. Sin ella, «CRIANZA» es una
        // palabra: nadie deduce que cuenta al ECLOSIONAR y no al juntar.
        texto(ctx, Text.translatable(crianza
                        ? "pokepad.lunaeternal.caza.ayuda_crianza"
                        : "pokepad.lunaeternal.caza.ayuda_caza"),
                PANT_X + PANT_W - MARGEN - anchoArte(Text.translatable(crianza
                        ? "pokepad.lunaeternal.caza.ayuda_crianza"
                        : "pokepad.lunaeternal.caza.ayuda_caza").getString(), 13),
                PANT_Y + MARGEN + 12, 13, TEXTO_SUAVE, false, false);
    }

    // ---- la lista ----------------------------------------------------------

    private int listaY() {
        return PANT_Y + MARGEN + PEST_H + 12;
    }

    private void dibujarLista(DrawContext ctx, int rx, int ry, boolean tercera) {
        var l = lista();
        if (!tercera) {
            encima = -1;
        }
        if (l.isEmpty()) {
            if (!tercera) {
                texto(ctx, Text.translatable("pokepad.lunaeternal.caza.vacio"),
                        PANT_X + PANT_W / 2, listaY() + 80, 16, TEXTO_SUAVE, true, false);
            }
            return;
        }
        int aw = PANT_W - 2 * MARGEN;
        for (int n = 0; n < l.size(); n++) {
            var o = l.get(n);
            int y = listaY() + n * FILA;
            int ax = PANT_X + MARGEN;

            if (tercera) {
                var id = Identifier.tryParse("cobblemon:"
                        + o.especie().toLowerCase(java.util.Locale.ROOT));
                if (id != null) {
                    Mascota3D.dibujarEspecie(ctx, id, "cazafila:" + o.especie(), "",
                            px(ax + COL_MODELO), py(y + 8), pl(116), pl(116),
                            0.10f, 0f, false);
                }
                premioIconos(ctx, o, ax + COL_PREMIO, y + 68, 22);
                continue;
            }

            boolean sel = n == elegido;
            boolean enc = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA - 8));
            if (enc) {
                encima = n;
            }
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA - 8),
                    sel ? FILA_SEL : (enc ? 0xFFD3DDF3 : FILA_FONDO));
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA - 8),
                    sel ? BORDE_ENCIMA : FILA_BORDE, Math.max(1, pl(2)));

            texto(ctx, recortar(especieEs(o.especie()).getString(),
                            COL_PREMIO - COL_TEXTO - 20, 28),
                    ax + COL_TEXTO, y + 14, 28, TEXTO_OSCURO, false, false);
            estrellas(ctx, ax + COL_TEXTO, y + 54, o.rareza(), 20, false);

            // Progreso, ancho de verdad: es lo que se mira de un vistazo.
            progreso(ctx, ax + COL_TEXTO, y + 88, COL_PREMIO - COL_TEXTO - 30, 24, o);

            // --- el premio, en su columna
            //
            // ⚠ Aquí el fondo es CLARO, así que los colores son los oscuros y
            //   el contorno el claro. Es la misma información que en el panel
            //   pintada al revés, y tiene que ser al revés.
            int pxx = ax + COL_PREMIO;
            texto(ctx, Text.translatable("pokepad.lunaeternal.caza.recompensa"),
                    pxx, y + 10, 12, TEXTO_SUAVE, false, false);
            texto(ctx, recortar(Text.translatable("pokepad.lunaeternal.caza.plata",
                            String.format("%,d", o.dolar())).getString(),
                            ANCHO_PREMIO, 17),
                    pxx, y + 26, 17, 0xFF7A5D00, false, CONTORNO_CLARO);
            texto(ctx, recortar(Text.translatable("pokepad.lunaeternal.caza.marcas",
                            String.format("%,d", o.marca())).getString(),
                            ANCHO_PREMIO, 15),
                    pxx, y + 48, 15, 0xFF1F5B85, false, 0);
            int ny = y + 70;
            for (var e : objetos(o)) {
                texto(ctx, recortar(nombreDe(e.id()).getString()
                                + " x" + e.cantidad(), ANCHO_PREMIO - 24, 14),
                        pxx + 24, ny, 14, TEXTO_OSCURO, false, 0);
                ny += 22;
            }

            // --- estado, pegado a la derecha
            int bx = ax + aw - 8 - ANCHO_BOTON;
            if (o.cobrado()) {
                textoDer(ctx, Text.translatable("pokepad.lunaeternal.caza.cobrado"),
                        ax + aw - 12, y + 52, 18, APAGADO, CONTORNO_CLARO);
            } else if (o.completo()) {
                botonPeq(ctx, rx, ry, bx, y + 44, ANCHO_BOTON, 44,
                        Text.translatable("pokepad.lunaeternal.caza.cobrar"),
                        !esperando(), true);
            } else {
                textoDer(ctx, Text.translatable("pokepad.lunaeternal.caza.en_curso"),
                        ax + aw - 12, y + 56, 15, TEXTO_SUAVE, 0);
            }
        }
    }

    /**
     * Las estrellas de la rareza.
     *
     * <p>⚠ Se dibujan <b>siempre las tres</b>, apagando las que no toquen. Con
     * solo las encendidas, ★★ y ★★★ se distinguen contando — y contar es
     * justo lo que un icono tiene que ahorrarte.
     */
    private void estrellas(DrawContext ctx, int cx, int y, int rareza, int lado,
                           boolean centrado) {
        int total = 3 * lado + 2 * 4;
        int ax = centrado ? cx - total / 2 : cx;
        for (int i = 0; i < 3; i++) {
            Iconos.estrella(ctx, px(ax + i * (lado + 4) + lado / 2), py(y + lado / 2),
                    pl(lado), i < rareza ? ORO : 0x40000000);
        }
    }

    /**
     * El cartelito de la recompensa al pasar el ratón.
     *
     * <p>Lo pidió el usuario: <i>«cuando se le pase el mouse a un pokemon sale
     * la recompensa»</i>.
     *
     * <p>⚠ Se dibuja <b>encima de todo y el último</b>, 3D incluido. Y se
     * coloca hacia dentro cuando está cerca del borde: un cartel que se sale
     * de la pantalla es peor que no tenerlo.
     */
    private void dibujarCartel(DrawContext ctx, int rx, int ry) {
        if (encima < 0) {
            return;
        }
        var l = lista();
        if (encima >= l.size()) {
            return;
        }
        var o = l.get(encima);

        int aw = 260, ah = 146;
        // Coordenadas de arte a partir del ratón.
        int mx = Math.round((rx - x0) / k), my = Math.round((ry - y0) / k);
        int ax = Math.min(mx + 16, PANT_X + PANT_W - aw - 4);
        int ay = Math.min(my + 12, PANT_Y + PANT_H - ah - 4);
        ay = Math.max(ay, PANT_Y + 4);

        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah), 0xF0161B29);
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), ORO, Math.max(2, pl(2)));

        texto(ctx, Text.translatable("pokepad.lunaeternal.caza.recompensa"),
                ax + 12, ay + 10, 14, ORO, false, 0);
        int y = ay + 32;
        y = premio(ctx, o, ax + 12, y, aw - 24, true);
        // ⚠ Los iconos van AQUI aunque el cartel se dibuje despues del 3D: el
        //   cartel es lo ultimo de todo, asi que ya no hay nada que le pise.
        premioIconos(ctx, o, ax + 12, ay + 32 + 2 * 22, 22);
        if (!o.completo()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.caza.faltan",
                            o.necesarios() - Math.min(o.hechos(), o.necesarios())),
                    ax + 12, ay + ah - 20, 13, 0xFFA8B4D0, false, 0);
        }
    }

    /**
     * Lo que falta para la rotación.
     *
     * <p>⚠ Un ciclo caducado <b>no se dice «hace 3 h»</b>: se dice que está
     * rotando. Lo crea la primera persona que mire, así que ese estado dura
     * segundos — pero mientras dura, un número negativo asustaría.
     */
    private static String queda(long cuando) {
        if (cuando <= 0) {
            return "—";
        }
        long ms = cuando - System.currentTimeMillis();
        if (ms <= 0) {
            return Text.translatable("pokepad.lunaeternal.caza.rotando").getString();
        }
        long horas = ms / 3_600_000L;
        long min = (ms % 3_600_000L) / 60_000L;
        if (horas > 0) {
            return horas + "h " + min + "m";
        }
        long seg = (ms % 60_000L) / 1000L;
        return min + "m " + seg + "s";
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
        int cx = PANEL_X + PANEL_W - 18 - 80;
        if (dentro(rx, ry, px(cx), py(cy) - pl(32), pl(80), pl(64))) {
            sonar();
            close();
            return true;
        }

        for (int i = 0; i < 2; i++) {
            int bx = PANT_X + MARGEN + i * (PEST_W + 6);
            if (dentro(rx, ry, px(bx), py(PANT_Y + MARGEN), pl(PEST_W), pl(PEST_H))) {
                if ((i == 1) != crianza) {
                    crianza = i == 1;
                    elegido = 0;
                    sonar();
                }
                return true;
            }
        }

        // El botón del panel.
        var sel = seleccionado();
        if (sel != null && dentro(rx, ry, px(PANEL_X + 30), py(PANEL_Y + PANEL_H - 72),
                pl(PANEL_W - 60), pl(56))) {
            cobrar(sel);
            return true;
        }

        var l = lista();
        int aw = PANT_W - 2 * MARGEN;
        for (int n = 0; n < l.size(); n++) {
            int y = listaY() + n * FILA;
            if (!dentro(rx, ry, px(PANT_X + MARGEN), py(y), pl(aw), pl(FILA - 8))) {
                continue;
            }
            elegido = n;
            sonar();
            // El botón de la fila hace lo mismo que el del panel: es un atajo,
            // no una segunda función.
            if (dentro(rx, ry, px(PANT_X + MARGEN + aw - 8 - ANCHO_BOTON),
                    py(y + 44), pl(ANCHO_BOTON), pl(44))) {
                cobrar(l.get(n));
            }
            return true;
        }
        return super.mouseClicked(mx, my, boton);
    }

    private void cobrar(Red.ObjetivoCaza o) {
        if (!o.completo() || o.cobrado() || esperando()) {
            return;
        }
        sonar();
        pulsado = System.currentTimeMillis();
        // ⚠ Solo viaja el identificador. El premio lo saca el servidor de su
        //   fila, y comprueba ahí que esté completo y sin cobrar (P6).
        ClientPlayNetworking.send(new Red.AccionCaza(o.id()));
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        if (tecla == 256) {
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

    private ItemStack pila(String id) {
        var item = Registries.ITEM.get(Identifier.tryParse(id));
        var p = new ItemStack(item);
        return p.isEmpty() ? new ItemStack(net.minecraft.item.Items.BARRIER) : p;
    }

    /** ⚠ El nombre lo pone el CLIENTE: el servidor solo tiene `en_us`. */
    private Text nombreDe(String itemId) {
        return pila(itemId).getName();
    }

    private static Text especieEs(String especie) {
        return Text.translatable("cobblemon.species."
                + especie.toLowerCase(java.util.Locale.ROOT)
                        .replace("-", "").replace(" ", "").replace("'", "")
                + ".name");
    }

    private void objeto(DrawContext ctx, ItemStack p, int ax, int ay, int altoArte) {
        float escala = altoArte * k / 16f;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(px(ax), py(ay), 0);
        m.scale(escala, escala, 1f);
        ctx.drawItem(p, 0, 0);
        m.pop();
    }

    private void boton(DrawContext ctx, int rx, int ry, int ax, int ay, int aw,
                       int ah, Text etiqueta, boolean activo, int color) {
        boolean enc = activo && dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah),
                !activo ? APAGADO : (enc ? aclarar(color) : color));
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), 0xFF10331E, Math.max(1, pl(2)));
        texto(ctx, etiqueta, ax + aw / 2, ay + ah / 2 - 12, 24,
                activo ? 0xFFFFFFFF : 0xFFD8DEEA, true, false);
    }

    private void botonPeq(DrawContext ctx, int rx, int ry, int ax, int ay, int aw,
                          int ah, Text etiqueta, boolean activo, boolean marcado) {
        boolean enc = activo && dentro(rx, ry, px(ax), py(ay), pl(aw), pl(ah));
        ctx.fill(px(ax), py(ay), px(ax + aw), py(ay + ah),
                !activo ? APAGADO : marcado
                        ? (enc ? aclarar(VERDE) : VERDE)
                        : (enc ? 0xFF5E86D8 : 0xFF4F6FB0));
        marco(ctx, px(ax), py(ay), pl(aw), pl(ah), 0xFF20283C, Math.max(1, pl(2)));
        int alto = 18;
        while (alto > 9 && anchoArte(etiqueta.getString(), alto) > aw - 10) {
            alto--;
        }
        texto(ctx, etiqueta, ax + aw / 2, ay + (ah - alto) / 2 - 1, alto,
                activo ? 0xFFFFFFFF : 0xFFD8DEEA, true, false);
    }

    private static int aclarar(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 48);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 48);
        int b = Math.min(255, (color & 0xFF) + 48);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Corta un texto para que quepa, con puntos suspensivos.
     *
     * <p>⚠ «Ultra Ball x3» cabe; «Caramelo Experiencia M x2» no. Sin cortar,
     * el nombre de un premio largo se mete <b>encima del botón de cobrar</b> —
     * y no da ningún error.
     */
    private Text recortar(String s, int anchoMax, int alto) {
        if (anchoArte(s, alto) <= anchoMax) {
            return Text.literal(s);
        }
        int corte = s.length();
        while (corte > 1 && anchoArte(s.substring(0, corte) + "…", alto) > anchoMax) {
            corte--;
        }
        return Text.literal(s.substring(0, corte).trim() + "…");
    }

    private List<String> partir(String s, int anchoMax, int altoArte) {
        var salida = new ArrayList<String>();
        var actual = new StringBuilder();
        for (String palabra : s.split(" ")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (anchoArte(prueba, altoArte) > anchoMax && !actual.isEmpty()) {
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

    private List<Text> partirLim(String s, int anchoMax, int alto, int maxLineas) {
        var crudas = partir(s, anchoMax, alto);
        var salida = new ArrayList<Text>();
        for (int i = 0; i < crudas.size() && i < maxLineas; i++) {
            salida.add(Text.literal(crudas.get(i)));
        }
        return salida;
    }

    private int anchoArte(String linea, int alto) {
        return Math.round(textRenderer.getWidth(linea) * alto
                / (float) textRenderer.fontHeight);
    }

    private void separador(DrawContext ctx, int artY) {
        ctx.fill(px(PANEL_X + 28), py(artY), px(PANEL_X + PANEL_W - 28),
                py(artY) + Math.max(1, pl(2)), SEPARADOR);
    }

    private void textoDer(DrawContext ctx, Text linea, int derecha, int arriba,
                          int alto, int color, int contorno) {
        int a = Math.round(textRenderer.getWidth(linea) * alto
                / (float) textRenderer.fontHeight);
        texto(ctx, linea, derecha - a, arriba, alto, color, false, contorno);
    }

    private void texto(DrawContext ctx, Text linea, int cx, int arriba, int alto,
                       int color, boolean centrado, boolean contorno) {
        texto(ctx, linea, cx, arriba, alto, color, centrado,
                contorno ? CONTORNO_CLARO : 0);
    }

    /** {@code contorno == 0} significa «sin contorno». */
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
}
