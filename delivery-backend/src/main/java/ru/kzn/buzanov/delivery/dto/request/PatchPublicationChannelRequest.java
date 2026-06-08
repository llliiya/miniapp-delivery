package ru.kzn.buzanov.delivery.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.kzn.buzanov.delivery.domain.ChatType;

public record PatchPublicationChannelRequest(
        String name,
        String externalId,
        String city,
        ChatType chatType,
        @JsonProperty("isActive") Boolean isActive
) {
}
