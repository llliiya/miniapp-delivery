package ru.kzn.buzanov.delivery.dto.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PatchOrderRequest(
        UUID pickupPointId,
        String deliveryAddress,
        String deliveryAddressFull,
        Double deliveryLat,
        Double deliveryLon,
        String apartment,
        String entrance,
        Instant deliveryTime,
        BigDecimal price,
        String customerPhone,
        String comment
) {
}
