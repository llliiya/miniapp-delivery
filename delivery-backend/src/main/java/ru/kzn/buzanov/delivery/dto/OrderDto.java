package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.OrderStatus;
import ru.kzn.buzanov.delivery.domain.PriceSource;
import ru.kzn.buzanov.delivery.domain.PublicationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDto(
        UUID id,
        Long publicNumber,
        UUID courierServiceId,
        UUID restaurantId,
        UUID pickupPointId,
        String pickupAddress,
        String deliveryAddress,
        String deliveryAddressFull,
        String apartment,
        String entrance,
        Double pickupLat,
        Double pickupLon,
        Double deliveryLat,
        Double deliveryLon,
        Instant deliveryTime,
        BigDecimal price,
        PriceSource priceSource,
        String customerPhone,
        String comment,
        OrderStatus status,
        Long courierUserId,
        Long courierPublicId,
        String courierDisplayName,
        String restaurantName,
        String restaurantCity,
        Long createdByUserId,
        UUID createdByOrganizationId,
        String createdBySource,
        Instant createdAt,
        PublicationStatus publicationStatus,
        Instant publishedAt,
        Instant acceptedAt,
        Instant completedAt,
        Instant cancelledAt,
        List<OrderPublicationFailureDto> publicationFailures,
        boolean canRepublish
) {
}
