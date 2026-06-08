package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.constraints.NotNull;
import ru.kzn.buzanov.delivery.domain.MemberRole;

/**
 * Основной сценарий: {@code role} + {@code fullName} + {@code phone}.
 * Legacy: {@code role} + {@code userId}.
 */
public record AddMemberRequest(
        @NotNull MemberRole role,
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
