package ru.kzn.buzanov.delivery.fulfillment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FulfillmentQuoteRequest(
        @NotNull FulfillmentType type,
        @NotNull @Min(0) @Max(100_000_000) Long itemsTotalMinor,
        /** Optional soft ownership check for internal S2S callers. */
        UUID companyId,
        /** Optional soft ownership check for internal S2S callers. */
        UUID organizationId,
        Double latitude,
        Double longitude
) {
    public FulfillmentQuoteRequest(FulfillmentType type, Long itemsTotalMinor) {
        this(type, itemsTotalMinor, null, null, null, null);
    }

    public FulfillmentQuoteRequest(FulfillmentType type, Long itemsTotalMinor, UUID companyId, UUID organizationId) {
        this(type, itemsTotalMinor, companyId, organizationId, null, null);
    }
}
