package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.PartnerBalanceTransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PartnerBalanceTransferDto(
        UUID id,
        BigDecimal amount,
        PartnerBalanceTransferStatus status,
        LocalDate scheduledExecutionDate,
        Instant executedAt,
        Instant createdAt
) {
}
