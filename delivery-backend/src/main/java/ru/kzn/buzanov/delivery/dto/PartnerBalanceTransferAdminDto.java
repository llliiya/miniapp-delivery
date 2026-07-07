package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.PartnerBalanceTransferStatus;
import ru.kzn.buzanov.delivery.domain.PartnerParticipantType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PartnerBalanceTransferAdminDto(
        UUID id,
        UUID partnerAccountId,
        PartnerParticipantType participantType,
        String participantName,
        UUID participantMemberId,
        UUID participantOrganizationId,
        BigDecimal amount,
        PartnerBalanceTransferStatus status,
        LocalDate scheduledExecutionDate,
        Instant executedAt,
        Instant createdAt
) {
}
