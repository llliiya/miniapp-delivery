package ru.kzn.buzanov.delivery.integration.account;

public record AccountProvisionRequest(
        String fullName,
        String phone,
        String email,
        Boolean generateCredentials,
        String source,
        String loginProfile,
        String loginBase,
        String login
) {
    public static AccountProvisionRequest forCourier(String fullName, String phone, String email, String login) {
        return new AccountProvisionRequest(
                fullName, phone, email, true, "delivery-backend", "courier", null, login);
    }

    public static AccountProvisionRequest forRestaurantOwner(String fullName, String phone, String email, String objectName) {
        return new AccountProvisionRequest(
                fullName, phone, email, true, "delivery-backend", "restaurant_owner", objectName, null);
    }

    public static AccountProvisionRequest forRestaurantManager(String fullName, String phone, String email, String objectName) {
        return new AccountProvisionRequest(
                fullName, phone, email, true, "delivery-backend", "restaurant_manager", objectName, null);
    }

    /** @deprecated use {@link #forCourier(String, String, String, String)} */
    @Deprecated
    public static AccountProvisionRequest forDeliveryEmployee(String fullName, String phone, String email) {
        return forCourier(fullName, phone, email, null);
    }
}
