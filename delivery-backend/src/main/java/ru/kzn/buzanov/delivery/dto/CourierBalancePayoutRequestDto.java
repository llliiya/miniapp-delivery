package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.CourierBalancePayoutMethod;
import ru.kzn.buzanov.delivery.domain.CourierBalancePayoutStatus;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutTransferType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CourierBalancePayoutRequestDto(
        UUID id,
        BigDecimal amount,
        CourierBalancePayoutMethod payoutMethod,
        CourierBalancePayoutStatus status,
        LocalDate scheduledPayoutDate,
        Instant createdAt,
        Instant processedAt,
        PartnerPayoutTransferType transferType,
        String cardMask,
        String recipientName,
        String rejectionComment
) {
}
