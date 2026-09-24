package net.pokereport.luna.tebex;

import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;
import net.pokereport.luna.traje.Traje;
import net.pokereport.luna.ui.Tablist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite oficial exhaustiva de pruebas unitarias para la FASE 4D de Tebex.
 * Cubre de forma rigurosa los 24 casos de prueba requeridos por la arquitectura:
 *
 * BLOQUE 1: REVERSIÓN DE LUNACOINS (TEST 01 - TEST 05)
 * - Débito contable exacto con saldo suficiente
 * - Desvío seguro a REQUIRES_REVIEW por déficit (sin saldo negativo ni débito parcial)
 * - Idempotencia ante reembolsos concurrentes
 * - Chargeback con saldo suficiente
 * - Chargeback con saldo insuficiente y auditoría [TEBEX-FRAUD]
 *
 * BLOQUE 2: REVERSIÓN DE RANGOS Y UPGRADES (TEST 06 - TEST 11)
 * - NONE -> Elite -> Refund -> NONE (Entrenador)
 * - Elite legítimo previo -> Master directo -> Refund -> Elite
 * - Elite -> Champion upgrade -> Refund -> Elite
 * - Elite -> Champion -> Master -> Refund Champion->Master -> Champion (último eslabón)
 * - Elite -> Champion -> Master -> Legend -> Refund Champion->Master -> REQUIRES_REVIEW (cadena rota)
 * - Elite -> Legend directo -> Refund Elite -> Legend intacto
 *
 * BLOQUE 3: PROTECCIÓN DE STAFF (TEST 12 - TEST 13)
 * - Intento de refund comercial sobre ADMIN retenido en REQUIRES_REVIEW
 * - Intento de chargeback sobre DEV retenido en REQUIRES_REVIEW
 *
 * BLOQUE 4: PROVENIENCIA DE TRAJES COSMÉTICOS (TEST 14 - TEST 15)
 * - Traje creado exclusivamente por la compra se revoca de player_suit_owned
 * - Traje preexistente legítimo NUNCA se revoca
 *
 * BLOQUE 5: CONCURRENCIA MULTI-HILO (TEST 16 - TEST 18)
 * - INITIAL y REFUND concurrentes
 * - INITIAL y CHARGEBACK concurrentes
 * - REFUND y CHARGEBACK concurrentes (cero doble reversión económica)
 *
 * BLOQUE 6: CASOS BORDE Y RESILIENCIA (TEST 19 - TEST 24)
 * - Reintento de Refund tras Refund ya completado (ALREADY_PROCESSED)
 * - Chargeback tras Refund completado (actualización contable sin doble débito)
 * - Refund previo a entrega efectiva (NOT_APPLICABLE sin débito ni revocar)
 * - Transacción multi-paquete (revierte solo el paquete solicitado)
 * - Transacción no encontrada (ORIGINAL_FULFILLMENT_NOT_FOUND)
 * - Mismatch de UUID (UUID_MISMATCH retenido en REQUIRES_REVIEW)
 */
public class TebexReversalTest {

    private static final String CATALOG_JSON = """
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

    private PackageRegistry registry;
    private MockReversalDatabaseState dbState;
    private TestableTebexReversalService tebexService;

    @BeforeEach
    void setUp() {
        registry = PackageRegistry.parseAndValidate(CATALOG_JSON, "TebexReversalTest");
        dbState = new MockReversalDatabaseState();
        tebexService = new TestableTebexReversalService(registry, dbState);
    }

    // =========================================================================
    // BLOQUE 1: REVERSIÓN DE LUNACOINS (TEST 01 - TEST 05)
    // =========================================================================

    @Test
    @DisplayName("TEST 01: Compra +4375 LC, Saldo 4375 -> Refund -> Saldo 0, 1 credit, 1 debit (COMPLETED)")
    void test01_refundLunaCoinsSufficientBalance() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Buyer01");
        assertEquals(0L, dbState.getBalance(playerId));

        // 1. Compra inicial
        TebexService.ExecutionResult fulfillRes = tebexService.fulfill("tx_01", uuid, "pkg_lc_4375", 1, "Buyer01");
        assertTrue(fulfillRes.isSuccess());
        assertEquals(4375L, dbState.getBalance(playerId));
        assertEquals(1, dbState.getLedgerEntriesCount());

        // 2. Reembolso
        TebexService.ExecutionResult refundRes = tebexService.refund("tx_01", uuid, "pkg_lc_4375");
        assertTrue(refundRes.isSuccess());
        assertEquals("COMPLETED", refundRes.status());
        assertEquals(0L, dbState.getBalance(playerId), "El saldo debe quedar exactamente en 0");
        assertEquals(2, dbState.getLedgerEntriesCount(), "Debe existir exactamente 1 crédito y 1 débito");

        // Verificar estado de BD
        MockReversalDatabaseState.FulfillmentRecord f = dbState.getFulfillment("tx_01", "pkg_lc_4375");
        assertNotNull(f);
        assertEquals(TebexReversalStatus.COMPLETED, f.reversalStatus);
        assertEquals(TebexFinancialEvent.REFUND, f.financialEvent);
        assertEquals("REFUND", f.status);
        assertTrue(dbState.isEffectReversed(f.id, TebexEffectType.COIN_CREDIT));
    }

    @Test
    @DisplayName("TEST 02: Compra +4375 LC, Saldo 2000 -> Refund -> Saldo 2000, REQUIRES_REVIEW por déficit de 2375 LC")
    void test02_refundLunaCoinsInsufficientBalanceRequiresReview() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Buyer02");

        // 1. Compra inicial
        tebexService.fulfill("tx_02", uuid, "pkg_lc_4375", 1, "Buyer02");
        assertEquals(4375L, dbState.getBalance(playerId));

        // 2. Jugador gasta 2375 LC en la tienda del juego
        dbState.setBalance(playerId, 2000L);

        // 3. Llega reembolso
        TebexService.ExecutionResult refundRes = tebexService.refund("tx_02", uuid, "pkg_lc_4375");
        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, refundRes.type());
        assertEquals("REQUIRES_REVIEW", refundRes.status());
        assertTrue(refundRes.reason().contains("INSUFFICIENT_LUNACOINS"), "Debe indicar insuficiencia de saldo");
        assertTrue(refundRes.reason().contains("deficit=2375"), "Debe calcular el déficit contable exacto");

        // El saldo debe permanecer intacto: CERO saldos negativos, CERO débitos parciales
        assertEquals(2000L, dbState.getBalance(playerId), "El saldo NO debe quedar negativo ni debitarse parcialmente");
        assertEquals(1, dbState.getLedgerEntriesCount(), "No se debe registrar ningún débito parcial en el ledger");

        MockReversalDatabaseState.FulfillmentRecord f = dbState.getFulfillment("tx_02", "pkg_lc_4375");
        assertNotNull(f);
        assertEquals(TebexReversalStatus.REQUIRES_REVIEW, f.reversalStatus);
        assertEquals(TebexFinancialEvent.REFUND, f.financialEvent);
        assertFalse(dbState.isEffectReversed(f.id, TebexEffectType.COIN_CREDIT));
    }

    @Test
    @DisplayName("TEST 03: Compra +4375 LC -> Refund concurrente x10 -> Saldo 0, exactamente 1 débito aplicado")
    void test03_concurrentDuplicateRefundsIdempotent() throws Exception {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Buyer03");

        tebexService.fulfill("tx_03", uuid, "pkg_lc_4375", 1, "Buyer03");
        assertEquals(4375L, dbState.getBalance(playerId));

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<TebexService.ExecutionResult>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                latch.await();
                return tebexService.refund("tx_03", uuid, "pkg_lc_4375");
            }));
        }

        latch.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        int successCount = 0;
        int alreadyProcessedCount = 0;

        for (Future<TebexService.ExecutionResult> f : futures) {
            TebexService.ExecutionResult r = f.get();
            if (r.type() == TebexService.ResultType.SUCCESS) {
                successCount++;
            } else if (r.type() == TebexService.ResultType.ALREADY_PROCESSED) {
                alreadyProcessedCount++;
            }
        }

        assertEquals(1, successCount, "Exactamente 1 invocación debe ejecutar la reversión");
        assertEquals(threads - 1, alreadyProcessedCount, "Las demás deben reconciliarse como ALREADY_PROCESSED");
        assertEquals(0L, dbState.getBalance(playerId), "El saldo final debe ser estrictamente 0");
        assertEquals(2, dbState.getLedgerEntriesCount(), "Exactamente 1 crédito y 1 débito total en ledger");
    }

    @Test
    @DisplayName("TEST 04: Compra +4375 LC -> Chargeback con saldo suficiente -> Saldo 0, 1 reversión bancaria")
    void test04_chargebackLunaCoinsSufficientBalance() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Buyer04");

        tebexService.fulfill("tx_04", uuid, "pkg_lc_4375", 1, "Buyer04");
        assertEquals(4375L, dbState.getBalance(playerId));

        TebexService.ExecutionResult cbRes = tebexService.chargeback("tx_04", uuid, "pkg_lc_4375");
        assertTrue(cbRes.isSuccess());
        assertEquals("COMPLETED", cbRes.status());
        assertEquals(0L, dbState.getBalance(playerId));
        assertEquals(2, dbState.getLedgerEntriesCount());

        MockReversalDatabaseState.FulfillmentRecord f = dbState.getFulfillment("tx_04", "pkg_lc_4375");
        assertNotNull(f);
        assertEquals(TebexReversalStatus.COMPLETED, f.reversalStatus);
        assertEquals(TebexFinancialEvent.CHARGEBACK, f.financialEvent);
        assertEquals("CHARGEBACK", f.status);
    }

    @Test
    @DisplayName("TEST 05: Chargeback con saldo insuficiente -> REQUIRES_REVIEW con auditoría [TEBEX-FRAUD]")
    void test05_chargebackInsufficientBalanceFraudLogged() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Buyer05");

        tebexService.fulfill("tx_05", uuid, "pkg_lc_4375", 1, "Buyer05");
        dbState.setBalance(playerId, 500L); // Gastó 3875 LC

        TebexService.ExecutionResult cbRes = tebexService.chargeback("tx_05", uuid, "pkg_lc_4375");
        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, cbRes.type());
        assertTrue(cbRes.reason().contains("INSUFFICIENT_LUNACOINS"));
        assertTrue(cbRes.reason().contains("deficit=3875"));

        assertEquals(500L, dbState.getBalance(playerId), "Saldo no debe ser alterado ni negativo");
        assertEquals(1, dbState.getLedgerEntriesCount());

        MockReversalDatabaseState.FulfillmentRecord f = dbState.getFulfillment("tx_05", "pkg_lc_4375");
        assertEquals(TebexReversalStatus.REQUIRES_REVIEW, f.reversalStatus);
        assertEquals(TebexFinancialEvent.CHARGEBACK, f.financialEvent);
    }

    // =========================================================================
    // BLOQUE 2: REVERSIÓN DE RANGOS Y UPGRADES (TEST 06 - TEST 11)
    // =========================================================================

    @Test
    @DisplayName("TEST 06: NONE -> Elite -> Refund -> NONE (ENTRENADOR)")
    void test06_refundDirectEliteToTrainer() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "RankBuyer06");
        assertEquals(Tablist.Rank.ENTRENADOR, dbState.getRank(playerId));

        tebexService.fulfill("tx_06", uuid, "pkg_rank_elite", 1, "RankBuyer06");
        assertEquals(Tablist.Rank.ELITE, dbState.getRank(playerId));
        assertTrue(dbState.hasSuit(playerId, "elite"));

        TebexService.ExecutionResult refundRes = tebexService.refund("tx_06", uuid, "pkg_rank_elite");
        assertTrue(refundRes.isSuccess());
        assertEquals("COMPLETED", refundRes.status());
        assertEquals(Tablist.Rank.ENTRENADOR, dbState.getRank(playerId), "Debe regresar al rango ENTRENADOR");
        assertFalse(dbState.hasSuit(playerId, "elite"), "El traje debe ser revocado al haber sido creado por la compra");
    }

    @Test
    @DisplayName("TEST 07: Elite previo legítimo -> Master directo -> Refund Master -> Elite")
    void test07_preexistingEliteBuysMasterRefundsToElite() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "RankBuyer07");
        dbState.setRank(playerId, Tablist.Rank.ELITE); // Rango legítimo preexistente

        tebexService.fulfill("tx_07", uuid, "pkg_rank_maestro", 1, "RankBuyer07");
        assertEquals(Tablist.Rank.MAESTRO, dbState.getRank(playerId));

        TebexService.ExecutionResult refundRes = tebexService.refund("tx_07", uuid, "pkg_rank_maestro");
        assertTrue(refundRes.isSuccess());
        assertEquals("COMPLETED", refundRes.status());
        assertEquals(Tablist.Rank.ELITE, dbState.getRank(playerId), "Debe restaurar el baseline legítimo ELITE");
    }

    @Test
    @DisplayName("TEST 08: Elite -> Champion upgrade -> Refund -> Elite")
    void test08_upgradeEliteToChampionRefundsToElite() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "RankBuyer08");

        // 1. Compra Élite
        tebexService.fulfill("tx_08_e", uuid, "pkg_rank_elite", 1, "RankBuyer08");
        assertEquals(Tablist.Rank.ELITE, dbState.getRank(playerId));

        // 2. Upgrade Élite -> Campeón
        tebexService.fulfill("tx_08_c", uuid, "pkg_upg_elite_campeon", 1, "RankBuyer08");
        assertEquals(Tablist.Rank.CAMPEON, dbState.getRank(playerId));
        assertTrue(dbState.hasSuit(playerId, "campeon"));

        // 3. Reembolso del Upgrade
        TebexService.ExecutionResult refundRes = tebexService.refund("tx_08_c", uuid, "pkg_upg_elite_campeon");
        assertTrue(refundRes.isSuccess());
        assertEquals("COMPLETED", refundRes.status());
        assertEquals(Tablist.Rank.ELITE, dbState.getRank(playerId), "Debe degradar a ELITE");
        assertTrue(dbState.hasSuit(playerId, "elite"), "Debe conservar el traje de Élite aún activo");
        assertFalse(dbState.hasSuit(playerId, "campeon"), "Debe revocar el traje de Campeón");
    }

    @Test
    @DisplayName("TEST 09: Elite -> Champion -> Master -> Refund Champion->Master -> Champion (último eslabón)")
    void test09_refundLastLinkOfUpgradeChain() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "RankBuyer09");

        tebexService.fulfill("tx_09_1", uuid, "pkg_rank_elite", 1, "RankBuyer09");
        tebexService.fulfill("tx_09_2", uuid, "pkg_upg_elite_campeon", 1, "RankBuyer09");
        tebexService.fulfill("tx_09_3", uuid, "pkg_upg_campeon_maestro", 1, "RankBuyer09");
        assertEquals(Tablist.Rank.MAESTRO, dbState.getRank(playerId));

        // Reembolso del último eslabón (Campeón -> Maestro)
        TebexService.ExecutionResult refundRes = tebexService.refund("tx_09_3", uuid, "pkg_upg_campeon_maestro");
        assertTrue(refundRes.isSuccess());
        assertEquals("COMPLETED", refundRes.status());
        assertEquals(Tablist.Rank.CAMPEON, dbState.getRank(playerId), "Debe degradar limpiamente a CAMPEON");
        assertFalse(dbState.hasSuit(playerId, "maestro"));
        assertTrue(dbState.hasSuit(playerId, "campeon"));
    }

    @Test
    @DisplayName("TEST 10: Elite -> Champion -> Master -> Legend -> Refund Champion->Master -> REQUIRES_REVIEW (cadena rota)")
    void test10_refundMiddleLinkOfUpgradeChainRequiresReview() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "RankBuyer10");

        tebexService.fulfill("tx_10_1", uuid, "pkg_rank_elite", 1, "RankBuyer10");
        tebexService.fulfill("tx_10_2", uuid, "pkg_upg_elite_campeon", 1, "RankBuyer10");
        tebexService.fulfill("tx_10_3", uuid, "pkg_upg_campeon_maestro", 1, "RankBuyer10");
        tebexService.fulfill("tx_10_4", uuid, "pkg_upg_maestro_leyenda", 1, "RankBuyer10");
        assertEquals(Tablist.Rank.LEYENDA, dbState.getRank(playerId));

        // Intento de reembolsar un eslabón intermedio
        TebexService.ExecutionResult refundRes = tebexService.refund("tx_10_3", uuid, "pkg_upg_campeon_maestro");
        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, refundRes.type());
        assertEquals("REQUIRES_REVIEW", refundRes.status());
        assertTrue(refundRes.reason().contains("CHAIN_DEPENDENCY_PREVENTS_AUTO_REVERSAL"),
                "Debe detectar dependencias posteriores activas");

        // El rango no se degrada a un estado corrupto
        assertEquals(Tablist.Rank.LEYENDA, dbState.getRank(playerId), "Rango debe mantenerse intacto en LEYENDA");
    }

    @Test
    @DisplayName("TEST 11: Elite -> Legend directo -> Refund Elite -> Legend permanece intacto")
    void test11_refundLowerRankWithIndependentHigherRankActive() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "RankBuyer11");

        tebexService.fulfill("tx_11_1", uuid, "pkg_rank_elite", 1, "RankBuyer11");
        tebexService.fulfill("tx_11_2", uuid, "pkg_rank_leyenda", 1, "RankBuyer11");
        assertEquals(Tablist.Rank.LEYENDA, dbState.getRank(playerId));

        // Reembolso del paquete Élite inicial
        TebexService.ExecutionResult refundRes = tebexService.refund("tx_11_1", uuid, "pkg_rank_elite");
        assertTrue(refundRes.isSuccess());
        assertEquals("COMPLETED", refundRes.status());
        assertEquals(Tablist.Rank.LEYENDA, dbState.getRank(playerId), "Debe conservar LEYENDA por compra independiente");
        assertTrue(dbState.hasSuit(playerId, "leyenda"));
    }

    // =========================================================================
    // BLOQUE 3: PROTECCIÓN DE STAFF (TEST 12 - TEST 13)
    // =========================================================================

    @Test
    @DisplayName("TEST 12: ADMIN + refund comercial -> ADMIN nunca desaparece (REQUIRES_REVIEW, STAFF_RANK_PROTECTED)")
    void test12_adminRefundStaffProtected() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "AdminStaff");

        // Registrar fulfillment previo
        dbState.injectDeliveredFulfillment("tx_12_admin", "pkg_rank_elite", uuid, playerId, "RANK", "ELITE");
        dbState.setRank(playerId, Tablist.Rank.ADMIN);

        TebexService.ExecutionResult refundRes = tebexService.refund("tx_12_admin", uuid, "pkg_rank_elite");
        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, refundRes.type());
        assertTrue(refundRes.reason().contains("STAFF_RANK_PROTECTED"));
        assertEquals(Tablist.Rank.ADMIN, dbState.getRank(playerId), "Rango ADMIN intocable");
    }

    @Test
    @DisplayName("TEST 13: DEV + chargeback -> DEV nunca se convierte en NONE (REQUIRES_REVIEW, STAFF_RANK_PROTECTED)")
    void test13_devChargebackStaffProtected() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "DevStaff");

        dbState.injectDeliveredFulfillment("tx_13_dev", "pkg_rank_campeon", uuid, playerId, "RANK", "CAMPEON");
        dbState.setRank(playerId, Tablist.Rank.DEV);

        TebexService.ExecutionResult cbRes = tebexService.chargeback("tx_13_dev", uuid, "pkg_rank_campeon");
        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, cbRes.type());
        assertTrue(cbRes.reason().contains("STAFF_RANK_PROTECTED"));
        assertEquals(Tablist.Rank.DEV, dbState.getRank(playerId), "Rango DEV intocable");
    }

    // =========================================================================
    // BLOQUE 4: PROVENIENCIA DE TRAJES COSMÉTICOS (TEST 14 - TEST 15)
    // =========================================================================

    @Test
    @DisplayName("TEST 14: Jugador no tenía traje -> Compra crea traje -> Refund seguro -> Traje se elimina")
    void test14_suitCreatedByPurchaseIsRevoked() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "SuitBuyer14");
        assertFalse(dbState.hasSuit(playerId, "campeon"));

        tebexService.fulfill("tx_14", uuid, "pkg_rank_campeon", 1, "SuitBuyer14");
        assertTrue(dbState.hasSuit(playerId, "campeon"));

        tebexService.refund("tx_14", uuid, "pkg_rank_campeon");
        assertFalse(dbState.hasSuit(playerId, "campeon"), "Traje creado por la transacción debe ser revocado");
    }

    @Test
    @DisplayName("TEST 15: Jugador YA tenía traje antes -> Compra rango -> Refund -> Traje permanece intacto")
    void test15_preexistingSuitPreservedOnRefund() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "SuitBuyer15");
        dbState.grantSuit(playerId, "campeon"); // Preexistente legítimo
        assertTrue(dbState.hasSuit(playerId, "campeon"));

        tebexService.fulfill("tx_15", uuid, "pkg_rank_campeon", 1, "SuitBuyer15");
        assertTrue(dbState.hasSuit(playerId, "campeon"));

        tebexService.refund("tx_15", uuid, "pkg_rank_campeon");
        assertTrue(dbState.hasSuit(playerId, "campeon"), "Traje preexistente jamás debe ser eliminado");
    }

    // =========================================================================
    // BLOQUE 5: CONCURRENCIA MULTI-HILO (TEST 16 - TEST 18)
    // =========================================================================

    @Test
    @DisplayName("TEST 16: INITIAL y REFUND concurrentes -> Estado determinista y consistente")
    void test16_concurrentInitialAndRefundDeterministic() throws Exception {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "ConcBuyer16");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<TebexService.ExecutionResult> fFulfill = pool.submit(() -> {
            latch.await();
            return tebexService.fulfill("tx_16", uuid, "pkg_lc_500", 1, "ConcBuyer16");
        });

        Future<TebexService.ExecutionResult> fRefund = pool.submit(() -> {
            latch.await();
            return tebexService.refund("tx_16", uuid, "pkg_lc_500");
        });

        latch.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        fFulfill.get();
        fRefund.get();

        long finalBalance = dbState.getBalance(playerId);
        assertTrue(finalBalance == 0L || finalBalance == 500L, "Saldo final debe ser 0 o 500, sin corrupción");
        assertTrue(finalBalance >= 0L, "Saldo nunca debe ser negativo");
    }

    @Test
    @DisplayName("TEST 17: INITIAL y CHARGEBACK concurrentes -> Determinista sin saldos negativos")
    void test17_concurrentInitialAndChargebackDeterministic() throws Exception {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "ConcBuyer17");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<TebexService.ExecutionResult> fFulfill = pool.submit(() -> {
            latch.await();
            return tebexService.fulfill("tx_17", uuid, "pkg_lc_2010", 1, "ConcBuyer17");
        });

        Future<TebexService.ExecutionResult> fCb = pool.submit(() -> {
            latch.await();
            return tebexService.chargeback("tx_17", uuid, "pkg_lc_2010");
        });

        latch.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        fFulfill.get();
        fCb.get();

        long finalBalance = dbState.getBalance(playerId);
        assertTrue(finalBalance == 0L || finalBalance == 2010L);
        assertTrue(finalBalance >= 0L);
    }

    @Test
    @DisplayName("TEST 18: REFUND y CHARGEBACK concurrentes -> Exactamente 1 reversión económica aplicada")
    void test18_concurrentRefundAndChargebackZeroDoubleDebit() throws Exception {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "ConcBuyer18");

        tebexService.fulfill("tx_18", uuid, "pkg_lc_4375", 1, "ConcBuyer18");
        assertEquals(4375L, dbState.getBalance(playerId));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<TebexService.ExecutionResult> fRefund = pool.submit(() -> {
            latch.await();
            return tebexService.refund("tx_18", uuid, "pkg_lc_4375");
        });

        Future<TebexService.ExecutionResult> fCb = pool.submit(() -> {
            latch.await();
            return tebexService.chargeback("tx_18", uuid, "pkg_lc_4375");
        });

        latch.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        TebexService.ExecutionResult r1 = fRefund.get();
        TebexService.ExecutionResult r2 = fCb.get();

        assertTrue(r1.isSuccess());
        assertTrue(r2.isSuccess());

        // Cero doble débito: Saldo final debe ser estrictamente 0, no -4375
        assertEquals(0L, dbState.getBalance(playerId), "CERO doble débito permitido");
        assertEquals(2, dbState.getLedgerEntriesCount(), "Exactamente 1 crédito y 1 débito");
    }

    // =========================================================================
    // BLOQUE 6: CASOS BORDE Y RESILIENCIA (TEST 19 - TEST 24)
    // =========================================================================

    @Test
    @DisplayName("TEST 19: Reintento de Refund tras Refund ya completado (ALREADY_PROCESSED)")
    void test19_refundRetryIsIdempotent() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "RetryBuyer19");

        tebexService.fulfill("tx_19", uuid, "pkg_lc_500", 1, "RetryBuyer19");
        TebexService.ExecutionResult firstRefund = tebexService.refund("tx_19", uuid, "pkg_lc_500");
        assertTrue(firstRefund.isSuccess());
        assertEquals("COMPLETED", firstRefund.status());

        // Segundo intento
        TebexService.ExecutionResult retry = tebexService.refund("tx_19", uuid, "pkg_lc_500");
        assertEquals(TebexService.ResultType.ALREADY_PROCESSED, retry.type());
        assertEquals("COMPLETED", retry.status());
        assertEquals(0L, dbState.getBalance(playerId));
    }

    @Test
    @DisplayName("TEST 20: Chargeback tras Refund completado -> Actualiza evento a CHARGEBACK sin doble débito")
    void test20_chargebackAfterRefundUpdatesFinancialEventWithoutDoubleDebit() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Buyer20");

        tebexService.fulfill("tx_20", uuid, "pkg_lc_2010", 1, "Buyer20");
        tebexService.refund("tx_20", uuid, "pkg_lc_2010");
        assertEquals(0L, dbState.getBalance(playerId));

        // Llega contracargo posterior del banco
        TebexService.ExecutionResult cbRes = tebexService.chargeback("tx_20", uuid, "pkg_lc_2010");
        assertEquals(TebexService.ResultType.ALREADY_PROCESSED, cbRes.type());
        assertEquals("COMPLETED", cbRes.status());
        assertEquals(0L, dbState.getBalance(playerId), "No debe debitar nuevamente");
        assertEquals(2, dbState.getLedgerEntriesCount());

        MockReversalDatabaseState.FulfillmentRecord f = dbState.getFulfillment("tx_20", "pkg_lc_2010");
        assertEquals(TebexFinancialEvent.CHARGEBACK, f.financialEvent, "Debe actualizarse al evento de mayor severidad");
        assertEquals("CHARGEBACK", dbState.getPaymentStatus("tx_20"));
    }

    @Test
    @DisplayName("TEST 21: Refund antes de entrega efectiva -> Cancela con NOT_APPLICABLE sin débito ni degradación")
    void test21_refundBeforeDeliveryCancelsCleanly() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Buyer21");

        // Simular orden en estado PROCESSING
        dbState.injectFulfillmentWithStatus("tx_21", "pkg_lc_500", uuid, playerId, "LUNACOINS", "500", "PROCESSING");

        TebexService.ExecutionResult res = tebexService.refund("tx_21", uuid, "pkg_lc_500");
        assertTrue(res.isSuccess());
        assertEquals("REFUND", res.status());

        MockReversalDatabaseState.FulfillmentRecord f = dbState.getFulfillment("tx_21", "pkg_lc_500");
        assertEquals(TebexReversalStatus.NOT_APPLICABLE, f.reversalStatus);
        assertEquals(0L, dbState.getBalance(playerId), "No debe debitarse porque nunca se acreditó");
        assertEquals(0, dbState.getLedgerEntriesCount());
    }

    @Test
    @DisplayName("TEST 22: Transacción multi-paquete -> Refund solo revierte el paquete solicitado")
    void test22_multiPackageTransactionRefundsOnlyTargetPackage() {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "MultiBuyer22");

        // Misma tx, 2 paquetes distintos
        tebexService.fulfill("tx_22_multi", uuid, "pkg_lc_500", 1, "MultiBuyer22");
        tebexService.fulfill("tx_22_multi", uuid, "pkg_rank_elite", 1, "MultiBuyer22");

        assertEquals(500L, dbState.getBalance(playerId));
        assertEquals(Tablist.Rank.ELITE, dbState.getRank(playerId));

        // Reembolsar solo LunaCoins
        TebexService.ExecutionResult res = tebexService.refund("tx_22_multi", uuid, "pkg_lc_500");
        assertTrue(res.isSuccess());
        assertEquals(0L, dbState.getBalance(playerId));
        assertEquals(Tablist.Rank.ELITE, dbState.getRank(playerId), "El rango debe permanecer DELIVERED");

        MockReversalDatabaseState.FulfillmentRecord fCoins = dbState.getFulfillment("tx_22_multi", "pkg_lc_500");
        MockReversalDatabaseState.FulfillmentRecord fRank = dbState.getFulfillment("tx_22_multi", "pkg_rank_elite");
        assertEquals(TebexReversalStatus.COMPLETED, fCoins.reversalStatus);
        assertEquals(TebexReversalStatus.NONE, fRank.reversalStatus);
        assertEquals("DELIVERED", fRank.status);
    }

    @Test
    @DisplayName("TEST 23: Reembolso de transacción inexistente -> REQUIRES_REVIEW (ORIGINAL_FULFILLMENT_NOT_FOUND)")
    void test23_refundMissingTransactionRequiresReview() {
        UUID uuid = UUID.randomUUID();

        TebexService.ExecutionResult res = tebexService.refund("tx_missing_99", uuid, "pkg_lc_500");
        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, res.type());
        assertTrue(res.reason().contains("ORIGINAL_FULFILLMENT_NOT_FOUND"));

        MockReversalDatabaseState.FulfillmentRecord f = dbState.getFulfillment("tx_missing_99", "pkg_lc_500");
        assertNotNull(f);
        assertEquals(TebexReversalStatus.REQUIRES_REVIEW, f.reversalStatus);
    }

    @Test
    @DisplayName("TEST 24: Mismatch de UUID en reembolso -> REQUIRES_REVIEW (UUID_MISMATCH)")
    void test24_refundUuidMismatchRequiresReview() {
        UUID realBuyer = UUID.randomUUID();
        UUID attackerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(realBuyer, "RealBuyer");

        tebexService.fulfill("tx_24_auth", realBuyer, "pkg_lc_4375", 1, "RealBuyer");
        assertEquals(4375L, dbState.getBalance(playerId));

        // Intento de reembolso con UUID no coincidente
        TebexService.ExecutionResult res = tebexService.refund("tx_24_auth", attackerUuid, "pkg_lc_4375");
        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, res.type());
        assertTrue(res.reason().contains("UUID_MISMATCH"));

        assertEquals(4375L, dbState.getBalance(playerId), "El saldo de la víctima no debe alterarse");
    }

    // =========================================================================
    // HARNESS DE PRUEBAS PARA REVERSIONES TEBEX
    // =========================================================================

    public static class MockReversalDatabaseState {
        private final Map<UUID, Long> uuidToPlayerId = new ConcurrentHashMap<>();
        private final Map<Long, Tablist.Rank> playerRanks = new ConcurrentHashMap<>();
        private final Map<Long, AtomicLong> playerBalances = new ConcurrentHashMap<>();
        private final Set<String> suitsOwned = ConcurrentHashMap.newKeySet();
        private final Set<String> ledgerKeys = ConcurrentHashMap.newKeySet();
        private final List<String> ledgerEntries = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, String> payments = new ConcurrentHashMap<>();
        private final Map<String, FulfillmentRecord> fulfillments = new ConcurrentHashMap<>();
        private final Map<Long, String> fulfillmentIdToKey = new ConcurrentHashMap<>();
        private final List<EffectRecord> effects = Collections.synchronizedList(new ArrayList<>());
        private final Map<Long, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
        private final AtomicLong playerIdCounter = new AtomicLong(100);
        private final AtomicLong fulfillmentIdCounter = new AtomicLong(1);
        private final AtomicLong effectIdCounter = new AtomicLong(1);

        public static class FulfillmentRecord {
            public final long id;
            public final String transactionId;
            public final String packageId;
            public final String playerUuid;
            public final Long playerId;
            public final String productType;
            public final String productValue;
            public String status;
            public TebexReversalStatus reversalStatus;
            public String reversalReason;
            public String failureReason;
            public TebexFinancialEvent financialEvent;

            public FulfillmentRecord(long id, String transactionId, String packageId, String playerUuid,
                                     Long playerId, String productType, String productValue, String status,
                                     TebexReversalStatus reversalStatus, TebexFinancialEvent financialEvent) {
                this.id = id;
                this.transactionId = transactionId;
                this.packageId = packageId;
                this.playerUuid = playerUuid;
                this.playerId = playerId;
                this.productType = productType;
                this.productValue = productValue;
                this.status = status;
                this.reversalStatus = reversalStatus;
                this.financialEvent = financialEvent;
            }
        }

        public static class EffectRecord {
            public final long id;
            public final long fulfillmentId;
            public final TebexEffectType type;
            public final String resourceId;
            public final String beforeValue;
            public final String afterValue;
            public final boolean createdByFulfillment;
            public boolean reversed;
            public Instant reversedAt;

            public EffectRecord(long id, long fulfillmentId, TebexEffectType type, String resourceId,
                                String beforeValue, String afterValue, boolean createdByFulfillment) {
                this.id = id;
                this.fulfillmentId = fulfillmentId;
                this.type = type;
                this.resourceId = resourceId;
                this.beforeValue = beforeValue;
                this.afterValue = afterValue;
                this.createdByFulfillment = createdByFulfillment;
                this.reversed = false;
                this.reversedAt = null;
            }
        }

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

        public long getBalance(long playerId) {
            return playerBalances.computeIfAbsent(playerId, k -> new AtomicLong(0)).get();
        }

        public void setBalance(long playerId, long amount) {
            playerBalances.computeIfAbsent(playerId, k -> new AtomicLong(0)).set(amount);
        }

        public synchronized long applyCreditAtomic(long playerId, long amount, String idempotencyKey) throws EconomyException {
            if (ledgerKeys.contains(idempotencyKey)) {
                throw new EconomyException(EconomyException.Kind.ALREADY_APPLIED, "Idempotency key duplicated: " + idempotencyKey);
            }
            ledgerKeys.add(idempotencyKey);
            ledgerEntries.add(idempotencyKey);
            return playerBalances.computeIfAbsent(playerId, k -> new AtomicLong(0)).addAndGet(amount);
        }

        public synchronized long applyDebitAtomic(long playerId, long amount, String idempotencyKey) throws EconomyException {
            if (ledgerKeys.contains(idempotencyKey)) {
                throw new EconomyException(EconomyException.Kind.ALREADY_APPLIED, "Idempotency key duplicated: " + idempotencyKey);
            }
            AtomicLong bal = playerBalances.computeIfAbsent(playerId, k -> new AtomicLong(0));
            if (bal.get() < amount) {
                throw new EconomyException(EconomyException.Kind.INSUFFICIENT_FUNDS, "Insufficient balance");
            }
            ledgerKeys.add(idempotencyKey);
            ledgerEntries.add(idempotencyKey);
            return bal.addAndGet(-amount);
        }

        public int getLedgerEntriesCount() {
            return ledgerEntries.size();
        }

        public boolean hasSuit(long playerId, String suitId) {
            return suitsOwned.contains(playerId + ":" + suitId);
        }

        public boolean grantSuit(long playerId, String suitId) {
            return suitsOwned.add(playerId + ":" + suitId);
        }

        public boolean removeSuit(long playerId, String suitId) {
            return suitsOwned.remove(playerId + ":" + suitId);
        }

        public void ensurePayment(String transactionId, String playerUuid) {
            payments.putIfAbsent(transactionId, "ACTIVE");
        }

        public void setPaymentStatus(String transactionId, String status) {
            payments.put(transactionId, status);
        }

        public String getPaymentStatus(String transactionId) {
            return payments.get(transactionId);
        }

        public FulfillmentRecord getFulfillment(String tx, String pkg) {
            return fulfillments.get(tx + ":" + pkg);
        }

        public synchronized long recordFulfillmentDelivered(String tx, String pkg, UUID uuid, Long playerId,
                                                            String productType, String productValue) {
            long id = fulfillmentIdCounter.incrementAndGet();
            FulfillmentRecord rec = new FulfillmentRecord(id, tx, pkg, uuid.toString(), playerId, productType,
                    productValue, "DELIVERED", TebexReversalStatus.NONE, TebexFinancialEvent.INITIAL);
            fulfillments.put(tx + ":" + pkg, rec);
            fulfillmentIdToKey.put(id, tx + ":" + pkg);
            return id;
        }

        public synchronized void injectDeliveredFulfillment(String tx, String pkg, UUID uuid, Long playerId,
                                                            String productType, String productValue) {
            recordFulfillmentDelivered(tx, pkg, uuid, playerId, productType, productValue);
        }

        public synchronized void injectFulfillmentWithStatus(String tx, String pkg, UUID uuid, Long playerId,
                                                             String productType, String productValue, String status) {
            long id = fulfillmentIdCounter.incrementAndGet();
            FulfillmentRecord rec = new FulfillmentRecord(id, tx, pkg, uuid.toString(), playerId, productType,
                    productValue, status, TebexReversalStatus.NONE, TebexFinancialEvent.INITIAL);
            fulfillments.put(tx + ":" + pkg, rec);
            fulfillmentIdToKey.put(id, tx + ":" + pkg);
        }

        public synchronized void recordReview(String tx, String pkg, UUID uuid, String productType, String productValue, String reason) {
            FulfillmentRecord existing = fulfillments.get(tx + ":" + pkg);
            if (existing != null) {
                existing.status = "REQUIRES_REVIEW";
                existing.reversalStatus = TebexReversalStatus.REQUIRES_REVIEW;
                existing.failureReason = reason;
                existing.reversalReason = reason;
            } else {
                long id = fulfillmentIdCounter.incrementAndGet();
                FulfillmentRecord rec = new FulfillmentRecord(id, tx, pkg, uuid.toString(), null, productType,
                        productValue, "REQUIRES_REVIEW", TebexReversalStatus.REQUIRES_REVIEW, TebexFinancialEvent.INITIAL);
                rec.failureReason = reason;
                rec.reversalReason = reason;
                fulfillments.put(tx + ":" + pkg, rec);
                fulfillmentIdToKey.put(id, tx + ":" + pkg);
            }
        }

        public synchronized void updateReversal(long fId, String status, TebexReversalStatus revStatus,
                                                String reason, TebexFinancialEvent finEvent) {
            String key = fulfillmentIdToKey.get(fId);
            if (key != null) {
                FulfillmentRecord rec = fulfillments.get(key);
                if (rec != null) {
                    rec.status = status;
                    rec.reversalStatus = revStatus;
                    rec.reversalReason = reason;
                    rec.failureReason = reason;
                    rec.financialEvent = finEvent;
                }
            }
        }

        public synchronized void updateReversalDirect(String tx, String pkg, String status,
                                                      TebexReversalStatus revStatus, String reason,
                                                      TebexFinancialEvent finEvent) {
            FulfillmentRecord rec = fulfillments.get(tx + ":" + pkg);
            if (rec != null) {
                rec.status = status;
                rec.reversalStatus = revStatus;
                rec.reversalReason = reason;
                rec.failureReason = reason;
                rec.financialEvent = finEvent;
            }
        }

        public synchronized void updateFinancialEventOnly(long fId, TebexFinancialEvent finEvent) {
            String key = fulfillmentIdToKey.get(fId);
            if (key != null) {
                FulfillmentRecord rec = fulfillments.get(key);
                if (rec != null) {
                    rec.financialEvent = finEvent;
                    rec.status = finEvent.name();
                }
            }
        }

        public synchronized void recordEffect(long fId, TebexEffectType type, String resourceId,
                                              String beforeValue, String afterValue, boolean createdByFulfillment) {
            long id = effectIdCounter.incrementAndGet();
            effects.add(new EffectRecord(id, fId, type, resourceId, beforeValue, afterValue, createdByFulfillment));
        }

        public synchronized EffectRecord getEffect(long fId, TebexEffectType type) {
            for (EffectRecord e : effects) {
                if (e.fulfillmentId == fId && e.type == type) {
                    return e;
                }
            }
            return null;
        }

        public synchronized void markEffectsReversed(long fId) {
            for (EffectRecord e : effects) {
                if (e.fulfillmentId == fId) {
                    e.reversed = true;
                    e.reversedAt = Instant.now();
                }
            }
        }

        public synchronized boolean isEffectReversed(long fId, TebexEffectType type) {
            EffectRecord e = getEffect(fId, type);
            return e != null && e.reversed;
        }
    }

    public static class TestableTebexReversalService extends TebexService {
        private final MockReversalDatabaseState state;

        public TestableTebexReversalService(PackageRegistry registry, MockReversalDatabaseState state) {
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
                        if ("executeUpdate".equals(m.getName())) return 1;
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
        protected void ensurePaymentRecord(Connection c, String transactionId, String playerUuid) {
            state.ensurePayment(transactionId, playerUuid);
        }

        @Override
        protected void checkAndUpdatePaymentStatus(Connection c, String transactionId, String targetStatus) {
            state.setPaymentStatus(transactionId, targetStatus);
        }

        @Override
        protected FulfillmentRow getFulfillmentRowForUpdate(Connection c, String transactionId, String packageId) {
            MockReversalDatabaseState.FulfillmentRecord rec = state.getFulfillment(transactionId, packageId);
            if (rec == null) return null;
            return new FulfillmentRow(
                    rec.id,
                    rec.transactionId,
                    rec.packageId,
                    rec.playerUuid,
                    rec.playerId,
                    rec.productType,
                    rec.productValue,
                    TebexStatus.de(rec.status),
                    rec.reversalStatus,
                    rec.financialEvent
            );
        }

        @Override
        protected TebexStatus getFulfillmentStatusForUpdate(Connection c, String transactionId, String packageId) {
            MockReversalDatabaseState.FulfillmentRecord rec = state.getFulfillment(transactionId, packageId);
            return rec != null ? TebexStatus.de(rec.status) : null;
        }

        @Override
        protected long markFulfillmentDeliveredAndGetId(Connection c, String transactionId, String packageId,
                                                        UUID playerUuid, Long playerId, String productType, String productValue) {
            return state.recordFulfillmentDelivered(transactionId, packageId, playerUuid, playerId, productType, productValue);
        }

        @Override
        protected void markFulfillmentDelivered(Connection c, String transactionId, String packageId,
                                                UUID playerUuid, Long playerId, String productType, String productValue) {
            state.recordFulfillmentDelivered(transactionId, packageId, playerUuid, playerId, productType, productValue);
        }

        @Override
        protected long getFulfillmentId(Connection c, String transactionId, String packageId) {
            MockReversalDatabaseState.FulfillmentRecord rec = state.getFulfillment(transactionId, packageId);
            return rec != null ? rec.id : -1L;
        }

        @Override
        protected void recordEffect(Connection c, long fulfillmentId, TebexEffectType type, String resourceId,
                                    String beforeValue, String afterValue, boolean createdByFulfillment) {
            state.recordEffect(fulfillmentId, type, resourceId, beforeValue, afterValue, createdByFulfillment);
        }

        @Override
        protected TebexFulfillmentEffect readEffect(Connection c, long fulfillmentId, TebexEffectType effectType) {
            MockReversalDatabaseState.EffectRecord rec = state.getEffect(fulfillmentId, effectType);
            if (rec == null) return null;
            return new TebexFulfillmentEffect(
                    rec.id,
                    rec.fulfillmentId,
                    rec.type,
                    rec.resourceId,
                    rec.beforeValue,
                    rec.afterValue,
                    rec.createdByFulfillment,
                    rec.reversed,
                    rec.reversedAt
            );
        }

        @Override
        protected void markEffectsReversed(Connection c, long fulfillmentId) {
            state.markEffectsReversed(fulfillmentId);
        }

        @Override
        protected Tablist.Rank readBaselineRank(Connection c, long playerId) {
            for (MockReversalDatabaseState.EffectRecord e : state.effects) {
                String key = state.fulfillmentIdToKey.get(e.fulfillmentId);
                if (key != null) {
                    MockReversalDatabaseState.FulfillmentRecord f = state.fulfillments.get(key);
                    if (f != null && f.playerId != null && f.playerId == playerId && e.type == TebexEffectType.RANK_CHANGE) {
                        if (e.beforeValue != null && !e.beforeValue.isBlank()) {
                            return Tablist.Rank.de(e.beforeValue);
                        }
                    }
                }
            }
            return Tablist.Rank.ENTRENADOR;
        }

        @Override
        protected List<TebexActiveRankFulfillment> readSubsequentActiveRankFulfillments(Connection c, long playerId, long fulfillmentId) {
            List<TebexActiveRankFulfillment> list = new ArrayList<>();
            for (MockReversalDatabaseState.FulfillmentRecord f : state.fulfillments.values()) {
                if (f.playerId != null && f.playerId == playerId && f.id > fulfillmentId
                        && "DELIVERED".equals(f.status) && f.reversalStatus != TebexReversalStatus.COMPLETED) {
                    list.add(new TebexActiveRankFulfillment(f.id, f.packageId, f.productType, f.productValue));
                }
            }
            list.sort(Comparator.comparingLong(TebexActiveRankFulfillment::id));
            return list;
        }

        @Override
        protected List<TebexActiveRankFulfillment> readAllActiveRankFulfillmentsExcluding(Connection c, long playerId, long excludedFulfillmentId) {
            List<TebexActiveRankFulfillment> list = new ArrayList<>();
            for (MockReversalDatabaseState.FulfillmentRecord f : state.fulfillments.values()) {
                if (f.playerId != null && f.playerId == playerId && f.id != excludedFulfillmentId
                        && "DELIVERED".equals(f.status) && f.reversalStatus != TebexReversalStatus.COMPLETED) {
                    list.add(new TebexActiveRankFulfillment(f.id, f.packageId, f.productType, f.productValue));
                }
            }
            list.sort(Comparator.comparingLong(TebexActiveRankFulfillment::id));
            return list;
        }

        @Override
        protected boolean isSuitGrantedByOtherActiveFulfillment(Connection c, long playerId, long excludedFulfillmentId, String suitId) {
            for (MockReversalDatabaseState.FulfillmentRecord f : state.fulfillments.values()) {
                if (f.playerId != null && f.playerId == playerId && f.id != excludedFulfillmentId
                        && "DELIVERED".equals(f.status) && f.reversalStatus != TebexReversalStatus.COMPLETED) {
                    MockReversalDatabaseState.EffectRecord eff = state.getEffect(f.id, TebexEffectType.SUIT_GRANT);
                    if (eff != null && suitId.equalsIgnoreCase(eff.resourceId)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        protected Tablist.Rank readRankForUpdate(Connection c, long playerId) {
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
        protected boolean playerHadSuit(Connection c, long playerId, Traje traje) {
            if (traje == null || traje.gratis()) return true;
            return state.hasSuit(playerId, traje.id());
        }

        @Override
        protected boolean applySuitGrant(Connection c, long playerId, Traje traje) {
            if (traje == null || traje.gratis()) return false;
            return state.grantSuit(playerId, traje.id());
        }

        @Override
        protected boolean applySuitRemoval(Connection c, long playerId, Traje traje) {
            if (traje == null || traje.gratis()) return false;
            return state.removeSuit(playerId, traje.id());
        }

        @Override
        protected long readEconomyBalanceForUpdate(Connection c, long playerId, Currency currency) {
            return state.getBalance(playerId);
        }

        @Override
        protected long applyEconomyCredit(Connection c, long playerId, long amount, String idempotencyKey, String reason)
                throws EconomyException {
            return state.applyCreditAtomic(playerId, amount, idempotencyKey);
        }

        @Override
        protected long applyEconomyDebit(Connection c, long playerId, long amount, String idempotencyKey, String reason)
                throws EconomyException {
            return state.applyDebitAtomic(playerId, amount, idempotencyKey);
        }

        @Override
        protected void recordFulfillmentReview(Connection c, String transactionId, String packageId, UUID playerUuid,
                                               String productType, String productValue, String reason) {
            state.recordReview(transactionId, packageId, playerUuid, productType, productValue, reason);
        }

        @Override
        protected void updateFulfillmentReversal(Connection c, long fulfillmentId, String status,
                                                 TebexReversalStatus reversalStatus, String reason,
                                                 TebexFinancialEvent financialEvent) {
            state.updateReversal(fulfillmentId, status, reversalStatus, reason, financialEvent);
        }

        @Override
        protected void updateFulfillmentReversalDirect(Connection c, String transactionId, String packageId,
                                                       String status, TebexReversalStatus reversalStatus,
                                                       String reason, TebexFinancialEvent financialEvent) {
            state.updateReversalDirect(transactionId, packageId, status, reversalStatus, reason, financialEvent);
        }

        @Override
        protected void updateFulfillmentFinancialEventOnly(Connection c, long fulfillmentId, TebexFinancialEvent financialEvent) {
            state.updateFinancialEventOnly(fulfillmentId, financialEvent);
        }

        @Override
        protected void updateFulfillmentStatus(Connection c, long fulfillmentId, String status,
                                               TebexReversalStatus reversalStatus, String reason,
                                               TebexFinancialEvent financialEvent) {
            state.updateReversal(fulfillmentId, status, reversalStatus, reason, financialEvent);
        }

        @Override
        public ExecutionResult fulfill(String transactionId, UUID playerUuid, String packageId, int quantity, String usernameHint) {
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

        @Override
        public ExecutionResult refund(String transactionId, UUID playerUuid, String packageId) {
            String rowLockKey = (transactionId + ":" + (packageId != null ? packageId.trim() : "")).intern();
            synchronized (rowLockKey) {
                long playerId = state.resolveOrCreatePlayer(playerUuid, null);
                ReentrantLock lock = state.getLockForPlayer(playerId);
                try {
                    return super.refund(transactionId, playerUuid, packageId);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        }

        @Override
        public ExecutionResult chargeback(String transactionId, UUID playerUuid, String packageId) {
            String rowLockKey = (transactionId + ":" + (packageId != null ? packageId.trim() : "")).intern();
            synchronized (rowLockKey) {
                long playerId = state.resolveOrCreatePlayer(playerUuid, null);
                ReentrantLock lock = state.getLockForPlayer(playerId);
                try {
                    return super.chargeback(transactionId, playerUuid, packageId);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        }
    }
}
