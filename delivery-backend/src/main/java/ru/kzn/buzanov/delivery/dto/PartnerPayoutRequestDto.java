package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.PartnerPayoutMethod;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutStatus;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutTransferType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PartnerPayoutRequestDto(
        UUID id,
        BigDecimal amount,
        PartnerPayoutMethod payoutMethod,
        PartnerPayoutStatus status,
        LocalDate scheduledPayoutDate,
        Instant createdAt,
        Instant processedAt,
        PartnerPayoutTransferType transferType,
        String cardMask,
        String recipientName,
        String rejectionComment
) {
}
