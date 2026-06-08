package ru.kzn.buzanov.delivery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record PickupPointDto(
        UUID id,
        UUID restaurantId,
        String name,
        String address,
        Double lat,
        Double lon,
        String phone,
        String comment,
        @JsonProperty("isDefault") boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
}
