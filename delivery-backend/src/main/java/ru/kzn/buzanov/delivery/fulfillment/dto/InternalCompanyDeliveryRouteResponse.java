package ru.kzn.buzanov.delivery.fulfillment.dto;

import java.util.UUID;

/**
 * S2S delivery routing result: winning branch + zone quote fields.
 * {@code branchId} is null when routing could not pick a serving branch.
 */
public record InternalCompanyDeliveryRouteResponse(
        UUID branchId,
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
}
