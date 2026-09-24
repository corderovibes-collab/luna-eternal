package net.pokereport.luna.tebex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.ui.Tablist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registro central e inmutable en memoria de los paquetes configurados para Tebex.
 *
 * <p>Los Package IDs de Tebex son configuración externa (viven en {@code config/luna_tebex_packages.json}).
 * Al arrancar el servidor, este registro valida exhaustivamente el archivo (fail-fast) y construye
 * un mapa inmutable {@code packageId -> ProductDefinition}.
 */
public final class PackageRegistry {

    public static final String CONFIG_FILENAME = "luna_tebex_packages.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, ProductDefinition> packagesById;
    private final boolean hasPlaceholders;

    private PackageRegistry(Map<String, ProductDefinition> packages, boolean hasPlaceholders) {
        this.packagesById = Map.copyOf(packages);
        this.hasPlaceholders = hasPlaceholders;
    }

    /**
     * Carga y valida el registro desde la ruta especificada.
     * Si el archivo no existe, genera una plantilla inicial con placeholders y lanza {@link IllegalStateException}.
     */
    public static PackageRegistry load(Path configDir) {
        Path filePath = configDir.resolve(CONFIG_FILENAME);
        if (!Files.exists(filePath)) {
            createTemplateFile(filePath);
            throw new IllegalStateException("[TEBEX] No se encontró " + CONFIG_FILENAME +
                    ". Se ha generado una plantilla en: " + filePath.toAbsolutePath() +
                    ". Configura los Package IDs reales antes de habilitar la tienda.");
        }

        String jsonContent;
        try {
            jsonContent = Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("[TEBEX] Error de lectura al abrir " + filePath, e);
        }

        return parseAndValidate(jsonContent, filePath.toString());
    }

    /**
     * Parsea y valida el contenido JSON directamente (útil para pruebas unitarias).
     */
    public static PackageRegistry parseAndValidate(String jsonContent, String originSource) {
        if (jsonContent == null || jsonContent.isBlank()) {
            throw new IllegalStateException("[TEBEX] La configuración en " + originSource + " está vacía");
        }

        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(jsonContent);
            if (!el.isJsonObject()) {
                throw new IllegalStateException("[TEBEX] La raíz de la configuración debe ser un objeto JSON");
            }
            root = el.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new IllegalStateException("[TEBEX] JSON corrupto o sintaxis inválida en " + originSource, e);
        }

        if (!root.has("packages") || !root.get("packages").isJsonObject()) {
            throw new IllegalStateException("[TEBEX] Falta el objeto 'packages' en " + originSource);
        }

        JsonObject packagesObj = root.getAsJsonObject("packages");
        if (packagesObj.isEmpty()) {
            throw new IllegalStateException("[TEBEX] El objeto 'packages' no contiene ningún producto en " + originSource);
        }

        Map<String, ProductDefinition> parsed = new HashMap<>();
        Set<String> seenPackageIds = new HashSet<>();
        boolean placeholdersFound = false;

        // Banderas de productos requeridos para el catálogo comercial oficial
        Set<Long> foundLcAcounts = new HashSet<>();
        Set<Tablist.Rank> foundRanks = new HashSet<>();
        Set<String> foundUpgrades = new HashSet<>();

        for (Map.Entry<String, JsonElement> entry : packagesObj.entrySet()) {
            String rawPkgId = entry.getKey();
            if (rawPkgId == null || rawPkgId.isBlank()) {
                throw new IllegalStateException("[TEBEX] Package ID vacío o nulo detectado en " + originSource);
            }
            String pkgId = rawPkgId.trim();

            if (!seenPackageIds.add(pkgId)) {
                throw new IllegalStateException("[TEBEX] Package ID duplicado detectado: " + pkgId);
            }

            if (pkgId.startsWith("PLACEHOLDER_") || pkgId.startsWith("PKG_")) {
                placeholdersFound = true;
            }

            if (!entry.getValue().isJsonObject()) {
                throw new IllegalStateException("[TEBEX] La definición de " + pkgId + " debe ser un objeto JSON");
            }
            JsonObject itemObj = entry.getValue().getAsJsonObject();

            // Soportar propiedad "type" o "product"
            String typeStr = itemObj.has("type") ? itemObj.get("type").getAsString() :
                    (itemObj.has("product") ? itemObj.get("product").getAsString() : null);

            if (typeStr == null || typeStr.isBlank()) {
                throw new IllegalStateException("[TEBEX] Falta el campo 'type' en el producto: " + pkgId);
            }

            ProductType type;
            try {
                type = ProductType.valueOf(typeStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("[TEBEX] Tipo de producto desconocido '" + typeStr + "' en " + pkgId);
            }

            long amount = 0;
            Tablist.Rank rank = null;
            Tablist.Rank requiredPrev = null;

            switch (type) {
                case LUNACOINS -> {
                    if (!itemObj.has("amount") || !itemObj.get("amount").isJsonPrimitive()
                            || !itemObj.get("amount").getAsJsonPrimitive().isNumber()) {
                        throw new IllegalStateException("[TEBEX] 'amount' numérico requerido para LUNACOINS en " + pkgId);
                    }
                    amount = itemObj.get("amount").getAsLong();
                    if (amount <= 0) {
                        throw new IllegalStateException("[TEBEX] 'amount' debe ser mayor a 0 para LUNACOINS en " + pkgId + ": " + amount);
                    }
                    foundLcAcounts.add(amount);
                }
                case RANK -> {
                    if (!itemObj.has("rank") || !itemObj.get("rank").isJsonPrimitive()) {
                        throw new IllegalStateException("[TEBEX] 'rank' requerido para RANK en " + pkgId);
                    }
                    String rankName = itemObj.get("rank").getAsString();
                    rank = resolveRank(rankName, pkgId);
                    if (rank.equipo) {
                        throw new IllegalStateException("[TEBEX] Prohibido vender rangos de equipo/staff (" + rank + ") en " + pkgId);
                    }
                    foundRanks.add(rank);
                }
                case RANK_UPGRADE -> {
                    String fromStr = itemObj.has("from") ? itemObj.get("from").getAsString() : null;
                    String toStr = itemObj.has("to") ? itemObj.get("to").getAsString() : null;
                    if (fromStr == null || toStr == null) {
                        throw new IllegalStateException("[TEBEX] 'from' y 'to' requeridos para RANK_UPGRADE en " + pkgId);
                    }
                    requiredPrev = resolveRank(fromStr, pkgId);
                    rank = resolveRank(toStr, pkgId);

                    if (requiredPrev.equipo || rank.equipo) {
                        throw new IllegalStateException("[TEBEX] Prohibido usar rangos de equipo en upgrade en " + pkgId);
                    }
                    if (requiredPrev == rank) {
                        throw new IllegalStateException("[TEBEX] 'from' y 'to' no pueden ser idénticos en upgrade: " + pkgId);
                    }
                    if (rank.escalon <= requiredPrev.escalon) {
                        throw new IllegalStateException("[TEBEX] Progresión de upgrade inválida (escalón destino " +
                                rank.escalon + " <= previo " + requiredPrev.escalon + ") en " + pkgId);
                    }
                    foundUpgrades.add(requiredPrev.name() + "->" + rank.name());
                }
            }

            parsed.put(pkgId, new ProductDefinition(pkgId, type, amount, rank, requiredPrev));
        }

        // Validación de catálogo requerido mínimo
        validateRequiredCatalog(foundLcAcounts, foundRanks, foundUpgrades, originSource);

        if (placeholdersFound) {
            LunaEternal.LOG.warn("[TEBEX] La configuración contiene Package IDs con prefijo PLACEHOLDER o PKG. " +
                    "Recuerda reemplazarlos con los IDs numéricos reales de Tebex antes de habilitar ventas públicas.");
        }

        return new PackageRegistry(parsed, placeholdersFound);
    }

    private static void validateRequiredCatalog(Set<Long> lcAmounts, Set<Tablist.Rank> ranks,
                                                Set<String> upgrades, String source) {
        // Tiers requeridos de LunaCoins: 500, 2010, 4375, 9850
        for (long requiredLc : new long[]{500L, 2010L, 4375L, 9850L}) {
            if (!lcAmounts.contains(requiredLc)) {
                throw new IllegalStateException("[TEBEX] Falta el paquete requerido de " + requiredLc + " LunaCoins en " + source);
            }
        }
        // Rangos requeridos: ELITE, CAMPEON, MAESTRO, LEYENDA
        for (Tablist.Rank requiredRank : new Tablist.Rank[]{
                Tablist.Rank.ELITE, Tablist.Rank.CAMPEON, Tablist.Rank.MAESTRO, Tablist.Rank.LEYENDA}) {
            if (!ranks.contains(requiredRank)) {
                throw new IllegalStateException("[TEBEX] Falta el rango comercial requerido " + requiredRank + " en " + source);
            }
        }
        // Upgrades requeridos
        for (String reqUp : new String[]{"ELITE->CAMPEON", "CAMPEON->MAESTRO", "MAESTRO->LEYENDA"}) {
            if (!upgrades.contains(reqUp)) {
                throw new IllegalStateException("[TEBEX] Falta el upgrade requerido " + reqUp + " en " + source);
            }
        }
    }

    private static Tablist.Rank resolveRank(String raw, String pkgId) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("[TEBEX] Rango nulo o vacío en " + pkgId);
        }
        String normalized = raw.trim().toUpperCase()
                .replace("É", "E")
                .replace("Ó", "O");

        // Alias comunes
        if ("CHAMPION".equals(normalized)) normalized = "CAMPEON";
        if ("MASTER".equals(normalized)) normalized = "MAESTRO";
        if ("LEGEND".equals(normalized)) normalized = "LEYENDA";

        try {
            return Tablist.Rank.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("[TEBEX] Rango desconocido '" + raw + "' en producto " + pkgId);
        }
    }

    private static void createTemplateFile(Path filePath) {
        try {
            Files.createDirectories(filePath.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("_comment", "Configuracion de paquetes Tebex para PokeReport Network. Reemplaza los IDs PLACEHOLDER por los IDs reales.");

            JsonObject packages = new JsonObject();

            // 4 Tiers LunaCoins
            packages.add("PLACEHOLDER_LC_500", createLcJson(500));
            packages.add("PLACEHOLDER_LC_2010", createLcJson(2010));
            packages.add("PLACEHOLDER_LC_4375", createLcJson(4375));
            packages.add("PLACEHOLDER_LC_9850", createLcJson(9850));

            // 4 Rangos directos
            packages.add("PLACEHOLDER_RANK_ELITE", createRankJson("ELITE"));
            packages.add("PLACEHOLDER_RANK_CAMPEON", createRankJson("CAMPEON"));
            packages.add("PLACEHOLDER_RANK_MAESTRO", createRankJson("MAESTRO"));
            packages.add("PLACEHOLDER_RANK_LEYENDA", createRankJson("LEYENDA"));

            // 3 Upgrades
            packages.add("PLACEHOLDER_UPG_E_C", createUpgradeJson("ELITE", "CAMPEON"));
            packages.add("PLACEHOLDER_UPG_C_M", createUpgradeJson("CAMPEON", "MAESTRO"));
            packages.add("PLACEHOLDER_UPG_M_L", createUpgradeJson("MAESTRO", "LEYENDA"));

            root.add("packages", packages);

            Files.writeString(filePath, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LunaEternal.LOG.error("[TEBEX] No se pudo crear el archivo plantilla en {}", filePath, e);
        }
    }

    private static JsonObject createLcJson(long amount) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "LUNACOINS");
        o.addProperty("amount", amount);
        return o;
    }

    private static JsonObject createRankJson(String rank) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "RANK");
        o.addProperty("rank", rank);
        return o;
    }

    private static JsonObject createUpgradeJson(String from, String to) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "RANK_UPGRADE");
        o.addProperty("from", from);
        o.addProperty("to", to);
        return o;
    }

    /**
     * Resuelve un {@link ProductDefinition} a partir de su Package ID de Tebex.
     * Devuelve {@code null} si el ID no está registrado.
     */
    public ProductDefinition resolve(String packageId) {
        if (packageId == null || packageId.isBlank()) {
            return null;
        }
        return packagesById.get(packageId.trim());
    }

    public boolean contains(String packageId) {
        return packageId != null && packagesById.containsKey(packageId.trim());
    }

    public Map<String, ProductDefinition> all() {
        return packagesById;
    }

    public boolean hasPlaceholders() {
        return hasPlaceholders;
    }
}
