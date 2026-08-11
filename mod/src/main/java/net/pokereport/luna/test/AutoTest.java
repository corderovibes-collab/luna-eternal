package net.pokereport.luna.test;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;
import net.pokereport.luna.economy.EconomyService;
import net.pokereport.luna.player.PlayerService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Batería de invariantes económicos, ejecutable en caliente con
 * {@code /luna autotest}.
 *
 * <p>Existe porque las propiedades que de verdad importan —idempotencia,
 * atomicidad, que un rechazo no deje rastro— no se pueden comprobar mirando el
 * código, y esperar a tener jugadores conectados para probarlas es una mala
 * idea. Usa jugadores sintéticos y **borra todo lo que crea**.
 *
 * <p>Si alguna vez falla algo aquí, hay un agujero por el que se duplica o se
 * pierde dinero. No se despliega nada con esto en rojo.
 */
public final class AutoTest {

    /** UUIDs reservados. Fijos para que la limpieza sea siempre fiable. */
    private static final UUID T1 = UUID.fromString("00000000-0000-3000-8000-000000000001");
    private static final UUID T2 = UUID.fromString("00000000-0000-3000-8000-000000000002");
    private static final String N1 = "__autotest_1";
    private static final String N2 = "__autotest_2";

    private final Database db;
    private final PlayerService players;
    private final EconomyService economy;
    private final Consumer<String> out;

    private int passed = 0;
    private int failed = 0;

    public AutoTest(Database db, PlayerService players, EconomyService economy,
                    Consumer<String> out) {
        this.db = db;
        this.players = players;
        this.economy = economy;
        this.out = out;
    }

    public boolean run() {
        out.accept("§7— autotest de invariantes economicos —");
        try {
            cleanup();  // por si una ejecución anterior murió a medias
            long a = players.resolve(T1, N1);
            long b = players.resolve(T2, N2);

            testCreditAndDebit(a);
            testIdempotency(a);
            testInsufficientFundsLeavesNoTrace(a);
            testMarksAreNotTradeable(a, b);
            testTransferIsAtomic(a, b);
            testLedgerMatchesBalance(a, b);
            testProgression(a);
            testShopCatalog();

        } catch (Exception e) {
            fail("excepcion inesperada", e.toString());
        } finally {
            try {
                cleanup();
                players.forget(T1);
                players.forget(T2);
                out.accept("§7datos de prueba eliminados");
            } catch (Exception e) {
                fail("limpieza", e.toString());
            }
        }

        boolean ok = failed == 0;
        out.accept(ok
            ? "§a" + passed + " comprobaciones correctas. Invariantes intactos."
            : "§c" + failed + " FALLOS de " + (passed + failed) + ". NO desplegar.");
        return ok;
    }

    // ------------------------------------------------------------ pruebas

    private void testCreditAndDebit(long p) throws Exception {
        economy.credit(p, Currency.POKEDOLLAR, 1000, "autotest", key());
        check("crédito deja saldo 1000",
              economy.balance(p, Currency.POKEDOLLAR) == 1000);

        economy.debit(p, Currency.POKEDOLLAR, 400, "autotest", key());
        check("débito deja saldo 600",
              economy.balance(p, Currency.POKEDOLLAR) == 600);
    }

    /** R4: repetir la misma clave NO puede aplicar el movimiento dos veces. */
    private void testIdempotency(long p) throws Exception {
        String k = key();
        long before = economy.balance(p, Currency.POKEDOLLAR);

        economy.credit(p, Currency.POKEDOLLAR, 500, "autotest_idem", k);
        long afterFirst = economy.balance(p, Currency.POKEDOLLAR);
        check("primer uso de la clave suma 500", afterFirst == before + 500);

        boolean rejected = false;
        try {
            economy.credit(p, Currency.POKEDOLLAR, 500, "autotest_idem", k);
        } catch (EconomyException e) {
            rejected = e.kind == EconomyException.Kind.ALREADY_APPLIED;
        }
        check("repetir la clave se rechaza", rejected);
        check("repetir la clave NO cambia el saldo",
              economy.balance(p, Currency.POKEDOLLAR) == afterFirst);
    }

    /** Un rechazo debe dejar el sistema exactamente como estaba. */
    private void testInsufficientFundsLeavesNoTrace(long p) throws Exception {
        long before = economy.balance(p, Currency.POKEDOLLAR);
        long entriesBefore = countEntries(p);

        boolean rejected = false;
        try {
            economy.debit(p, Currency.POKEDOLLAR, before + 1, "autotest_over", key());
        } catch (EconomyException e) {
            rejected = e.kind == EconomyException.Kind.INSUFFICIENT_FUNDS;
        }
        check("saldo insuficiente se rechaza", rejected);
        check("saldo intacto tras el rechazo",
              economy.balance(p, Currency.POKEDOLLAR) == before);
        check("el rechazo NO deja asiento en el libro",
              countEntries(p) == entriesBefore);
    }

    /**
     * ECO-001: ninguna moneda vinculada circula entre jugadores.
     *
     * <p>Son los dos invariantes que sostienen el modelo de negocio:
     * <ul>
     *   <li><b>Marcas</b> — si se transfirieran, la progresión se compraría a
     *       otro jugador.</li>
     *   <li><b>ReportCoins</b> — si se transfirieran, se revenderían por
     *       PokéDólares y existiría de facto la conversión prohibida, creando
     *       un mercado gris que nadie decidió abrir.</li>
     * </ul>
     */
    private void testMarksAreNotTradeable(long a, long b) throws Exception {
        for (Currency c : Currency.values()) {
            if (c.tradeable) continue;

            economy.credit(a, c, 50, "autotest", key());
            boolean rejected = false;
            try {
                economy.transfer(a, b, c, 10, "autotest", key());
            } catch (EconomyException e) {
                rejected = e.kind == EconomyException.Kind.NOT_TRADEABLE;
            }
            check("transferir " + c.displayName + " se rechaza", rejected);
            check(c.displayName + ": el origen conserva su saldo",
                  economy.balance(a, c) == 50);
            check(c.displayName + ": el destino no recibe nada",
                  economy.balance(b, c) == 0);
        }
    }

    /** Las dos patas de una transferencia ocurren o no ocurre ninguna. */
    private void testTransferIsAtomic(long a, long b) throws Exception {
        long aBefore = economy.balance(a, Currency.POKEDOLLAR);
        long bBefore = economy.balance(b, Currency.POKEDOLLAR);

        economy.transfer(a, b, Currency.POKEDOLLAR, 300, "autotest_tx", key());
        check("el origen pierde 300",
              economy.balance(a, Currency.POKEDOLLAR) == aBefore - 300);
        check("el destino gana 300",
              economy.balance(b, Currency.POKEDOLLAR) == bBefore + 300);

        // Transferencia imposible: no debe mover nada en ninguno de los dos.
        long aMid = economy.balance(a, Currency.POKEDOLLAR);
        long bMid = economy.balance(b, Currency.POKEDOLLAR);
        try {
            economy.transfer(a, b, Currency.POKEDOLLAR, aMid + 1_000_000,
                             "autotest_tx_fail", key());
        } catch (EconomyException ignored) { }
        check("una transferencia fallida no toca el origen",
              economy.balance(a, Currency.POKEDOLLAR) == aMid);
        check("una transferencia fallida no toca el destino",
              economy.balance(b, Currency.POKEDOLLAR) == bMid);
    }

    /** R3: el saldo es una caché del libro. Si no cuadra, hay un agujero. */
    private void testLedgerMatchesBalance(long a, long b) throws Exception {
        for (long p : new long[]{a, b}) {
            for (Currency c : Currency.values()) {
                check("saldo == suma del libro (jugador " + p + ", " + c + ")",
                      economy.auditDiscrepancy(p, c) == 0);
            }
        }
    }

    /**
     * Progresión: una concesión grande debe cruzar varios niveles de golpe.
     *
     * <p>Si no lo hiciera, el jugador se quedaría con XP de sobra y el nivel
     * sin subir — un fallo silencioso que solo se detecta mirando la tabla.
     */
    private void testProgression(long p) throws Exception {
        var prog = LunaEternal.progression();
        var path = net.pokereport.luna.progression.Path.EXPLORADOR;

        var s1 = prog.grant(p, path, 50);
        check("50 de XP no sube de nivel", s1.level() == 0 && s1.xp() == 50);

        // 100 sube a nivel 1 (necesita 100) y deja 50 hacia el 2.
        var s2 = prog.grant(p, path, 100);
        check("cruzar el umbral sube de nivel", s2.level() == 1);
        check("el sobrante se conserva", s2.xp() == 50);

        // 100 + 400 + 1200 + 3000 + 7500 = 12 200 en total hasta el máximo.
        var s3 = prog.grant(p, path, 100_000);
        check("una concesión enorme llega al nivel máximo",
              s3.level() == net.pokereport.luna.progression.Path.MAX_LEVEL);
        check("al máximo no se acumula XP sobrante", s3.xp() == 0);

        var dominant = prog.dominant(p);
        check("la vía dominante es la de mayor nivel",
              dominant != null && dominant.path() == path);

        var all = prog.all(p);
        check("las cinco vías existen siempre",
              all.size() == net.pokereport.luna.progression.Path.values().length);
    }

    /**
     * El catálogo de la tienda no puede permitir ganar dinero comprando y
     * revendiendo.
     *
     * <p>Es el error clásico que mata una economía en días, y el que audité a
     * mano en la configuración de producción. Aquí queda automatizado para que
     * <b>no pueda reaparecer</b> el día que alguien retoque un precio.
     */
    private void testShopCatalog() {
        var catalog = LunaEternal.shop();
        check("el catálogo de la tienda está cargado", catalog != null);
        if (catalog == null) return;

        int arbitrage = 0, nonPositive = 0, total = 0;
        for (var c : catalog.categories()) {
            for (var e : c.entries()) {
                total++;
                if (e.sell() >= e.buy()) arbitrage++;
                if (e.buy() <= 0) nonPositive++;
            }
        }
        check("hay objetos en la tienda", total > 0);
        check("ningún objeto se vende por más de lo que cuesta", arbitrage == 0);
        check("todos los precios de compra son positivos", nonPositive == 0);

        // La moneda premium no puede recomprarse: sería un mercado gris.
        int premiumBuyback = 0;
        for (var c : catalog.categories()) {
            for (var e : c.entries()) {
                if (!e.currency().tradeable && e.sell() > 0) premiumBuyback++;
            }
        }
        check("la moneda premium no se puede recomprar", premiumBuyback == 0);
    }

    // ------------------------------------------------------------ auxiliares

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private long countEntries(long playerId) throws Exception {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM ledger_entry WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** Borra en orden inverso a las claves ajenas. */
    private void cleanup() throws Exception {
        List<String> uuids = new ArrayList<>(List.of(T1.toString(), T2.toString()));
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                for (String sql : List.of(
                    "DELETE pp FROM player_path pp JOIN player p "
                        + "ON p.player_id = pp.player_id WHERE p.mc_uuid = ?",
                    "DELETE le FROM ledger_entry le JOIN player p "
                        + "ON p.player_id = le.player_id WHERE p.mc_uuid = ?",
                    "DELETE pe FROM player_economy pe JOIN player p "
                        + "ON p.player_id = pe.player_id WHERE p.mc_uuid = ?",
                    "DELETE FROM player WHERE mc_uuid = ?")) {
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        for (String u : uuids) {
                            ps.setString(1, u);
                            ps.executeUpdate();
                        }
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private void check(String name, boolean ok) {
        if (ok) {
            passed++;
            out.accept("  §a✔ §7" + name);
        } else {
            fail(name, "no se cumple");
        }
    }

    private void fail(String name, String detail) {
        failed++;
        out.accept("  §c✘ " + name + " §8(" + detail + ")");
        LunaEternal.LOG.error("Autotest FALLO: {} — {}", name, detail);
    }
}
