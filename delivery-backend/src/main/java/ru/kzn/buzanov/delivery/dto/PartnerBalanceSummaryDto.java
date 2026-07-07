package ru.kzn.buzanov.delivery.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PartnerBalanceSummaryDto(
        BigDecimal balance,
        BigDecimal accruedNotYetEligible,
        BigDecimal eligibleForRequest,
        BigDecimal awaitingExecution,
        BigDecimal carriedOver,
        BigDecimal paidOut,
        BigDecimal transferredToMainBalance,
        boolean requestWindowOpen,
        String currentPayoutCycleMonth,
        LocalDate nextScheduledPayoutDate,
        boolean canCreatePayoutRequest
) {
}
