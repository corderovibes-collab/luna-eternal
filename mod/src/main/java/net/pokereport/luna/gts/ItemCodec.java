package net.pokereport.luna.gts;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryWrapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Serializa objetos para guardarlos en custodia.
 *
 * <p>Se usa NBT comprimido en vez de guardar solo el identificador y la
 * cantidad. La diferencia importa: un objeto con encantamientos, nombre
 * personalizado o componentes propios <b>perdería todo eso</b> al pasar por el
 * mercado, y el jugador vendería una cosa y el comprador recibiría otra.
 *
 * <p>El mismo formato servirá para Pokémon cuando Cobblemon esté instalado:
 * cambia qué se serializa, no cómo.
 */
public final class ItemCodec {

    private ItemCodec() {}

    /** Convierte un objeto en bytes. Devuelve {@code null} si está vacío. */
    public static byte[] encode(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            NbtCompound root = new NbtCompound();
            root.put("item", stack.encode(registries));

            var out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el objeto", e);
        }
    }

    /**
     * Recupera un objeto. Devuelve vacío si los datos están corruptos o si el
     * objeto ya no existe — por ejemplo si se desinstaló el mod que lo añadía.
     */
    public static ItemStack decode(byte[] data, RegistryWrapper.WrapperLookup registries) {
        if (data == null || data.length == 0) return ItemStack.EMPTY;
        try {
            NbtCompound root = NbtIo.readCompressed(
                new ByteArrayInputStream(data), NbtSizeTracker.ofUnlimitedBytes());
            return ItemStack.fromNbt(registries, root.get("item")).orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            net.pokereport.luna.LunaEternal.LOG.error(
                "Payload de GTS ilegible; el objeto no se puede entregar", e);
            return ItemStack.EMPTY;
        }
    }
}
