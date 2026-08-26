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
        String deliveryPricingMode,
        Instant updatedAt
) {
    public BranchFulfillmentSettingsResponse(
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
        this(configured, branchId, deliveryEnabled, pickupEnabled, minimumDeliveryOrderMinor,
                deliveryFeeMinor, freeDeliveryFromMinor, deliveryEstimatedMinMinutes,
                deliveryEstimatedMaxMinutes, pickupEstimatedMinutes, "FLAT", updatedAt);
    }
}
