package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.OrderStatus;

import java.util.UUID;

public record OrderAssignedEventDto(
        UUID orderId,
        Long courierId,
        OrderStatus status
) {
}
