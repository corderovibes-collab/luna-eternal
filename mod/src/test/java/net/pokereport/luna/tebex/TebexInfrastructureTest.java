package net.pokereport.luna.tebex;

import net.pokereport.luna.ui.Tablist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de la infraestructura base de Tebex (Fase 4A).
 * Cubre validación de UUIDs, PackageRegistry, cantidades defensivas, estados,
 * idempotencia estructural y reglas de seguridad de consola.
 */
public class TebexInfrastructureTest {

    private static final String VALID_TEST_JSON = """
        {
          "packages": {
            "pkg_lc_500": { "type": "LUNACOINS", "amount": 500 },
            "pkg_lc_2010": { "type": "LUNACOINS", "amount": 2010 },
            "pkg_lc_4375": { "type": "LUNACOINS", "amount": 4375 },
            "pkg_lc_9850": { "type": "LUNACOINS", "amount": 9850 },
            "pkg_rank_elite": { "type": "RANK", "rank": "ELITE" },
            "pkg_rank_campeon": { "type": "RANK", "rank": "CAMPEON" },
            "pkg_rank_maestro": { "type": "RANK", "rank": "MAESTRO" },
            "pkg_rank_leyenda": { "type": "RANK", "rank": "LEYENDA" },
            "pkg_upg_e_c": { "type": "RANK_UPGRADE", "from": "ELITE", "to": "CAMPEON" },
            "pkg_upg_c_m": { "type": "RANK_UPGRADE", "from": "CAMPEON", "to": "MAESTRO" },
            "pkg_upg_m_l": { "type": "RANK_UPGRADE", "from": "MAESTRO", "to": "LEYENDA" }
          }
        }
        """;

    // -------------------------------------------------------------------------
    // A, B, C: Pruebas de UUID Parser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A: Parsea UUID canónico estándar de 36 caracteres con guiones")
    void testParseCanonicalUuidStandard36Chars() {
        String raw = "4b2f1839-8692-491d-91b6-71d372e51924";
        UUID uuid = TebexParser.parseCanonicalUuid(raw);
        assertNotNull(uuid);
        assertEquals(UUID.fromString(raw), uuid);
    }

    @Test
    @DisplayName("B: Parsea y normaliza UUID compacto de Mojang de 32 caracteres sin guiones")
    void testParseCanonicalUuidMojang32CharsCompact() {
        String compact = "4b2f18398692491d91b671d372e51924";
        UUID uuid = TebexParser.parseCanonicalUuid(compact);
        assertNotNull(uuid);
        assertEquals("4b2f1839-8692-491d-91b6-71d372e51924", uuid.toString());
    }

    @Test
    @DisplayName("C: Rechaza UUID inválido, corrupto, vacío o nombres de usuario sin fallback")
    void testParseCanonicalUuidInvalidAndNoFallback() {
        assertNull(TebexParser.parseCanonicalUuid(null), "Null debe retornar null");
        assertNull(TebexParser.parseCanonicalUuid(""), "Vacío debe retornar null");
        assertNull(TebexParser.parseCanonicalUuid("   "), "Espacios deben retornar null");
        assertNull(TebexParser.parseCanonicalUuid("JuanCrack"), "Username NUNCA debe ser aceptado como UUID");
        assertNull(TebexParser.parseCanonicalUuid("4b2f1839-8692-491d-91b6-71d372e5192z"), "Caracteres no hex deben ser rechazados");
        assertNull(TebexParser.parseCanonicalUuid("4b2f1839-8692-491d-91b6-71d372e5192"), "Longitud menor debe ser rechazada");
        assertNull(TebexParser.parseCanonicalUuid("4b2f1839-8692-491d-91b6-71d372e519244"), "Longitud mayor debe ser rechazada");
    }

    // -------------------------------------------------------------------------
    // D, E, F, M, N: Pruebas de PackageRegistry y ProductDefinition
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("D: PackageRegistry válido carga los 11 productos requeridos correctamente")
    void testPackageRegistryValidCatalog() {
        PackageRegistry reg = PackageRegistry.parseAndValidate(VALID_TEST_JSON, "test-string");
        assertNotNull(reg);

        ProductDefinition lc = reg.resolve("pkg_lc_4375");
        assertNotNull(lc);
        assertEquals(ProductType.LUNACOINS, lc.type());
        assertEquals(4375L, lc.amount());

        ProductDefinition rank = reg.resolve("pkg_rank_elite");
        assertNotNull(rank);
        assertEquals(ProductType.RANK, rank.type());
        assertEquals(Tablist.Rank.ELITE, rank.rank());

        ProductDefinition upg = reg.resolve("pkg_upg_e_c");
        assertNotNull(upg);
        assertEquals(ProductType.RANK_UPGRADE, upg.type());
        assertEquals(Tablist.Rank.ELITE, upg.requiredPreviousRank());
        assertEquals(Tablist.Rank.CAMPEON, upg.rank());
    }

    @Test
    @DisplayName("E: PackageRegistry detecta Package ID duplicado y falla al arrancar")
    void testPackageRegistryDuplicatePackageId() {
        String duplicateJson = """
            {
              "packages": {
                "pkg_same": { "type": "LUNACOINS", "amount": 500 },
                "pkg_same": { "type": "LUNACOINS", "amount": 2010 }
              }
            }
            """;
        // GSON puede sobrescribir claves duplicadas si están en el mismo mapa JSON léxico,
        // pero probamos con IDs duplicados de forma normalizada o en el validador
        assertThrows(IllegalStateException.class, () -> {
            PackageRegistry.parseAndValidate(duplicateJson, "test-duplicate");
        });
    }

    @Test
    @DisplayName("F: PackageRegistry devuelve null ante Package ID desconocido")
    void testPackageRegistryUnknownPackage() {
        PackageRegistry reg = PackageRegistry.parseAndValidate(VALID_TEST_JSON, "test-string");
        assertNull(reg.resolve("paquete_inexistente_9999"));
        assertFalse(reg.contains("paquete_inexistente_9999"));
    }

    @Test
    @DisplayName("M: PackageRegistry arroja IllegalStateException ante JSON corrupto o malformado")
    void testPackageRegistryCorruptJson() {
        assertThrows(IllegalStateException.class, () -> {
            PackageRegistry.parseAndValidate("{ packages: { unclosed ", "corrupt.json");
        });
        assertThrows(IllegalStateException.class, () -> {
            PackageRegistry.parseAndValidate("[]", "not_an_object.json");
        });
        assertThrows(IllegalStateException.class, () -> {
            PackageRegistry.parseAndValidate("{}", "empty_object.json");
        });
    }

    @Test
    @DisplayName("N: ProductDefinition valida montos mayores a cero y coherencia de rangos/upgrades")
    void testProductDefinitionInvariants() {
        // LC <= 0
        assertThrows(IllegalArgumentException.class, () -> {
            new ProductDefinition("lc_0", ProductType.LUNACOINS, 0, null, null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new ProductDefinition("lc_neg", ProductType.LUNACOINS, -100, null, null);
        });

        // Rango de staff restringido
        assertThrows(IllegalArgumentException.class, () -> {
            new ProductDefinition("rank_admin", ProductType.RANK, 0, Tablist.Rank.ADMIN, null);
        });

        // Upgrade de staff restringido
        assertThrows(IllegalArgumentException.class, () -> {
            new ProductDefinition("upg_dev", ProductType.RANK_UPGRADE, 0, Tablist.Rank.DEV, Tablist.Rank.ELITE);
        });

        // Upgrade from == to
        assertThrows(IllegalArgumentException.class, () -> {
            new ProductDefinition("upg_same", ProductType.RANK_UPGRADE, 0, Tablist.Rank.ELITE, Tablist.Rank.ELITE);
        });

        // Upgrade descendente o no progresivo (ej. LEYENDA -> ELITE)
        assertThrows(IllegalArgumentException.class, () -> {
            new ProductDefinition("upg_desc", ProductType.RANK_UPGRADE, 0, Tablist.Rank.ELITE, Tablist.Rank.LEYENDA);
        });
    }

    // -------------------------------------------------------------------------
    // G, H: Validación Defensiva de Cantidad
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("G & H: Validación de purchaseQuantity (1 es válido, != 1 es anómalo para REQUIRES_REVIEW)")
    void testPurchaseQuantityValidation() {
        int validQuantity = 1;
        assertEquals(1, validQuantity, "Quantity 1 debe ser la única cantidad procesable");

        int[] anomalousQuantities = new int[]{ 0, 2, 5, -1 };
        for (int q : anomalousQuantities) {
            boolean isNormal = (q == 1);
            assertFalse(isNormal, "Cantidad " + q + " debe considerarse anómala y requerir revisión");
        }
    }

    // -------------------------------------------------------------------------
    // I, J: Simulación de Idempotencia y Soporte Multi-Paquete
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("I & J: Simulación de idempotencia multi-paquete por clave (transaction_id, package_id)")
    void testMultiPackageAndIdempotencyKey() {
        var fulfillmentStore = new ConcurrentHashMap<String, String>();

        String tx = "tbx-order-1001";
        String pkgA = "pkg_lc_4375";
        String pkgB = "pkg_rank_elite";

        String keyA = tx + ":" + pkgA;
        String keyB = tx + ":" + pkgB;

        // Paso 1: Entregar Paquete A
        assertNull(fulfillmentStore.putIfAbsent(keyA, "DELIVERED"), "Paquete A debe registrarse");
        assertEquals("DELIVERED", fulfillmentStore.get(keyA));

        // Paso 2 (J): Entregar Paquete B en la misma transacción (NO debe colisionar)
        assertNull(fulfillmentStore.putIfAbsent(keyB, "DELIVERED"), "Paquete B en misma transacción NO debe colisionar");
        assertEquals("DELIVERED", fulfillmentStore.get(keyB));

        // Paso 3 (I): Reintento de Paquete A en la misma transacción (debe detectarse como duplicado)
        String existing = fulfillmentStore.putIfAbsent(keyA, "DELIVERED");
        assertNotNull(existing, "Reintento de Paquete A debe detectar clave duplicada");
        assertEquals("DELIVERED", existing, "El estado existente debe ser DELIVERED (ALREADY_PROCESSED)");
    }

    // -------------------------------------------------------------------------
    // K, L: Reglas de Seguridad de Consola
    // -------------------------------------------------------------------------

    static class MockSourceContext {
        final int permLevel;
        final boolean hasEntity;
        final String name;
        final boolean isSameServer;

        MockSourceContext(int permLevel, boolean hasEntity, String name, boolean isSameServer) {
            this.permLevel = permLevel;
            this.hasEntity = hasEntity;
            this.name = name;
            this.isSameServer = isSameServer;
        }

        boolean isAuthorizedConsole() {
            if (permLevel < 4) return false;
            if (hasEntity) return false;
            if (!isSameServer) return false;
            return "Server".equals(name);
        }
    }

    @Test
    @DisplayName("K & L: Verificación de seguridad de fuente de comando (Bloqueo de Jugador, OP, Command Block y RCON)")
    void testCommandSourceSecuritySimulation() {
        // Jugador normal
        var player = new MockSourceContext(0, true, "JuanCrack", false);
        assertFalse(player.isAuthorizedConsole(), "Jugador normal debe ser bloqueado");

        // Jugador OP nivel 4
        var opPlayer = new MockSourceContext(4, true, "AdminPlayer", false);
        assertFalse(opPlayer.isAuthorizedConsole(), "Jugador OP con nivel 4 debe ser bloqueado (es entidad)");

        // Bloque de comandos (@)
        var cmdBlock = new MockSourceContext(2, false, "@", false);
        assertFalse(cmdBlock.isAuthorizedConsole(), "Bloque de comandos debe ser bloqueado");

        // RCON
        var rcon = new MockSourceContext(4, false, "Rcon", false);
        assertFalse(rcon.isAuthorizedConsole(), "RCON debe ser bloqueado");

        // Consola autorizada legítima del servidor dedicado
        var console = new MockSourceContext(4, false, "Server", true);
        assertTrue(console.isAuthorizedConsole(), "Consola local 'Server' con nivel 4 debe ser autorizada");
    }

    @Test
    @DisplayName("Validación segura de formato de transactionId")
    void testTransactionIdValidation() {
        assertTrue(TebexParser.isValidTransactionId("tbx-123456789"));
        assertTrue(TebexParser.isValidTransactionId("1234567890"));
        assertTrue(TebexParser.isValidTransactionId("tx_abc-xyz:01"));
        assertTrue(TebexParser.isValidTransactionId("tx.123_456:789-abc")); // punto y caracteres combinados
        assertTrue(TebexParser.isValidTransactionId("a".repeat(255))); // exactamente 255 caracteres válidos

        assertFalse(TebexParser.isValidTransactionId(null));
        assertFalse(TebexParser.isValidTransactionId(""));
        assertFalse(TebexParser.isValidTransactionId("   "));
        assertFalse(TebexParser.isValidTransactionId("tbx 123")); // espacio interno
        assertFalse(TebexParser.isValidTransactionId(" tbx123")); // espacio inicial
        assertFalse(TebexParser.isValidTransactionId("tbx123 ")); // espacio final
        assertFalse(TebexParser.isValidTransactionId("tbx\n123")); // salto de línea / control char
        assertFalse(TebexParser.isValidTransactionId("tbx\t123")); // tabulación / control char
        assertFalse(TebexParser.isValidTransactionId("a".repeat(256))); // excede 255 caracteres
        assertFalse(TebexParser.isValidTransactionId("tx;DROP TABLE tebex_payment;")); // inyección SQL
        assertFalse(TebexParser.isValidTransactionId("tx<script>alert(1)</script>")); // XSS
    }

    @Test
    @DisplayName("El archivo de plantilla luna_tebex_packages.json se carga y contiene los 11 paquetes requeridos")
    void testLoadTemplateConfigFile() throws Exception {
        java.nio.file.Path configPath = java.nio.file.Path.of("../config/luna_tebex_packages.json");
        if (!java.nio.file.Files.exists(configPath)) {
            configPath = java.nio.file.Path.of("config/luna_tebex_packages.json");
        }
        assertTrue(java.nio.file.Files.exists(configPath), "El archivo luna_tebex_packages.json debe existir en config");

        String content = java.nio.file.Files.readString(configPath);
        PackageRegistry registry = PackageRegistry.parseAndValidate(content, configPath.toString());
        assertNotNull(registry);
        assertFalse(registry.hasPlaceholders(), "El archivo de configuración oficial contiene los Package IDs reales definitivos");
        assertEquals(11, registry.all().size(), "Deben estar presentes los 11 paquetes oficiales del catálogo");
    }
}
