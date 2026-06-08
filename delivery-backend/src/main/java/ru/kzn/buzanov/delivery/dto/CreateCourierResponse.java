package ru.kzn.buzanov.delivery.dto;

public record CreateCourierResponse(
        CourierDto courier,
        ProvisioningCredentialsDto credentials
) {
}
