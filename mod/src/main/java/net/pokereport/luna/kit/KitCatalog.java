package net.pokereport.luna.kit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.pokereport.luna.LunaEternal;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de kits, con el tope de valor comprobado al arrancar.
 *
 * <p><b>Los kits nunca pueden ser la fuente principal de recursos.</b> Si el
 * dinero y los objetos vienen de reclamar, el jugador optimiza hacia el
 * cooldown y deja de jugar: entra, reclama y se va. Eso es exactamente el
 * problema que el brief describe del servidor actual.
 *
 * <p>Por eso el tope diario no es una recomendación en un documento: es una
 * comprobación de arranque. Si un kit periódico se pasa, <b>el servidor no
 * arranca</b> — la misma defensa que el anti-arbitraje de la tienda.
 */
public final class KitCatalog {

    /**
     * Un objeto de un kit.
     *
     * <p>⚠ {@code encantamientos} va por identificador y no por objeto de
     * registro: el catálogo se lee <b>al arrancar</b>, antes de que exista el
     * registro dinámico del servidor. Se resuelve al entregar, y uno que no
     * exista se salta con un aviso — no se cae la entrega entera por un nombre
     * mal escrito.
     */
    public record KitItem(Item item, int count, long unitValue,
                          Map<Identifier, Integer> encantamientos) {
        public long totalValue() { return count * unitValue; }
    }

    public record Kit(String id, String name, Item icon, String description,
                      int cooldownHours, boolean once, String requiredRank,
                      List<KitItem> items) {

        public long value() {
            return items.stream().mapToLong(KitItem::totalValue).sum();
        }

        /** Valor que inyecta al día. Los de una sola vez no cuentan. */
        public long dailyValue() {
            if (once || cooldownHours <= 0) return 0;
            return value() * 24 / cooldownHours;
        }
    }

    private final List<Kit> kits;
    private final long maxDailyValue;

    private KitCatalog(List<Kit> kits, long maxDailyValue) {
        this.kits = kits;
        this.maxDailyValue = maxDailyValue;
    }

    public List<Kit> kits() { return kits; }

    public Kit byId(String id) {
        return kits.stream().filter(k -> k.id().equals(id)).findFirst().orElse(null);
    }

    public long maxDailyValue() { return maxDailyValue; }

    // ------------------------------------------------------------ carga

    public static KitCatalog load() {
        try (InputStream in = KitCatalog.class
                .getResourceAsStream("/data/lunaeternal/kits.json")) {

            if (in == null) throw new IllegalStateException("Falta kits.json");
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            long maxDaily = root.get("maxDailyValue").getAsLong();
            List<Kit> kits = new ArrayList<>();
            int omitidos = 0;

            for (var el : root.getAsJsonArray("kits")) {
                JsonObject k = el.getAsJsonObject();
                List<KitItem> items = new ArrayList<>();

                for (var ie : k.getAsJsonArray("items")) {
                    JsonObject o = ie.getAsJsonObject();
                    Item item = resolve(o.get("item").getAsString());
                    if (item == null) { omitidos++; continue; }
                    Map<Identifier, Integer> ench = new LinkedHashMap<>();
                    if (o.has("enchants")) {
                        for (var en : o.getAsJsonObject("enchants").entrySet()) {
                            Identifier id = Identifier.tryParse(en.getKey());
                            if (id == null) {
                                LunaEternal.LOG.warn(
                                    "Encantamiento con nombre invalido en kits.json: {}",
                                    en.getKey());
                                continue;
                            }
                            ench.put(id, en.getValue().getAsInt());
                        }
                    }
                    items.add(new KitItem(item, o.get("count").getAsInt(),
                                          o.get("value").getAsLong(),
                                          Map.copyOf(ench)));
                }
                if (items.isEmpty()) continue;

                kits.add(new Kit(
                    k.get("id").getAsString(),
                    k.get("name").getAsString(),
                    resolveOr(k.get("icon").getAsString(), Items.CHEST),
                    k.has("description") ? k.get("description").getAsString() : "",
                    k.get("cooldownHours").getAsInt(),
                    k.has("once") && k.get("once").getAsBoolean(),
                    k.has("requiredRank") ? k.get("requiredRank").getAsString() : null,
                    List.copyOf(items)));
            }

            KitCatalog catalog = new KitCatalog(List.copyOf(kits), maxDaily);
            catalog.validate();

            LunaEternal.LOG.info("Kits: {} cargados ({} objetos omitidos por no existir)",
                kits.size(), omitidos);
            return catalog;

        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar el catálogo de kits", e);
        }
    }

    /** Comprueba que ningún kit periódico se pasa del tope diario. */
    public void validate() {
        List<String> problemas = new ArrayList<>();

        for (Kit k : kits) {
            if (k.cooldownHours() < 0) {
                problemas.add(k.id() + ": cooldown negativo");
            }
            if (!k.once() && k.cooldownHours() == 0) {
                // Sin cooldown y repetible = reclamar en bucle.
                problemas.add(k.id() + ": repetible SIN cooldown — barra libre");
            }
            long diario = k.dailyValue();
            if (diario > maxDailyValue) {
                problemas.add(k.id() + ": inyecta " + diario
                    + "/día, por encima del tope de " + maxDailyValue);
            }
        }

        // La suma también importa: cinco kits de 5 000 son 25 000 al día
        // aunque ninguno pase el tope por separado.
        long total = kits.stream().mapToLong(Kit::dailyValue).sum();
        if (total > maxDailyValue) {
            problemas.add("la SUMA de los kits periódicos inyecta " + total
                + "/día, por encima del tope de " + maxDailyValue);
        }

        if (!problemas.isEmpty()) {
            throw new IllegalStateException(
                "Los kits romperían la economía:\n  - "
                + String.join("\n  - ", problemas));
        }
    }

    private static Item resolve(String id) {
        Identifier i = Identifier.tryParse(id);
        if (i == null || !Registries.ITEM.containsId(i)) return null;
        return Registries.ITEM.get(i);
    }

    private static Item resolveOr(String id, Item fallback) {
        Item i = resolve(id);
        return i != null ? i : fallback;
    }
}
