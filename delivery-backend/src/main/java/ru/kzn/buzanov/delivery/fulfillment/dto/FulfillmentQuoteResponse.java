package ru.kzn.buzanov.delivery.fulfillment.dto;

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
        String reasonCode
) {
}
