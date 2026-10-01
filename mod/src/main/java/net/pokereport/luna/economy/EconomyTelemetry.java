package net.pokereport.luna.economy;

import net.pokereport.luna.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Persistencia de KPIs orgánicos; nunca ajusta precios por sí sola. */
public final class EconomyTelemetry {

    private final Database db;

    public EconomyTelemetry(Database db) {
        this.db = db;
    }

    public void exclude(long playerId, String reason) throws SQLException {
        String safe = reason == null || reason.isBlank() ? "exclusion administrativa" : reason;
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO economy_excluded_player(player_id, reason)
                 VALUES (?,?)
                 ON DUPLICATE KEY UPDATE reason=VALUES(reason)
                 """)) {
            ps.setLong(1, playerId);
            ps.setString(2, safe.length() <= 120 ? safe : safe.substring(0, 120));
            ps.executeUpdate();
        }
    }

    public void recordNpcTrade(String category, String itemId, boolean playerBought,
                               long quantity, long value) throws SQLException {
        if (quantity <= 0 || value < 0) throw new IllegalArgumentException("trade NPC invalido");
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO npc_trade_daily
                   (trade_date, category_id, item_id, bought_qty, bought_value,
                    sold_qty, sold_value)
                 VALUES (CURRENT_DATE(),?,?,?,?,?,?)
                 ON DUPLICATE KEY UPDATE
                   bought_qty=bought_qty+VALUES(bought_qty),
                   bought_value=bought_value+VALUES(bought_value),
                   sold_qty=sold_qty+VALUES(sold_qty),
                   sold_value=sold_value+VALUES(sold_value)
                 """)) {
            ps.setString(1, category);
            ps.setString(2, itemId);
            ps.setLong(3, playerBought ? quantity : 0);
            ps.setLong(4, playerBought ? value : 0);
            ps.setLong(5, playerBought ? 0 : quantity);
            ps.setLong(6, playerBought ? 0 : value);
            ps.executeUpdate();
        }
    }

    /** Guarda un snapshot por moneda. Debe ejecutarse en el pool de I/O. */
    public void snapshot() throws SQLException {
        for (Currency currency : Currency.values()) {
            if (currency == Currency.MARK) continue;
            snapshot(currency);
        }
    }

    private void snapshot(Currency currency) throws SQLException {
        try (Connection c = db.connection()) {
            long wallet = scalar(c,
                "SELECT COALESCE(SUM(balance),0) FROM player_economy WHERE currency=?",
                currency.name());
            long escrow = currency == Currency.POKEDOLLAR ? scalar(c, """
                SELECT COALESCE(SUM(unit_price * (qty_total-qty_filled)),0)
                FROM market_order
                WHERE side='COMPRA' AND state='ABIERTA'
                """) : 0;
            long clans = currency == Currency.POKEDOLLAR
                ? scalar(c, "SELECT COALESCE(SUM(treasury),0) FROM clan") : 0;
            long total;
            try {
                total = Math.addExact(wallet, Math.addExact(escrow, clans));
            } catch (ArithmeticException overflow) {
                throw new SQLException("Oferta monetaria fuera de rango", overflow);
            }

            List<Long> balances = organicBalances(c, currency);
            long activeSupply = balances.stream().reduce(0L, Math::addExact);
            long activePlayers = balances.size();
            long faucets = classifiedVolume(c, currency, EconomyFlowKind.FAUCET);
            long sinks = classifiedVolume(c, currency, EconomyFlowKind.SINK);
            long transfers = classifiedVolume(c, currency, EconomyFlowKind.TRANSFER);
            long unclassified = scalar(c, """
                SELECT COUNT(*) FROM ledger_entry l
                WHERE l.currency=? AND l.flow_kind IS NULL
                  AND l.created_at >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 24 HOUR)
                """, currency.name());

            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO economy_snapshot
                  (currency, wallet_supply, market_escrow, clan_treasury,
                   total_supply, active_wallet_supply, active_players,
                   p10,p25,p50,p75,p90,p95,p99,
                   faucets_24h,sinks_24h,transfer_volume_24h,unclassified_24h)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
                int i = 1;
                ps.setString(i++, currency.name());
                ps.setLong(i++, wallet);
                ps.setLong(i++, escrow);
                ps.setLong(i++, clans);
                ps.setLong(i++, total);
                ps.setLong(i++, activeSupply);
                ps.setLong(i++, activePlayers);
                for (int p : new int[]{10,25,50,75,90,95,99}) ps.setLong(i++, percentile(balances, p));
                ps.setLong(i++, faucets);
                ps.setLong(i++, sinks);
                ps.setLong(i++, transfers);
                ps.setLong(i, unclassified);
                ps.executeUpdate();
            }
        }
    }

    private List<Long> organicBalances(Connection c, Currency currency) throws SQLException {
        List<Long> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
            SELECT e.balance
            FROM player_economy e
            WHERE e.currency=?
              AND NOT EXISTS (SELECT 1 FROM economy_excluded_player x
                              WHERE x.player_id=e.player_id)
              AND NOT EXISTS (SELECT 1 FROM ledger_entry bad
                              WHERE bad.player_id=e.player_id
                                AND bad.flow_kind IN ('TEST','ADMIN'))
              AND EXISTS (SELECT 1 FROM ledger_entry recent
                          WHERE recent.player_id=e.player_id
                            AND recent.created_at >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 24 HOUR))
            ORDER BY e.balance
            """)) {
            ps.setString(1, currency.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getLong(1));
            }
        }
        return out;
    }

    private long classifiedVolume(Connection c, Currency currency, EconomyFlowKind kind)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
            SELECT COALESCE(SUM(ABS(l.delta)),0)
            FROM ledger_entry l
            WHERE l.currency=? AND l.flow_kind=?
              AND l.created_at >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 24 HOUR)
              AND NOT EXISTS (SELECT 1 FROM economy_excluded_player x
                              WHERE x.player_id=l.player_id)
              AND NOT EXISTS (SELECT 1 FROM ledger_entry bad
                              WHERE bad.player_id=l.player_id
                                AND bad.flow_kind IN ('TEST','ADMIN'))
            """)) {
            ps.setString(1, currency.name());
            ps.setString(2, kind.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static long scalar(Connection c, String sql, String... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setString(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static long percentile(List<Long> ordered, int p) {
        if (ordered.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * ordered.size()) - 1;
        return ordered.get(Math.max(0, Math.min(index, ordered.size() - 1)));
    }
}
