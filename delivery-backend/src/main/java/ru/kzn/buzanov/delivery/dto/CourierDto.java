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
        Instant createdAt,
        String email,
        String phone
) {
    public CourierDto withoutContacts() {
        return new CourierDto(
                memberId,
                publicId,
                courierServiceId,
                userId,
                displayName,
                status,
                balance,
                completedOrdersCount,
                createdAt,
                null,
                null);
    }

    public CourierDto withContacts(String email, String phone) {
        return new CourierDto(
                memberId,
                publicId,
                courierServiceId,
                userId,
                displayName,
                status,
                balance,
                completedOrdersCount,
                createdAt,
                email,
                phone);
    }
}
