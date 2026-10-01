package net.pokereport.luna.shop;

import net.pokereport.luna.LunaEternal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Calculadora de precios dinámicos para NPCs.
 * Resuelve la vulnerabilidad de granjas infinitas aplicando "Presión de Oferta".
 */
public final class DynamicPricing {

    // Clave: itemId, Valor: cantidad vendida hoy
    private static final Map<String, Long> soldTodayCache = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // ¿A partir de cuántas unidades vendidas globalmente al día el precio empieza a caer?
    // TODO: Esto debería ser configurable por ítem. Usamos 5000 por defecto para bayas, etc.
    private static final long DEFAULT_SATURATION = 5000;

    public static void start() {
        // Actualizar caché cada minuto
        scheduler.scheduleAtFixedRate(DynamicPricing::refreshCache, 0, 1, TimeUnit.MINUTES);
    }

    public static void stop() {
        scheduler.shutdown();
    }

    private static void refreshCache() { try (java.sql.Connection c = net.pokereport.luna.LunaEternal.database().connection(); java.sql.PreparedStatement ps = c.prepareStatement("SELECT item_id, SUM(CASE WHEN trade_date = CURRENT_DATE() THEN sold_qty ELSE 0 END) as today_qty, SUM(CASE WHEN trade_date = DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY) THEN sold_qty ELSE 0 END) as yesterday_qty FROM npc_trade_daily WHERE trade_date >= DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY) GROUP BY item_id")) { try (java.sql.ResultSet rs = ps.executeQuery()) { soldTodayCache.clear(); double dayFraction = (System.currentTimeMillis() % 86400000.0) / 86400000.0; while (rs.next()) { long today = rs.getLong("today_qty"); long yesterday = rs.getLong("yesterday_qty"); long rolling = (long) (today + (yesterday * (1.0 - dayFraction))); soldTodayCache.put(rs.getString("item_id"), rolling); } } } catch (java.sql.SQLException e) {} }

    public static long getDynamicSellPrice(ShopCatalog.Entry entry) { long basePrice = entry.sell(); if (basePrice <= 0) return 0; long supply = soldTodayCache.getOrDefault(entry.clave(), 0L); long threshold = DEFAULT_SATURATION; double floor = 0.10; double elasticity = 2.0; double factor = floor + ((1.0 - floor) / (1.0 + Math.pow((double) supply / threshold, elasticity))); long currentPrice = (long) (basePrice * factor); return Math.max((long)(basePrice * floor), Math.min(basePrice, currentPrice)); }

    public static long getDynamicBuyPrice(ShopCatalog.Entry entry) {
        long basePrice = entry.buy();
        if (basePrice <= 0) return 0; // No se puede comprar
        
        double multiplier = 1.0;
        if (LunaEternal.economicController() != null) {
            multiplier = LunaEternal.economicController().getNPCBuyMultiplier();
        }
        
        // Cap de inflación: máximo cuesta el doble, mínimo cuesta la mitad.
        double cappedMultiplier = Math.max(0.5, Math.min(2.0, multiplier));
        
        return (long) (basePrice * cappedMultiplier);
    }
}
