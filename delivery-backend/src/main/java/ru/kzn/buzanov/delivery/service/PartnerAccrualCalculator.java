package ru.kzn.buzanov.delivery.service;

import ru.kzn.buzanov.delivery.domain.PartnerCalculationBase;
import ru.kzn.buzanov.delivery.domain.PartnerCalculationType;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

final class PartnerAccrualCalculator {

    private PartnerAccrualCalculator() {
    }

    static BigDecimal resolveCalculationBase(
            PartnerCalculationBase base,
            BigDecimal deliveryPrice,
            Map<String, Object> accrualConditions) {
        BigDecimal price = deliveryPrice != null ? deliveryPrice : BigDecimal.ZERO;
        return switch (base) {
            case DELIVERY_PRICE -> price;
            case COURIER_EARNING -> price;
            case PLATFORM_COMMISSION -> percentOf(price, accrualConditions.get("platformCommissionPercent"));
            case PLATFORM_PROFIT -> percentOf(price, accrualConditions.get("platformProfitPercent"));
            case FIXED_PER_DELIVERY -> BigDecimal.ONE;
        };
    }

    static BigDecimal calculateAmount(PartnerProgramRule rule, BigDecimal baseAmount) {
        if (rule.getCalculationBase() == PartnerCalculationBase.FIXED_PER_DELIVERY) {
            return rule.getFixedAmount() != null ? rule.getFixedAmount() : BigDecimal.ZERO;
        }
        if (rule.getCalculationType() == PartnerCalculationType.FIXED) {
            return rule.getFixedAmount() != null ? rule.getFixedAmount() : BigDecimal.ZERO;
        }
        if (rule.getPercentValue() == null) {
            return BigDecimal.ZERO;
        }
        return baseAmount
                .multiply(rule.getPercentValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentOf(BigDecimal amount, Object percentValue) {
        if (!(percentValue instanceof Number number) || amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return amount
                .multiply(BigDecimal.valueOf(number.doubleValue()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
