package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignOrderRequest(
        @NotNull @Positive Long courierId
) {
}
