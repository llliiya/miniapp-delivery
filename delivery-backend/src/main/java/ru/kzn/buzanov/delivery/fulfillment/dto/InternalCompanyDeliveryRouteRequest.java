package ru.kzn.buzanov.delivery.fulfillment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * S2S request: resolve serving branch from coordinates among candidate branches of one company.
 */
public record InternalCompanyDeliveryRouteRequest(
        UUID organizationId,
        @NotEmpty List<@NotNull UUID> branchIds,
        @NotNull @Min(0) @Max(100_000_000) Long itemsTotalMinor,
        @NotNull Double latitude,
        @NotNull Double longitude
) {
}
