package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.ChannelPlatform;

import java.util.UUID;

public record OrderPublicationFailureDto(
        UUID channelId,
        String channelName,
        ChannelPlatform platform,
        String errorMessage
) {
}
