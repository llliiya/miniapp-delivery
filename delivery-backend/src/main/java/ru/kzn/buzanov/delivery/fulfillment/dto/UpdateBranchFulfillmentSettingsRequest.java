package ru.kzn.buzanov.delivery.fulfillment.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateBranchFulfillmentSettingsRequest(
        @NotNull Boolean deliveryEnabled,
        @NotNull Boolean pickupEnabled,
        @NotNull @Min(0) @Max(100_000_000) Long minimumDeliveryOrderMinor,
        @NotNull @Min(0) @Max(100_000_000) Long deliveryFeeMinor,
        @Min(0) @Max(100_000_000) Long freeDeliveryFromMinor,
        @NotNull @Min(1) @Max(1440) Integer deliveryEstimatedMinMinutes,
        @NotNull @Min(1) @Max(1440) Integer deliveryEstimatedMaxMinutes,
        @NotNull @Min(1) @Max(1440) Integer pickupEstimatedMinutes,
        /** FLAT (default) or ZONES. Null treated as FLAT for backward compatibility. */
        String deliveryPricingMode
) {
    public UpdateBranchFulfillmentSettingsRequest(
            Boolean deliveryEnabled,
            Boolean pickupEnabled,
            Long minimumDeliveryOrderMinor,
            Long deliveryFeeMinor,
            Long freeDeliveryFromMinor,
            Integer deliveryEstimatedMinMinutes,
            Integer deliveryEstimatedMaxMinutes,
            Integer pickupEstimatedMinutes
    ) {
        this(deliveryEnabled, pickupEnabled, minimumDeliveryOrderMinor, deliveryFeeMinor,
                freeDeliveryFromMinor, deliveryEstimatedMinMinutes, deliveryEstimatedMaxMinutes,
                pickupEstimatedMinutes, "FLAT");
    }

    @AssertTrue(message = "At least one of deliveryEnabled or pickupEnabled must be true")
    public boolean isAtLeastOneMethodEnabled() {
        return Boolean.TRUE.equals(deliveryEnabled) || Boolean.TRUE.equals(pickupEnabled);
    }

    @AssertTrue(message = "deliveryEstimatedMinMinutes must be <= deliveryEstimatedMaxMinutes")
    public boolean isDeliveryTimeRangeValid() {
        if (deliveryEstimatedMinMinutes == null || deliveryEstimatedMaxMinutes == null) {
            return true;
        }
        return deliveryEstimatedMinMinutes <= deliveryEstimatedMaxMinutes;
    }
}
