package ru.kzn.buzanov.delivery.service.order;

public enum MessengerAssignOutcome {
    ASSIGNED,
    ORDER_ALREADY_TAKEN,
    ORDER_NOT_AVAILABLE,
    NOT_LINKED,
    NOT_COURIER,
    PENDING,
    BLOCKED
}
