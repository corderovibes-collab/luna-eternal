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

    /** ECO-001: las Marcas no circulan. Es lo que impide comprar progresión. */
    private void testMarksAreNotTradeable(long a, long b) throws Exception {
        economy.credit(a, Currency.MARK, 50, "autotest", key());
        boolean rejected = false;
        try {
            economy.transfer(a, b, Currency.MARK, 10, "autotest", key());
        } catch (EconomyException e) {
            rejected = e.kind == EconomyException.Kind.NOT_TRADEABLE;
        }
        check("transferir Marcas se rechaza", rejected);
        check("las Marcas del origen siguen ahí",
              economy.balance(a, Currency.MARK) == 50);
        check("el destino no recibió Marcas",
              economy.balance(b, Currency.MARK) == 0);
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
