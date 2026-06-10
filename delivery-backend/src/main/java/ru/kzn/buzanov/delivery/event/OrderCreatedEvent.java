package ru.kzn.buzanov.delivery.event;

import java.util.UUID;

public record OrderCreatedEvent(UUID orderId) {
}
