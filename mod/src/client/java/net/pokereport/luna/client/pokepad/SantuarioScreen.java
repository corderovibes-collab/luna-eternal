package net.pokereport.luna.client.pokepad;

import java.util.List;
import java.util.UUID;

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
 * SANTUARIO: los nichos de Monumentos.
 *
 * <h2>⚠⚠ DOS VISTAS EN UNA PANTALLA, como en Protecciones</h2>
 *
 * La lista (que nichos hay, de quien son, cuanto queda) y «mi nicho» (titulo,
 * historia y foto). Una pantalla aparte obligaria a duplicar chasis, navegacion
 * y escalado -- que ya estuvo copiado en once sitios con seis variantes
 * distintas -- y el boton INICIO cobra sentido solo: del detalle a la lista, de
 * la lista al Pad.
 *
 * <h2>⚠⚠ EL PRECIO LO DICE EL SERVIDOR, AQUI SOLO SE DIBUJA</h2>
 *
 * {@code EstadoSantuario} trae los dos precios: si estuvieran escritos aqui
 * tambien, habria dos sitios que pueden dejar de estar de acuerdo y un boton
 * que enseña un precio que no es el que cobra (P6, y la leccion de la tienda).
 *
 * <h2>⚠ ANTES DE TOCARLA, LEE {@code docs/ui/dibujado.md}</h2>
 */
public class SantuarioScreen extends Screen {

    private static final Identifier CHASIS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/pokepad_cosmeticos.png");
    private static final Identifier ATRAS =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_atras.png");
    private static final Identifier CERRAR =
            Identifier.of("lunaeternal", "textures/gui/pokepad/boton_cerrar.png");
    private static final Identifier ICONO =
            Identifier.of("lunaeternal", "textures/gui/pokepad/santuario.png");

    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;
    private static final int PANEL_X = 63, PANEL_Y = 70, PANEL_W = 315, PANEL_H = 692;
    private static final int PANT_X = 460, PANT_Y = 204, PANT_W = 801, PANT_H = 494;
    private static final int NAV_ALTO = 72;
    private static final int MARGEN = 14;
    private static final int FILA_ALTO = 92, FILA_AIRE = 10;
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
    private static final int VERDE = 0xFF2E9E56;
    private static final int ORO = 0xFFD9A32B;

    private final Screen anterior;

    private float k;
    private int ancho, alto, x0, y0;
    private int pagina;

    /** El nicho abierto en la vista «mi nicho», o "" si se ve la lista. */
    private String abierta = "";
    private TextFieldWidget campoTitulo;
    private TextFieldWidget campoHistoria;
    /** De que nicho se rellenaron ya los campos, para no pisar lo tecleado. */
    private String rellenado = "";
    /** La ultima subida que ya refrescamos: evita pedir mis fotos en bucle. */
    private String vistoSubida = "";

    public SantuarioScreen(Screen anterior) {
        super(Text.translatable("pokepad.lunaeternal.app.santuario"));
        this.anterior = anterior;
    }

    @Override
    protected void init() {
        recalcular();
        campoTitulo = campo(32, textoDe(campoTitulo));
        campoHistoria = campo(320, textoDe(campoHistoria));
        addSelectableChild(campoTitulo);
        addSelectableChild(campoHistoria);
        ClientPlayNetworking.send(new Red.PedirSantuario());
        ClientPlayNetworking.send(new Red.PedirFotos());
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

    // ---- dibujado ----------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        super.render(ctx, rx, ry, delta);
        // ⚠ Cuando llega la respuesta a una subida, se piden las fotos de
        //   nuevo: la nueva aparece en la lista sin que el jugador toque nada.
        var subida = EstadoCliente.fotoSubida();
        if (subida != null && !subida.idem().equals(vistoSubida)) {
            vistoSubida = subida.idem();
            ClientPlayNetworking.send(new Red.PedirFotos());
        }
        dibujarTextura(ctx, CHASIS, x0, y0, ancho, alto, NAT_ANCHO, NAT_ALTO);
        dibujarNav(ctx, rx, ry);
        dibujarPanel(ctx);
        if (abierta.isEmpty()) {
            dibujarFilas(ctx, rx, ry);
            dibujarPie(ctx, rx, ry);
        } else {
            dibujarMio(ctx, rx, ry);
        }
    }

    private void dibujarNav(DrawContext ctx, int rx, int ry) {
        int cy = PANEL_Y + NAV_ALTO / 2;
        boolean sobreAtras = dentro(rx, ry, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48));
        dibujarTextura(ctx, ATRAS, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48), 80, 64);
        if (sobreAtras) {
            marco(ctx, px(PANEL_X + 18) - 2, cy - pl(24) - 2, pl(60) + 4, pl(48) + 4,
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

    private void dibujarPanel(DrawContext ctx) {
        int cx = PANEL_X + PANEL_W / 2;
        dibujarTextura(ctx, ICONO, px(cx - 62), py(PANEL_Y + NAV_ALTO + 18),
                pl(124), pl(124), 100, 100);
        texto(ctx, Text.translatable("pokepad.lunaeternal.app.santuario"),
                cx, PANEL_Y + NAV_ALTO + 158, 30, 0xFFFFFFFF, true, false);

        int y = PANEL_Y + NAV_ALTO + 202;
        for (String linea : partir(
                Text.translatable("pokepad.lunaeternal.santuario.explica").getString(),
                PANEL_W - 56, 17)) {
            texto(ctx, Text.literal(linea), cx, y, 17, TEXTO_SUAVE, true, false);
            y += 21;
        }
        separador(ctx, y + 16);
        y += 40;
        var e = EstadoCliente.santuario();
        if (e == null) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                    cx, y + 20, 20, TEXTO_SUAVE, true, false);
            return;
        }
        int ocupados = 0;
        for (var n : e.nichos()) {
            if (!n.estado().dueno().isEmpty()) {
                ocupados++;
            }
        }
        texto(ctx, Text.translatable("pokepad.lunaeternal.santuario.ocupados"),
                cx, y, 18, TEXTO_SUAVE, true, false);
        texto(ctx, Text.literal(ocupados + " / " + e.nichos().size()),
                cx, y + 26, 40, 0xFFFFFFFF, true, false);
    }

    private int filaY(int n) {
        return PANT_Y + MARGEN + n * (FILA_ALTO + FILA_AIRE);
    }

    private void dibujarFilas(DrawContext ctx, int rx, int ry) {
        var e = EstadoCliente.santuario();
        if (e == null) {
            centrado(ctx, "pokepad.lunaeternal.cargando", 24, TEXTO_SUAVE, 0);
            return;
        }
        // ⚠⚠ «TODOS LIBRES» Y «AUN NO CONSTRUIDO» SE DIBUJAN IGUAL Y
        //    SIGNIFICAN LO CONTRARIO, la leccion de `hayMod` en protecciones.
        if (!e.hayNichos()) {
            centrado(ctx, "pokepad.lunaeternal.santuario.sin_nichos", 24, TEXTO_OSCURO, -26);
            centrado(ctx, "pokepad.lunaeternal.santuario.sin_nichos2", 18, TEXTO_SUAVE, 14);
            return;
        }
        var lista = e.nichos();
        int desde = pagina * filasCaben();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= lista.size()) {
                break;
            }
            var nicho = lista.get(i);
            var estado = nicho.estado();
            int y = filaY(n);
            boolean encima = dentro(rx, ry, px(ax), py(y), pl(aw), pl(FILA_ALTO));
            ctx.fill(px(ax), py(y), px(ax + aw), py(y + FILA_ALTO),
                    encima ? FILA_ENCIMA : FILA_FONDO);
            marco(ctx, px(ax), py(y), pl(aw), pl(FILA_ALTO), FILA_BORDE,
                    Math.max(1, pl(2)));

            texto(ctx, Text.literal(nicho.nombre()), ax + 16, y + 10, 24,
                    TEXTO_OSCURO, false, false);
            String linea;
            int color;
            if (estado.dueno().isEmpty()) {
                linea = Text.translatable("pokepad.lunaeternal.santuario.libre").getString();
                color = VERDE;
            } else if (estado.mio()) {
                linea = Text.translatable("pokepad.lunaeternal.santuario.tuyo",
                        estado.permanente()
                                ? Text.translatable("pokepad.lunaeternal.santuario.para_siempre").getString()
                                : Text.translatable("pokepad.lunaeternal.santuario.queda",
                                        falta(estado.segundos())).getString()).getString();
                color = BORDE_ENCIMA;
            } else {
                linea = Text.translatable("pokepad.lunaeternal.santuario.de",
                        estado.dueno()).getString()
                        + " · " + Text.translatable(estado.permanente()
                                ? "pokepad.lunaeternal.santuario.para_siempre"
                                : "pokepad.lunaeternal.santuario.queda",
                                falta(estado.segundos())).getString();
                color = TEXTO_SUAVE;
            }
            texto(ctx, Text.literal(linea), ax + 16, y + 42, 18, color, false, false);
            texto(ctx, Text.literal((nicho.memorial().titulo().isEmpty()
                            ? "" : nicho.memorial().titulo() + "  ·  ")
                            + "\u2661 " + nicho.memorial().honores()),
                    ax + 16, y + 64, 16, TEXTO_OSCURO, false, false);

            // Los botones de la derecha: una sola accion por estado, como un
            // escaparate -- quien mira la lista ve QUE puede hacer con cada fila.
            if (estado.dueno().isEmpty()) {
                boton(ctx, rx, ry, ax + aw - 384, y + 26, 178, 42,
                        Text.translatable("pokepad.lunaeternal.santuario.alquilar"),
                        true, 0xFF3E63C8);
                boton(ctx, rx, ry, ax + aw - 196, y + 26, 180, 42,
                        Text.translatable("pokepad.lunaeternal.santuario.comprar"),
                        true, ORO);
            } else if (estado.mio()) {
                boton(ctx, rx, ry, ax + aw - 384, y + 26, 178, 42,
                        Text.translatable("pokepad.lunaeternal.santuario.ver"),
                        true, 0xFF3E63C8);
                boton(ctx, rx, ry, ax + aw - 196, y + 26, 180, 42,
                        Text.translatable("pokepad.lunaeternal.santuario.mi_nicho"),
                        true, VERDE);
            } else {
                boton(ctx, rx, ry, ax + aw - 196, y + 26, 180, 42,
                        Text.translatable("pokepad.lunaeternal.santuario.ver"),
                        true, 0xFF3E63C8);
            }
        }
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

    // ---- mi nicho ----------------------------------------------------------

    private Red.NichoSantuario elAbierto() {
        var e = EstadoCliente.santuario();
        if (e == null) {
            return null;
        }
        for (var n : e.nichos()) {
            if (n.id().equals(abierta)) {
                return n;
            }
        }
        return null;
    }

    private void dibujarMio(DrawContext ctx, int rx, int ry) {
        var nicho = elAbierto();
        if (nicho == null) {
            centrado(ctx, "pokepad.lunaeternal.cargando", 24, TEXTO_SUAVE, 0);
            return;
        }
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        texto(ctx, Text.literal(nicho.nombre()), ax + 8, PANT_Y + MARGEN - 4, 28,
                TEXTO_OSCURO, false, false);

        // ⚠ La primera vez que llega el estado se rellenan los campos; despues
        //   no: se borraria lo que el jugador este tecleando.
        if (!abierta.equals(rellenado)) {
            rellenado = abierta;
            campoTitulo.setText(nicho.memorial().titulo());
            campoHistoria.setText(nicho.memorial().descripcion());
        }

        int y = PANT_Y + MARGEN + 30;
        rotulo(ctx, "pokepad.lunaeternal.santuario.titulo", ax, y);
        colocar(campoTitulo, ax, y + 26, 380, 32);
        campoTitulo.render(ctx, rx, ry, 0);

        rotulo(ctx, "pokepad.lunaeternal.santuario.historia", ax, y + 76);
        colocar(campoHistoria, ax, y + 102, aw - 190, 120);
        campoHistoria.render(ctx, rx, ry, 0);

        boton(ctx, rx, ry, ax + 30, y + 242, 170, 40,
                Text.translatable("pokepad.lunaeternal.santuario.guardar"),
                !campoTitulo.getText().trim().isEmpty(), VERDE);

        // ---- la foto
        int fx = ax + aw - 208;
        rotulo(ctx, "pokepad.lunaeternal.santuario.foto", fx, y);
        var actual = TexturasFotoActual(nicho.memorial().foto());
        if (actual != null) {
            dibujarFoto(ctx, actual, fx, y + 26, 180, 150);
        } else {
            ctx.fill(px(fx), py(y + 26), px(fx + 180), py(y + 176), FILA_FONDO);
            marco(ctx, px(fx), py(y + 26), pl(180), pl(150), FILA_BORDE, Math.max(1, pl(2)));
            texto(ctx, Text.translatable("pokepad.lunaeternal.santuario.sin_foto"),
                    fx + 90, y + 86, 16, TEXTO_SUAVE, true, false);
        }
        boton(ctx, rx, ry, fx, y + 186, 180, 38,
                Text.translatable("pokepad.lunaeternal.santuario.subir"),
                true, 0xFF3E63C8);
        if (!nicho.memorial().foto().isEmpty()) {
            boton(ctx, rx, ry, fx, y + 232, 180, 38,
                    Text.translatable("pokepad.lunaeternal.santuario.quitar_foto"),
                    true, 0xFFA9707A);
        }

        // ---- mis fotos: la lista para elegir cual poner
        rotulo(ctx, "pokepad.lunaeternal.santuario.mis_fotos", ax, y + 292);
        var fotos = EstadoCliente.misFotos();
        if (fotos == null) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.cargando"),
                    ax, y + 320, 17, TEXTO_SUAVE, false, false);
        } else if (fotos.fotos().isEmpty()) {
            texto(ctx, Text.translatable("pokepad.lunaeternal.santuario.sin_fotos"),
                    ax, y + 320, 17, TEXTO_SUAVE, false, false);
        } else {
            int n = 0;
            for (var f : fotos.fotos()) {
                int fy = y + 320 + n * 34;
                if (fy > PANT_Y + PANT_H - 60) {
                    break;
                }
                String etiqueta = Text.translatable(
                        "pokepad.lunaeternal.santuario.estado." + f.estado().toLowerCase())
                        .getString();
                texto(ctx, Text.literal("#" + f.fotoId() + " · " + etiqueta),
                        ax, fy + 6, 16, TEXTO_OSCURO, false, false);
                if ("APROBADA".equals(f.estado())) {
                    boton(ctx, rx, ry, ax + 330, fy, 150, 30,
                            Text.translatable("pokepad.lunaeternal.santuario.poner"),
                            true, VERDE);
                }
                n++;
            }
        }
    }

    /** La textura de la foto actual, ya pedida si hacia falta. */
    private TexturasFotoActual TexturasFotoActual(String sha1) {
        if (sha1 == null || sha1.isEmpty()) {
            return null;
        }
        var foto = net.pokereport.luna.client.TexturasFoto.lista(sha1);
        if (foto == null) {
            net.pokereport.luna.client.TexturasFoto.pedir(sha1);
            return null;
        }
        return new TexturasFotoActual(foto, sha1);
    }

    private record TexturasFotoActual(
            net.pokereport.luna.client.TexturasFoto.Foto foto, String sha1) {}

    /** La foto en su hueco, sin deformar: cabe entera, centrada. */
    private void dibujarFoto(DrawContext ctx, TexturasFotoActual actual,
                             int ax, int ay, int aw, int ah) {
        var foto = actual.foto();
        float kFoto = Math.min(aw / (float) foto.ancho(), ah / (float) foto.alto());
        int dw = Math.round(foto.ancho() * kFoto);
        int dh = Math.round(foto.alto() * kFoto);
        int dx = px(ax + (aw - dw) / 2);
        int dy = py(ay + (ah - dh) / 2);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(foto.textura(), dx, dy, pl(dw), pl(dh), 0f, 0f,
                foto.ancho(), foto.alto(), foto.ancho(), foto.alto());
        RenderSystem.disableBlend();
    }

    // ---- interaccion -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) {
            return super.mouseClicked(mx, my, boton);
        }
        int rx = (int) mx, ry = (int) my;
        int cy = py(PANEL_Y + NAV_ALTO / 2);
        if (dentro(rx, ry, px(PANEL_X + 18), cy - pl(24), pl(60), pl(48))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            if (!abierta.isEmpty()) {
                abierta = "";
                rellenado = "";
                setFocused(null);
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
            return clicMio(rx, ry, mx, my, boton) || super.mouseClicked(mx, my, boton);
        }
        return clicLista(rx, ry) || super.mouseClicked(mx, my, boton);
    }

    private boolean clicLista(int rx, int ry) {
        if (paginas() > 1) {
            int cx = PANT_X + PANT_W / 2;
            if (pagina > 0 && dentro(rx, ry, px(cx - PAG_SEP), py(PAG_Y), pl(40), pl(40))) {
                pagina--;
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                return true;
            }
            if (pagina < paginas() - 1
                    && dentro(rx, ry, px(cx + PAG_SEP - 40), py(PAG_Y), pl(40), pl(40))) {
                pagina++;
                sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                return true;
            }
        }
        var e = EstadoCliente.santuario();
        if (e == null || e.nichos().isEmpty()) {
            return false;
        }
        int desde = pagina * filasCaben();
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        for (int n = 0; n < filasCaben(); n++) {
            int i = desde + n;
            if (i >= e.nichos().size()) {
                break;
            }
            var nicho = e.nichos().get(i);
            var estado = nicho.estado();
            int y = filaY(n);
            if (estado.dueno().isEmpty()) {
                if (dentro(rx, ry, px(ax + aw - 384), py(y + 26), pl(178), pl(42))) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
                    ClientPlayNetworking.send(new Red.AlquilarNicho(
                            nicho.id(), UUID.randomUUID().toString()));
                    return true;
                }
                if (dentro(rx, ry, px(ax + aw - 196), py(y + 26), pl(180), pl(42))) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f);
                    ClientPlayNetworking.send(new Red.ComprarNicho(
                            nicho.id(), UUID.randomUUID().toString()));
                    return true;
                }
            } else if (estado.mio()) {
                if (dentro(rx, ry, px(ax + aw - 384), py(y + 26), pl(178), pl(42))) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                    client.setScreen(new MemorialScreen(this, nicho));
                    return true;
                }
                if (dentro(rx, ry, px(ax + aw - 196), py(y + 26), pl(180), pl(42))) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                    abierta = nicho.id();
                    rellenado = "";
                    setFocused(null);
                    return true;
                }
            } else {
                if (dentro(rx, ry, px(ax + aw - 196), py(y + 26), pl(180), pl(42))) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                    client.setScreen(new MemorialScreen(this, nicho));
                    return true;
                }
            }
        }
        return false;
    }

    private boolean clicMio(int rx, int ry, double mx, double my, int boton) {
        var nicho = elAbierto();
        if (nicho == null) {
            return false;
        }
        for (var c : new TextFieldWidget[] {campoTitulo, campoHistoria}) {
            if (c.mouseClicked(mx, my, boton)) {
                setFocused(c);
                return true;
            }
        }
        int ax = PANT_X + MARGEN, aw = PANT_W - 2 * MARGEN;
        int y = PANT_Y + MARGEN + 30;
        if (!campoTitulo.getText().trim().isEmpty()
                && dentro(rx, ry, px(ax + 30), py(y + 242), pl(170), pl(40))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
            ClientPlayNetworking.send(new Red.TextosNicho(abierta,
                    campoTitulo.getText().trim(), campoHistoria.getText().trim()));
            return true;
        }
        int fx = ax + aw - 208;
        if (dentro(rx, ry, px(fx), py(y + 186), pl(180), pl(38))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            net.pokereport.luna.client.DialogoFoto.abrir(this);
            return true;
        }
        if (!nicho.memorial().foto().isEmpty()
                && dentro(rx, ry, px(fx), py(y + 232), pl(180), pl(38))) {
            sonar(SoundEvents.UI_BUTTON_CLICK.value(), 0.9f);
            ClientPlayNetworking.send(new Red.QuitarFoto(abierta));
            return true;
        }
        var fotos = EstadoCliente.misFotos();
        if (fotos != null) {
            int n = 0;
            for (var f : fotos.fotos()) {
                int fy = y + 320 + n * 34;
                if ("APROBADA".equals(f.estado())
                        && dentro(rx, ry, px(ax + 330), py(fy), pl(150), pl(30))) {
                    sonar(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f);
                    ClientPlayNetworking.send(new Red.PonerFoto(abierta, f.fotoId()));
                    return true;
                }
                n++;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int tecla, int escaneo, int mods) {
        if (tecla == 256 && getFocused() != null) {
            setFocused(null);
            return true;
        }
        for (var c : new TextFieldWidget[] {campoTitulo, campoHistoria}) {
            if (getFocused() == c && c.keyPressed(tecla, escaneo, mods)) {
                return true;
            }
        }
        return super.keyPressed(tecla, escaneo, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        for (var f : new TextFieldWidget[] {campoTitulo, campoHistoria}) {
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

    // ---- utilidades (mismo juego de piezas que ProteccionesScreen) ---------

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

    private List<String> partir(String texto, int anchoArte, int altoArte) {
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

    private int filasCaben() {
        return 4;
    }

    private int paginas() {
        var e = EstadoCliente.santuario();
        int total = e == null ? 0 : e.nichos().size();
        return Math.max(1, (total + filasCaben() - 1) / filasCaben());
    }

    /** «7 h 12 min» / «12 min» / «40 s», para la fila. */
    private static String falta(long s) {
        if (s >= 3600) {
            return (s / 3600) + " h " + ((s % 3600) / 60) + " min";
        }
        return s >= 60 ? (s / 60) + " min" : s + " s";
    }
}
