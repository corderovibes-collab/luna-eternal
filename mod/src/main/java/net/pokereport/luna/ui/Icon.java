package net.pokereport.luna.ui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Constructor de iconos de menú.
 *
 * <p>Encapsula tres detalles de Minecraft que, si se olvidan, dejan la interfaz
 * con aspecto de mod casero:
 * <ol>
 *   <li>los nombres personalizados salen <i>en cursiva</i> por defecto</li>
 *   <li>la descripción se pone por componentes, no por NBT (1.21+)</li>
 *   <li>hay que ocultar el texto extra que el objeto trae de serie</li>
 * </ol>
 */
public final class Icon {

    private final ItemStack stack;
    private final List<Text> lore = new ArrayList<>();

    private Icon(Item item, int count) {
        this.stack = new ItemStack(item, count);
        // Sin esto se cuela el tooltip del objeto (durabilidad, encantamientos…)
        this.stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP,
                       net.minecraft.util.Unit.INSTANCE);
    }

    public static Icon of(Item item) {
        return new Icon(item, 1);
    }

    public static Icon of(Item item, int count) {
        return new Icon(item, count);
    }

    /** Panel de relleno para marcos y separadores. Sin nombre ni descripción. */
    public static ItemStack filler(Item pane) {
        return Icon.of(pane).name(" ").build();
    }

    public Icon name(String text) {
        stack.set(DataComponentTypes.CUSTOM_NAME, plain(text));
        return this;
    }

    /** Una línea de descripción. Se puede llamar varias veces. */
    public Icon line(String text) {
        lore.add(plain(text));
        return this;
    }

    /** Línea en blanco, para separar bloques de texto. */
    public Icon blank() {
        lore.add(Text.empty());
        return this;
    }

    /**
     * Aplica el estado: añade su etiqueta y, si no es pulsable, deja claro que
     * no lo es. Es lo que garantiza que ningún icono mienta sobre lo que hace.
     */
    public Icon state(LockState state) {
        if (state.label != null) {
            lore.add(Text.empty());
            lore.add(plain(state.label));
        }
        return this;
    }

    /** Pie de "qué hacer ahora" — el elemento que casi siempre se olvida. */
    public Icon action(String text) {
        lore.add(Text.empty());
        lore.add(plain("§8▶ §7" + text));
        return this;
    }

    public ItemStack build() {
        if (!lore.isEmpty()) {
            stack.set(DataComponentTypes.LORE, new LoreComponent(List.copyOf(lore)));
        }
        return stack;
    }

    /** Texto sin la cursiva que Minecraft aplica por defecto. */
    private static MutableText plain(String text) {
        return Text.literal(text).setStyle(Style.EMPTY.withItalic(false));
    }
}
