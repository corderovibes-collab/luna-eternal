package net.pokereport.luna.command;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyStats;

import java.util.function.Consumer;

/**
 * Informe económico, en texto.
 *
 * <p>Va por comando y no por menú a propósito: P9 exige interfaces para el
 * jugador, pero esto es <b>herramienta de administración</b> y son datos
 * densos. Una tabla de números se lee mejor en texto que en una rejilla de
 * iconos, y además se puede copiar del log.
 */
public final class EconomyReport {

    private EconomyReport() {}

    public static void render(EconomyStats stats, int horas, Consumer<String> out)
            throws Exception {

        out.accept("§8§m                                                    ");
        out.accept("§6§lINFORME ECONÓMICO §8· §7últimas " + horas + " h");
        out.accept("");

        long total = stats.totalPlayers();
        long activos = stats.activePlayers(horas);
        out.accept("§7Jugadores: §f" + total + " §8registrados · §f" + activos
                   + " §8con actividad económica");
        out.accept("");

        for (Currency c : Currency.values()) {
            renderMoneda(stats, c, horas, out);
        }

        out.accept("§8§m                                                    ");
    }

    private static void renderMoneda(EconomyStats stats, Currency c, int horas,
                                     Consumer<String> out) throws Exception {

        var supply = stats.moneySupply();
        long masa = supply.getOrDefault(c, 0L);
        long descuadre = stats.supplyDiscrepancy(c);
        var reparto = stats.wealth(c);
        var flujos = stats.flows(c, horas);

        out.accept(c.color + "§l" + c.displayName.toUpperCase());

        // El cuadre va PRIMERO: si falla, todo lo demás es sospechoso.
        if (descuadre == 0) {
            out.accept("  §a✔ §7Masa: §f" + fmt(masa)
                       + " §8(cuadra con el libro de asientos)");
        } else {
            out.accept("  §c✘ §7Masa: §f" + fmt(masa)
                       + " §c· DESCUADRE de " + fmt(descuadre));
            out.accept("  §c   Hay dinero creado o perdido fuera del sistema.");
        }

        if (reparto.players() > 0) {
            out.accept("  §7Reparto: §8P50 §f" + fmt(reparto.p50())
                       + " §8· P90 §f" + fmt(reparto.p90())
                       + " §8· P99 §f" + fmt(reparto.p99())
                       + " §8· máx §f" + fmt(reparto.max()));

            // La distancia entre P50 y P99 dice si la economía se concentra.
            if (reparto.p50() > 0) {
                long ratio = reparto.p99() / Math.max(1, reparto.p50());
                out.accept("  §7Concentración: §f×" + ratio
                           + " §8(P99 frente a P50" + valoracion(ratio) + "§8)");
            }
        }

        if (c == Currency.POKEDOLLAR) {
            long mediana = stats.medianDailyIncome(c);
            out.accept("  §7Ingreso diario (mediana): §f" + fmt(mediana));
            out.accept("  §8   Es el numero que calibra el margen de expedicion,");
            out.accept("  §8   el tope de los kits y los tramos del impuesto.");
        }

        if (flujos.isEmpty()) {
            out.accept("  §8Sin movimientos en el periodo.");
        } else {
            out.accept("  §7Flujos §8(entra / sale / neto):");
            long creado = 0, destruido = 0;
            for (var f : flujos) {
                creado += f.created();
                destruido += f.destroyed();
                out.accept("  §8   " + pad(f.reason(), 16)
                           + " §a+" + pad(fmt(f.created()), 12)
                           + " §c-" + pad(fmt(f.destroyed()), 12)
                           + (f.net() >= 0 ? "§a" : "§c") + fmt(f.net())
                           + " §8(" + f.operations() + " ops)");
            }
            long neto = creado - destruido;
            out.accept("  §7   TOTAL: §a+" + fmt(creado) + " §c-" + fmt(destruido)
                       + " §8= " + (neto >= 0 ? "§a+" : "§c") + fmt(neto));
            out.accept("  §8   " + diagnostico(creado, destruido));
        }
        out.accept("");
    }

    /**
     * Lectura del balance entre lo que entra y lo que sale.
     *
     * <p>Es el dato que decide si la economía va a inflarse. Un servidor sano
     * crea algo más de lo que destruye —los jugadores deben progresar— pero no
     * mucho más.
     */
    private static String diagnostico(long creado, long destruido) {
        if (creado == 0) return "Sin datos suficientes.";
        double ratio = (double) creado / Math.max(1, destruido);
        if (destruido == 0) return "§c¡No hay sinks funcionando! Todo entra y nada sale.";
        if (ratio > 3.0)  return "§cInflacion severa: entra " + String.format("%.1f", ratio)
                                 + "x lo que sale. Revisar sinks.";
        if (ratio > 1.8)  return "§6Inflacion moderada (" + String.format("%.1f", ratio)
                                 + "x). Vigilar.";
        if (ratio > 1.15) return "§aSano (" + String.format("%.1f", ratio) + "x).";
        if (ratio > 0.9)  return "§aEquilibrado (" + String.format("%.1f", ratio) + "x).";
        return "§6Deflacion: sale mas de lo que entra. Los jugadores no progresan.";
    }

    private static String valoracion(long ratio) {
        if (ratio <= 5) return ", muy igualado";
        if (ratio <= 20) return ", normal";
        if (ratio <= 100) return ", desigual";
        return ", §cmuy concentrado";
    }

    private static String pad(String s, int n) {
        return s.length() >= n ? s : s + " ".repeat(n - s.length());
    }

    private static String fmt(long v) {
        return String.format("%,d", v);
    }

    /** Registra el informe en el log del servidor, para historial. */
    public static void logDaily() {
        LunaEternal.submit(() -> {
            try {
                render(LunaEternal.stats(), 24,
                    line -> LunaEternal.LOG.info(line.replaceAll("§.", "")));
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo generar el informe economico", e);
            }
        });
    }

    /** Envía el informe a quien ejecutó el comando. */
    public static void send(ServerCommandSource src, int horas) {
        LunaEternal.submit(() -> {
            try {
                var lineas = new java.util.ArrayList<String>();
                render(LunaEternal.stats(), horas, lineas::add);
                src.getServer().execute(() -> {
                    for (String l : lineas) {
                        src.sendFeedback(() -> Text.literal(l), false);
                    }
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("Error generando el informe", e);
                src.getServer().execute(() -> src.sendError(
                    Text.literal("No se pudo generar el informe.")));
            }
        });
    }
}
