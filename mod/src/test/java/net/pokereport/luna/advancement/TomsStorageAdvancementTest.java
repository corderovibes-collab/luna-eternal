package net.pokereport.luna.advancement;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para A05: Corrección de advancement inválido de Tom's Storage (unlock_redstone).
 */
public class TomsStorageAdvancementTest {

    @Test
    @DisplayName("El advancement unlock_redstone sustituye redstone_dust por minecraft:redstone válido en 1.21")
    void testUnlockRedstoneAdvancementValid() throws Exception {
        String resourcePath = "/data/toms_storage/advancement/unlock_redstone.json";
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "El archivo de sobreescritura debe existir en el classpath: " + resourcePath);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertFalse(json.contains("redstone_dust"), "No debe contener el identificador inexistente 'redstone_dust'");
            assertTrue(json.contains("minecraft:redstone"), "Debe referenciar el ítem canónico 'minecraft:redstone'");

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            assertTrue(root.has("criteria"), "Debe contener criteria");
            JsonObject criteria = root.getAsJsonObject("criteria");
            assertTrue(criteria.has("inv_changed-redstone"), "Debe contener criterio inv_changed-redstone");

            JsonObject conditions = criteria.getAsJsonObject("inv_changed-redstone").getAsJsonObject("conditions");
            JsonArray items = conditions.getAsJsonArray("items");
            assertEquals(1, items.size());
            assertEquals("minecraft:redstone", items.get(0).getAsJsonObject().get("items").getAsString());

            assertTrue(root.has("rewards"), "Debe contener rewards con recetas de toms_storage");
            JsonArray recipes = root.getAsJsonObject("rewards").getAsJsonArray("recipes");
            assertTrue(recipes.size() > 0, "Debe desbloquear al menos una receta");
        }
    }
}
