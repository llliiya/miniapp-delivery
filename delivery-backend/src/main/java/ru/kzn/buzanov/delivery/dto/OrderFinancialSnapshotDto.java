package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.PlatformFeeType;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderFinancialSnapshotDto(
        UUID orderId,
        BigDecimal deliveryPrice,
        boolean platformFeeEnabled,
        PlatformFeeType platformFeeType,
        BigDecimal platformFeeValue,
        BigDecimal platformFeeAmount,
        BigDecimal partnerRewardAmount,
        BigDecimal courierNetEarning,
        UUID partnerRuleId
) {
}
