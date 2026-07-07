package ru.kzn.buzanov.delivery.service;

import ru.kzn.buzanov.delivery.domain.CourierServiceFinancialSettings;
import ru.kzn.buzanov.delivery.domain.PlatformFeeType;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class PlatformFeeCalculator {

    private PlatformFeeCalculator() {
    }

    static BigDecimal calculateAmount(CourierServiceFinancialSettings settings, BigDecimal deliveryPrice) {
        if (settings == null || !settings.isPlatformFeeEnabled()) {
            return BigDecimal.ZERO;
        }
        BigDecimal price = deliveryPrice != null ? deliveryPrice : BigDecimal.ZERO;
        if (price.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = settings.getPlatformFeeValue() != null
                ? settings.getPlatformFeeValue()
                : BigDecimal.ZERO;
        if (settings.getPlatformFeeType() == PlatformFeeType.FIXED) {
            return value.setScale(2, RoundingMode.HALF_UP);
        }
        return price.multiply(value)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
