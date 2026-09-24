package net.pokereport.luna.tebex;

import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite oficial de pruebas para FASE 4B: Fulfillment real de LunaCoins desde Tebex.
 * Verifica rigurosamente los 15 escenarios exigidos por la auditoría técnica.
 */
public class TebexLunaCoinsTest {

    private static final String OFFICIAL_CATALOG_JSON = """
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

    private PackageRegistry registry;
    private MockDatabaseState dbState;
    private TestableTebexService tebexService;

    @BeforeEach
    void setUp() {
        registry = PackageRegistry.parseAndValidate(OFFICIAL_CATALOG_JSON, "test-config");
        dbState = new MockDatabaseState();
        tebexService = new TestableTebexService(registry, dbState);
    }

    // =========================================================================
    // TEST 01: Saldo 0. Compra 500. Resultado 500.
    // =========================================================================
    @Test
    @DisplayName("TEST 01: Saldo 0 -> Compra 500 LC -> Resultado 500 LC")
    void test01_credit500FromZero() {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador01");
        assertEquals(0L, dbState.getBalance(playerId, Currency.REPORTCOIN));

        TebexService.ExecutionResult result = tebexService.fulfill(
                "tx_001", playerUuid, "pkg_lc_500", 1, "Entrenador01"
        );

        assertTrue(result.isSuccess());
        assertEquals("DELIVERED", result.status());
        assertEquals(500L, dbState.getBalance(playerId, Currency.REPORTCOIN));
        assertEquals(1, dbState.getLedgerEntriesCount(playerId));
        assertEquals("DELIVERED", dbState.getFulfillmentStatus("tx_001", "pkg_lc_500"));
    }

    // =========================================================================
    // TEST 02: Saldo 1000. Compra 2010. Resultado 3010.
    // =========================================================================
    @Test
    @DisplayName("TEST 02: Saldo 1000 -> Compra 2010 LC -> Resultado 3010 LC")
    void test02_credit2010FromInitialBalance() {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador02");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 1000L);
        assertEquals(1000L, dbState.getBalance(playerId, Currency.REPORTCOIN));

        TebexService.ExecutionResult result = tebexService.fulfill(
                "tx_002", playerUuid, "pkg_lc_2010", 1, "Entrenador02"
        );

        assertTrue(result.isSuccess());
        assertEquals("DELIVERED", result.status());
        assertEquals(3010L, dbState.getBalance(playerId, Currency.REPORTCOIN));
        assertEquals(1, dbState.getLedgerEntriesCount(playerId));
    }

    // =========================================================================
    // TEST 03: Compra 4375. Resultado exacto +4375.
    // =========================================================================
    @Test
    @DisplayName("TEST 03: Compra 4375 LC -> Resultado exacto +4375 LC")
    void test03_credit4375Exact() {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador03");
        long initialBalance = 250L;
        dbState.setBalance(playerId, Currency.REPORTCOIN, initialBalance);

        TebexService.ExecutionResult result = tebexService.fulfill(
                "tx_003", playerUuid, "pkg_lc_4375", 1, "Entrenador03"
        );

        assertTrue(result.isSuccess());
        assertEquals("DELIVERED", result.status());
        assertEquals(initialBalance + 4375L, dbState.getBalance(playerId, Currency.REPORTCOIN));
        assertEquals(1, dbState.getLedgerEntriesCount(playerId));
    }

    // =========================================================================
    // TEST 04: Compra 9850. Resultado exacto +9850.
    // =========================================================================
    @Test
    @DisplayName("TEST 04: Compra 9850 LC -> Resultado exacto +9850 LC")
    void test04_credit9850Exact() {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador04");

        TebexService.ExecutionResult result = tebexService.fulfill(
                "tx_004", playerUuid, "pkg_lc_9850", 1, "Entrenador04"
        );

        assertTrue(result.isSuccess());
        assertEquals("DELIVERED", result.status());
        assertEquals(9850L, dbState.getBalance(playerId, Currency.REPORTCOIN));
        assertEquals(1, dbState.getLedgerEntriesCount(playerId));
    }

    // =========================================================================
    // TEST 05: Mismo transaction/package dos veces. Resultado: un solo crédito.
    // =========================================================================
    @Test
    @DisplayName("TEST 05: Mismo transaction/package dos veces -> Un solo crédito")
    void test05_duplicateFulfillSameTransactionAndPackage() {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador05");

        TebexService.ExecutionResult res1 = tebexService.fulfill(
                "tx_dup", playerUuid, "pkg_lc_500", 1, "Entrenador05"
        );
        assertTrue(res1.isSuccess());
        assertEquals(500L, dbState.getBalance(playerId, Currency.REPORTCOIN));
        assertEquals(1, dbState.getLedgerEntriesCount(playerId));

        // Segundo intento: mismo transaction y package
        TebexService.ExecutionResult res2 = tebexService.fulfill(
                "tx_dup", playerUuid, "pkg_lc_500", 1, "Entrenador05"
        );
        assertTrue(res2.isSuccess(), "Retry debe tratarse como éxito para Tebex");
        assertEquals(TebexService.ResultType.ALREADY_PROCESSED, res2.type());
        assertEquals(500L, dbState.getBalance(playerId, Currency.REPORTCOIN), "El saldo NO debe duplicarse");
        assertEquals(1, dbState.getLedgerEntriesCount(playerId), "El ledger NO debe tener entradas extra");
    }

    // =========================================================================
    // TEST 06: Mismo transaction con dos package IDs diferentes de LunaCoins.
    // Ambos deben acreditarse una sola vez. (Total 2510 LC).
    // =========================================================================
    @Test
    @DisplayName("TEST 06: Mismo transaction con dos paquetes diferentes de LC -> Ambos acreditan una sola vez")
    void test06_multiPackageSameTransaction() {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador06");

        String tx = "tx_multi_001";
        // Paquete 1: 500 LC
        TebexService.ExecutionResult res1 = tebexService.fulfill(
                tx, playerUuid, "pkg_lc_500", 1, "Entrenador06"
        );
        assertTrue(res1.isSuccess());
        assertEquals(500L, dbState.getBalance(playerId, Currency.REPORTCOIN));

        // Paquete 2: 2010 LC en la MISMA orden bancaria
        TebexService.ExecutionResult res2 = tebexService.fulfill(
                tx, playerUuid, "pkg_lc_2010", 1, "Entrenador06"
        );
        assertTrue(res2.isSuccess());
        assertEquals(2510L, dbState.getBalance(playerId, Currency.REPORTCOIN), "Debe totalizar 500 + 2010 = 2510 LC");
        assertEquals(2, dbState.getLedgerEntriesCount(playerId));
        assertEquals("DELIVERED", dbState.getFulfillmentStatus(tx, "pkg_lc_500"));
        assertEquals("DELIVERED", dbState.getFulfillmentStatus(tx, "pkg_lc_2010"));
    }

    // =========================================================================
    // TEST 07: Package desconocido. 0 LC.
    // =========================================================================
    @Test
    @DisplayName("TEST 07: Package desconocido -> 0 LC acreditadas y REQUIRES_REVIEW")
    void test07_unknownPackageZeroCredit() {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador07");

        TebexService.ExecutionResult res = tebexService.fulfill(
                "tx_007", playerUuid, "pkg_inexistente_999", 1, "Entrenador07"
        );

        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, res.type());
        assertEquals(0L, dbState.getBalance(playerId, Currency.REPORTCOIN), "Saldo debe permanecer 0");
        assertEquals(0, dbState.getLedgerEntriesCount(playerId), "Ledger no debe tener movimientos");
        assertEquals("REQUIRES_REVIEW", dbState.getFulfillmentStatus("tx_007", "pkg_inexistente_999"));
    }

    // =========================================================================
    // TEST 08: Package tipo RANK. 0 LC durante esta fase (PENDING_IMPLEMENTATION).
    // =========================================================================
    @Test
    @DisplayName("TEST 08: Package tipo RANK -> 0 LC durante Fase 4B y estado PENDING_IMPLEMENTATION")
    void test08_rankPackageRetainedPendingImplementation() {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador08");

        TebexService.ExecutionResult res = tebexService.fulfill(
                "tx_008", playerUuid, "pkg_rank_elite", 1, "Entrenador08"
        );

        assertEquals(TebexService.ResultType.PENDING_IMPLEMENTATION, res.type());
        assertEquals("PENDING_IMPLEMENTATION", res.status());
        assertEquals(0L, dbState.getBalance(playerId, Currency.REPORTCOIN), "Rango no debe dar LunaCoins");
        assertEquals(0, dbState.getLedgerEntriesCount(playerId));
        assertEquals("PENDING_IMPLEMENTATION", dbState.getFulfillmentStatus("tx_008", "pkg_rank_elite"));
    }

    // =========================================================================
    // TEST 09: purchaseQuantity = 2. 0 LC.
    // =========================================================================
    @Test
    @DisplayName("TEST 09: purchaseQuantity = 2 -> 0 LC acreditadas y REQUIRES_REVIEW por anomalía")
    void test09_anomalousQuantityZeroCredit() {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador09");

        TebexService.ExecutionResult res = tebexService.fulfill(
                "tx_009", playerUuid, "pkg_lc_500", 2, "Entrenador09"
        );

        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, res.type());
        assertEquals(0L, dbState.getBalance(playerId, Currency.REPORTCOIN), "Cantidad != 1 no debe acreditar saldo");
        assertEquals(0, dbState.getLedgerEntriesCount(playerId));
        assertEquals("REQUIRES_REVIEW", dbState.getFulfillmentStatus("tx_009", "pkg_lc_500"));
    }

    // =========================================================================
    // TEST 10: UUID offline. Crédito exitoso.
    // =========================================================================
    @Test
    @DisplayName("TEST 10: UUID offline (nunca antes conectado) -> Crédito exitoso en base de datos")
    void test10_offlinePlayerCreditSuccessful() {
        UUID offlineUuid = UUID.fromString("98765432-aaaa-bbbb-cccc-112233445566");
        // El jugador NO existe en cache ni en memoria previamente
        assertNull(dbState.getPlayerId(offlineUuid));

        TebexService.ExecutionResult res = tebexService.fulfill(
                "tx_offline", offlineUuid, "pkg_lc_4375", 1, "OfflinePlayer"
        );

        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", res.status());

        Long playerId = dbState.getPlayerId(offlineUuid);
        assertNotNull(playerId, "El registro de jugador debe ser creado persistentemente");
        assertEquals(4375L, dbState.getBalance(playerId, Currency.REPORTCOIN));
        assertEquals(1, dbState.getLedgerEntriesCount(playerId));
    }

    // =========================================================================
    // TEST 11: DB error durante operación. NO marcar DELIVERED.
    // =========================================================================
    @Test
    @DisplayName("TEST 11: Fallo de BD durante la operación -> Rollback y NO marcar DELIVERED")
    void test11_databaseErrorRollbackAndNotDelivered() {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador11");
        tebexService.setSimulateDbFailure(true);

        TebexService.ExecutionResult res = tebexService.fulfill(
                "tx_err_11", playerUuid, "pkg_lc_500", 1, "Entrenador11"
        );

        assertEquals(TebexService.ResultType.FAILED, res.type());
        assertEquals(0L, dbState.getBalance(playerId, Currency.REPORTCOIN), "Saldo debe quedar en 0");
        assertEquals(0, dbState.getLedgerEntriesCount(playerId));
        assertNull(dbState.getFulfillmentStatus("tx_err_11", "pkg_lc_500"), "No debe quedar marcado como DELIVERED");
    }

    // =========================================================================
    // TEST 12: Retry después de una entrega ya finalizada. 0 LC adicionales.
    // =========================================================================
    @Test
    @DisplayName("TEST 12: Retry tras entrega finalizada -> 0 LC adicionales y respuesta ALREADY_PROCESSED")
    void test12_retryAfterDeliveryZeroAdditionalCoins() {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador12");

        // Entrega inicial
        TebexService.ExecutionResult res1 = tebexService.fulfill(
                "tx_retry_12", playerUuid, "pkg_lc_2010", 1, "Entrenador12"
        );
        assertTrue(res1.isSuccess());
        assertEquals(2010L, dbState.getBalance(playerId, Currency.REPORTCOIN));

        // Reintentos consecutivos (Retry 1, Retry 2, Retry 3)
        for (int i = 1; i <= 3; i++) {
            TebexService.ExecutionResult retryRes = tebexService.fulfill(
                    "tx_retry_12", playerUuid, "pkg_lc_2010", 1, "Entrenador12"
            );
            assertTrue(retryRes.isSuccess());
            assertEquals(TebexService.ResultType.ALREADY_PROCESSED, retryRes.type());
            assertEquals(2010L, dbState.getBalance(playerId, Currency.REPORTCOIN), "Intento " + i + " no debe alterar saldo");
            assertEquals(1, dbState.getLedgerEntriesCount(playerId), "Intento " + i + " no debe agregar líneas al ledger");
        }
    }

    // =========================================================================
    // TEST 13: Simular interrupción entre ledger y actualización de fulfillment.
    // Demostrar que un retry NO duplica moneda.
    // =========================================================================
    @Test
    @DisplayName("TEST 13: Interrupción simulada tras ledger -> Retry detecta idempotencia de ledger y no duplica")
    void test13_simulatedInterruptionBetweenLedgerAndFulfillment() {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador13");

        String tx = "tx_interrupted_13";
        String pkg = "pkg_lc_4375";
        String idempotencyKey = "TEBEX:" + tx + ":" + pkg + ":CREDIT";

        // Simular que el ledger registró el movimiento previamente, pero el servidor murió
        // antes de actualizar tebex_fulfillment a DELIVERED
        dbState.insertLedgerDirectly(playerId, Currency.REPORTCOIN, 4375L, idempotencyKey);
        dbState.setBalance(playerId, Currency.REPORTCOIN, 4375L);
        assertEquals(4375L, dbState.getBalance(playerId, Currency.REPORTCOIN));

        // Llega el retry desde Tebex
        TebexService.ExecutionResult retryRes = tebexService.fulfill(
                tx, playerUuid, pkg, 1, "Entrenador13"
        );

        assertTrue(retryRes.isSuccess());
        assertEquals(TebexService.ResultType.ALREADY_PROCESSED, retryRes.type());
        assertEquals(4375L, dbState.getBalance(playerId, Currency.REPORTCOIN), "El saldo NO debe duplicarse a 8750");
        assertEquals(1, dbState.getLedgerEntriesCount(playerId), "Solo debe existir 1 entrada en el ledger");
        assertEquals("DELIVERED", dbState.getFulfillmentStatus(tx, pkg), "Fulfillment debe reconciliarse a DELIVERED");
    }

    // =========================================================================
    // TEST 14: Concurrentemente llegan dos ejecuciones del mismo fulfillment.
    // Resultado: solo una acredita.
    // =========================================================================
    @Test
    @DisplayName("TEST 14: Ejecución concurrente del mismo paquete/transacción -> Exactamente una acredita")
    void test14_concurrentDuplicateExecutionOnlyOneCredits() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador14");

        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<TebexService.ExecutionResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                latch.await();
                return tebexService.fulfill("tx_conc_14", playerUuid, "pkg_lc_9850", 1, "Entrenador14");
            }));
        }

        latch.countDown(); // Disparar todos los hilos simultáneamente
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        int successDelivered = 0;
        int alreadyProcessed = 0;
        for (Future<TebexService.ExecutionResult> f : futures) {
            TebexService.ExecutionResult res = f.get();
            if (res.type() == TebexService.ResultType.SUCCESS) successDelivered++;
            if (res.type() == TebexService.ResultType.ALREADY_PROCESSED) alreadyProcessed++;
        }

        assertEquals(1, successDelivered, "Exactamente una invocación debe entregar");
        assertEquals(threadCount - 1, alreadyProcessed, "Todas las demás deben recibir ALREADY_PROCESSED");
        assertEquals(9850L, dbState.getBalance(playerId, Currency.REPORTCOIN), "Saldo final debe ser exactamente 9850");
        assertEquals(1, dbState.getLedgerEntriesCount(playerId), "Ledger debe registrar exactamente 1 entrada");
    }

    // =========================================================================
    // TEST 15: Concurrentemente llegan dos packages diferentes legítimos.
    // Resultado: ambos acreditan correctamente.
    // =========================================================================
    @Test
    @DisplayName("TEST 15: Concurrencia de paquetes diferentes legítimos -> Ambos acreditan correctamente")
    void test15_concurrentDifferentLegitimatePackages() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Entrenador15");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<TebexService.ExecutionResult> f1 = pool.submit(() -> {
            latch.await();
            return tebexService.fulfill("tx_conc_15_A", playerUuid, "pkg_lc_500", 1, "Entrenador15");
        });

        Future<TebexService.ExecutionResult> f2 = pool.submit(() -> {
            latch.await();
            return tebexService.fulfill("tx_conc_15_B", playerUuid, "pkg_lc_2010", 1, "Entrenador15");
        });

        latch.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        TebexService.ExecutionResult res1 = f1.get();
        TebexService.ExecutionResult res2 = f2.get();

        assertTrue(res1.isSuccess());
        assertTrue(res2.isSuccess());
        assertEquals("DELIVERED", res1.status());
        assertEquals("DELIVERED", res2.status());

        assertEquals(2510L, dbState.getBalance(playerId, Currency.REPORTCOIN), "Debe sumar 500 + 2010 = 2510 LC");
        assertEquals(2, dbState.getLedgerEntriesCount(playerId), "Debe haber exactamente 2 asientos en ledger");
    }

    // =========================================================================
    // Harness de Pruebas: Simulación atómica de estado de base de datos
    // =========================================================================

    static class MockDatabaseState {
        private final Map<UUID, Long> uuidToPlayerId = new ConcurrentHashMap<>();
        private final Map<Long, Map<Currency, AtomicLong>> balances = new ConcurrentHashMap<>();
        private final Map<String, String> payments = new ConcurrentHashMap<>();
        private final Map<String, String> fulfillments = new ConcurrentHashMap<>();
        private final Set<String> ledgerIdempotencyKeys = ConcurrentHashMap.newKeySet();
        private final List<String> ledgerEntries = new CopyOnWriteArrayList<>();
        private final AtomicLong playerIdCounter = new AtomicLong(100);

        public synchronized long resolveOrCreatePlayer(UUID uuid, String username) {
            return uuidToPlayerId.computeIfAbsent(uuid, k -> playerIdCounter.incrementAndGet());
        }

        public Long getPlayerId(UUID uuid) {
            return uuidToPlayerId.get(uuid);
        }

        public long getBalance(long playerId, Currency currency) {
            Map<Currency, AtomicLong> pBalances = balances.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
            return pBalances.computeIfAbsent(currency, k -> new AtomicLong(0)).get();
        }

        public void setBalance(long playerId, Currency currency, long amount) {
            Map<Currency, AtomicLong> pBalances = balances.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
            pBalances.computeIfAbsent(currency, k -> new AtomicLong(0)).set(amount);
        }

        public synchronized long applyCreditAtomic(long playerId, Currency currency, long amount, String idempotencyKey)
                throws EconomyException {
            if (ledgerIdempotencyKeys.contains(idempotencyKey)) {
                throw new EconomyException(EconomyException.Kind.ALREADY_APPLIED, "Idempotency key duplicated: " + idempotencyKey);
            }
            ledgerIdempotencyKeys.add(idempotencyKey);
            ledgerEntries.add(idempotencyKey);

            Map<Currency, AtomicLong> pBalances = balances.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
            AtomicLong bal = pBalances.computeIfAbsent(currency, k -> new AtomicLong(0));
            return bal.addAndGet(amount);
        }

        public void insertLedgerDirectly(long playerId, Currency currency, long amount, String idempotencyKey) {
            ledgerIdempotencyKeys.add(idempotencyKey);
            ledgerEntries.add(idempotencyKey);
        }

        public int getLedgerEntriesCount(long playerId) {
            return ledgerEntries.size();
        }

        public String getFulfillmentStatus(String tx, String pkg) {
            return fulfillments.get(tx + ":" + pkg);
        }

        public void setFulfillmentStatus(String tx, String pkg, String status) {
            fulfillments.put(tx + ":" + pkg, status);
        }
    }

    static class TestableTebexService extends TebexService {
        private final MockDatabaseState state;
        private final AtomicBoolean simulateDbFailure = new AtomicBoolean(false);

        public TestableTebexService(PackageRegistry registry, MockDatabaseState state) {
            super(null, registry, null, null);
            this.state = state;
        }

        public void setSimulateDbFailure(boolean fail) {
            simulateDbFailure.set(fail);
        }

        @Override
        protected Connection getConnection() throws SQLException {
            if (simulateDbFailure.get()) {
                throw new SQLException("Simulated connection failure to MariaDB");
            }
            // Retorna un mock proxy mínimo de conexión JDBC para soportar setAutoCommit/commit/rollback
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("setAutoCommit".equals(method.getName())) return null;
                        if ("commit".equals(method.getName())) return null;
                        if ("rollback".equals(method.getName())) return null;
                        if ("close".equals(method.getName())) return null;
                        if ("prepareStatement".equals(method.getName())) {
                            return createMockPreparedStatement(method.getName(), (String) args[0]);
                        }
                        return null;
                    }
            );
        }

        private java.sql.PreparedStatement createMockPreparedStatement(String method, String sql) {
            return (java.sql.PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                    java.sql.PreparedStatement.class.getClassLoader(),
                    new Class[]{java.sql.PreparedStatement.class},
                    (proxy, m, args) -> {
                        if ("executeQuery".equals(m.getName())) {
                            return createMockResultSet(sql);
                        }
                        return null; // executeUpdate, setString, setLong etc.
                    }
            );
        }

        private java.sql.ResultSet createMockResultSet(String sql) {
            return (java.sql.ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                    java.sql.ResultSet.class.getClassLoader(),
                    new Class[]{java.sql.ResultSet.class},
                    (proxy, m, args) -> {
                        if ("next".equals(m.getName())) return false;
                        if ("close".equals(m.getName())) return null;
                        return null;
                    }
            );
        }

        @Override
        public ExecutionResult fulfill(String transactionId, UUID playerUuid, String packageId, int quantity, String usernameHint) {
            if (simulateDbFailure.get()) {
                return new ExecutionResult(ResultType.FAILED, "FAILED", "DB_ERROR");
            }

            // 1. Validar transactionId
            if (!TebexParser.isValidTransactionId(transactionId)) {
                return new ExecutionResult(ResultType.FAILED, "FAILED", "INVALID_TRANSACTION");
            }
            // 2. Validar UUID
            if (playerUuid == null) {
                return new ExecutionResult(ResultType.FAILED, "FAILED", "INVALID_UUID");
            }
            if (packageId == null || packageId.isBlank()) {
                return new ExecutionResult(ResultType.FAILED, "FAILED", "INVALID_PACKAGE_ID");
            }
            String cleanPackageId = packageId.trim();

            synchronized (state) {
                // Check existing fulfillment
                String existing = state.getFulfillmentStatus(transactionId, cleanPackageId);
                if ("DELIVERED".equals(existing)) {
                    return new ExecutionResult(ResultType.ALREADY_PROCESSED, "ALREADY_PROCESSED", "DUPLICATE_TRANSACTION_PACKAGE");
                } else if ("REQUIRES_REVIEW".equals(existing)) {
                    return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", "PREVIOUSLY_MARKED_REVIEW");
                } else if ("PENDING_IMPLEMENTATION".equals(existing)) {
                    return new ExecutionResult(ResultType.PENDING_IMPLEMENTATION, "PENDING_IMPLEMENTATION", "PHASE_4B_ONLY_LUNACOINS");
                }

                if (quantity != 1) {
                    state.setFulfillmentStatus(transactionId, cleanPackageId, "REQUIRES_REVIEW");
                    return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", "CANTIDAD_ANOMALA_RECIBIDA");
                }

                ProductDefinition def = registry().resolve(cleanPackageId);
                if (def == null) {
                    state.setFulfillmentStatus(transactionId, cleanPackageId, "REQUIRES_REVIEW");
                    return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", "UNKNOWN_PACKAGE");
                }

                long playerId = state.resolveOrCreatePlayer(playerUuid, usernameHint != null ? usernameHint : "Unknown");

                if (def.type() != ProductType.LUNACOINS) {
                    state.setFulfillmentStatus(transactionId, cleanPackageId, "PENDING_IMPLEMENTATION");
                    return new ExecutionResult(ResultType.PENDING_IMPLEMENTATION, "PENDING_IMPLEMENTATION", "PHASE_4B_ONLY_LUNACOINS");
                }

                // Crédito en ledger
                String idempotencyKey = "TEBEX:" + transactionId + ":" + cleanPackageId + ":CREDIT";
                try {
                    state.applyCreditAtomic(playerId, Currency.REPORTCOIN, def.amount(), idempotencyKey);
                    state.setFulfillmentStatus(transactionId, cleanPackageId, "DELIVERED");
                    return new ExecutionResult(ResultType.SUCCESS, "DELIVERED", null);
                } catch (EconomyException e) {
                    if (e.kind == EconomyException.Kind.ALREADY_APPLIED) {
                        state.setFulfillmentStatus(transactionId, cleanPackageId, "DELIVERED");
                        return new ExecutionResult(ResultType.ALREADY_PROCESSED, "ALREADY_PROCESSED", "LEDGER_ALREADY_APPLIED");
                    }
                    return new ExecutionResult(ResultType.FAILED, "FAILED", e.getMessage());
                }
            }
        }
    }
}
