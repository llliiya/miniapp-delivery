package ru.kzn.buzanov.delivery.dto;

import java.util.List;
import java.util.UUID;

public record RestaurantChannelsResponseDto(
        UUID restaurantId,
        List<RestaurantBoundChannelDto> channels
) {
}
