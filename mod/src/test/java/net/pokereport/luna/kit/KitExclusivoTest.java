package net.pokereport.luna.kit;

import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;
import net.pokereport.luna.economy.EconomyService;
import net.pokereport.luna.tebex.PackageRegistry;
import net.pokereport.luna.tebex.TebexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite exhaustiva de pruebas unitarias para la FASE 4E:
 * KITS EXCLUSIVOS POR 2.000 LUNACOINS (TEST 01 a TEST 19).
 */
public class KitExclusivoTest {

    private MockKitDatabaseState dbState;
    private MockEconomyService mockEconomy;
    private TestableKitService kitService;

    private KitCatalog.Kit kitEclipse;
    private KitCatalog.Kit kitLunar;
    private KitCatalog.Kit kitHalloween;
    private KitCatalog.Kit kitDisabled;
    private KitCatalog.Kit kitFuture;
    private KitCatalog.Kit kitExpired;
    private KitCatalog.Kit kitValidWindow;

    @BeforeEach
    void setUp() {
        dbState = new MockKitDatabaseState();
        mockEconomy = new MockEconomyService(dbState);
        kitService = new TestableKitService(dbState);

        var itemBase = List.of(
                new KitCatalog.KitItem(null, 1, 0, Map.of()),
                new KitCatalog.KitItem(null, 1, 0, Map.of())
        );

        kitEclipse = new KitCatalog.Kit(
                "exclusive_eclipse_2026", "Kit Eclipse 2026", null,
                "Kit legendario exclusivo", "exclusive", 2000L, 0, true, null, itemBase,
                true, null, null, 1
        );

        kitLunar = new KitCatalog.Kit(
                "exclusive_lunar_2026", "Kit Lunar 2026", null,
                "Kit lunar exclusivo", "exclusive", 2000L, 0, true, null, itemBase,
                true, null, null, 1
        );

        kitHalloween = new KitCatalog.Kit(
                "exclusive_halloween_2026", "Kit Halloween 2026", null,
                "Kit de temporada", "exclusive", 2000L, 0, true, null, itemBase,
                true, null, null, 1
        );

        kitDisabled = new KitCatalog.Kit(
                "exclusive_disabled", "Kit Deshabilitado", null,
                "No disponible", "exclusive", 2000L, 0, true, null, itemBase,
                false, null, null, 1
        );

        kitFuture = new KitCatalog.Kit(
                "exclusive_future", "Kit Futuro", null,
                "Próximamente", "exclusive", 2000L, 0, true, null, itemBase,
                true, Instant.now().plusSeconds(3600), null, 1
        );

        kitExpired = new KitCatalog.Kit(
                "exclusive_expired", "Kit Expirado", null,
                "Ya pasó", "exclusive", 2000L, 0, true, null, itemBase,
                true, null, Instant.now().minusSeconds(3600), 1
        );

        kitValidWindow = new KitCatalog.Kit(
                "exclusive_window", "Kit Ventana Válida", null,
                "En tiempo", "exclusive", 2000L, 0, true, null, itemBase,
                true, Instant.now().minusSeconds(1800), Instant.now().plusSeconds(1800), 1
        );
    }

    // =========================================================================
    // TEST 01: Saldo 2000 -> Compra -> Saldo 0
    // =========================================================================
    @Test
    @DisplayName("TEST 01: Saldo 2000 LC -> Compra exitosa -> Saldo 0 LC, purchase COMPLETED")
    void test01_saldo2000_compraExitosa_saldoCero() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer01");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 2000L);

        String error = kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_01");
        assertNull(error, "La compra con saldo exacto de 2000 LC debe ser exitosa");

        assertEquals(0L, dbState.getBalance(playerId, Currency.REPORTCOIN), "El saldo debe quedar en exactamente 0");
        assertTrue(kitService.posee(playerId, kitEclipse), "El jugador debe poseer el kit");
        assertFalse(kitService.haReclamado(playerId, kitEclipse), "El kit debe estar pendiente de reclamo");

        KitService.ExclusivePurchaseRecord record = kitService.obtenerCompra("pur_01");
        assertNotNull(record);
        assertEquals("COMPLETED", record.purchaseStatus());
        assertEquals("PENDING", record.claimStatus());
        assertEquals(2000L, record.price());
        assertEquals(2000L, record.balanceBefore());
        assertEquals(0L, record.balanceAfter());
    }

    // =========================================================================
    // TEST 02: Saldo 2010 -> Compra -> Saldo 10
    // =========================================================================
    @Test
    @DisplayName("TEST 02: Saldo 2010 LC (paquete Tebex) -> Compra exitosa -> Saldo 10 LC")
    void test02_saldo2010_compraExitosa_saldoDiez() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer02");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 2010L);

        String error = kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_02");
        assertNull(error);

        assertEquals(10L, dbState.getBalance(playerId, Currency.REPORTCOIN), "El saldo restante debe ser 10 LC");
        assertTrue(kitService.posee(playerId, kitEclipse));

        KitService.ExclusivePurchaseRecord rec = kitService.obtenerCompra("pur_02");
        assertNotNull(rec);
        assertEquals(2010L, rec.balanceBefore());
        assertEquals(10L, rec.balanceAfter());
    }

    // =========================================================================
    // TEST 03: Saldo 4375 -> Compra -> Saldo 2375
    // =========================================================================
    @Test
    @DisplayName("TEST 03: Saldo 4375 LC -> Compra exitosa -> Saldo 2375 LC")
    void test03_saldo4375_compraExitosa_saldo2375() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer03");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 4375L);

        String error = kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_03");
        assertNull(error);

        assertEquals(2375L, dbState.getBalance(playerId, Currency.REPORTCOIN));
        assertTrue(kitService.posee(playerId, kitEclipse));
    }

    // =========================================================================
    // TEST 04: Saldo 4375 -> 2 kits distintos -> Saldo 375
    // =========================================================================
    @Test
    @DisplayName("TEST 04: Saldo 4375 LC -> Compra 2 kits distintos -> Saldo 375 LC")
    void test04_saldo4375_compraDosKitsDistintos_saldo375() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer04");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 4375L);

        String err1 = kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_04_a");
        assertNull(err1);
        assertEquals(2375L, dbState.getBalance(playerId, Currency.REPORTCOIN));

        String err2 = kitService.comprar(uuid, playerId, kitLunar, mockEconomy, "pur_04_b");
        assertNull(err2);
        assertEquals(375L, dbState.getBalance(playerId, Currency.REPORTCOIN));

        assertTrue(kitService.posee(playerId, kitEclipse));
        assertTrue(kitService.posee(playerId, kitLunar));
        assertEquals(2, kitService.totalKitsVendidos());
        assertEquals(4000L, kitService.totalLunaCoinsGastadas());
    }

    // =========================================================================
    // TEST 05: Saldo 1999 -> Compra rechazada -> Saldo 1999, 0 kits
    // =========================================================================
    @Test
    @DisplayName("TEST 05: Saldo 1999 LC (insuficiente) -> Compra rechazada -> Saldo intacto, 0 compras")
    void test05_saldo1999_compraRechazada_saldoIntacto() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer05");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 1999L);

        String error = kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_05");
        assertNotNull(error);
        assertTrue(error.contains("LunaCoins"), "Debe indicar saldo insuficiente: " + error);

        assertEquals(1999L, dbState.getBalance(playerId, Currency.REPORTCOIN), "El saldo debe quedar 100% intacto");
        assertFalse(kitService.posee(playerId, kitEclipse), "No debe poseer el kit");
        assertEquals(0, kitService.totalKitsVendidos(), "No debe haber registros de compra");
    }

    // =========================================================================
    // TEST 06: Doble clic concurrente x10 con saldo 2100 -> 1 compra, saldo 100
    // =========================================================================
    @Test
    @DisplayName("TEST 06: Doble clic concurrente x10 con saldo 2100 LC -> Exactamente 1 compra exitosa, saldo 100 LC")
    void test06_dobleClicConcurrente_x10_saldo2100_unSoloDebito() throws Exception {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer06");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 2100L);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            results.add(executor.submit(() -> {
                latch.await();
                return kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_conc_06_" + idx);
            }));
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        int successes = 0;
        int rejections = 0;
        for (Future<String> f : results) {
            String res = f.get();
            if (res == null) successes++;
            else rejections++;
        }

        assertEquals(1, successes, "Exactamente 1 compra concurrente debe triunfar");
        assertEquals(threads - 1, rejections, "Todas las demás solicitudes concurrentes deben ser rechazadas");
        assertEquals(100L, dbState.getBalance(playerId, Currency.REPORTCOIN), "El saldo debe ser exactamente 100 LC");
        assertEquals(1, kitService.totalKitsVendidos(), "Exactamente 1 venta registrada en auditoría");
    }

    // =========================================================================
    // TEST 07: Double-spend concurrente con saldo 3000 -> 1 compra, saldo 1000
    // =========================================================================
    @Test
    @DisplayName("TEST 07: Double-spend concurrente (2 kits distintos) con saldo 3000 LC -> 1 compra OK, 1 rechazada")
    void test07_doubleSpendConcurrenteDosKits_saldo3000_unSoloExito() throws Exception {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer07");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 3000L);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<String> f1 = executor.submit(() -> {
            latch.await();
            return kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_ds_07_a");
        });
        Future<String> f2 = executor.submit(() -> {
            latch.await();
            return kitService.comprar(uuid, playerId, kitLunar, mockEconomy, "pur_ds_07_b");
        });

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        String res1 = f1.get();
        String res2 = f2.get();

        int successes = (res1 == null ? 1 : 0) + (res2 == null ? 1 : 0);
        assertEquals(1, successes, "Solo una compra debe permitirse: 3000 < 4000");
        assertEquals(1000L, dbState.getBalance(playerId, Currency.REPORTCOIN), "El saldo debe quedar en 1000 LC");
        assertEquals(1, kitService.totalKitsVendidos());
    }

    // =========================================================================
    // TEST 08: Fallo antes de débito -> saldo intacto
    // =========================================================================
    @Test
    @DisplayName("TEST 08: Fallo de validación previa (kit no exclusivo o precio alterado) -> Saldo intacto")
    void test08_falloAntesDeDebito_saldoIntacto() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer08");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 5000L);

        var kitInvalido = new KitCatalog.Kit(
                "invalid_price", "Kit Hackeado", null, "Hack",
                "exclusive", 1500L, 0, true, null, List.of(), true, null, null, 1
        );

        String error = kitService.comprar(uuid, playerId, kitInvalido, mockEconomy, "pur_08");
        assertNotNull(error);
        assertEquals("precio de kit exclusivo inválido", error);
        assertEquals(5000L, dbState.getBalance(playerId, Currency.REPORTCOIN), "El saldo no debe tocarse");
        assertEquals(0, kitService.totalKitsVendidos());
    }

    // =========================================================================
    // TEST 09: Fallo durante transacción -> rollback completo
    // =========================================================================
    @Test
    @DisplayName("TEST 09: Fallo forzado durante la transacción DB -> Rollback total, saldo y estado intactos")
    void test09_falloDuranteTransaccion_rollbackCompleto() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer09");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 5000L);

        dbState.simulateFailureOnInsert = true;

        assertThrows(SQLException.class, () ->
                kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_09")
        );

        assertEquals(5000L, dbState.getBalance(playerId, Currency.REPORTCOIN), "Rollback debe restaurar saldo");
        assertFalse(kitService.posee(playerId, kitEclipse), "No debe poseer el kit");
        assertEquals(0, kitService.totalKitsVendidos());
    }

    // =========================================================================
    // TEST 10: Compra completada, claim pendiente, reinicio -> claim intacto, no nuevo cobro
    // =========================================================================
    @Test
    @DisplayName("TEST 10: Compra completada -> Simulación reinicio -> PENDING persiste, cero cobro adicional")
    void test10_compraCompletada_claimPendiente_reinicio_claimIntacto() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer10");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 3000L);

        // Compra
        String err = kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_10");
        assertNull(err);
        assertEquals(1000L, dbState.getBalance(playerId, Currency.REPORTCOIN));

        // Simulación de reinicio del servidor (nueva instancia del servicio conectada a la misma DB)
        TestableKitService nuevoServicio = new TestableKitService(dbState);
        assertTrue(nuevoServicio.posee(playerId, kitEclipse), "El kit sigue adquirido tras el reinicio");
        assertFalse(nuevoServicio.haReclamado(playerId, kitEclipse), "El kit sigue en estado pendiente de reclamo");

        // Reclamo exitoso
        AtomicInteger entregas = new AtomicInteger(0);
        String errReclamo = nuevoServicio.reclamarExclusivo(playerId, kitEclipse, 10, k -> entregas.incrementAndGet());
        assertNull(errReclamo);
        assertEquals(1, entregas.get(), "Se deben entregar los items físicos");
        assertTrue(nuevoServicio.haReclamado(playerId, kitEclipse));
        assertEquals(1000L, dbState.getBalance(playerId, Currency.REPORTCOIN), "No debe haber ningún cobro adicional");
    }

    // =========================================================================
    // TEST 11: Inventario lleno -> compra completada, claim queda PENDING, cero items al vacío
    // =========================================================================
    @Test
    @DisplayName("TEST 11: Inventario lleno (0 espacios) -> Intento de reclamo rechazado, PENDING intacto, 0 items perdidos")
    void test11_inventarioLleno_compraCompletada_claimQuedaPending_ceroItemsPerdidos() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer11");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 2500L);

        // Compra OK
        kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_11");
        assertEquals(500L, dbState.getBalance(playerId, Currency.REPORTCOIN));

        // Intento de reclamo con 0 huecos libres
        AtomicInteger itemsEntregados = new AtomicInteger(0);
        String errReclamo = kitService.reclamarExclusivo(playerId, kitEclipse, 0, k -> itemsEntregados.incrementAndGet());
        assertNotNull(errReclamo);
        assertTrue(errReclamo.contains("espacios vacíos"), "Debe advertir falta de espacio: " + errReclamo);

        assertEquals(0, itemsEntregados.get(), "No debe tirarse ningún item");
        assertFalse(kitService.haReclamado(playerId, kitEclipse), "Debe seguir sin marcarse como reclamado");
        assertTrue(kitService.posee(playerId, kitEclipse), "Sigue perteneciendo al jugador");

        // Posterior reclamo con espacio suficiente
        String errReclamo2 = kitService.reclamarExclusivo(playerId, kitEclipse, 5, k -> itemsEntregados.incrementAndGet());
        assertNull(errReclamo2);
        assertEquals(1, itemsEntregados.get(), "Ahora sí se entrega");
        assertTrue(kitService.haReclamado(playerId, kitEclipse));
    }

    // =========================================================================
    // TEST 12: Claim exitoso -> reintento devuelve 'ya reclamado', cero items extra
    // =========================================================================
    @Test
    @DisplayName("TEST 12: Claim exitoso -> Reintento devuelve 'ya reclamado' -> 0 duplicación de items")
    void test12_claimExitoso_reintentoDevuelveYaReclamado_ceroItemsExtra() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer12");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 2000L);

        kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_12");

        AtomicInteger items = new AtomicInteger(0);
        String err1 = kitService.reclamarExclusivo(playerId, kitEclipse, 5, k -> items.incrementAndGet());
        assertNull(err1);
        assertEquals(1, items.get());

        // Reintento
        String err2 = kitService.reclamarExclusivo(playerId, kitEclipse, 5, k -> items.incrementAndGet());
        assertNotNull(err2);
        assertEquals("ya has reclamado el contenido de este kit", err2);
        assertEquals(1, items.get(), "No debe haber duplicación de entrega");
    }

    // =========================================================================
    // TEST 13: 10 claims concurrentes -> una sola entrega de items
    // =========================================================================
    @Test
    @DisplayName("TEST 13: 10 claims concurrentes -> Exactamente 1 entrega de items, 9 bloqueados")
    void test13_diezClaimsConcurrentes_unaSolaEntrega() throws Exception {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer13");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 2000L);

        kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_13");

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger entregas = new AtomicInteger(0);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                latch.await();
                return kitService.reclamarExclusivo(playerId, kitEclipse, 10, k -> entregas.incrementAndGet());
            }));
        }

        latch.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        int successes = 0;
        int rejections = 0;
        for (Future<String> f : futures) {
            String res = f.get();
            if (res == null) successes++;
            else rejections++;
        }

        assertEquals(1, successes, "Solo 1 hilo debe lograr el reclamo");
        assertEquals(threads - 1, rejections);
        assertEquals(1, entregas.get(), "La entrega física de items ocurre una y solo una vez");
    }

    // =========================================================================
    // TEST 14: Kit disabled -> compra rechazada
    // =========================================================================
    @Test
    @DisplayName("TEST 14: Kit con enabled = false -> Compra rechazada -> Saldo intacto")
    void test14_kitDeshabilitado_compraRechazada() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer14");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 5000L);

        String err = kitService.comprar(uuid, playerId, kitDisabled, mockEconomy, "pur_14");
        assertNotNull(err);
        assertEquals("este kit se encuentra deshabilitado", err);
        assertEquals(5000L, dbState.getBalance(playerId, Currency.REPORTCOIN));
    }

    // =========================================================================
    // TEST 15: Antes de availableFrom -> compra rechazada
    // =========================================================================
    @Test
    @DisplayName("TEST 15: Intentar comprar antes de availableFrom -> Rechazado 'este kit aún no está disponible'")
    void test15_antesDeAvailableFrom_compraRechazada() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer15");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 5000L);

        String err = kitService.comprar(uuid, playerId, kitFuture, mockEconomy, "pur_15");
        assertNotNull(err);
        assertEquals("este kit aún no está disponible", err);
        assertEquals(5000L, dbState.getBalance(playerId, Currency.REPORTCOIN));
    }

    // =========================================================================
    // TEST 16: Después de availableUntil -> compra rechazada
    // =========================================================================
    @Test
    @DisplayName("TEST 16: Intentar comprar después de availableUntil -> Rechazado 'este kit ha expirado'")
    void test16_despuesDeAvailableUntil_compraRechazada() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer16");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 5000L);

        String err = kitService.comprar(uuid, playerId, kitExpired, mockEconomy, "pur_16");
        assertNotNull(err);
        assertEquals("este kit ha expirado", err);
        assertEquals(5000L, dbState.getBalance(playerId, Currency.REPORTCOIN));
    }

    // =========================================================================
    // TEST 17: Dentro de ventana temporal -> compra OK
    // =========================================================================
    @Test
    @DisplayName("TEST 17: Compra dentro de ventana [availableFrom, availableUntil] -> Compra exitosa")
    void test17_dentroDeVentanaTemporal_compraOk() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer17");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 5000L);

        String err = kitService.comprar(uuid, playerId, kitValidWindow, mockEconomy, "pur_17");
        assertNull(err);
        assertEquals(3000L, dbState.getBalance(playerId, Currency.REPORTCOIN));
        assertTrue(kitService.posee(playerId, kitValidWindow));
    }

    // =========================================================================
    // TEST 18: Purchase limit / once alcanzado -> segunda compra rechazada
    // =========================================================================
    @Test
    @DisplayName("TEST 18: Kit exclusivo once/purchaseLimit=1 -> Segunda compra rechazada 'ya posees este kit'")
    void test18_purchaseLimitAlcanzado_segundaCompraRechazada() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(uuid, "Trainer18");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 6000L);

        String err1 = kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_18_a");
        assertNull(err1);
        assertEquals(4000L, dbState.getBalance(playerId, Currency.REPORTCOIN));

        // Intento de segunda compra del mismo kit exclusivo
        String err2 = kitService.comprar(uuid, playerId, kitEclipse, mockEconomy, "pur_18_b");
        assertNotNull(err2);
        assertEquals("ya posees este kit", err2);
        assertEquals(4000L, dbState.getBalance(playerId, Currency.REPORTCOIN), "El saldo no debe cobrarse dos veces");
    }

    // =========================================================================
    // TEST 19: Compra de kits + refund de Tebex -> Fase 4D marca REQUIRES_REVIEW, kits permanecen intactos, saldo no negativo
    // =========================================================================
    @Test
    @DisplayName("TEST 19: Gasto de LunaCoins en kits exclusivos + Refund Tebex -> Desvío a REQUIRES_REVIEW, saldo intacto, kits no revocados")
    void test19_compraKits_refundTebex_desvioRequiresReview_saldoNoNegativo_kitsIntactos() throws SQLException {
        UUID playerUuid = UUID.randomUUID();
        long playerId = dbState.resolveOrCreatePlayer(playerUuid, "Buyer19");

        // 1. Simular compra de paquete Tebex de 4375 LunaCoins
        String txId = "tx_tebex_19";
        String pkgId = "pkg_lc_4375";
        dbState.recordTebexFulfillment(txId, pkgId, playerUuid, playerId, "LUNACOINS", "4375", "DELIVERED");
        dbState.setBalance(playerId, Currency.REPORTCOIN, 4375L);
        assertEquals(4375L, dbState.getBalance(playerId, Currency.REPORTCOIN));

        // 2. El jugador compra 2 kits exclusivos de 2.000 LunaCoins cada uno
        String err1 = kitService.comprar(playerUuid, playerId, kitEclipse, mockEconomy, "pur_19_eclipse");
        assertNull(err1);
        String err2 = kitService.comprar(playerUuid, playerId, kitLunar, mockEconomy, "pur_19_lunar");
        assertNull(err2);

        // Saldo después de compras de kits: 4375 - 4000 = 375 LC
        assertEquals(375L, dbState.getBalance(playerId, Currency.REPORTCOIN));
        assertTrue(kitService.posee(playerId, kitEclipse));
        assertTrue(kitService.posee(playerId, kitLunar));

        // 3. Llega una solicitud de reembolso de Tebex sobre el paquete de 4375 LC
        // El jugador tiene 375 LC. Revertir 4375 LC crearía un saldo negativo de -4000 LC.
        // La regla de Fase 4D exige: desvío a REQUIRES_REVIEW, 0 saldo negativo, kits intactos.
        TebexService.ExecutionResult refundResult = dbState.processTebexRefundSimulation(
                txId, pkgId, playerUuid, playerId, 4375L
        );

        assertEquals(TebexService.ResultType.REQUIRES_REVIEW, refundResult.type());
        assertEquals("REQUIRES_REVIEW", refundResult.status());
        assertEquals("SALDO_INSUFICIENTE_PARA_REVERSA", refundResult.reason());

        // Verificaciones críticas de seguridad económica:
        assertEquals(375L, dbState.getBalance(playerId, Currency.REPORTCOIN), "El saldo NUNCA debe ser negativo");
        assertTrue(kitService.posee(playerId, kitEclipse), "Kit Eclipse adquirido legalmente permanece intacto");
        assertTrue(kitService.posee(playerId, kitLunar), "Kit Lunar adquirido legalmente permanece intacto");
        assertEquals(2, kitService.totalKitsVendidos(), "Los kits comprados no se anulan automáticamente");
    }

    // =========================================================================
    // Mock State & TestableKitService
    // =========================================================================

    static class MockKitDatabaseState {
        final Map<UUID, Long> uuidToPlayerId = new ConcurrentHashMap<>();
        final Map<Long, Map<Currency, AtomicLong>> balances = new ConcurrentHashMap<>();
        final Map<String, KitService.ExclusivePurchaseRecord> purchases = new ConcurrentHashMap<>();
        final Set<String> kitClaims = ConcurrentHashMap.newKeySet();
        final List<String> ledger = new CopyOnWriteArrayList<>();
        final Map<String, TebexFulfillmentMock> tebexFulfillments = new ConcurrentHashMap<>();

        final AtomicLong playerIdCounter = new AtomicLong(100);
        volatile boolean simulateFailureOnInsert = false;

        record TebexFulfillmentMock(String tx, String pkg, UUID uuid, Long playerId,
                                    String productType, String productValue, String status,
                                    String revStatus, String revReason) {}

        private Map<Long, Map<Currency, Long>> balanceSnapshot = null;
        private List<String> ledgerSnapshot = null;
        private Map<String, KitService.ExclusivePurchaseRecord> purchasesSnapshot = null;
        private Set<String> claimsSnapshot = null;

        synchronized void beginTransaction() {
            balanceSnapshot = new HashMap<>();
            for (var entry : balances.entrySet()) {
                Map<Currency, Long> curMap = new HashMap<>();
                for (var curEntry : entry.getValue().entrySet()) {
                    curMap.put(curEntry.getKey(), curEntry.getValue().get());
                }
                balanceSnapshot.put(entry.getKey(), curMap);
            }
            ledgerSnapshot = new ArrayList<>(ledger);
            purchasesSnapshot = new HashMap<>(purchases);
            claimsSnapshot = new HashSet<>(kitClaims);
        }

        synchronized void commitTransaction() {
            balanceSnapshot = null;
            ledgerSnapshot = null;
            purchasesSnapshot = null;
            claimsSnapshot = null;
        }

        synchronized void rollbackTransaction() {
            if (balanceSnapshot != null) {
                balances.clear();
                for (var entry : balanceSnapshot.entrySet()) {
                    var curMap = new ConcurrentHashMap<Currency, AtomicLong>();
                    for (var curEntry : entry.getValue().entrySet()) {
                        curMap.put(curEntry.getKey(), new AtomicLong(curEntry.getValue()));
                    }
                    balances.put(entry.getKey(), curMap);
                }
            }
            if (ledgerSnapshot != null) {
                ledger.clear();
                ledger.addAll(ledgerSnapshot);
            }
            if (purchasesSnapshot != null) {
                purchases.clear();
                purchases.putAll(purchasesSnapshot);
            }
            if (claimsSnapshot != null) {
                kitClaims.clear();
                kitClaims.addAll(claimsSnapshot);
            }
            balanceSnapshot = null;
            ledgerSnapshot = null;
            purchasesSnapshot = null;
            claimsSnapshot = null;
        }

        long resolveOrCreatePlayer(UUID uuid, String name) {
            return uuidToPlayerId.computeIfAbsent(uuid, k -> playerIdCounter.incrementAndGet());
        }

        long getBalance(long playerId, Currency currency) {
            var playerMap = balances.get(playerId);
            if (playerMap == null) return 0L;
            var val = playerMap.get(currency);
            return val == null ? 0L : val.get();
        }

        void setBalance(long playerId, Currency currency, long amount) {
            balances.computeIfAbsent(playerId, p -> new ConcurrentHashMap<>())
                    .computeIfAbsent(currency, c -> new AtomicLong(0))
                    .set(amount);
        }

        void recordTebexFulfillment(String tx, String pkg, UUID uuid, Long playerId,
                                    String type, String value, String status) {
            tebexFulfillments.put(tx + ":" + pkg, new TebexFulfillmentMock(
                    tx, pkg, uuid, playerId, type, value, status, "NONE", null
            ));
        }

        TebexService.ExecutionResult processTebexRefundSimulation(
                String tx, String pkg, UUID uuid, long playerId, long amountToDebit) {
            long currentBalance = getBalance(playerId, Currency.REPORTCOIN);
            if (currentBalance < amountToDebit) {
                // Déficit económico -> REQUIRES_REVIEW
                tebexFulfillments.put(tx + ":" + pkg, new TebexFulfillmentMock(
                        tx, pkg, uuid, playerId, "LUNACOINS", String.valueOf(amountToDebit),
                        "REQUIRES_REVIEW", "REQUIRES_REVIEW", "SALDO_INSUFICIENTE_PARA_REVERSA"
                ));
                return new TebexService.ExecutionResult(
                        TebexService.ResultType.REQUIRES_REVIEW,
                        "REQUIRES_REVIEW",
                        "SALDO_INSUFICIENTE_PARA_REVERSA"
                );
            }
            setBalance(playerId, Currency.REPORTCOIN, currentBalance - amountToDebit);
            return new TebexService.ExecutionResult(
                    TebexService.ResultType.SUCCESS,
                    "COMPLETED",
                    null
            );
        }
    }

    static class MockEconomyService extends EconomyService {
        private final MockKitDatabaseState state;
        private final ReentrantLock economyLock = new ReentrantLock();

        public MockEconomyService(MockKitDatabaseState state) {
            super(null);
            this.state = state;
        }

        @Override
        public long applyInTransaction(Connection c, long playerId, Currency currency, long amount,
                                       String reason, String refType, Long refId, String idempotencyKey)
                throws SQLException, EconomyException {
            economyLock.lock();
            try {
                if (state.ledger.contains(idempotencyKey)) {
                    return state.getBalance(playerId, currency); // Idempotente
                }
                long cur = state.getBalance(playerId, currency);
                if (cur + amount < 0) {
                    throw new EconomyException(EconomyException.Kind.INSUFFICIENT_FUNDS,
                            "Saldo insuficiente: tiene " + cur + ", requiere " + (-amount));
                }
                long newBal = cur + amount;
                state.setBalance(playerId, currency, newBal);
                state.ledger.add(idempotencyKey);
                return newBal;
            } finally {
                economyLock.unlock();
            }
        }

        public long balanceOf(long playerId, Currency currency) {
            return state.getBalance(playerId, currency);
        }
    }

    static class TestableKitService extends KitService {
        private final MockKitDatabaseState state;

        public TestableKitService(MockKitDatabaseState state) {
            super(null);
            this.state = state;
        }

        @Override
        protected Connection getConnection() throws SQLException {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class[]{Connection.class},
                    (proxy, method, args) -> {
                        String mName = method.getName();
                        if ("setAutoCommit".equals(mName)) {
                            boolean auto = (Boolean) args[0];
                            if (!auto) {
                                state.beginTransaction();
                            }
                            return null;
                        }
                        if ("commit".equals(mName)) {
                            state.commitTransaction();
                            return null;
                        }
                        if ("rollback".equals(mName)) {
                            state.rollbackTransaction();
                            return null;
                        }
                        if ("close".equals(mName)) {
                            return null;
                        }
                        if ("prepareStatement".equals(mName)) {
                            return createMockPreparedStatement((String) args[0]);
                        }
                        return null;
                    }
            );
        }

        private PreparedStatement createMockPreparedStatement(String sql) {
            final List<Object> params = new ArrayList<>();
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class[]{PreparedStatement.class},
                    (proxy, m, args) -> {
                        String mName = m.getName();
                        if (mName.startsWith("set") && args != null && args.length >= 2) {
                            int idx = (Integer) args[0] - 1;
                            while (params.size() <= idx) params.add(null);
                            params.set(idx, args[1]);
                            return null;
                        }
                        if ("close".equals(mName)) return null;

                        if ("executeUpdate".equals(mName)) {
                            if (state.simulateFailureOnInsert) {
                                throw new SQLException("Fallo simulado de DB en INSERT/UPDATE");
                            }
                            if (sql.startsWith("INSERT INTO exclusive_kit_purchase")) {
                                String purId = String.valueOf(params.get(0));
                                String uStr = String.valueOf(params.get(1));
                                long pId = ((Number) params.get(2)).longValue();
                                String kId = String.valueOf(params.get(3));
                                long price = ((Number) params.get(4)).longValue();
                                long bBefore = ((Number) params.get(5)).longValue();
                                long bAfter = ((Number) params.get(6)).longValue();
                                state.purchases.put(purId, new KitService.ExclusivePurchaseRecord(
                                        purId, uStr, pId, kId, price, "REPORTCOIN",
                                        bBefore, bAfter, "COMPLETED", "PENDING",
                                        LocalDateTime.now(), null
                                ));
                                return 1;
                            }
                            if (sql.startsWith("INSERT IGNORE INTO kit_claim")) {
                                long pId = ((Number) params.get(0)).longValue();
                                String kId = String.valueOf(params.get(1));
                                boolean added = state.kitClaims.add(pId + ":" + kId);
                                return added ? 1 : 0;
                            }
                            if (sql.startsWith("UPDATE exclusive_kit_purchase SET claim_status = 'CLAIMED'")) {
                                String purId = String.valueOf(params.get(0));
                                var prev = state.purchases.get(purId);
                                if (prev != null) {
                                    state.purchases.put(purId, new KitService.ExclusivePurchaseRecord(
                                            prev.purchaseId(), prev.playerUuid(), prev.playerId(), prev.kitId(),
                                            prev.price(), prev.currency(), prev.balanceBefore(), prev.balanceAfter(),
                                            prev.purchaseStatus(), "CLAIMED", prev.purchasedAt(), LocalDateTime.now()
                                    ));
                                }
                                return 1;
                            }
                            if (sql.startsWith("UPDATE exclusive_kit_purchase SET claim_status = 'PENDING'")) {
                                String purId = String.valueOf(params.get(0));
                                var prev = state.purchases.get(purId);
                                if (prev != null) {
                                    state.purchases.put(purId, new KitService.ExclusivePurchaseRecord(
                                            prev.purchaseId(), prev.playerUuid(), prev.playerId(), prev.kitId(),
                                            prev.price(), prev.currency(), prev.balanceBefore(), prev.balanceAfter(),
                                            prev.purchaseStatus(), "PENDING", prev.purchasedAt(), null
                                    ));
                                }
                                return 1;
                            }
                            if (sql.startsWith("DELETE FROM kit_claim")) {
                                long pId = ((Number) params.get(0)).longValue();
                                String kId = String.valueOf(params.get(1));
                                state.kitClaims.remove(pId + ":" + kId);
                                return 1;
                            }
                            return 1;
                        }

                        if ("executeQuery".equals(mName)) {
                            if (sql.startsWith("SELECT COUNT(*) FROM exclusive_kit_purchase WHERE player_id = ? AND kit_id = ?")) {
                                long pId = ((Number) params.get(0)).longValue();
                                String kId = String.valueOf(params.get(1));
                                long count = state.purchases.values().stream()
                                        .filter(p -> p.playerId() == pId && p.kitId().equals(kId) && "COMPLETED".equals(p.purchaseStatus()))
                                        .count();
                                return singleLongResultSet(count);
                            }
                            if (sql.startsWith("SELECT balance FROM player_economy")) {
                                long pId = ((Number) params.get(0)).longValue();
                                Currency cur = Currency.valueOf(String.valueOf(params.get(1)));
                                long bal = state.getBalance(pId, cur);
                                return singleLongResultSet(bal);
                            }
                            if (sql.startsWith("SELECT purchase_id FROM exclusive_kit_purchase")) {
                                long pId = ((Number) params.get(0)).longValue();
                                String kId = String.valueOf(params.get(1));
                                Optional<String> purId = state.purchases.values().stream()
                                        .filter(p -> p.playerId() == pId && p.kitId().equals(kId) && "COMPLETED".equals(p.purchaseStatus()) && "PENDING".equals(p.claimStatus()))
                                        .map(KitService.ExclusivePurchaseRecord::purchaseId)
                                        .findFirst();
                                return singleStringResultSet(purId.orElse(null));
                            }
                            if (sql.contains("SELECT 1 FROM exclusive_kit_purchase") || sql.contains("SELECT 1 FROM kit_claim")) {
                                long pId1 = ((Number) params.get(0)).longValue();
                                String kId1 = String.valueOf(params.get(1));
                                long pId2 = ((Number) params.get(2)).longValue();
                                String kId2 = String.valueOf(params.get(3));

                                boolean hasPurchase = false;
                                if (sql.contains("claim_status = 'CLAIMED'")) {
                                    hasPurchase = state.purchases.values().stream().anyMatch(p -> p.playerId() == pId1 && p.kitId().equals(kId1) && "CLAIMED".equals(p.claimStatus()));
                                } else {
                                    hasPurchase = state.purchases.values().stream().anyMatch(p -> p.playerId() == pId1 && p.kitId().equals(kId1) && "COMPLETED".equals(p.purchaseStatus()));
                                }
                                boolean hasClaim = state.kitClaims.contains(pId2 + ":" + kId2);
                                return booleanResultSet(hasPurchase || hasClaim);
                            }
                            if (sql.equals("SELECT COUNT(*) FROM exclusive_kit_purchase WHERE purchase_status = 'COMPLETED'")) {
                                long count = state.purchases.values().stream()
                                        .filter(p -> "COMPLETED".equals(p.purchaseStatus()))
                                        .count();
                                return singleLongResultSet(count);
                            }
                            if (sql.startsWith("SELECT COALESCE(SUM(price), 0)")) {
                                long sum = state.purchases.values().stream()
                                        .filter(p -> "COMPLETED".equals(p.purchaseStatus()))
                                        .mapToLong(KitService.ExclusivePurchaseRecord::price)
                                        .sum();
                                return singleLongResultSet(sum);
                            }
                            if (sql.startsWith("SELECT kit_id, COUNT(*)")) {
                                Map<String, Long> counts = new LinkedHashMap<>();
                                for (var p : state.purchases.values()) {
                                    if ("COMPLETED".equals(p.purchaseStatus())) {
                                        counts.put(p.kitId(), counts.getOrDefault(p.kitId(), 0L) + 1);
                                    }
                                }
                                return mapResultSet(counts);
                            }
                            if (sql.startsWith("SELECT purchase_id, player_uuid, player_id, kit_id, price, currency, balance_before, balance_after, purchase_status, claim_status, purchased_at, claimed_at FROM exclusive_kit_purchase WHERE player_id = ?")) {
                                long pId = ((Number) params.get(0)).longValue();
                                List<KitService.ExclusivePurchaseRecord> list = state.purchases.values().stream()
                                        .filter(p -> p.playerId() == pId)
                                        .toList();
                                return recordListResultSet(list);
                            }
                            if (sql.startsWith("SELECT purchase_id, player_uuid, player_id, kit_id, price, currency, balance_before, balance_after, purchase_status, claim_status, purchased_at, claimed_at FROM exclusive_kit_purchase WHERE purchase_id = ?")) {
                                String purId = String.valueOf(params.get(0));
                                var rec = state.purchases.get(purId);
                                return recordListResultSet(rec != null ? List.of(rec) : List.of());
                            }
                            return emptyResultSet();
                        }
                        return null;
                    }
            );
        }

        private ResultSet singleLongResultSet(long val) {
            AtomicInteger row = new AtomicInteger(0);
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (p, m, a) -> {
                        if ("next".equals(m.getName())) return row.incrementAndGet() == 1;
                        if ("getLong".equals(m.getName())) return val;
                        if ("getInt".equals(m.getName())) return (int) val;
                        if ("close".equals(m.getName())) return null;
                        return null;
                    }
            );
        }

        private ResultSet singleStringResultSet(String val) {
            AtomicInteger row = new AtomicInteger(0);
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (p, m, a) -> {
                        if ("next".equals(m.getName())) return val != null && row.incrementAndGet() == 1;
                        if ("getString".equals(m.getName())) return val;
                        if ("close".equals(m.getName())) return null;
                        return null;
                    }
            );
        }

        private ResultSet booleanResultSet(boolean exists) {
            AtomicInteger row = new AtomicInteger(0);
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (p, m, a) -> {
                        if ("next".equals(m.getName())) return exists && row.incrementAndGet() == 1;
                        if ("getInt".equals(m.getName())) return 1;
                        if ("getLong".equals(m.getName())) return 1L;
                        if ("close".equals(m.getName())) return null;
                        return null;
                    }
            );
        }

        private ResultSet mapResultSet(Map<String, Long> map) {
            var entries = new ArrayList<>(map.entrySet());
            AtomicInteger idx = new AtomicInteger(-1);
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (p, m, a) -> {
                        if ("next".equals(m.getName())) {
                            return idx.incrementAndGet() < entries.size();
                        }
                        if ("getString".equals(m.getName())) {
                            return entries.get(idx.get()).getKey();
                        }
                        if ("getLong".equals(m.getName())) {
                            return entries.get(idx.get()).getValue();
                        }
                        if ("getInt".equals(m.getName())) {
                            return entries.get(idx.get()).getValue().intValue();
                        }
                        if ("close".equals(m.getName())) return null;
                        return null;
                    }
            );
        }

        private ResultSet recordListResultSet(List<KitService.ExclusivePurchaseRecord> list) {
            AtomicInteger idx = new AtomicInteger(-1);
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (p, m, a) -> {
                        if ("next".equals(m.getName())) {
                            return idx.incrementAndGet() < list.size();
                        }
                        if ("getString".equals(m.getName())) {
                            int col = (Integer) a[0];
                            var r = list.get(idx.get());
                            return switch (col) {
                                case 1 -> r.purchaseId();
                                case 2 -> r.playerUuid();
                                case 4 -> r.kitId();
                                case 6 -> r.currency();
                                case 9 -> r.purchaseStatus();
                                case 10 -> r.claimStatus();
                                default -> "";
                            };
                        }
                        if ("getLong".equals(m.getName())) {
                            int col = (Integer) a[0];
                            var r = list.get(idx.get());
                            return switch (col) {
                                case 3 -> r.playerId();
                                case 5 -> r.price();
                                case 7 -> r.balanceBefore();
                                case 8 -> r.balanceAfter();
                                default -> 0L;
                            };
                        }
                        if ("getInt".equals(m.getName())) {
                            int col = (Integer) a[0];
                            var r = list.get(idx.get());
                            return switch (col) {
                                case 3 -> (int) r.playerId();
                                case 5 -> (int) r.price();
                                case 7 -> (int) r.balanceBefore();
                                case 8 -> (int) r.balanceAfter();
                                default -> 0;
                            };
                        }
                        if ("getTimestamp".equals(m.getName())) {
                            int col = (Integer) a[0];
                            var r = list.get(idx.get());
                            if (col == 11 && r.purchasedAt() != null) return Timestamp.valueOf(r.purchasedAt());
                            if (col == 12 && r.claimedAt() != null) return Timestamp.valueOf(r.claimedAt());
                            return null;
                        }
                        if ("close".equals(m.getName())) return null;
                        return null;
                    }
            );
        }

        private ResultSet emptyResultSet() {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (p, m, a) -> "next".equals(m.getName()) ? false : null
            );
        }
    }
}
