package net.pokereport.luna.tebex;

/**
 * Estados del proceso de reversión del entitlement (efectos de la compra).
 * Distingue el evento financiero del resultado técnico de la reversión.
 */
public enum TebexReversalStatus {
    NONE,
    PROCESSING,
    COMPLETED,
    REQUIRES_REVIEW,
    NOT_APPLICABLE
}
