package ru.kzn.buzanov.delivery.fulfillment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * S2S request: resolve serving branch from coordinates among candidate branches of one company.
 *
 * @param preferredBranchId when set and that branch has an active zone covering the point,
 *                          that branch wins even if another candidate zone has higher priority
 */
public record InternalCompanyDeliveryRouteRequest(
        UUID organizationId,
        @NotEmpty List<@NotNull UUID> branchIds,
        @NotNull @Min(0) @Max(100_000_000) Long itemsTotalMinor,
        @NotNull Double latitude,
        @NotNull Double longitude,
        UUID preferredBranchId
) {
    public InternalCompanyDeliveryRouteRequest(
            UUID organizationId,
            List<UUID> branchIds,
            Long itemsTotalMinor,
            Double latitude,
            Double longitude
    ) {
        this(organizationId, branchIds, itemsTotalMinor, latitude, longitude, null);
    }
}
