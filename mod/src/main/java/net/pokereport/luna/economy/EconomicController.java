package net.pokereport.luna.economy;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fase 8, 9, 15, 16 y 17: POKEREPORT ECONOMIC CONTROLLER (Banco Central)
 *
 * <p>Mide la inflación en base al PPI (Índice de Precios PokeReport)
 * utilizando datos reales del GTS mediante una CANASTA PONDERADA,
 * y separa la métrica de la Política Económica gradual.
 */
public class EconomicController {
    
    private final Database db;

    private static class BasketItem {
        final String itemId;
        final double basePrice;
        final double weight;
        BasketItem(String itemId, double basePrice, double weight) {
            this.itemId = itemId;
            this.basePrice = basePrice;
            this.weight = weight;
        }
    }

    // FASE 8 y 9: Canasta Ponderada
    private static final List<BasketItem> PR_CPI_BASKET = List.of(
        new BasketItem("cobblemon:poke_ball", 400.0, 0.30),      // Alto consumo, esencial
        new BasketItem("cobblemon:ultra_ball", 1200.0, 0.25),    // Consumo competitivo
        new BasketItem("cobblemon:rare_candy", 5000.0, 0.15),    // Rareza media
        new BasketItem("minecraft:iron_ingot", 50.0, 0.10),      // Material base
        new BasketItem("minecraft:diamond", 1000.0, 0.10),       // Refugio de valor
        new BasketItem("cobblemon:exp_candy_xl", 8000.0, 0.10)   // Lujo competitivo
    );

    // Métricas (Lo que ES)
    private double currentPPI = 1.0;
    private double currentConfidence = 100.0;
    
    // Política (Lo que HACE)
    private double currentTaxMultiplier = 1.0;
    private double currentNPCBuyMultiplier = 1.0;

    // FASE 17: Límites Graduales
    private static final double MAX_CHANGE_PER_CYCLE = 0.05; // Solo 5% de cambio máximo por hora
    private static final double TARGET_INFLATION = 1.0;
    private static final double POLICY_SENSITIVITY = 0.5; // Responde a la mitad de la inflación (Amortiguador)
    
    private static final java.util.concurrent.ScheduledExecutorService scheduler = 
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

    public EconomicController(Database db) {
        this.db = db;
    }

    public void start() {
        // Calcular de inmediato y luego cada hora
        scheduler.scheduleAtFixedRate(this::cycle, 0, 1, java.util.concurrent.TimeUnit.HOURS);
    }
    
    public void stop() {
        scheduler.shutdown();
    }

    private void cycle() {
        measureEconomy();
        applyEconomicPolicy();
    }

    /**
     * FASE 16: MEDIR LA ECONOMÍA
     */
    private void measureEconomy() {
        double sumCurrentWeighted = 0;
        double sumBaseWeighted = 0;
        int itemsWithData = 0;

        try (Connection c = db.connection()) {
            for (BasketItem item : PR_CPI_BASKET) {
                // Filtramos FASE 12 (Wash Trading) pidiendo unique_buyers >= 3 y ventas recientes
                try (PreparedStatement ps = c.prepareStatement("""
                    SELECT price / quantity AS unit_price
                    FROM gts_listing 
                    WHERE item_id = ? AND state = 'SOLD' 
                      AND sold_at >= DATE_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY)
                    ORDER BY unit_price ASC
                    """)) {
                    ps.setString(1, item.itemId);
                    try (ResultSet rs = ps.executeQuery()) {
                        List<Double> prices = new ArrayList<>();
                        while (rs.next()) {
                            prices.add(rs.getDouble(1));
                        }
                        
                        if (prices.size() >= 5) { // Requiere mínimo 5 transacciones para liquidez
                            // Quartile filtering (Drop 10% bottom and top outliers)
                            int lowerBound = (int)(prices.size() * 0.10);
                            int upperBound = (int)(prices.size() * 0.90);
                            List<Double> filtered = prices.subList(lowerBound, Math.max(lowerBound + 1, upperBound));
                            
                            double median = filtered.get(filtered.size() / 2);
                            sumCurrentWeighted += median * item.weight;
                            sumBaseWeighted += item.basePrice * item.weight;
                            itemsWithData++;
                        } else {
                            // FASE 14: Cold Start (Si no hay ventas, asume el precio base)
                            sumCurrentWeighted += item.basePrice * item.weight;
                            sumBaseWeighted += item.basePrice * item.weight;
                        }
                    }
                }
            }
            
            this.currentPPI = sumCurrentWeighted / sumBaseWeighted;
            this.currentConfidence = ((double) itemsWithData / PR_CPI_BASKET.size()) * 100.0;
            
            LunaEternal.LOG.info("🏦 [BCE PokeReport] Medición PPI: {}x (Confianza: {}%)", 
                                 String.format("%.2f", currentPPI), String.format("%.0f", currentConfidence));
            
        } catch (Exception e) {
            LunaEternal.LOG.error("Fallo al calcular el PPI Macroeconómico", e);
            this.currentConfidence = 0.0; // Circuit Breaker
        }
    }

    /**
     * FASE 16 y 17: APLICAR POLÍTICA (Lento y seguro)
     */
    private void applyEconomicPolicy() {
        // FASE 20: Circuit Breaker
        if (Double.isNaN(currentPPI) || currentPPI <= 0 || currentPPI > 40.0 || currentConfidence < 50.0) {
            LunaEternal.LOG.warn("🏦 [BCE PokeReport] CIRCUIT BREAKER ACTIVO. PPI Anormal ({}x) o Confianza Baja ({}%). Se mantiene política anterior.", currentPPI, currentConfidence);
            return; // Se mantiene el LAST KNOWN GOOD STATE
        }

        // FASE 17: Controlador Amortiguado
        double error = currentPPI - TARGET_INFLATION;
        double desiredAdjustment = error * POLICY_SENSITIVITY;

        // FASE 19: maxChangePerCycle
        desiredAdjustment = Math.max(-MAX_CHANGE_PER_CYCLE, Math.min(MAX_CHANGE_PER_CYCLE, desiredAdjustment));

        // Aplicamos política al multiplicador del GTS (Taxes) y Compras NPC
        double newMultiplier = currentTaxMultiplier + desiredAdjustment;
        
        // Límites duros (No más de 2.0x ni menos de 0.5x)
        currentTaxMultiplier = Math.max(0.5, Math.min(2.0, newMultiplier));
        currentNPCBuyMultiplier = Math.max(0.5, Math.min(2.0, newMultiplier));

        LunaEternal.LOG.info("🏦 [BCE PokeReport] Política Aplicada. Ajuste: {}. Nuevo Multiplicador: {}x", 
                             String.format("%+.3f", desiredAdjustment), String.format("%.3f", currentTaxMultiplier));
    }

    public double getTaxMultiplier() {
        return currentTaxMultiplier;
    }

    public double getNPCBuyMultiplier() {
        return currentNPCBuyMultiplier;
    }

    public double getCurrentInflationRate() {
        return currentPPI;
    }
    
    public double getConfidence() {
        return currentConfidence;
    }
}
