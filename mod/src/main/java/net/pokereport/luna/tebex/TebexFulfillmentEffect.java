package net.pokereport.luna.tebex;

import java.time.Instant;

/**
 * Representación inmutable de un efecto granular auditado en tebex_fulfillment_effect.
 */
public record TebexFulfillmentEffect(
        long id,
        long fulfillmentId,
        TebexEffectType effectType,
        String resourceId,
        String beforeValue,
        String afterValue,
        boolean createdByFulfillment,
        boolean reversed,
        Instant reversedAt
) {}
