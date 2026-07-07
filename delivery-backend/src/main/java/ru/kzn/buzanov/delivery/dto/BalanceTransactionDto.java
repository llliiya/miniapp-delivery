package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.BalanceTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BalanceTransactionDto(
        UUID id,
        BigDecimal amount,
        BalanceTransactionType type,
        UUID orderId,
        Long orderPublicNumber,
        Instant createdAt
) {
}
