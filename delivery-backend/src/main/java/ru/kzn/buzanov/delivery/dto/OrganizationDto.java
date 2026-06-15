package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.OrganizationType;

import java.time.Instant;
import java.util.UUID;

public record OrganizationDto(
        UUID id,
        Long publicId,
        OrganizationType type,
        String name,
        Long ownerUserId,
        UUID courierServiceId,
        boolean active,
        Instant createdAt,
        String city
) {
}
