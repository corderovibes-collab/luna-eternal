package net.pokereport.luna.ui;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.pokereport.luna.LunaEternal;

/**
 * ICONOS QUE SE ESCRIBEN COMO SI FUERAN LETRAS.
 *
 * <h2>&#9888;&#9888;&#9888; MINECRAFT NO TIENE «IMAGE DISPLAY», Y POR ESO ESTO ES
 * UNA FUENTE</h2>
 *
 * Las entidades de dibujo que existen son tres --texto, objeto y bloque-- asi
 * que un PNG flotando en el mundo solo se puede hacer de dos maneras: pintando
 * un quad a mano en {@code WorldRenderEvents} (que es lo que hace el holograma
 * del santuario, y son cientos de lineas) o <b>metiendo el PNG en una fuente</b>.
 *
 * <p>Como glifo, el icono es <b>un caracter mas</b>: cabe dentro de un
 * {@code TextDisplay} que ya existe, escala con el texto, mira al jugador solo,
 * y de propina sirve igual en el chat y en cualquier pantalla del PokePad. Cero
 * codigo de dibujado.
 *
 * <p>El arte y la fuente los instala {@code tools/gen_icono_clic.py}.
 *
 * <h2>&#9888;&#9888; EL COLOR DE UN GLIFO NO ES DECORACION: LO TIÑE</h2>
 *
 * Minecraft dibuja los glifos de un mapa de bits <b>multiplicando por el color
 * del estilo</b>, exactamente igual que a una letra. Un icono heredado dentro de
 * una linea dorada <b>sale dorado</b>, y con {@code BOLD} sale <b>dibujado dos
 * veces desplazado</b> -- que en un raton de doce pixeles se ve como una mancha.
 *
 * <p>Por eso cada icono se construye con el estilo <b>puesto a mano</b>: fuente
 * propia, blanco (que multiplica por uno, o sea que no tiñe) y negrita apagada.
 * No se confia en heredar, porque heredar es justo lo que rompe.
 */
public final class Iconos {

    /**
     * La fuente del mod.
     *
     * <p>&#9888; Vive en {@code assets/lunaeternal/font/iconos.json}, que es
     * <b>cliente</b>. El servidor solo manda el estilo; quien resuelve el dibujo
     * es el cliente, y por eso esta clase puede estar en {@code main} y usarse
     * desde los dos lados.
     */
    public static final Identifier FUENTE =
            Identifier.of(LunaEternal.MOD_ID, "iconos");

    /** El raton con el boton derecho encendido. Zona de uso privado. */
    private static final String CLIC_DERECHO = "";

    private Iconos() {
    }

    /**
     * El raton del clic derecho.
     *
     * <p>&#9888;&#9888; <b>Se escribe SIEMPRE con su palabra al lado</b>, nunca
     * solo. Un {@code TextDisplay} guarda su texto <b>en el mundo</b>: si algun
     * dia el glifo no estuviera --un cliente sin el pack, un renombrado del
     * fichero-- lo que quedaria plantado en la ciudadela para siempre seria
     * <b>un cuadrado blanco</b>, y solo se arreglaria volviendo a colocar el
     * cartel. Con la palabra detras, el peor caso es un cuadradito raro delante
     * de algo que se lee.
     */
    public static MutableText clicDerecho() {
        return Text.literal(CLIC_DERECHO).setStyle(Style.EMPTY
                .withFont(FUENTE)
                // ⚠ Blanco NO es «pintarlo de blanco»: es multiplicar por uno,
                //   o sea dejar el arte como esta. Y la negrita se apaga a mano
                //   porque un glifo en negrita se dibuja DOS VECES desplazado.
                .withColor(Formatting.WHITE)
                .withBold(false)
                .withItalic(false));
    }
}
