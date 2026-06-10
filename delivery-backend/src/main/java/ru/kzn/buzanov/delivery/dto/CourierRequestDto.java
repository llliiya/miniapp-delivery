package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.CourierRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record CourierRequestDto(
        UUID id,
        String fullName,
        String phone,
        String email,
        String city,
        String transport,
        String comment,
        String messengerProvider,
        String messengerExternalId,
        String messengerUsername,
        String source,
        CourierRequestStatus status,
        Long linkedUserId,
        Instant createdAt
) {
}
