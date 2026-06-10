package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequestStatus;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationSourceType;

import java.time.Instant;
import java.util.UUID;

public record RestaurantRegistrationRequestDto(
        UUID id,
        String restaurantName,
        String address,
        String contactPerson,
        String phone,
        String email,
        String comment,
        RestaurantRegistrationSourceType sourceType,
        String sourceLabel,
        String partnerCode,
        UUID courierMemberId,
        String courierName,
        UUID restaurantId,
        RestaurantRegistrationRequestStatus status,
        Instant createdAt,
        Instant processedAt,
        Long processedBy
) {
}
