package net.pokereport.luna.tebex;

import net.pokereport.luna.traje.Traje;
import net.pokereport.luna.ui.Tablist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite exhaustiva de pruebas unitarias automatizadas para la FASE 4C de Tebex.
 * Cubre de forma rigurosa los 27 casos de prueba obligatorios (TEST 01 a TEST 27):
 * - Compras directas (TEST 01 - TEST 08)
 * - Upgrades con validación exacta (TEST 09 - TEST 15)
 * - Compras y resolución offline (TEST 16 - TEST 17)
 * - Rewards únicas y protección contra duplicación (TEST 18 - TEST 19)
 * - Concurrencia multi-hilo y serialización anti-downgrade (TEST 20 - TEST 23)
 * - Protección absoluta de cuentas Staff (TEST 24 - TEST 26)
 * - Transición segura de PENDING_IMPLEMENTATION a DELIVERED (TEST 27)
 */
public class TebexRankTest {

    private PackageRegistry registry;
    private MockRankDatabaseState dbState;
    private TestableTebexRankService tebexService;

    @BeforeEach
    void setUp() {
        String json = """
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
            "pkg_upg_elite_campeon": { "type": "RANK_UPGRADE", "from": "ELITE", "to": "CAMPEON" },
            "pkg_upg_campeon_maestro": { "type": "RANK_UPGRADE", "from": "CAMPEON", "to": "MAESTRO" },
            "pkg_upg_maestro_leyenda": { "type": "RANK_UPGRADE", "from": "MAESTRO", "to": "LEYENDA" }
          }
        }
        """;
        registry = PackageRegistry.parseAndValidate(json, "TebexRankTest");
        dbState = new MockRankDatabaseState();
        tebexService = new TestableTebexRankService(registry, dbState);
    }

    // =========================================================================
    // BLOQUE 1: COMPRA DIRECTA DE RANGOS (TEST 01 - TEST 08)
    // =========================================================================

    @Test
    @DisplayName("TEST 01: NONE + Elite -> Elite")
    void test01_noneToEliteDirect() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player01");
        assertEquals(Tablist.Rank.ENTRENADOR, dbState.getRank(playerId));

        TebexService.ExecutionResult res = tebexService.fulfill("tx_01", uuid, "pkg_rank_elite", 1, "Player01");

        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", res.status());
        assertEquals(Tablist.Rank.ELITE, dbState.getRank(playerId));
        assertTrue(dbState.hasSuit(playerId, "elite"), "Debe otorgar el traje cosmético de Élite");
        assertEquals("DELIVERED", dbState.getFulfillmentStatus("tx_01", "pkg_rank_elite"));
    }

    @Test
    @DisplayName("TEST 02: NONE + Champion -> Champion")
    void test02_noneToChampionDirect() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player02");

        TebexService.ExecutionResult res = tebexService.fulfill("tx_02", uuid, "pkg_rank_campeon", 1, "Player02");

        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", res.status());
        assertEquals(Tablist.Rank.CAMPEON, dbState.getRank(playerId));
        assertTrue(dbState.hasSuit(playerId, "campeon"), "Debe otorgar el traje cosmético de Campeón");
        assertFalse(dbState.hasSuit(playerId, "elite"), "No debe conceder trajes de rangos no comprados");
    }

    @Test
    @DisplayName("TEST 03: NONE + Master -> Master")
    void test03_noneToMasterDirect() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player03");

        TebexService.ExecutionResult res = tebexService.fulfill("tx_03", uuid, "pkg_rank_maestro", 1, "Player03");

        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", res.status());
        assertEquals(Tablist.Rank.MAESTRO, dbState.getRank(playerId));
        assertTrue(dbState.hasSuit(playerId, "maestro"));
    }

    @Test
    @DisplayName("TEST 04: NONE + Legend -> Legend")
    void test04_noneToLegendDirect() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player04");

        TebexService.ExecutionResult res = tebexService.fulfill("tx_04", uuid, "pkg_rank_leyenda", 1, "Player04");

        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", res.status());
        assertEquals(Tablist.Rank.LEYENDA, dbState.getRank(playerId));
        assertTrue(dbState.hasSuit(playerId, "leyenda"));
    }

    @Test
    @DisplayName("TEST 05: Elite + Master directo -> Master (Sin degradación)")
    void test05_eliteToMasterDirect() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player05");
        dbState.setRank(playerId, Tablist.Rank.ELITE);
        dbState.grantSuit(playerId, "elite");

        TebexService.ExecutionResult res = tebexService.fulfill("tx_05", uuid, "pkg_rank_maestro", 1, "Player05");

        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", res.status());
        assertEquals(Tablist.Rank.MAESTRO, dbState.getRank(playerId));
        assertTrue(dbState.hasSuit(playerId, "elite"), "Conserva su traje previo de Élite");
        assertTrue(dbState.hasSuit(playerId, "maestro"), "Recibe el traje de Maestro");
        assertFalse(dbState.hasSuit(playerId, "campeon"), "No recibe trajes intermedios no adquiridos");
    }

    @Test
    @DisplayName("TEST 06: Master + Elite directo -> NO downgrade / REQUIRES_REVIEW")
    void test06_masterBuysEliteAntiDowngrade() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player06");
        dbState.setRank(playerId, Tablist.Rank.MAESTRO);
        dbState.grantSuit(playerId, "maestro");

        TebexService.ExecutionResult res = tebexService.fulfill("tx_06", uuid, "pkg_rank_elite", 1, "Player06");

        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, res.type());
        assertEquals(Tablist.Rank.MAESTRO, dbState.getRank(playerId), "Rango jamás debe degradarse a Élite");
        assertEquals("REQUIRES_REVIEW", dbState.getFulfillmentStatus("tx_06", "pkg_rank_elite"));
        assertTrue(dbState.getFailureReason("tx_06", "pkg_rank_elite").contains("LOWER_RANK_PURCHASED"));
    }

    @Test
    @DisplayName("TEST 07: Master + nueva compra Master -> REQUIRES_REVIEW (No duplicate rewards)")
    void test07_masterBuysMasterNewTransactionRedundant() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player07");
        dbState.setRank(playerId, Tablist.Rank.MAESTRO);
        dbState.grantSuit(playerId, "maestro");

        // Nueva transacción distinta para el mismo rango que ya posee
        TebexService.ExecutionResult res = tebexService.fulfill("tx_07_new", uuid, "pkg_rank_maestro", 1, "Player07");

        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, res.type());
        assertEquals(Tablist.Rank.MAESTRO, dbState.getRank(playerId));
        assertEquals("REQUIRES_REVIEW", dbState.getFulfillmentStatus("tx_07_new", "pkg_rank_maestro"));
        assertTrue(dbState.getFailureReason("tx_07_new", "pkg_rank_maestro").contains("RANK_ALREADY_OWNED"));
        assertEquals(1, dbState.countSuits(playerId), "No debe duplicar trajes");
    }

    @Test
    @DisplayName("TEST 08: Retry misma transacción Master -> Idempotente ALREADY_PROCESSED")
    void test08_masterRetrySameTransactionIdempotent() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player08");

        TebexService.ExecutionResult res1 = tebexService.fulfill("tx_08", uuid, "pkg_rank_maestro", 1, "Player08");
        assertTrue(res1.isSuccess());
        assertEquals("DELIVERED", res1.status());
        assertEquals(Tablist.Rank.MAESTRO, dbState.getRank(playerId));

        // Reintento con misma transacción y mismo paquete
        TebexService.ExecutionResult res2 = tebexService.fulfill("tx_08", uuid, "pkg_rank_maestro", 1, "Player08");
        assertTrue(res2.isSuccess(), "Retry debe responder éxito a Tebex para liberar cola");
        assertEquals(TebexService.ResultType.ALREADY_PROCESSED, res2.type());
        assertEquals(Tablist.Rank.MAESTRO, dbState.getRank(playerId));
        assertEquals(1, dbState.countSuits(playerId));
    }

    // =========================================================================
    // BLOQUE 2: UPGRADES DE RANGO (TEST 09 - TEST 15)
    // =========================================================================

    @Test
    @DisplayName("TEST 09: Elite -> Champion upgrade -> Champion")
    void test09_eliteToChampionUpgrade() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player09");
        dbState.setRank(playerId, Tablist.Rank.ELITE);
        dbState.grantSuit(playerId, "elite");

        TebexService.ExecutionResult res = tebexService.fulfill("tx_09", uuid, "pkg_upg_elite_campeon", 1, "Player09");

        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", res.status());
        assertEquals(Tablist.Rank.CAMPEON, dbState.getRank(playerId));
        assertTrue(dbState.hasSuit(playerId, "elite"), "Conserva Élite");
        assertTrue(dbState.hasSuit(playerId, "campeon"), "Adquiere Campeón");
    }

    @Test
    @DisplayName("TEST 10: Champion -> Master upgrade -> Master")
    void test10_championToMasterUpgrade() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player10");
        dbState.setRank(playerId, Tablist.Rank.CAMPEON);
        dbState.grantSuit(playerId, "campeon");

        TebexService.ExecutionResult res = tebexService.fulfill("tx_10", uuid, "pkg_upg_campeon_maestro", 1, "Player10");

        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", res.status());
        assertEquals(Tablist.Rank.MAESTRO, dbState.getRank(playerId));
        assertTrue(dbState.hasSuit(playerId, "maestro"));
    }

    @Test
    @DisplayName("TEST 11: Master -> Legend upgrade -> Legend")
    void test11_masterToLegendUpgrade() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player11");
        dbState.setRank(playerId, Tablist.Rank.MAESTRO);
        dbState.grantSuit(playerId, "maestro");

        TebexService.ExecutionResult res = tebexService.fulfill("tx_11", uuid, "pkg_upg_maestro_leyenda", 1, "Player11");

        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", res.status());
        assertEquals(Tablist.Rank.LEYENDA, dbState.getRank(playerId));
        assertTrue(dbState.hasSuit(playerId, "leyenda"));
    }

    @Test
    @DisplayName("TEST 12: NONE intenta upgrade -> REQUIRES_REVIEW (0 cambio de rango)")
    void test12_noneAttemptsUpgradeFails() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player12");
        assertEquals(Tablist.Rank.ENTRENADOR, dbState.getRank(playerId));

        TebexService.ExecutionResult res = tebexService.fulfill("tx_12", uuid, "pkg_upg_elite_campeon", 1, "Player12");

        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, res.type());
        assertEquals(Tablist.Rank.ENTRENADOR, dbState.getRank(playerId), "Rango no debe cambiar");
        assertTrue(dbState.getFailureReason("tx_12", "pkg_upg_elite_campeon").contains("UPGRADE_PREVIOUS_RANK_MISMATCH"));
        assertEquals(0, dbState.countSuits(playerId));
    }

    @Test
    @DisplayName("TEST 13: Elite intenta Champion -> Master -> REQUIRES_REVIEW (Salto inválido)")
    void test13_eliteAttemptsChampionToMasterMismatch() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player13");
        dbState.setRank(playerId, Tablist.Rank.ELITE);

        TebexService.ExecutionResult res = tebexService.fulfill("tx_13", uuid, "pkg_upg_campeon_maestro", 1, "Player13");

        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, res.type());
        assertEquals(Tablist.Rank.ELITE, dbState.getRank(playerId), "Rango debe mantenerse en Élite");
        assertTrue(dbState.getFailureReason("tx_13", "pkg_upg_campeon_maestro").contains("UPGRADE_PREVIOUS_RANK_MISMATCH"));
    }

    @Test
    @DisplayName("TEST 14: Legend intenta cualquier upgrade -> REQUIRES_REVIEW (Tope de jerarquía)")
    void test14_legendAttemptsUpgradeFails() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player14");
        dbState.setRank(playerId, Tablist.Rank.LEYENDA);

        TebexService.ExecutionResult res = tebexService.fulfill("tx_14", uuid, "pkg_upg_maestro_leyenda", 1, "Player14");

        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, res.type());
        assertEquals(Tablist.Rank.LEYENDA, dbState.getRank(playerId), "Rango se mantiene en Leyenda");
        assertTrue(dbState.getFailureReason("tx_14", "pkg_upg_maestro_leyenda").contains("UPGRADE_PREVIOUS_RANK_MISMATCH"));
    }

    @Test
    @DisplayName("TEST 15: Retry upgrade misma transacción -> No duplicación / ALREADY_PROCESSED")
    void test15_retryUpgradeIdempotent() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player15");
        dbState.setRank(playerId, Tablist.Rank.ELITE);
        dbState.grantSuit(playerId, "elite");

        TebexService.ExecutionResult res1 = tebexService.fulfill("tx_15", uuid, "pkg_upg_elite_campeon", 1, "Player15");
        assertTrue(res1.isSuccess());
        assertEquals(Tablist.Rank.CAMPEON, dbState.getRank(playerId));

        // Retry con misma tx
        TebexService.ExecutionResult res2 = tebexService.fulfill("tx_15", uuid, "pkg_upg_elite_campeon", 1, "Player15");
        assertTrue(res2.isSuccess());
        assertEquals(TebexService.ResultType.ALREADY_PROCESSED, res2.type());
        assertEquals(Tablist.Rank.CAMPEON, dbState.getRank(playerId));
        assertEquals(2, dbState.countSuits(playerId)); // elite + campeon
    }

    // =========================================================================
    // BLOQUE 3: COMPORTAMIENTO OFFLINE (TEST 16 - TEST 17)
    // =========================================================================

    @Test
    @DisplayName("TEST 16: Jugador offline NONE compra Legend -> Al reconectar es Legend")
    void test16_offlinePlayerBuysLegend() {
        UUID uuid = UUID.randomUUID(); // Jugador nunca antes conectado

        TebexService.ExecutionResult res = tebexService.fulfill("tx_16", uuid, "pkg_rank_leyenda", 1, "OfflinePlayer16");

        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", res.status());

        // Simular reconexión del jugador: lee de la base de datos
        long playerId = dbState.resolveOrCreatePlayer(uuid, "OfflinePlayer16");
        assertEquals(Tablist.Rank.LEYENDA, dbState.getRank(playerId), "Al reconectar el jugador debe ser LEYENDA");
        assertTrue(dbState.hasSuit(playerId, "leyenda"));
    }

    @Test
    @DisplayName("TEST 17: Jugador offline Elite compra upgrade Champion -> Al reconectar es Champion")
    void test17_offlinePlayerBuysUpgrade() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "OfflinePlayer17");
        dbState.setRank(playerId, Tablist.Rank.ELITE);
        dbState.grantSuit(playerId, "elite");

        // Compra procesada con jugador offline
        TebexService.ExecutionResult res = tebexService.fulfill("tx_17", uuid, "pkg_upg_elite_campeon", 1, "OfflinePlayer17");

        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", res.status());

        // Al reconectar
        assertEquals(Tablist.Rank.CAMPEON, dbState.getRank(playerId));
        assertTrue(dbState.hasSuit(playerId, "campeon"));
    }

    // =========================================================================
    // BLOQUE 4: RECOMPENSAS ÚNICAS (REWARDS) E IDEMPOTENCIA (TEST 18 - TEST 19)
    // =========================================================================

    @Test
    @DisplayName("TEST 18: Compra rango con reward única -> Simular retry -> Reward total = 1")
    void test18_uniqueRewardRetryNoDuplicate() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player18");

        TebexService.ExecutionResult res1 = tebexService.fulfill("tx_18", uuid, "pkg_rank_elite", 1, "Player18");
        assertTrue(res1.isSuccess());
        assertEquals(1, dbState.countSuits(playerId));

        // Reintentos consecutivos
        for (int i = 0; i < 3; i++) {
            TebexService.ExecutionResult retryRes = tebexService.fulfill("tx_18", uuid, "pkg_rank_elite", 1, "Player18");
            assertEquals(TebexService.ResultType.ALREADY_PROCESSED, retryRes.type());
            assertEquals(1, dbState.countSuits(playerId), "El traje nunca debe duplicarse en player_suit_owned");
        }
    }

    @Test
    @DisplayName("TEST 19: Simular fallo parcial / crash -> Retry no duplica reward")
    void test19_crashSimulationAndRecoveryNoDuplicateReward() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player19");

        // Simular que el traje se insertó previamente en la base pero el fulfillment se interrumpió
        dbState.grantSuit(playerId, "elite");
        assertEquals(1, dbState.countSuits(playerId));

        // El reintento de Tebex llega
        TebexService.ExecutionResult res = tebexService.fulfill("tx_19", uuid, "pkg_rank_elite", 1, "Player19");

        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", res.status());
        assertEquals(Tablist.Rank.ELITE, dbState.getRank(playerId));
        assertEquals(1, dbState.countSuits(playerId), "INSERT IGNORE previene cualquier duplicación");
    }

    // =========================================================================
    // BLOQUE 5: CONCURRENCIA MULTI-HILO (TEST 20 - TEST 23)
    // =========================================================================

    @Test
    @DisplayName("TEST 20: 10 hilos misma compra directa concurrente -> Exactamente 1 grant efectivo")
    void test20_concurrentSamePurchaseOnlyOneGrants() throws Exception {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player20");

        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<TebexService.ExecutionResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                latch.await();
                return tebexService.fulfill("tx_conc_20", uuid, "pkg_rank_leyenda", 1, "Player20");
            }));
        }

        latch.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        int successCount = 0;
        int alreadyProcessedCount = 0;

        for (Future<TebexService.ExecutionResult> f : futures) {
            TebexService.ExecutionResult r = f.get();
            if (r.type() == TebexService.ResultType.SUCCESS && "DELIVERED".equals(r.status())) {
                successCount++;
            } else if (r.type() == TebexService.ResultType.ALREADY_PROCESSED) {
                alreadyProcessedCount++;
            }
        }

        assertEquals(1, successCount, "Exactamente 1 hilo debió procesar la entrega");
        assertEquals(9, alreadyProcessedCount, "Los otros 9 hilos debieron recibir ALREADY_PROCESSED");
        assertEquals(Tablist.Rank.LEYENDA, dbState.getRank(playerId));
        assertEquals(1, dbState.countSuits(playerId));
    }

    @Test
    @DisplayName("TEST 21: Dos rangos diferentes concurrentes (Elite y Master) -> No downgrade (Resultado = Master)")
    void test21_concurrentDifferentRanksNoDowngrade() throws Exception {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player21");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<TebexService.ExecutionResult> f1 = pool.submit(() -> {
            latch.await();
            return tebexService.fulfill("tx_21_elite", uuid, "pkg_rank_elite", 1, "Player21");
        });

        Future<TebexService.ExecutionResult> f2 = pool.submit(() -> {
            latch.await();
            return tebexService.fulfill("tx_21_master", uuid, "pkg_rank_maestro", 1, "Player21");
        });

        latch.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        f1.get();
        f2.get();

        assertEquals(Tablist.Rank.MAESTRO, dbState.getRank(playerId), "El rango final jamás puede quedar degradado a Élite");
    }

    @Test
    @DisplayName("TEST 22: Dos upgrades concurrentes para Elite -> No saltar dos niveles")
    void test22_concurrentUpgradesNoDoubleJump() throws Exception {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player22");
        dbState.setRank(playerId, Tablist.Rank.ELITE);
        dbState.grantSuit(playerId, "elite");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        // Dos transacciones bancarias diferentes intentando el mismo upgrade Elite->Campeon
        Future<TebexService.ExecutionResult> f1 = pool.submit(() -> {
            latch.await();
            return tebexService.fulfill("tx_22_A", uuid, "pkg_upg_elite_campeon", 1, "Player22");
        });

        Future<TebexService.ExecutionResult> f2 = pool.submit(() -> {
            latch.await();
            return tebexService.fulfill("tx_22_B", uuid, "pkg_upg_elite_campeon", 1, "Player22");
        });

        latch.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        TebexService.ExecutionResult r1 = f1.get();
        TebexService.ExecutionResult r2 = f2.get();

        // Exactamente uno debe entregar DELIVERED y el otro desviar a REQUIRES_REVIEW (rango ya no es Elite)
        assertTrue((r1.type() == TebexService.ResultType.SUCCESS && r2.type() == TebexService.ResultType.REQUIRES_REVIEW) ||
                   (r2.type() == TebexService.ResultType.SUCCESS && r1.type() == TebexService.ResultType.REQUIRES_REVIEW));

        assertEquals(Tablist.Rank.CAMPEON, dbState.getRank(playerId), "No debe saltar a Maestro ni pasar de Campeón");
    }

    @Test
    @DisplayName("TEST 23: Compra Legend + upgrade concurrente -> Estado final nunca inferior a Legend")
    void test23_concurrentLegendAndUpgradeNeverBelowLegend() throws Exception {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Player23");
        dbState.setRank(playerId, Tablist.Rank.ELITE);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<TebexService.ExecutionResult> f1 = pool.submit(() -> {
            latch.await();
            return tebexService.fulfill("tx_23_upg", uuid, "pkg_upg_elite_campeon", 1, "Player23");
        });

        Future<TebexService.ExecutionResult> f2 = pool.submit(() -> {
            latch.await();
            return tebexService.fulfill("tx_23_legend", uuid, "pkg_rank_leyenda", 1, "Player23");
        });

        latch.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        f1.get();
        f2.get();

        assertEquals(Tablist.Rank.LEYENDA, dbState.getRank(playerId), "El rango final debe ser LEYENDA");
    }

    // =========================================================================
    // BLOQUE 6: PROTECCIÓN ABSOLUTA DE CUENTAS STAFF (TEST 24 - TEST 26)
    // =========================================================================

    @Test
    @DisplayName("TEST 24: ADMIN compra Elite -> No sobrescribir, REQUIRES_REVIEW")
    void test24_adminBuysEliteStaffProtected() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "StaffAdmin");
        dbState.setRank(playerId, Tablist.Rank.ADMIN);

        TebexService.ExecutionResult res = tebexService.fulfill("tx_24_admin", uuid, "pkg_rank_elite", 1, "StaffAdmin");

        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, res.type());
        assertEquals(Tablist.Rank.ADMIN, dbState.getRank(playerId), "Rango ADMIN no puede ser destruido");
        assertTrue(dbState.getFailureReason("tx_24_admin", "pkg_rank_elite").contains("STAFF_RANK_PROTECTED"));
        assertEquals(0, dbState.countSuits(playerId));
    }

    @Test
    @DisplayName("TEST 25: MOD compra Legend -> No sobrescribir, REQUIRES_REVIEW")
    void test25_modBuysLegendStaffProtected() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "StaffMod");
        dbState.setRank(playerId, Tablist.Rank.MODERADOR);

        TebexService.ExecutionResult res = tebexService.fulfill("tx_25_mod", uuid, "pkg_rank_leyenda", 1, "StaffMod");

        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, res.type());
        assertEquals(Tablist.Rank.MODERADOR, dbState.getRank(playerId), "Rango MODERADOR no puede ser destruido");
        assertTrue(dbState.getFailureReason("tx_25_mod", "pkg_rank_leyenda").contains("STAFF_RANK_PROTECTED"));
    }

    @Test
    @DisplayName("TEST 26: DEV compra upgrade -> No sobrescribir, REQUIRES_REVIEW")
    void test26_devBuysUpgradeStaffProtected() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "StaffDev");
        dbState.setRank(playerId, Tablist.Rank.DEV);

        TebexService.ExecutionResult res = tebexService.fulfill("tx_26_dev", uuid, "pkg_upg_elite_campeon", 1, "StaffDev");

        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, res.type());
        assertEquals(Tablist.Rank.DEV, dbState.getRank(playerId), "Rango DEV no puede ser destruido");
        assertTrue(dbState.getFailureReason("tx_26_dev", "pkg_upg_elite_campeon").contains("STAFF_RANK_PROTECTED"));
    }

    // =========================================================================
    // BLOQUE 7: TRANSICIÓN DE PENDING_IMPLEMENTATION A DELIVERED (TEST 27)
    // =========================================================================

    @Test
    @DisplayName("TEST 27: Transición segura de PENDING_IMPLEMENTATION (Fase 4B) a DELIVERED en Fase 4C")
    void test27_transitionFromPendingImplementationToDelivered() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "PendingBuyer");

        // Simular estado previo de Fase 4B
        dbState.setFulfillmentStatus("tx_prev_4b", "pkg_rank_campeon", "PENDING_IMPLEMENTATION");
        assertEquals("PENDING_IMPLEMENTATION", dbState.getFulfillmentStatus("tx_prev_4b", "pkg_rank_campeon"));

        // Al procesarse bajo Fase 4C
        TebexService.ExecutionResult res = tebexService.fulfill("tx_prev_4b", uuid, "pkg_rank_campeon", 1, "PendingBuyer");

        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", res.status());
        assertEquals(Tablist.Rank.CAMPEON, dbState.getRank(playerId));
        assertTrue(dbState.hasSuit(playerId, "campeon"));
        assertEquals("DELIVERED", dbState.getFulfillmentStatus("tx_prev_4b", "pkg_rank_campeon"));
        assertNull(dbState.getFailureReason("tx_prev_4b", "pkg_rank_campeon"), "failure_reason se limpia");
    }

    // =========================================================================
    // HARNESS DE PRUEBAS PARA RANGOS: Simulación atómica de motor MariaDB
    // =========================================================================

    static class MockRankDatabaseState {
        private final Map<UUID, Long> uuidToPlayerId = new ConcurrentHashMap<>();
        private final Map<Long, Tablist.Rank> playerRanks = new ConcurrentHashMap<>();
        private final Set<String> suitsOwned = ConcurrentHashMap.newKeySet();
        private final Map<String, String> fulfillments = new ConcurrentHashMap<>();
        private final Map<String, String> failureReasons = new ConcurrentHashMap<>();
        private final Map<Long, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
        private final AtomicLong playerIdCounter = new AtomicLong(200);

        public ReentrantLock getLockForPlayer(long playerId) {
            return playerLocks.computeIfAbsent(playerId, k -> new ReentrantLock());
        }

        public synchronized long resolveOrCreatePlayer(UUID uuid, String username) {
            return uuidToPlayerId.computeIfAbsent(uuid, k -> playerIdCounter.incrementAndGet());
        }

        public Tablist.Rank getRank(long playerId) {
            return playerRanks.getOrDefault(playerId, Tablist.Rank.ENTRENADOR);
        }

        public void setRank(long playerId, Tablist.Rank rank) {
            playerRanks.put(playerId, rank);
        }

        public boolean hasSuit(long playerId, String suitId) {
            return suitsOwned.contains(playerId + ":" + suitId);
        }

        public boolean grantSuit(long playerId, String suitId) {
            return suitsOwned.add(playerId + ":" + suitId);
        }

        public int countSuits(long playerId) {
            int count = 0;
            String prefix = playerId + ":";
            for (String key : suitsOwned) {
                if (key.startsWith(prefix)) count++;
            }
            return count;
        }

        public String getFulfillmentStatus(String tx, String pkg) {
            return fulfillments.get(tx + ":" + pkg);
        }

        public void setFulfillmentStatus(String tx, String pkg, String status) {
            fulfillments.put(tx + ":" + pkg, status);
        }

        public String getFailureReason(String tx, String pkg) {
            return failureReasons.get(tx + ":" + pkg);
        }

        public void setFailureReason(String tx, String pkg, String reason) {
            if (reason == null) {
                failureReasons.remove(tx + ":" + pkg);
            } else {
                failureReasons.put(tx + ":" + pkg, reason);
            }
        }
    }

    static class TestableTebexRankService extends TebexService {
        private final MockRankDatabaseState state;

        public TestableTebexRankService(PackageRegistry registry, MockRankDatabaseState state) {
            super(null, registry, null, null, null, null);
            this.state = state;
        }

        @Override
        protected Connection getConnection() throws SQLException {
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("setAutoCommit".equals(method.getName())) return null;
                        if ("commit".equals(method.getName())) return null;
                        if ("rollback".equals(method.getName())) return null;
                        if ("close".equals(method.getName())) return null;
                        if ("prepareStatement".equals(method.getName())) {
                            return createMockPreparedStatement((String) args[0]);
                        }
                        return null;
                    }
            );
        }

        private java.sql.PreparedStatement createMockPreparedStatement(String sql) {
            return (java.sql.PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                    java.sql.PreparedStatement.class.getClassLoader(),
                    new Class[]{java.sql.PreparedStatement.class},
                    (proxy, m, args) -> {
                        if ("executeUpdate".equals(m.getName())) {
                            return 1;
                        }
                        if ("executeQuery".equals(m.getName())) {
                            return (java.sql.ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                                    java.sql.ResultSet.class.getClassLoader(),
                                    new Class[]{java.sql.ResultSet.class},
                                    (p, rsM, rsArgs) -> {
                                        if ("next".equals(rsM.getName())) return false;
                                        return null;
                                    }
                            );
                        }
                        return null;
                    }
            );
        }

        @Override
        protected Long resolvePlayerId(Connection c, UUID playerUuid, String usernameHint) {
            return state.resolveOrCreatePlayer(playerUuid, usernameHint != null ? usernameHint : "TebexBuyer");
        }

        @Override
        protected Tablist.Rank readRankForUpdate(Connection c, long playerId) {
            // Simula el bloqueo SELECT ... FOR UPDATE bloqueando el lock reentrante del jugador
            ReentrantLock lock = state.getLockForPlayer(playerId);
            lock.lock();
            return state.getRank(playerId);
        }

        @Override
        protected boolean applyRankChange(Connection c, long playerId, Tablist.Rank nuevo) {
            state.setRank(playerId, nuevo);
            return true;
        }

        @Override
        protected boolean applySuitGrant(Connection c, long playerId, Traje traje) {
            if (traje == null || traje.gratis()) return false;
            return state.grantSuit(playerId, traje.id());
        }

        @Override
        protected void markFulfillmentProcessing(Connection c, String transactionId, String packageId) {
            state.setFulfillmentStatus(transactionId, packageId, "PROCESSING");
        }

        @Override
        protected TebexStatus getFulfillmentStatusForUpdate(Connection c, String transactionId, String packageId) {
            String s = state.getFulfillmentStatus(transactionId, packageId);
            return s != null ? TebexStatus.valueOf(s) : null;
        }

        @Override
        protected void markFulfillmentDelivered(Connection c, String transactionId, String packageId,
                                                UUID playerUuid, Long playerId, String productType, String productValue) {
            state.setFulfillmentStatus(transactionId, packageId, "DELIVERED");
            state.setFailureReason(transactionId, packageId, null);
        }

        @Override
        protected void recordFulfillmentReview(Connection c, String transactionId, String packageId, UUID playerUuid,
                                               String productType, String productValue, String reason) {
            state.setFulfillmentStatus(transactionId, packageId, "REQUIRES_REVIEW");
            state.setFailureReason(transactionId, packageId, reason);
        }

        @Override
        public ExecutionResult fulfill(String transactionId, UUID playerUuid, String packageId, int quantity, String usernameHint) {
            // Serializa la transacción a nivel de fila (transaction, package) igual que SELECT ... FOR UPDATE en MariaDB
            String rowLockKey = (transactionId + ":" + (packageId != null ? packageId.trim() : "")).intern();
            synchronized (rowLockKey) {
                long playerId = state.resolveOrCreatePlayer(playerUuid, usernameHint);
                ReentrantLock lock = state.getLockForPlayer(playerId);
                try {
                    return super.fulfill(transactionId, playerUuid, packageId, quantity, usernameHint);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        }
    }
}
