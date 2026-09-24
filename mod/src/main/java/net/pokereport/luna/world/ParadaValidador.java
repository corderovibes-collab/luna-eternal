package net.pokereport.luna.world;

import java.util.Locale;

/**
 * Validador puro para nombres y etiquetas asociados a paradas de viaje (moto-taxi Miraidon).
 *
 * <p>Separado de {@link Decorativos} para permitir pruebas unitarias sin dependencias
 * del entorno de Minecraft / Cobblemon.
 */
public final class ParadaValidador {

    private ParadaValidador() {}

    /**
     * Evalúa si un texto (etiqueta o nombre personalizado) corresponde a una parada/taxi Miraidon.
     *
     * @param texto Cadena a evaluar (etiqueta de entidad o nombre visible)
     * @return {@code true} si contiene palabras clave como "parada", "taxi" o "miraidon"
     */
    public static boolean esTextoParada(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        String t = texto.toLowerCase(Locale.ROOT);
        return t.contains("parada") || t.contains("taxi") || t.contains("miraidon");
    }
}
