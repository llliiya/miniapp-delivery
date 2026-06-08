package ru.kzn.buzanov.delivery.dto;

import java.util.List;

public record PatchOrderResponseDto(
        OrderDto order,
        List<String> warnings
) {
}
