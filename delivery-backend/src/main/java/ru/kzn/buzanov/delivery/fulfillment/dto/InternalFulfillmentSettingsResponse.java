package ru.kzn.buzanov.delivery.fulfillment.dto;

/**
 * S2S snapshot of whether a branch has saved fulfillment settings.
 * Unconfigured rows are not persisted; defaults are pickup-only and must not
 * be treated as a real launch configuration.
 */
public record InternalFulfillmentSettingsResponse(
        boolean configured,
        boolean pickupEnabled,
        boolean deliveryEnabled,
        String pricingMode,
        int activeDeliveryZonesCount
) {
    public InternalFulfillmentSettingsResponse(boolean configured, boolean pickupEnabled, boolean deliveryEnabled) {
        this(configured, pickupEnabled, deliveryEnabled, "FLAT", 0);
    }

    public InternalFulfillmentSettingsResponse(
            boolean configured,
            boolean pickupEnabled,
            boolean deliveryEnabled,
            String pricingMode
    ) {
        this(configured, pickupEnabled, deliveryEnabled, pricingMode, 0);
    }
}
