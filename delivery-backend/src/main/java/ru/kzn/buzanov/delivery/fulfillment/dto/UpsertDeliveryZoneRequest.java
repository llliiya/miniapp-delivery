package ru.kzn.buzanov.delivery.fulfillment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertDeliveryZoneRequest(
        @NotBlank @Size(max = 80) String name,
        Boolean active,
        @NotNull Integer priority,
        @NotNull JsonNode geometry,
        @NotNull @Min(0) @Max(100_000_000) Long deliveryFeeMinor,
        @Min(0) @Max(100_000_000) Long freeDeliveryFromMinor,
        @Min(0) @Max(100_000_000) Long minOrderAmountMinor,
        @Min(1) @Max(1440) Integer etaMinMinutes,
        @Min(1) @Max(1440) Integer etaMaxMinutes
) {
}
