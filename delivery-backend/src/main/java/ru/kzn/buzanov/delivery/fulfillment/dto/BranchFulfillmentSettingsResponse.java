package ru.kzn.buzanov.delivery.fulfillment.dto;

import java.time.Instant;
import java.util.UUID;

public record BranchFulfillmentSettingsResponse(
        boolean configured,
        UUID branchId,
        boolean deliveryEnabled,
        boolean pickupEnabled,
        long minimumDeliveryOrderMinor,
        long deliveryFeeMinor,
        Long freeDeliveryFromMinor,
        int deliveryEstimatedMinMinutes,
        int deliveryEstimatedMaxMinutes,
        int pickupEstimatedMinutes,
        Instant updatedAt
) {
}
