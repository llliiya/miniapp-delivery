package ru.kzn.buzanov.delivery.dto;

import java.util.UUID;

public record OrderEventSubscription(
        UUID courierServiceId,
        UUID restaurantId
) {
    public boolean isEmpty() {
        return courierServiceId == null && restaurantId == null;
    }
}
