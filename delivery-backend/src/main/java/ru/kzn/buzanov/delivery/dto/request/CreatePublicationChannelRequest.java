package ru.kzn.buzanov.delivery.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.kzn.buzanov.delivery.domain.ChannelPlatform;
import ru.kzn.buzanov.delivery.domain.ChatType;

import java.util.UUID;

public record CreatePublicationChannelRequest(
        @NotNull UUID courierServiceId,
        @NotNull ChannelPlatform type,
        @NotNull ChatType chatType,
        @NotBlank String name,
        @NotBlank String externalId,
        String city,
        @JsonProperty("isActive") Boolean isActive
) {
}
