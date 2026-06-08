package ru.kzn.buzanov.delivery.dto;

import java.util.List;

public record RepublishOrderResponseDto(
        OrderDto order,
        List<String> warnings
) {
}
