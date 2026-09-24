package net.pokereport.luna.tebex;

/**
 * Estados del ciclo de vida para transacciones y entregas de Tebex.
 */
public enum TebexStatus {
    RECEIVED,
    VALIDATING,
    PROCESSING,
    DELIVERED,
    ALREADY_PROCESSED,
    PENDING_IMPLEMENTATION,
    PENDING_PLAYER_RESOLUTION,
    REQUIRES_REVIEW,
    FAILED,
    REFUND,
    REFUNDED,
    REFUNDED_DEFICIT,
    CHARGEBACK,
    REVERSED;

    public static TebexStatus de(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            if ("REFUNDED".equalsIgnoreCase(val)) {
                return REFUND;
            }
            return FAILED;
        }
    }
}
