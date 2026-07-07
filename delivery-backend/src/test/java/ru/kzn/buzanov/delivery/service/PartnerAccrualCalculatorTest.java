package ru.kzn.buzanov.delivery.service;

import org.junit.jupiter.api.Test;
import ru.kzn.buzanov.delivery.domain.PartnerCalculationBase;
import ru.kzn.buzanov.delivery.domain.PartnerCalculationType;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartnerAccrualCalculatorTest {

    @Test
    void percentFromDeliveryPrice() {
        PartnerProgramRule rule = percentRule("5");
        BigDecimal base = PartnerAccrualCalculator.resolveCalculationBase(
                PartnerCalculationBase.DELIVERY_PRICE, new BigDecimal("200.00"), Map.of());
        BigDecimal amount = PartnerAccrualCalculator.calculateAmount(rule, base);
        assertEquals(new BigDecimal("10.00"), amount);
    }

    @Test
    void fixedPerDelivery() {
        PartnerProgramRule rule = new PartnerProgramRule();
        rule.setCalculationBase(PartnerCalculationBase.FIXED_PER_DELIVERY);
        rule.setFixedAmount(new BigDecimal("20.00"));

        BigDecimal base = PartnerAccrualCalculator.resolveCalculationBase(
                PartnerCalculationBase.FIXED_PER_DELIVERY, new BigDecimal("200.00"), Map.of());
        BigDecimal amount = PartnerAccrualCalculator.calculateAmount(rule, base);
        assertEquals(new BigDecimal("20.00"), amount);
    }

    @Test
    void fixedAmountCalculationType() {
        PartnerProgramRule rule = new PartnerProgramRule();
        rule.setCalculationType(PartnerCalculationType.FIXED);
        rule.setCalculationBase(PartnerCalculationBase.FIXED_PER_DELIVERY);
        rule.setFixedAmount(new BigDecimal("15.00"));

        BigDecimal amount = PartnerAccrualCalculator.calculateAmount(rule, new BigDecimal("300.00"));
        assertEquals(new BigDecimal("15.00"), amount);
    }

    private static PartnerProgramRule percentRule(String percent) {
        PartnerProgramRule rule = new PartnerProgramRule();
        rule.setId(UUID.randomUUID());
        rule.setCalculationType(PartnerCalculationType.PERCENT);
        rule.setCalculationBase(PartnerCalculationBase.DELIVERY_PRICE);
        rule.setPercentValue(new BigDecimal(percent));
        return rule;
    }
}
