package ru.kzn.buzanov.delivery.dto;

import java.util.List;

public record PartnerProgramDto(
        String partnerCode,
        String restaurantInviteUrl,
        String courierInviteUrl,
        long totalInvitations,
        long pendingCount,
        long connectedCount,
        List<PartnerReferralDto> referrals,
        PartnerBalanceSummaryDto balance,
        List<PartnerConnectedReferralDto> connectedCouriers,
        List<PartnerConnectedReferralDto> connectedRestaurants,
        List<PartnerAccrualDto> accrualHistory,
        List<PartnerPayoutRequestDto> payoutHistory,
        List<PartnerBalanceTransferDto> balanceTransferHistory,
        List<String> availablePayoutMethods,
        java.math.BigDecimal minPayoutAmount,
        boolean enabled
) {
    public static PartnerProgramDto disabled() {
        return new PartnerProgramDto(
                null,
                null,
                null,
                0,
                0,
                0,
                List.of(),
                new PartnerBalanceSummaryDto(
                        java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO,
                        true,
                        null,
                        null,
                        false),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                java.math.BigDecimal.ZERO,
                false);
    }
}
