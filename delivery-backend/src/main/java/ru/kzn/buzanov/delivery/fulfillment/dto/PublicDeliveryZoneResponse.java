package ru.kzn.buzanov.delivery.fulfillment.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/** Public-safe zone for storefront map. No priority / audit / internal ids beyond zone id. */
public record PublicDeliveryZoneResponse(
        UUID id,
        String name,
        JsonNode geometry,
        long deliveryFeeMinor,
        Long freeDeliveryFromMinor,
        Long minOrderAmountMinor,
        Integer etaMinMinutes,
        Integer etaMaxMinutes
) {
}
