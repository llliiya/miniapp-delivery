package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record PartnerReferralDto(
        UUID requestId,
        String restaurantName,
        Instant submittedAt,
        RestaurantRegistrationRequestStatus status,
        Instant connectedAt
) {
}
