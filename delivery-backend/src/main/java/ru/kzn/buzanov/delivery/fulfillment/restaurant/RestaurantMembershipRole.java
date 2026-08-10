package ru.kzn.buzanov.delivery.fulfillment.restaurant;

public enum RestaurantMembershipRole {
    OWNER,
    ADMIN,
    OPERATOR;

    public boolean canEditFulfillmentSettings() {
        return this == OWNER || this == ADMIN;
    }

    public static RestaurantMembershipRole fromApi(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return RestaurantMembershipRole.valueOf(raw.trim().toUpperCase());
    }
}
