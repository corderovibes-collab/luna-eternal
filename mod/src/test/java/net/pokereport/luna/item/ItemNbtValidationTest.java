package net.pokereport.luna.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para A10: Validación de NBT de ítems y prevención del error MapLike[{}].
 */
public class ItemNbtValidationTest {

    @Test
    @DisplayName("Estructura nula es rechazada como inválida")
    void testNullMapReturnsFalse() {
        assertFalse(ItemNbtValidator.esValido(null), "null debe ser rechazado");
    }

    @Test
    @DisplayName("Mapa vacío {} es rechazado para evitar 'No key id in MapLike[{}]'")
    void testEmptyMapReturnsFalsePreventingMapLikeError() {
        assertFalse(ItemNbtValidator.esValido(Collections.emptyMap()),
                "Un mapa vacío {} representa MapLike[{}] y debe ser rechazado");
    }

    @Test
    @DisplayName("Compuesto sin clave 'id' es rechazado")
    void testMissingIdKeyReturnsFalse() {
        Map<String, Object> data = new HashMap<>();
        data.put("count", 1);
        data.put("Damage", 0);
        assertFalse(ItemNbtValidator.esValido(data), "Un compuesto sin clave 'id' no es un ItemStack válido");
    }

    @Test
    @DisplayName("Compuesto con 'id' en blanco es rechazado")
    void testBlankIdReturnsFalse() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", "   ");
        data.put("count", 1);
        assertFalse(ItemNbtValidator.esValido(data), "Un 'id' con solo espacios en blanco debe ser rechazado");
    }

    @Test
    @DisplayName("Ítem canónico de Minecraft con 'id' válido es aceptado")
    void testValidItemReturnsTrue() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", "minecraft:diamond");
        data.put("count", 64);
        assertTrue(ItemNbtValidator.esValido(data), "minecraft:diamond debe ser aceptado");
    }

    @Test
    @DisplayName("Ítem modded con 'id' válido es aceptado")
    void testCustomModdedItemReturnsTrue() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", "cobblemon:poke_ball");
        data.put("count", 16);
        assertTrue(ItemNbtValidator.esValido(data), "cobblemon:poke_ball debe ser aceptado");
    }

    @Test
    @DisplayName("ItemCodec.java implementa la guarda esNbtValido antes de ItemStack.fromNbt")
    void testItemCodecSourceGuardsEmptyNbt() throws Exception {
        Path path = Path.of("src/main/java/net/pokereport/luna/gts/ItemCodec.java");
        if (!Files.exists(path)) {
            path = Path.of("mod/src/main/java/net/pokereport/luna/gts/ItemCodec.java");
        }
        assertTrue(Files.exists(path), "ItemCodec.java debe existir");
        String src = Files.readString(path);
        assertTrue(src.contains("esNbtValido"), "ItemCodec debe definir y usar el método de validación esNbtValido");
        assertTrue(src.contains("cmp.contains(\"id\""), "Debe verificar la existencia de la clave 'id'");
        assertTrue(src.contains("!esNbtValido(itemElem)"), "decode debe comprobar esNbtValido antes de fromNbt");
        assertTrue(src.contains("!esNbtValido(encoded)"), "encode debe comprobar esNbtValido antes de serializar");
    }
}
