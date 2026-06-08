package ru.kzn.buzanov.delivery.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CreatePickupPointRequest(
        @NotBlank String name,
        @NotBlank String address,
        Double lat,
        Double lon,
        String phone,
        String comment,
        @JsonProperty("isDefault") Boolean isDefault
) {
}
