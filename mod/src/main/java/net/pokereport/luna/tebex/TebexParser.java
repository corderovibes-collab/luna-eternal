package net.pokereport.luna.tebex;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Parser y normalizador seguro de identificadores de Tebex para Minecraft Java.
 */
public final class TebexParser {

    private static final Pattern HEX_32 = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern SAFE_TX = Pattern.compile("^[a-zA-Z0-9\\-_:.]{1,255}$");

    private TebexParser() {}

    /**
     * Parsea y normaliza un identificador de jugador entregado por Tebex.
     * Soporta UUID canónico con guiones (36 chars) y formato compacto Mojang sin guiones (32 chars hex).
     *
     * <p>⚠ PROHIBIDO usar usernames o identificadores arbitrarios como fallback silencioso.
     * Si la entrada no es un UUID válido, devuelve {@code null}.
     */
    public static UUID parseCanonicalUuid(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // Formato estándar con guiones (36 caracteres)
        if (trimmed.length() == 36 && trimmed.charAt(8) == '-' && trimmed.charAt(13) == '-'
                && trimmed.charAt(18) == '-' && trimmed.charAt(23) == '-') {
            try {
                return UUID.fromString(trimmed);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        // Formato compacto Mojang (32 caracteres hexadecimales)
        if (trimmed.length() == 32 && HEX_32.matcher(trimmed).matches()) {
            String hyphenated = trimmed.substring(0, 8) + "-" +
                    trimmed.substring(8, 12) + "-" +
                    trimmed.substring(12, 16) + "-" +
                    trimmed.substring(16, 20) + "-" +
                    trimmed.substring(20, 32);
            try {
                return UUID.fromString(hyphenated);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Valida que el identificador de transacción cumpla con restricciones seguras de longitud y caracteres.
     * Rechaza cadenas vacías, longitud > 255, espacios en blanco y caracteres de control.
     * Acepta caracteres seguros necesarios para IDs de Tebex: letras, dígitos, guiones, guiones bajos, dos puntos y puntos.
     */
    public static boolean isValidTransactionId(String transactionId) {
        if (transactionId == null || transactionId.isBlank() || transactionId.length() > 255) {
            return false;
        }
        if (transactionId.length() != transactionId.trim().length()) {
            return false;
        }
        return SAFE_TX.matcher(transactionId).matches();
    }
}
