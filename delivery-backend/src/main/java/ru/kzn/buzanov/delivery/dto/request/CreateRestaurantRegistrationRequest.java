package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateRestaurantRegistrationRequest(
        @NotBlank String restaurantName,
        @NotBlank String address,
        @NotBlank String contactPerson,
        @NotBlank String phone,
        @NotBlank @Email String email,
        String comment,
        String partnerCode
) {
}
