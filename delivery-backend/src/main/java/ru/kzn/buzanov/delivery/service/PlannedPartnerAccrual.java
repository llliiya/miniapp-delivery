package ru.kzn.buzanov.delivery.service;

import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerReferral;
import ru.kzn.buzanov.delivery.dto.PartnerRuleSnapshotDto;

import java.math.BigDecimal;
import java.util.UUID;

record PlannedPartnerAccrual(
        PartnerReferral referral,
        PartnerProgramRule rule,
        BigDecimal calculationBaseAmount,
        BigDecimal amount,
        PartnerRuleSnapshotDto ruleSnapshot
) {
    UUID ruleId() {
        return rule.getId();
    }
}
