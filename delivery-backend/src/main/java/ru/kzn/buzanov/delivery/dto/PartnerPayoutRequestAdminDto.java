package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.PartnerParticipantType;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutMethod;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutStatus;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutTransferType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PartnerPayoutRequestAdminDto(
        UUID id,
        UUID partnerAccountId,
        PartnerParticipantType participantType,
        String participantName,
        UUID participantMemberId,
        UUID participantOrganizationId,
        String balanceSource,
        BigDecimal amount,
        PartnerPayoutMethod payoutMethod,
        PartnerPayoutStatus status,
        LocalDate scheduledPayoutDate,
        Instant createdAt,
        Instant processedAt,
        PartnerPayoutTransferType transferType,
        String cardNumber,
        String phoneNumber,
        String recipientName,
        String bankName,
        boolean payoutDetailsComplete
) {
}
