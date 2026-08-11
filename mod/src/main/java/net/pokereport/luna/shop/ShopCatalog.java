package net.pokereport.luna.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Catálogo de la tienda, cargado del JSON.
 *
 * <p>Al cargar <b>valida el invariante anti-arbitraje</b>: ningún objeto puede
 * venderse al banco por más de lo que cuesta comprarlo. Es el error clásico que
 * genera dinero infinito — se compra barato, se vende caro, se repite. En la
 * auditoría de producción comprobé a mano que no existía; aquí queda
 * automatizado para que <b>no pueda reaparecer</b> al tocar un precio.
 *
 * <p>Si la validación falla, el servidor <b>no arranca</b>. Un fallo de
 * arranque cuesta minutos; una economía rota con jugadores dentro no se
 * arregla nunca del todo.
 */
public final class ShopCatalog {

    /** Un objeto a la venta. */
    public record Entry(Item item, String label, long buy, long sell, Currency currency) {

        /** Nombre a mostrar: la etiqueta del catálogo o el del objeto. */
        public String displayName() {
            return label != null ? label : "§f" + item.getName().getString();
        }

        public boolean sellable() {
            return sell > 0;
        }
    }

    /** Un grupo de objetos. */
    public record Category(String id, String name, Item icon,
                           String description, List<Entry> entries) {}

    private final List<Category> categories;

    private ShopCatalog(List<Category> categories) {
        this.categories = categories;
    }

    public List<Category> categories() {
        return categories;
    }

    public Category category(String id) {
        return categories.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    // ------------------------------------------------------------ carga

    public static ShopCatalog load() {
        try (InputStream in = ShopCatalog.class
                .getResourceAsStream("/data/lunaeternal/shop_catalog.json")) {

            if (in == null) throw new IllegalStateException("Falta shop_catalog.json");

            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            List<Category> categories = new ArrayList<>();
            int skipped = 0;

            for (var element : root.getAsJsonArray("categories")) {
                JsonObject cat = element.getAsJsonObject();

                Currency currency = cat.has("currency")
                    ? Currency.valueOf(cat.get("currency").getAsString())
                    : Currency.POKEDOLLAR;

                List<Entry> entries = new ArrayList<>();
                JsonArray raw = cat.getAsJsonArray("entries");
                for (var e : raw) {
                    JsonObject o = e.getAsJsonObject();
                    String id = o.get("item").getAsString();
                    Item item = resolve(id);
                    if (item == null) { skipped++; continue; }

                    entries.add(new Entry(
                        item,
                        o.has("label") ? o.get("label").getAsString() : null,
                        o.get("buy").getAsLong(),
                        o.get("sell").getAsLong(),
                        currency));
                }

                // Una categoría cuyos objetos no existen todavía no se muestra:
                // enseñar una sección vacía es peor que no enseñarla.
                if (entries.isEmpty()) continue;

                categories.add(new Category(
                    cat.get("id").getAsString(),
                    cat.get("name").getAsString(),
                    resolveOr(cat.get("icon").getAsString(), Items.CHEST),
                    cat.has("description") ? cat.get("description").getAsString() : "",
                    List.copyOf(entries)));
            }

            ShopCatalog catalog = new ShopCatalog(List.copyOf(categories));
            catalog.validate();

            LunaEternal.LOG.info("Tienda: {} categorías, {} objetos ({} omitidos por no existir)",
                categories.size(),
                categories.stream().mapToInt(c -> c.entries().size()).sum(),
                skipped);

            return catalog;

        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar el catálogo de la tienda", e);
        }
    }

    /**
     * Comprueba los invariantes económicos del catálogo.
     *
     * @throws IllegalStateException si alguno se incumple — y el servidor no
     *         arranca, que es exactamente lo que debe pasar.
     */
    public void validate() {
        List<String> problems = new ArrayList<>();

        for (Category c : categories) {
            for (Entry e : c.entries()) {
                String name = c.id() + "/" + Registries.ITEM.getId(e.item());

                if (e.buy() <= 0) {
                    problems.add(name + ": precio de compra no positivo (" + e.buy() + ")");
                }
                if (e.sell() < 0) {
                    problems.add(name + ": precio de venta negativo (" + e.sell() + ")");
                }
                // EL invariante. Con sell >= buy, comprar y revender genera
                // dinero de la nada y la economía muere en días.
                if (e.sell() >= e.buy()) {
                    problems.add(name + ": ARBITRAJE — se vende por " + e.sell()
                               + " y se compra por " + e.buy());
                }
                if (!e.currency().tradeable && e.sell() > 0) {
                    problems.add(name + ": una moneda no comerciable no puede "
                               + "recomprarse (crearía un mercado gris)");
                }
            }
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                "El catálogo de la tienda rompe la economía:\n  - "
                + String.join("\n  - ", problems));
        }
    }

    private static Item resolve(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            LunaEternal.LOG.debug("Objeto de tienda no encontrado, se omite: {}", id);
            return null;
        }
        return Registries.ITEM.get(identifier);
    }

    private static Item resolveOr(String id, Item fallback) {
        Item item = resolve(id);
        return item != null ? item : fallback;
    }
}
