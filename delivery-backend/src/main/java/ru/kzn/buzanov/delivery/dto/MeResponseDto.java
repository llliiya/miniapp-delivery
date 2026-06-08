package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.UserDeliveryStatus;

import java.util.List;
import java.util.UUID;

public record MeResponseDto(
        Long userId,
        UserDeliveryStatus status,
        UUID activeOrganizationId,
        String interfaceMode,
        String deliveryRole,
        String accountStatus,
        List<MembershipDto> memberships
) {
}
