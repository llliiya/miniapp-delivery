package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.PublicationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderPublicationEventDto(
        UUID orderId,
        PublicationStatus publicationStatus,
        Instant publishedAt,
        List<OrderPublicationFailureDto> publicationFailures,
        boolean canRepublish
) {
}
