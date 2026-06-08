package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.constraints.NotNull;
import ru.kzn.buzanov.delivery.domain.OrderStatus;

public record ChangeOrderStatusRequest(
        @NotNull OrderStatus status
) {
}
