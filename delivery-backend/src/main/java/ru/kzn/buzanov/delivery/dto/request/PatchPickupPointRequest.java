package ru.kzn.buzanov.delivery.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PatchPickupPointRequest(
        String name,
        String address,
        Double lat,
        Double lon,
        String phone,
        String comment,
        @JsonProperty("isDefault") Boolean isDefault
) {
}
