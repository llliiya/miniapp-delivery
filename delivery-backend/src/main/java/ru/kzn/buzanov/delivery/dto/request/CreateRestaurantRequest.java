package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Основной сценарий: {@code name} + {@code courierServiceId} + {@code owner}.
 * Legacy: только название и служба (владелец = текущий пользователь службы).
 */
public record CreateRestaurantRequest(
        @NotBlank String name,
        @NotNull UUID courierServiceId,
        @Valid RestaurantOwnerRequest owner
) {
    public boolean isProvisioningFlow() {
        return owner != null;
    }
}
