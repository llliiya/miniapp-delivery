package ru.kzn.buzanov.delivery.dto;

public record CreateRestaurantResponse(
        OrganizationDto object,
        ProvisioningCredentialsDto ownerCredentials
) {
}
