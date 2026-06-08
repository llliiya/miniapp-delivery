package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RestaurantOwnerRequest(
        @NotBlank String fullName,
        @NotBlank String phone,
        String email
) {
}
