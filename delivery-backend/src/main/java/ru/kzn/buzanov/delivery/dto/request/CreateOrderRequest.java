package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID restaurantId,
        @NotNull UUID pickupPointId,
        @NotBlank String deliveryAddress,
        String deliveryAddressFull,
        Double deliveryLat,
        Double deliveryLon,
        String apartment,
        String entrance,
        @NotNull Instant deliveryTime,
        @NotNull @Positive BigDecimal price,
        @NotBlank String customerPhone,
        String comment
) {
}
