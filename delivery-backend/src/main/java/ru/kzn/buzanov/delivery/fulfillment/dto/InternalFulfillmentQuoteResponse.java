package ru.kzn.buzanov.delivery.fulfillment.dto;

import java.util.UUID;

/**
 * Narrow S2S contract for miniapp-restaurant public checkout.
 * Maps from {@link FulfillmentQuoteResponse} plus settings free-delivery threshold.
 */
public record InternalFulfillmentQuoteResponse(
        FulfillmentType type,
        boolean available,
        long itemsTotalMinor,
        long deliveryFeeMinor,
        long minimumOrderMinor,
        boolean minimumOrderSatisfied,
        Long freeDeliveryThresholdMinor,
        Integer estimatedMinutesMin,
        Integer estimatedMinutesMax,
        String issueCode,
        String pricingMode,
        UUID zoneId,
        String zoneName
) {
    public InternalFulfillmentQuoteResponse(
            FulfillmentType type,
            boolean available,
            long itemsTotalMinor,
            long deliveryFeeMinor,
            long minimumOrderMinor,
            boolean minimumOrderSatisfied,
            Long freeDeliveryThresholdMinor,
            Integer estimatedMinutesMin,
            Integer estimatedMinutesMax,
            String issueCode
    ) {
        this(type, available, itemsTotalMinor, deliveryFeeMinor, minimumOrderMinor,
                minimumOrderSatisfied, freeDeliveryThresholdMinor, estimatedMinutesMin,
                estimatedMinutesMax, issueCode, "FLAT", null, null);
    }
}
