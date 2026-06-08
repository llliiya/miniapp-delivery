package ru.kzn.buzanov.delivery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.kzn.buzanov.delivery.domain.ChannelPlatform;
import ru.kzn.buzanov.delivery.domain.ChatType;

import java.util.UUID;

public record RestaurantBoundChannelDto(
        UUID id,
        ChannelPlatform type,
        ChatType chatType,
        String name,
        String city,
        @JsonProperty("isActive") boolean isActive
) {
}
