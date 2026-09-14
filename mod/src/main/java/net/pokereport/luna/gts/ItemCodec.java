package net.pokereport.luna.gts;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
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

    /**
     * Valida si un elemento NBT representa una pila de objeto potencialmente válida
     * (no nulo, de tipo NbtCompound, no vacío y con la clave obligatoria 'id' de Minecraft 1.21+).
     *
     * <p>Evita el error en log: {@code Tried to load invalid item: 'No key id in MapLike[{}]'}
     * que dispara el codec de Mojang al intentar deserializar un NBT vacío o sin identificador.
     */
    public static boolean esNbtValido(NbtElement elem) {
        if (!(elem instanceof NbtCompound cmp)) return false;
        if (cmp.isEmpty()) return false;
        return cmp.contains("id", NbtElement.STRING_TYPE) && !cmp.getString("id").isBlank();
    }

    /** Convierte un objeto en bytes. Devuelve {@code null} si está vacío o inválido. */
    public static byte[] encode(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            NbtElement encoded = stack.encode(registries);
            if (!esNbtValido(encoded)) return null;

            NbtCompound root = new NbtCompound();
            root.put("item", encoded);

            var out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el objeto", e);
        }
    }

    /**
     * Recupera un objeto. Devuelve vacío si los datos están corruptos, vacíos o si el
     * objeto ya no existe — por ejemplo si se desinstaló el mod que lo añadía.
     */
    public static ItemStack decode(byte[] data, RegistryWrapper.WrapperLookup registries) {
        if (data == null || data.length == 0) return ItemStack.EMPTY;
        try {
            NbtCompound root = NbtIo.readCompressed(
                new ByteArrayInputStream(data), NbtSizeTracker.ofUnlimitedBytes());
            if (root == null || root.isEmpty()) return ItemStack.EMPTY;

            NbtElement itemElem = root.contains("item") ? root.get("item") : root;
            if (!esNbtValido(itemElem)) {
                return ItemStack.EMPTY;
            }
            return ItemStack.fromNbt(registries, itemElem).orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            net.pokereport.luna.LunaEternal.LOG.error(
                "Payload de GTS ilegible; el objeto no se puede entregar", e);
            return ItemStack.EMPTY;
        }
    }
}
