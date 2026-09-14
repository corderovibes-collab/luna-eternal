package net.pokereport.luna.item;

import java.util.Map;

/**
 * Validador de estructuras NBT / MapLike para ítems serializados.
 *
 * <p>Previene el error del codec de Mojang / DataFixerUpper:
 * {@code Tried to load invalid item: 'No key id in MapLike[{}]'}
 * asegurando que todo compuesto o mapa contenga una clave 'id' no vacía
 * antes de pasarlo a la deserialización de Minecraft.
 */
public final class ItemNbtValidator {

    private ItemNbtValidator() {}

    /**
     * Comprueba si una estructura MapLike / NBT contiene un identificador de ítem válido.
     */
    public static boolean esValido(Map<String, ?> compound) {
        if (compound == null || compound.isEmpty()) {
            return false;
        }
        Object id = compound.get("id");
        if (!(id instanceof String str)) {
            return false;
        }
        return !str.isBlank();
    }
}
