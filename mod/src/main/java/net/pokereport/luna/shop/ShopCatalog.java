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

    /**
     * Un objeto a la venta.
     *
     * @param entrega quién fabrica lo que se entrega. Vacío = el objeto tal
     *                cual. Ver {@link #clave()} y {@code ShopService}
     */
    public record Entry(Item item, String label, long buy, long sell,
                        Currency currency, String entrega) {

        /**
         * CÓMO SE NOMBRA UNA ENTRADA EN EL PROTOCOLO.
         *
         * <h2>⚠⚠⚠ NO BASTA CON EL IDENTIFICADOR DEL OBJETO</h2>
         *
         * El servidor buscaba la entrada por {@code Registries.ITEM.getId(item)}
         * dentro de la categoría, y eso valía mientras <b>ningún objeto se
         * repitiera</b>. Las cinco protecciones son las cinco un
         * {@code minecraft:player_head} —lo que las distingue es su etiqueta
         * {@code protectionstones:stone_type}, no el objeto— así que con la
         * búsqueda vieja <b>siempre habría ganado la primera</b>: pagas la
         * Master Ball y te llevas la Poké Ball, sin un solo error.
         *
         * <p>⚠⚠ Y NO ES UN ÍNDICE, a propósito. Un índice ata al cliente al
         * orden exacto del JSON, y cambiar el catálogo con la tienda abierta le
         * haría comprar el artículo de al lado. Esto es un nombre estable.
         */
        public String clave() {
            String id = Registries.ITEM.getId(item).toString();
            return entrega == null || entrega.isEmpty() ? id : id + "#" + entrega;
        }

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

    /**
     * Cuántos artículos del JSON se han caído por no existir el objeto.
     *
     * <h2>⚠⚠⚠ ESTE NÚMERO ERA SOLO UNA LÍNEA DE LOG, Y NADIE MIRA EL LOG</h2>
     *
     * {@code load()} se salta el objeto que no exista y sigue. Eso está bien —
     * un catálogo con un hueco es mejor que un servidor que no arranca— pero
     * <b>callarlo no</b>: el síntoma es un artículo que se generó, se publicó y
     * <b>no aparece</b>, sin un solo error a la vista.
     *
     * <p>⚠⚠ Y con 620 artículos de <b>cuatro mods distintos</b> deja de ser
     * teórico: basta con que uno de esos mods no esté en el servidor para que
     * <b>una categoría entera</b> desaparezca. Hoy lo comprueba el autotest.
     */
    private final int omitidos;

    /** Ver {@link #omitidos}. */
    public int omitidos() {
        return omitidos;
    }

    private ShopCatalog(List<Category> categories, int omitidos) {
        this.categories = categories;
        this.omitidos = omitidos;
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

                // La moneda de la CATEGORIA es el valor por defecto de sus
                // articulos, no una regla: ver abajo.
                Currency porDefecto = cat.has("currency")
                    ? Currency.valueOf(cat.get("currency").getAsString())
                    : Currency.POKEDOLLAR;

                List<Entry> entries = new ArrayList<>();
                JsonArray raw = cat.getAsJsonArray("entries");
                for (var e : raw) {
                    JsonObject o = e.getAsJsonObject();
                    String id = o.get("item").getAsString();
                    Item item = resolve(id);
                    if (item == null) { skipped++; continue; }

                    // ⚠⚠ LA MONEDA PUEDE SER DE LA ENTRADA, no solo de la
                    //    categoria. Hace falta para PROTECCIONES: las cuatro
                    //    primeras se pagan en Plata y solo la ultima en
                    //    LunaCoins (decision del usuario, 2026-09-04). Meterlas
                    //    en dos categorias por eso habria partido en dos una
                    //    lista que el jugador lee como UNA escalera.
                    Currency moneda = o.has("currency")
                        ? Currency.valueOf(o.get("currency").getAsString())
                        : porDefecto;
                    entries.add(new Entry(
                        item,
                        o.has("label") ? o.get("label").getAsString() : null,
                        o.get("buy").getAsLong(),
                        o.get("sell").getAsLong(),
                        moneda,
                        o.has("give") ? o.get("give").getAsString() : ""));
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

            ShopCatalog catalog = new ShopCatalog(List.copyOf(categories), skipped);
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
                String name = c.id() + "/" + e.clave();

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

        // ⚠⚠⚠ DOS ENTRADAS CON LA MISMA CLAVE SON UNA SOLA PARA EL SERVIDOR.
        //    La busqueda se para en la primera que casa, asi que la segunda
        //    seria INCOMPRABLE: se dibuja, se pulsa, y te cobra y te entrega la
        //    otra. Sin un solo error. Es justo lo que habria pasado con las
        //    cinco protecciones antes de que existiera `clave()`.
        for (Category c : categories) {
            java.util.Set<String> vistas = new java.util.HashSet<>();
            for (Entry e : c.entries()) {
                if (!vistas.add(e.clave())) {
                    problems.add(c.id() + ": dos articulos con la clave "
                               + e.clave() + " -- el segundo seria incomprable");
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
