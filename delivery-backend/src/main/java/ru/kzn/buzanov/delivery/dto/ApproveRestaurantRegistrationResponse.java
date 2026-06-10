package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequestStatus;

import java.util.UUID;

public record ApproveRestaurantRegistrationResponse(
        UUID requestId,
        RestaurantRegistrationRequestStatus status,
        UUID restaurantId,
        ProvisioningCredentialsDto ownerCredentials,
        String message
) {
}
