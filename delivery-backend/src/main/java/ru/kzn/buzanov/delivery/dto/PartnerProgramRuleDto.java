package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.PartnerCalculationBase;
import ru.kzn.buzanov.delivery.domain.PartnerCalculationType;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PartnerProgramRuleDto(
        UUID id,
        UUID courierServiceId,
        PartnerReferrerType referrerType,
        PartnerReferralType inviteeType,
        boolean enabled,
        PartnerCalculationType calculationType,
        BigDecimal percentValue,
        BigDecimal fixedAmount,
        PartnerCalculationBase calculationBase,
        Integer durationMonths,
        LocalDate effectiveFrom,
        Map<String, Object> accrualConditions,
        Map<String, Object> payoutRestrictions,
        BigDecimal minPayoutAmount,
        List<String> payoutMethods
) {
}
