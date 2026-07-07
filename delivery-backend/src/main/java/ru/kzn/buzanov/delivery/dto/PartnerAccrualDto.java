package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.PartnerAccrualStatus;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PartnerAccrualDto(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        BigDecimal calculationBaseAmount,
        PartnerAccrualStatus status,
        PartnerReferrerType referrerType,
        PartnerReferralType inviteeType,
        String inviteeDisplayName,
        Instant createdAt,
        Instant availableFrom,
        String accrualPeriodMonth,
        String payoutCycleMonth,
        Instant reversedAt
) {
}
