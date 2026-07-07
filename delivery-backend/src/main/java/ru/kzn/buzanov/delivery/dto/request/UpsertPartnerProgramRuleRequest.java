package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import ru.kzn.buzanov.delivery.domain.PartnerCalculationBase;
import ru.kzn.buzanov.delivery.domain.PartnerCalculationType;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutMethod;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record UpsertPartnerProgramRuleRequest(
        @NotNull PartnerReferrerType referrerType,
        @NotNull PartnerReferralType inviteeType,
        boolean enabled,
        @NotNull PartnerCalculationType calculationType,
        BigDecimal percentValue,
        BigDecimal fixedAmount,
        @NotNull PartnerCalculationBase calculationBase,
        @PositiveOrZero
        Integer durationMonths,
        @NotNull LocalDate effectiveFrom,
        Map<String, Object> accrualConditions,
        Map<String, Object> payoutRestrictions,
        @PositiveOrZero
        BigDecimal minPayoutAmount,
        @NotEmpty List<PartnerPayoutMethod> payoutMethods
) {
}
