package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.PartnerReferralJournalStatus;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PartnerReferralAdminDto(
        UUID id,
        PartnerReferrerType referrerType,
        String referrerName,
        String referrerPhone,
        PartnerReferralType inviteeType,
        String inviteeName,
        String inviteePhone,
        Instant createdAt,
        Instant connectedAt,
        PartnerReferralJournalStatus status,
        String relationshipLabel,
        long accrualCount,
        BigDecimal accruedAmount,
        BigDecimal reversedAmount,
        BigDecimal netAmount,
        Instant lastAccrualAt
) {
}
