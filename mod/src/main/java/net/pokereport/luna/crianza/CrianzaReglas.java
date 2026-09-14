package net.pokereport.luna.crianza;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas puras de herencia biológica para la crianza (Luna Eternal).
 *
 * <p>Aisla las decisiones de variantes regionales, habilidades ocultas y movimientos huevo
 * para permitir validación determinista y desacoplada de la API de Cobblemon.
 */
public final class CrianzaReglas {

    private CrianzaReglas() {}

    public static final Set<String> FORMAS_REGIONALES = Set.of(
            "alolan", "galarian", "hisuian", "paldean"
    );

    public static String determinarAspectoRegional(
            Set<String> aspectosMadre, boolean everMadre, boolean madreEsDitto,
            Set<String> aspectosPadre, boolean everPadre, boolean padreEsDitto,
            double rng) {
        boolean madreElegible = everMadre && !madreEsDitto && aspectosMadre != null;
        boolean padreElegible = everPadre && !padreEsDitto && aspectosPadre != null;

        String aspectoMadre = null;
        if (madreElegible) {
            for (String asp : aspectosMadre) {
                if (FORMAS_REGIONALES.contains(asp.toLowerCase(Locale.ROOT))) {
                    aspectoMadre = asp.toLowerCase(Locale.ROOT);
                    break;
                }
            }
        }

        String aspectoPadre = null;
        if (padreElegible) {
            for (String asp : aspectosPadre) {
                if (FORMAS_REGIONALES.contains(asp.toLowerCase(Locale.ROOT))) {
                    aspectoPadre = asp.toLowerCase(Locale.ROOT);
                    break;
                }
            }
        }

        if (aspectoMadre != null && aspectoPadre != null) {
            return rng < 0.5 ? aspectoMadre : aspectoPadre;
        }
        if (aspectoMadre != null) return aspectoMadre;
        if (aspectoPadre != null) return aspectoPadre;
        return null;
    }

    public static boolean puedeTransmitirHabilidadOculta(
            boolean madreHO, boolean padreHO, boolean madreEsDitto, boolean padreEsDitto) {
        if (!madreEsDitto && madreHO) return true;
        if (madreEsDitto && padreHO) return true;
        if (padreEsDitto && madreHO) return true;
        return false;
    }

    public static List<String> filtrarMovimientosHuevo(
            List<String> movimientosMadre, List<String> movimientosPadre, Set<String> eggMovesValidos) {
        List<String> resultado = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        if (eggMovesValidos == null || eggMovesValidos.isEmpty()) return resultado;

        List<List<String>> listas = new ArrayList<>();
        if (movimientosPadre != null) listas.add(movimientosPadre);
        if (movimientosMadre != null) listas.add(movimientosMadre);

        for (var lista : listas) {
            for (String m : lista) {
                if (m == null || m.isBlank()) continue;
                String lower = m.toLowerCase(Locale.ROOT);
                if (vistos.contains(lower)) continue;
                for (String egg : eggMovesValidos) {
                    if (egg != null && egg.equalsIgnoreCase(m)) {
                        resultado.add(m);
                        vistos.add(lower);
                        break;
                    }
                }
            }
        }
        return resultado;
    }
}
