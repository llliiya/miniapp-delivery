package ru.kzn.buzanov.delivery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.kzn.buzanov.delivery.domain.ChannelPlatform;
import ru.kzn.buzanov.delivery.domain.ChatType;

import java.time.Instant;
import java.util.UUID;

public record PublicationChannelDto(
        UUID id,
        UUID courierServiceId,
        ChannelPlatform type,
        ChatType chatType,
        String name,
        String externalId,
        String city,
        @JsonProperty("isActive") boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
