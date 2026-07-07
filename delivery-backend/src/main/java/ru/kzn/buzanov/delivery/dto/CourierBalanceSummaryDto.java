package ru.kzn.buzanov.delivery.dto;

import java.math.BigDecimal;
import java.util.List;

public record CourierBalanceSummaryDto(
        BigDecimal balance,
        BigDecimal availableForPayout,
        BigDecimal pendingPayout,
        BigDecimal paidOut,
        boolean canCreatePayoutRequest,
        List<CourierBalancePayoutRequestDto> payoutHistory,
        List<BalanceTransactionDto> earningHistory
) {
}
