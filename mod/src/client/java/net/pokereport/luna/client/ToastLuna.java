package net.pokereport.luna.client;

import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * El aviso de la esquina, con el aspecto de los logros de Minecraft.
 *
 * <h2>Por qué un toast y no un mensaje de chat</h2>
 *
 * Lo pidió el usuario comparándolo con el aviso de la Pokédex: <i>«cuando subes
 * de nivel que salga una notificación y sonido, así como cuando escaneas un
 * Pokémon»</i>. Y tiene razón de producto: subir de oficio es un <b>logro</b>, y
 * un logro que sale en el chat se pierde entre lo que digan los demás — que es
 * exactamente lo que le pasaba.
 *
 * <p>Se manda además al <b>chat</b> y a la <b>barra de acción</b>, y las tres
 * cosas hacen falta por motivos distintos:
 *
 * <ul>
 *   <li><b>Toast</b>: se ve aunque estés mirando el mundo, y no lo tapa nadie.</li>
 *   <li><b>Barra de acción</b>: sale donde ya miras cuando estás picando.</li>
 *   <li><b>Chat</b>: <i>persiste</i>. Es lo único que puedes releer después para
 *       saber cuánta Plata te dieron.</li>
 * </ul>
 *
 * <h2>⚠ El fondo es el de los logros de vanilla, a propósito</h2>
 *
 * Podría dibujarse uno propio con la paleta del PokePad, y sería peor: el jugador
 * ya sabe qué significa ese marco. Reutilizarlo hace que un oficio se lea como un
 * logro sin explicar nada. El arte propio se guarda para las pantallas, donde
 * <i>hay</i> algo que diferenciar.
 */
public class ToastLuna implements Toast {

    /** El marco de los logros. Es un sprite de la interfaz, no una textura suelta. */
    private static final Identifier FONDO = Identifier.ofVanilla("toast/advancement");

    /** Lo que dura en pantalla. Los logros de vanilla usan 5 s; esto se lee antes. */
    private static final long DURACION_MS = 5000L;

    private final Text titulo;
    private final Text detalle;
    private final ItemStack icono;

    /** Se calcula UNA VEZ: `draw` corre en cada fotograma y no es sitio para partir texto. */
    private final List<net.minecraft.text.OrderedText> lineas;

    public ToastLuna(MinecraftClient cliente, Text titulo, Text detalle, String objeto) {
        this.titulo = titulo;
        this.detalle = detalle;
        this.icono = iconoDe(objeto);
        this.lineas = cliente.textRenderer.wrapLines(detalle, getWidth() - 34);
    }

    /**
     * ⚠ Un identificador desconocido devuelve el aire, no {@code null}, así que se
     * comprueba contra el aire. Con {@code get()} a secas un fallo de escritura
     * daría un hueco vacío en vez de un icono, y nadie lo relacionaría con el
     * nombre del objeto.
     */
    private static ItemStack iconoDe(String objeto) {
        if (objeto == null || objeto.isEmpty()) {
            return new ItemStack(net.minecraft.item.Items.EXPERIENCE_BOTTLE);
        }
        var id = Identifier.tryParse(objeto);
        var item = id == null ? null : Registries.ITEM.getOrEmpty(id).orElse(null);
        return item == null
                ? new ItemStack(net.minecraft.item.Items.EXPERIENCE_BOTTLE)
                : new ItemStack(item);
    }

    @Override
    public int getHeight() {
        // Dos líneas de detalle caben en la altura de siempre; a partir de tres
        // hay que crecer, o el texto se sale por debajo del marco.
        return Math.max(Toast.BASE_HEIGHT, 20 + lineas.size() * 11 + 4);
    }

    @Override
    public Visibility draw(DrawContext ctx, ToastManager gestor, long tiempoMs) {
        ctx.drawGuiTexture(FONDO, 0, 0, getWidth(), getHeight());

        var fuente = gestor.getClient().textRenderer;
        ctx.drawText(fuente, titulo, 30, 7, 0xFFFF9E2C, false);
        int y = 18;
        for (var linea : lineas) {
            ctx.drawText(fuente, linea, 30, y, 0xFFFFFFFF, false);
            y += 11;
        }
        // El objeto se dibuja el último para que quede sobre el marco.
        ctx.drawItem(icono, 8, getHeight() / 2 - 8);

        return tiempoMs >= DURACION_MS * gestor.getNotificationDisplayTimeMultiplier()
                ? Visibility.HIDE : Visibility.SHOW;
    }

    /**
     * ⚠ <b>El tipo agrupa los toasts que se sustituyen entre sí.</b> Con el tipo
     * por defecto —que es la clase— dos subidas seguidas se pisarían y solo se
     * vería la última. Cada aviso lleva su propio objeto de tipo, así que se
     * apilan: si subes Minero y Agricultor a la vez, ves los dos.
     */
    private final Object tipo = new Object();

    @Override
    public Object getType() {
        return tipo;
    }

    /** El detalle completo, para el registro. No se usa al dibujar. */
    public Text detalle() {
        return detalle;
    }
}
