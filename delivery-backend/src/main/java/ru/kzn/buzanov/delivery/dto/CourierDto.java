package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.MemberStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourierDto(
        UUID memberId,
        Long publicId,
        UUID courierServiceId,
        Long userId,
        String displayName,
        MemberStatus status,
        BigDecimal balance,
        int completedOrdersCount,
        Instant createdAt
) {
}
