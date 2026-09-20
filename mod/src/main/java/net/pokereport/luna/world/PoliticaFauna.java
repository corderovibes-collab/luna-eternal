package net.pokereport.luna.world;

import java.util.Set;

/** Solo fauna vanilla y entrenadores ambientales; nunca entidades genéricas del mundo. */
public final class PoliticaFauna {
    public static final float DENSIDAD_HOGAR = 0.04f;
    public static final float DENSIDAD_SALVAJE = 3.0f;
    public static final float MULTIPLICADOR_ULTRARRARO_SALVAJE = 1.10f;
    private static final Set<String> RAREZAS_PROHIBIDAS_HOGAR = Set.of(
            "legendary", "mythical", "ultra_beast", "paradox", "restricted");
    private PoliticaFauna() {}
    public static boolean bloquear(String namespace, boolean mob, boolean personalizado,
                                   boolean entrenadorAmbiental, boolean persistente) {
        if (!mob || personalizado) return false;
        if (namespace.equals("minecraft")) return true;
        return entrenadorAmbiental && !persistente;
    }
    public static float densidad(String dimension) {
        if (dimension.equals("minecraft:overworld")) return DENSIDAD_HOGAR;
        if (dimension.matches("lunaeternal:salvaje[2-6]?")) return DENSIDAD_SALVAJE;
        return 1f;
    }

    public static boolean esSalvaje(String dimension) {
        return dimension != null && dimension.matches("lunaeternal:salvaje[2-6]?");
    }

    /** Política de aparicion natural: solo Gen 1-2; Hogar sin shiny ni rarezas mayores. */
    public static boolean permitirPokemon(String dimension, int dex, boolean shiny,
                                           Set<String> etiquetas) {
        if (dex < 1 || dex > 251) return false;
        if (!"minecraft:overworld".equals(dimension)) return true;
        if (shiny) return false;
        if (etiquetas == null) return true;
        for (String etiqueta : etiquetas) {
            if (RAREZAS_PROHIBIDAS_HOGAR.contains(etiqueta.toLowerCase())) return false;
        }
        return true;
    }
}
