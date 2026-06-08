package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Основной сценарий: {@code fullName} + {@code phone} (+ optional {@code email}).
 * Legacy: {@code userId} (+ optional {@code displayName}).
 */
public record CreateCourierRequest(
        @NotNull UUID courierServiceId,
        String fullName,
        String phone,
        String email,
        Long userId,
        String displayName
) {
    public boolean isProvisioningFlow() {
        return fullName != null && !fullName.isBlank()
                && phone != null && !phone.isBlank();
    }

    public boolean isLegacyFlow() {
        return userId != null && userId > 0;
    }
}
