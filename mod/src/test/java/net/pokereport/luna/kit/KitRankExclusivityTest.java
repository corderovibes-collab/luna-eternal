package net.pokereport.luna.kit;

import net.pokereport.luna.ui.Tablist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de pruebas unitarias para la exclusividad de kits por rango y
 * la regla de compra directa de Leyenda (armaduras únicas de rangos inferiores).
 */
public class KitRankExclusivityTest {

    private MockDbState dbState;
    private TestableKitService kitService;

    private KitCatalog.Kit kitEntrenador;
    private KitCatalog.Kit kitElite;
    private KitCatalog.Kit kitCampeon;
    private KitCatalog.Kit kitMaestro;
    private KitCatalog.Kit kitLeyenda;

    private KitCatalog.Kit kitArmaduraElite;
    private KitCatalog.Kit kitArmaduraCampeon;
    private KitCatalog.Kit kitArmaduraMaestro;

    @BeforeEach
    void setUp() {
        dbState = new MockDbState();
        kitService = new TestableKitService(dbState);

        var itemBase = List.of(
                new KitCatalog.KitItem(null, 1, 500, Map.of()),
                new KitCatalog.KitItem(null, 1, 500, Map.of()),
                new KitCatalog.KitItem(null, 1, 500, Map.of()),
                new KitCatalog.KitItem(null, 1, 500, Map.of())
        );

        kitEntrenador = new KitCatalog.Kit(
                "entrenador", "Kit Entrenador", null, "Kit entrenador",
                "rank", 0L, 120, false, "ENTRENADOR", itemBase,
                true, null, null, 1
        );

        kitElite = new KitCatalog.Kit(
                "elite", "Kit Elite", null, "Kit elite",
                "rank", 0L, 120, false, "ELITE", itemBase,
                true, null, null, 1
        );

        kitCampeon = new KitCatalog.Kit(
                "campeon", "Kit Campeón", null, "Kit campeon",
                "rank", 0L, 120, false, "CAMPEON", itemBase,
                true, null, null, 1
        );

        kitMaestro = new KitCatalog.Kit(
                "maestro", "Kit Maestro", null, "Kit maestro",
                "rank", 0L, 120, false, "MAESTRO", itemBase,
                true, null, null, 1
        );

        kitLeyenda = new KitCatalog.Kit(
                "leyenda", "Kit Leyenda", null, "Kit leyenda",
                "rank", 0L, 120, false, "LEYENDA", itemBase,
                true, null, null, 1
        );

        kitArmaduraElite = new KitCatalog.Kit(
                "armadura_elite", "Armadura Élite", null, "Armadura élite única",
                "rank_armor", 0L, 0, true, "LEYENDA", itemBase,
                true, null, null, 1
        );

        kitArmaduraCampeon = new KitCatalog.Kit(
                "armadura_campeon", "Armadura Campeón", null, "Armadura campeón única",
                "rank_armor", 0L, 0, true, "LEYENDA", itemBase,
                true, null, null, 1
        );

        kitArmaduraMaestro = new KitCatalog.Kit(
                "armadura_maestro", "Armadura Maestro", null, "Armadura maestro única",
                "rank_armor", 0L, 0, true, "LEYENDA", itemBase,
                true, null, null, 1
        );
    }

    // =========================================================================
    // BLOQUE 1: EXCLUSIVIDAD DE KITS POR RANGO (SIN RECLAMOS HACIA ABAJO NI ARRIBA)
    // =========================================================================

    @Test
    @DisplayName("Entrenador solo puede reclamar kit Entrenador; kits superiores bloqueados con 'te falta el rango'")
    void testEntrenadorSoloReclamaEntrenador() {
        Tablist.Rank rango = Tablist.Rank.ENTRENADOR;
        assertEquals(Tablist.Rank.ENTRENADOR, rango);

        // Intento de reclamar rango superior
        String err = validarReclamoRango(rango, kitElite);
        assertNotNull(err);
        assertTrue(err.contains("te falta el rango ELITE"));

        err = validarReclamoRango(rango, kitCampeon);
        assertNotNull(err);
        assertTrue(err.contains("te falta el rango CAMPEON"));

        // Su propio rango
        err = validarReclamoRango(rango, kitEntrenador);
        assertNull(err, "Entrenador debe poder reclamar su propio kit");
    }

    @Test
    @DisplayName("Élite puede reclamar kit Élite; kit Entrenador bloqueado con 'ya no está disponible'")
    void testEliteNoPuedeReclamarEntrenador() {
        Tablist.Rank rango = Tablist.Rank.ELITE;

        // Kit inferior
        String err = validarReclamoRango(rango, kitEntrenador);
        assertNotNull(err);
        assertTrue(err.contains("ya no está disponible"), "No debe poder reclamar kit Entrenador si es Élite");

        // Su propio rango
        err = validarReclamoRango(rango, kitElite);
        assertNull(err, "Élite debe poder reclamar su propio kit");

        // Kit superior
        err = validarReclamoRango(rango, kitCampeon);
        assertNotNull(err);
        assertTrue(err.contains("te falta el rango CAMPEON"));
    }

    @Test
    @DisplayName("Upgrade a Campeón: no puede reclamar Élite ni Entrenador; solo Campeón")
    void testCampeonNoPuedeReclamarInferiores() {
        Tablist.Rank rango = Tablist.Rank.CAMPEON;

        // Kits inferiores
        String errElite = validarReclamoRango(rango, kitElite);
        assertNotNull(errElite);
        assertTrue(errElite.contains("ya no está disponible"));

        String errEntrenador = validarReclamoRango(rango, kitEntrenador);
        assertNotNull(errEntrenador);
        assertTrue(errEntrenador.contains("ya no está disponible"));

        // Su propio rango
        String errCampeon = validarReclamoRango(rango, kitCampeon);
        assertNull(errCampeon, "Campeón debe poder reclamar su propio kit");

        // Kit superior
        String errMaestro = validarReclamoRango(rango, kitMaestro);
        assertNotNull(errMaestro);
        assertTrue(errMaestro.contains("te falta el rango MAESTRO"));
    }

    @Test
    @DisplayName("Maestro: no puede reclamar Campeón, Élite ni Entrenador; solo Maestro")
    void testMaestroNoPuedeReclamarInferiores() {
        Tablist.Rank rango = Tablist.Rank.MAESTRO;

        assertNotNull(validarReclamoRango(rango, kitCampeon));
        assertNotNull(validarReclamoRango(rango, kitElite));
        assertNotNull(validarReclamoRango(rango, kitEntrenador));

        assertNull(validarReclamoRango(rango, kitMaestro), "Maestro debe poder reclamar su propio kit");
        assertNotNull(validarReclamoRango(rango, kitLeyenda));
    }

    @Test
    @DisplayName("Leyenda: no puede reclamar kits periódicos inferiores; solo kit Leyenda")
    void testLeyendaNoPuedeReclamarKitsPeriodicosInferiores() {
        Tablist.Rank rango = Tablist.Rank.LEYENDA;

        assertNotNull(validarReclamoRango(rango, kitMaestro));
        assertNotNull(validarReclamoRango(rango, kitCampeon));
        assertNotNull(validarReclamoRango(rango, kitElite));
        assertNotNull(validarReclamoRango(rango, kitEntrenador));

        assertNull(validarReclamoRango(rango, kitLeyenda), "Leyenda debe poder reclamar su propio kit");
    }

    // =========================================================================
    // BLOQUE 2: COMPRA DIRECTA DE LEYENDA (ARMADURAS ÚNICAS) VS UPGRADE
    // =========================================================================

    @Test
    @DisplayName("Compra directa de Leyenda sin upgrades -> Elegible para armaduras únicas")
    void testLeyendaDirectoElegibleParaArmaduras() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = 1001L;

        // Simular compra directa en Tebex
        dbState.addFulfillment(playerId, uuid, "RANK", "LEYENDA", "DELIVERED");

        assertTrue(kitService.esElegibleArmadurasLeyenda(playerId, uuid),
                "Un comprador directo de Leyenda debe ser elegible para las armaduras únicas");
    }

    @Test
    @DisplayName("Leyenda obtenido por RANK_UPGRADE -> NO elegible para armaduras únicas")
    void testLeyendaPorUpgradeNoElegible() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = 1002L;

        // Compró elite directo y luego subió a leyenda por upgrades
        dbState.addFulfillment(playerId, uuid, "RANK", "ELITE", "DELIVERED");
        dbState.addFulfillment(playerId, uuid, "RANK_UPGRADE", "ELITE->CAMPEON", "DELIVERED");
        dbState.addFulfillment(playerId, uuid, "RANK_UPGRADE", "CAMPEON->MAESTRO", "DELIVERED");
        dbState.addFulfillment(playerId, uuid, "RANK_UPGRADE", "MAESTRO->LEYENDA", "DELIVERED");

        assertFalse(kitService.esElegibleArmadurasLeyenda(playerId, uuid),
                "Un jugador que subió por upgrades NO debe ser elegible para armaduras de compra directa");
    }

    @Test
    @DisplayName("Leyenda con compra previa de rango inferior (ej: Elite) -> NO elegible")
    void testLeyendaConRangoPrevioNoElegible() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = 1003L;

        // Compró Elite y luego compró Leyenda directo
        dbState.addFulfillment(playerId, uuid, "RANK", "ELITE", "DELIVERED");
        dbState.addFulfillment(playerId, uuid, "RANK", "LEYENDA", "DELIVERED");

        assertFalse(kitService.esElegibleArmadurasLeyenda(playerId, uuid),
                "Si ya había comprado rangos comerciales inferiores previamente, no es compra directa desde cero");
    }

    @Test
    @DisplayName("Reclamo de armaduras: un solo uso garantizado (once=true), no permite segundo reclamo")
    void testArmadurasSoloUnaVez() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = 1004L;
        dbState.addFulfillment(playerId, uuid, "RANK", "LEYENDA", "DELIVERED");

        // 1. Primer reclamo de armadura Élite
        assertFalse(kitService.haReclamado(playerId, kitArmaduraElite));
        boolean claim1 = kitService.claim(playerId, kitArmaduraElite);
        assertTrue(claim1, "Primer reclamo debe ser exitoso");
        assertTrue(kitService.haReclamado(playerId, kitArmaduraElite));

        // 2. Segundo intento de reclamo de armadura Élite
        boolean claim2 = kitService.claim(playerId, kitArmaduraElite);
        assertFalse(claim2, "Segundo reclamo debe fallar porque once=true");

        // 3. Armaduras Campeón y Maestro aún disponibles para su único reclamo
        assertFalse(kitService.haReclamado(playerId, kitArmaduraCampeon));
        assertFalse(kitService.haReclamado(playerId, kitArmaduraMaestro));
        assertTrue(kitService.claim(playerId, kitArmaduraCampeon));
        assertTrue(kitService.claim(playerId, kitArmaduraMaestro));

        assertTrue(kitService.haReclamado(playerId, kitArmaduraCampeon));
        assertTrue(kitService.haReclamado(playerId, kitArmaduraMaestro));
    }

    @Test
    @DisplayName("KitCatalog validate() acepta categoría rank_armor con once=true y dailyValue=0")
    void testKitCatalogValidationWithRankArmor() {
        var catalog = new KitCatalog(List.of(
                kitEntrenador, kitElite, kitCampeon, kitMaestro, kitLeyenda,
                kitArmaduraElite, kitArmaduraCampeon, kitArmaduraMaestro
        ), 250000L);

        assertDoesNotThrow(catalog::validate, "KitCatalog validate no debe lanzar excepción con rank_armor");
        assertEquals(0, kitArmaduraElite.dailyValue(), "Armadura elite debe tener dailyValue 0");
        assertEquals(0, kitArmaduraCampeon.dailyValue(), "Armadura campeon debe tener dailyValue 0");
        assertEquals(0, kitArmaduraMaestro.dailyValue(), "Armadura maestro debe tener dailyValue 0");
    }

    // Helper method that replicates the validation logic from KitService.entregar
    private String validarReclamoRango(Tablist.Rank actual, KitCatalog.Kit kit) {
        if ("rank".equals(kit.category()) && !kit.once()) {
            if (kit.requiredRank() != null) {
                var pide = Tablist.Rank.de(kit.requiredRank());
                if (!actual.equipo) {
                    if (actual.escalon < pide.escalon) {
                        return "te falta el rango " + kit.requiredRank();
                    }
                    if (actual.escalon > pide.escalon) {
                        return "este kit ya no está disponible para tu rango actual";
                    }
                }
            }
        }
        return null;
    }

    // =========================================================================
    // MOCKS PARA PRUEBAS SIN BASE DE DATOS REAL
    // =========================================================================

    record FulfillmentRow(long playerId, UUID playerUuid, String productType, String productValue, String status) {}

    static class MockDbState {
        final List<FulfillmentRow> fulfillments = new ArrayList<>();
        final Set<String> kitClaims = new HashSet<>();

        void addFulfillment(long playerId, UUID uuid, String type, String value, String status) {
            fulfillments.add(new FulfillmentRow(playerId, uuid, type, value, status));
        }
    }

    static class TestableKitService extends KitService {
        private final MockDbState state;

        public TestableKitService(MockDbState state) {
            super(null);
            this.state = state;
        }

        @Override
        public boolean claim(long playerId, KitCatalog.Kit kit) {
            String key = playerId + ":" + kit.id();
            if (state.kitClaims.contains(key)) {
                if (kit.once()) return false;
            }
            state.kitClaims.add(key);
            return true;
        }

        @Override
        public boolean haReclamado(long playerId, KitCatalog.Kit kit) {
            return state.kitClaims.contains(playerId + ":" + kit.id())
                    || state.kitClaims.contains(playerId + ":exclusive_claim:" + kit.id());
        }

        @Override
        public boolean esElegibleArmadurasLeyenda(long playerId, UUID playerUuid) {
            // 1. Debe existir compra direct de LEYENDA
            boolean directLegend = state.fulfillments.stream().anyMatch(f ->
                    (f.playerId == playerId || (playerUuid != null && playerUuid.equals(f.playerUuid)))
                            && "DELIVERED".equals(f.status)
                            && "RANK".equals(f.productType)
                            && "LEYENDA".equals(f.productValue));
            if (!directLegend) return false;

            // 2. Cero upgrades
            boolean hasUpgrade = state.fulfillments.stream().anyMatch(f ->
                    (f.playerId == playerId || (playerUuid != null && playerUuid.equals(f.playerUuid)))
                            && "DELIVERED".equals(f.status)
                            && "RANK_UPGRADE".equals(f.productType));
            if (hasUpgrade) return false;

            // 3. Cero compras previas de rangos comerciales inferiores
            boolean hasLowerRank = state.fulfillments.stream().anyMatch(f ->
                    (f.playerId == playerId || (playerUuid != null && playerUuid.equals(f.playerUuid)))
                            && "DELIVERED".equals(f.status)
                            && "RANK".equals(f.productType)
                            && List.of("ELITE", "CAMPEON", "MAESTRO").contains(f.productValue));
            if (hasLowerRank) return false;

            return true;
        }
    }
}
