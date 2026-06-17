package ru.kzn.buzanov.delivery.service.order;

import java.util.UUID;

public record MessengerAssignResult(
        MessengerAssignOutcome outcome,
        String userMessage,
        UUID orderId,
        Long courierUserId) {}
