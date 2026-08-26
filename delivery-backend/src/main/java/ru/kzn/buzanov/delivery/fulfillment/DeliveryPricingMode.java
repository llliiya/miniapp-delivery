package ru.kzn.buzanov.delivery.fulfillment;

public enum DeliveryPricingMode {
    FLAT,
    ZONES;

    public static DeliveryPricingMode fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return FLAT;
        }
        try {
            return DeliveryPricingMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("deliveryPricingMode must be FLAT or ZONES");
        }
    }
}
