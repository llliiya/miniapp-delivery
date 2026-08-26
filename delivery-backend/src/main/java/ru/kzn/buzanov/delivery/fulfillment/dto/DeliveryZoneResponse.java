package ru.kzn.buzanov.delivery.fulfillment.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Admin zone DTO. Color is presentation-only and never returned or stored. */
public record DeliveryZoneResponse(
        UUID id,
        UUID branchId,
        String name,
        boolean active,
        int priority,
        JsonNode geometry,
        long deliveryFeeMinor,
        Long freeDeliveryFromMinor,
        Long minOrderAmountMinor,
        Integer etaMinMinutes,
        Integer etaMaxMinutes,
        Instant createdAt,
        Instant updatedAt
) {
}
