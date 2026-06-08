package ru.kzn.buzanov.delivery.dto;

import java.util.List;

public record CreateOrderResponseDto(
        OrderDto order,
        List<String> warnings
) {
}
