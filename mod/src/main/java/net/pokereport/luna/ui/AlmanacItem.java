package net.pokereport.luna.ui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * El objeto que abre El Almanaque.
 *
 * <p>Existe porque <b>al jugador le da pereza escribir comandos</b> (P9). Si el
 * menú solo se abriera con {@code /menu}, para buena parte de la gente el juego
 * entero no existiría.
 *
 * <p>Tres garantías, y las tres importan:
 * <ul>
 *   <li>se entrega al conectar, siempre en el mismo hueco;</li>
 *   <li>no se puede perder: si falta, se repone solo;</li>
 *   <li>si se tira al suelo, el objeto tirado se desvanece — así no queda un
 *       Almanaque huérfano en el mundo ni se duplica al reponerlo.</li>
 * </ul>
 */
public final class AlmanacItem {

    /** Marca interna, invisible para el jugador. */
    private static final String TAG = "luna_almanac";

    /** Hueco fijo de la barra rápida: siempre en el mismo sitio. */
    public static final int SLOT = 8;

    private AlmanacItem() {}

    public static ItemStack create() {
        ItemStack stack = Icon.of(Items.KNOWLEDGE_BOOK)
                .name("§6✦ El Almanaque")
                .line("§7Todo lo que has descubierto.")
                .blank()
                .line("§8Se escribe solo conforme juegas.")
                .action("Clic derecho para abrirlo")
                .build();

        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(TAG, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        return stack;
    }

    public static boolean is(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.KNOWLEDGE_BOOK)) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.copyNbt().getBoolean(TAG);
    }

    /** Se asegura de que el jugador lo tenga, en su hueco. Idempotente. */
    public static void ensure(ServerPlayerEntity player) {
        var inv = player.getInventory();

        boolean found = false;
        for (int i = 0; i < inv.size(); i++) {
            if (is(inv.getStack(i))) {
                if (found) {
                    // Un segundo Almanaque no aporta nada y confunde.
                    inv.setStack(i, ItemStack.EMPTY);
                } else {
                    found = true;
                }
            }
        }
        if (found) return;

        ItemStack current = inv.getStack(SLOT);
        if (current.isEmpty()) {
            inv.setStack(SLOT, create());
        } else if (!inv.insertStack(create())) {
            // Inventario lleno: se hace hueco desplazando lo que hubiera.
            inv.offerOrDrop(current);
            inv.setStack(SLOT, create());
        }
    }

    /**
     * Descarta un Almanaque tirado al suelo. Se llama cuando aparece una
     * entidad de objeto en el mundo: como el jugador lo recupera solo, dejar
     * el tirado crearía duplicados.
     */
    public static boolean discardIfAlmanac(ItemEntity entity) {
        if (!is(entity.getStack())) return false;
        entity.discard();
        return true;
    }
}
