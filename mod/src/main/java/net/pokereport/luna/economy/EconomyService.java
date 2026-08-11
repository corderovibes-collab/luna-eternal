package net.pokereport.luna.economy;

import net.pokereport.luna.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Movimientos de dinero. Implementa las reglas R2, R3 y R4 de
 * {@code docs/technical/data-model.md}.
 *
 * <p><b>El saldo es una caché; la verdad es el libro de asientos.</b> Cada
 * movimiento inserta una fila en {@code ledger_entry} y actualiza
 * {@code player_economy} <em>en la misma transacción</em>. Si alguna vez
 * {@code balance != SUM(delta)}, hay un fallo y se puede detectar.
 */
public final class EconomyService {

    private final Database db;

    public EconomyService(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------ consulta

    public long balance(long playerId, Currency currency) throws SQLException {
        try (Connection c = db.connection()) {
            return readBalance(c, playerId, currency, false);
        }
    }

    // ------------------------------------------------------------ mutación

    /** Ingresa dinero. {@code amount} debe ser positivo. */
    public long credit(long playerId, Currency currency, long amount,
                       String reason, String idempotencyKey)
            throws SQLException, EconomyException {
        return apply(playerId, currency, amount, reason, null, null, idempotencyKey);
    }

    /** Retira dinero. {@code amount} debe ser positivo. */
    public long debit(long playerId, Currency currency, long amount,
                      String reason, String idempotencyKey)
            throws SQLException, EconomyException {
        return apply(playerId, currency, -amount, reason, null, null, idempotencyKey);
    }

    /**
     * Aplica un movimiento de forma atómica.
     *
     * @param delta positivo ingresa, negativo retira. Nunca cero.
     * @param idempotencyKey clave única. Un reintento con la misma clave
     *                       falla con {@link EconomyException.Kind#ALREADY_APPLIED}
     *                       en vez de duplicar el movimiento (R4).
     * @return el saldo resultante
     */
    public long apply(long playerId, Currency currency, long delta,
                      String reason, String refType, Long refId,
                      String idempotencyKey)
            throws SQLException, EconomyException {

        if (delta == 0) {
            throw new EconomyException(EconomyException.Kind.INVALID_AMOUNT,
                "El importe no puede ser cero");
        }

        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                long after = applyInTransaction(
                    c, playerId, currency, delta, reason, refType, refId, idempotencyKey);
                c.commit();
                return after;
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * El núcleo. Se expone con {@link Connection} para que operaciones
     * compuestas —una venta de GTS mueve tres asientos— compartan una sola
     * transacción. Ver {@code docs/trading/gts.md}.
     *
     * <p>El llamante es responsable de {@code commit} y {@code rollback}.
     */
    public long applyInTransaction(Connection c, long playerId, Currency currency,
                                   long delta, String reason, String refType,
                                   Long refId, String idempotencyKey)
            throws SQLException, EconomyException {

        ensureRow(c, playerId, currency);

        // FOR UPDATE: bloquea la fila hasta el commit. Sin esto, dos
        // operaciones concurrentes leerían el mismo saldo y una pisaría a la
        // otra — que es exactamente cómo se duplica dinero.
        long current = readBalance(c, playerId, currency, true);
        long after = current + delta;

        if (after < 0) {
            throw new EconomyException(EconomyException.Kind.INSUFFICIENT_FUNDS,
                "Saldo insuficiente: tiene " + current + ", necesita " + (-delta));
        }

        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO ledger_entry
                  (player_id, currency, delta, balance_after, reason,
                   ref_type, ref_id, idempotency_key)
                VALUES (?,?,?,?,?,?,?,?)
                """)) {
            ps.setLong(1, playerId);
            ps.setString(2, currency.name());
            ps.setLong(3, delta);
            ps.setLong(4, after);
            ps.setString(5, reason);
            if (refType == null) ps.setNull(6, java.sql.Types.VARCHAR);
            else ps.setString(6, refType);
            if (refId == null) ps.setNull(7, java.sql.Types.BIGINT);
            else ps.setLong(7, refId);
            ps.setString(8, idempotencyKey);
            ps.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException dup) {
            throw new EconomyException(EconomyException.Kind.ALREADY_APPLIED,
                "Esta operacion ya se aplico (" + idempotencyKey + ")");
        }

        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE player_economy SET balance = ?
                WHERE player_id = ? AND currency = ?
                """)) {
            ps.setLong(1, after);
            ps.setLong(2, playerId);
            ps.setString(3, currency.name());
            ps.executeUpdate();
        }

        return after;
    }

    /**
     * Transferencia entre jugadores, atómica.
     *
     * <p>Las Marcas no se transfieren nunca (ECO-001 §2): son la garantía de
     * que la progresión no se puede comprar.
     */
    public void transfer(long fromId, long toId, Currency currency, long amount,
                         String reason, String idempotencyKey)
            throws SQLException, EconomyException {

        if (!currency.tradeable) {
            throw new EconomyException(EconomyException.Kind.NOT_TRADEABLE,
                currency.displayName + " no se puede transferir");
        }
        if (amount <= 0) {
            throw new EconomyException(EconomyException.Kind.INVALID_AMOUNT,
                "El importe debe ser positivo");
        }
        if (fromId == toId) {
            throw new EconomyException(EconomyException.Kind.INVALID_AMOUNT,
                "Origen y destino son el mismo jugador");
        }

        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                // Bloqueo en orden ascendente de player_id: si dos
                // transferencias cruzadas coinciden, esto evita el interbloqueo.
                long first = Math.min(fromId, toId);
                long second = Math.max(fromId, toId);
                ensureRow(c, first, currency);
                ensureRow(c, second, currency);
                readBalance(c, first, currency, true);
                readBalance(c, second, currency, true);

                applyInTransaction(c, fromId, currency, -amount, reason,
                                   "transfer", toId, idempotencyKey + ":out");
                applyInTransaction(c, toId, currency, amount, reason,
                                   "transfer", fromId, idempotencyKey + ":in");
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // ------------------------------------------------------------ auxiliares

    private static void ensureRow(Connection c, long playerId, Currency currency)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO player_economy (player_id, currency, balance)
                VALUES (?,?,0)
                ON DUPLICATE KEY UPDATE player_id = player_id
                """)) {
            ps.setLong(1, playerId);
            ps.setString(2, currency.name());
            ps.executeUpdate();
        }
    }

    private static long readBalance(Connection c, long playerId,
                                    Currency currency, boolean forUpdate)
            throws SQLException {
        String sql = "SELECT balance FROM player_economy "
                   + "WHERE player_id = ? AND currency = ?"
                   + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setString(2, currency.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /**
     * Comprueba que el saldo cuadra con el libro de asientos.
     * Un desajuste significa duplicación o pérdida: hay que investigarlo.
     */
    public long auditDiscrepancy(long playerId, Currency currency)
            throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                SELECT
                  (SELECT COALESCE(balance,0) FROM player_economy
                    WHERE player_id = ? AND currency = ?)
                  -
                  (SELECT COALESCE(SUM(delta),0) FROM ledger_entry
                    WHERE player_id = ? AND currency = ?)
                """)) {
            ps.setLong(1, playerId);
            ps.setString(2, currency.name());
            ps.setLong(3, playerId);
            ps.setString(4, currency.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }
}
