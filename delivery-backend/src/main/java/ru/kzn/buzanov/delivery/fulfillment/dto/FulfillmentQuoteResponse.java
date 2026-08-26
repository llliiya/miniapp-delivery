package ru.kzn.buzanov.delivery.fulfillment.dto;

import java.util.UUID;

public record FulfillmentQuoteResponse(
        FulfillmentType type,
        boolean available,
        long itemsTotalMinor,
        long minimumOrderMinor,
        boolean minimumOrderMet,
        long minimumOrderShortfallMinor,
        long deliveryFeeMinor,
        boolean freeDeliveryApplied,
        long totalMinor,
        Integer estimatedMinMinutes,
        Integer estimatedMaxMinutes,
        String reasonCode,
        UUID zoneId,
        String zoneName
) {
    public FulfillmentQuoteResponse(
            FulfillmentType type,
            boolean available,
            long itemsTotalMinor,
            long minimumOrderMinor,
            boolean minimumOrderMet,
            long minimumOrderShortfallMinor,
            long deliveryFeeMinor,
            boolean freeDeliveryApplied,
            long totalMinor,
            Integer estimatedMinMinutes,
            Integer estimatedMaxMinutes,
            String reasonCode
    ) {
        this(type, available, itemsTotalMinor, minimumOrderMinor, minimumOrderMet,
                minimumOrderShortfallMinor, deliveryFeeMinor, freeDeliveryApplied, totalMinor,
                estimatedMinMinutes, estimatedMaxMinutes, reasonCode, null, null);
    }
}
