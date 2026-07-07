package ru.kzn.buzanov.delivery.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.repository.PartnerProgramRuleRepository;
import ru.kzn.buzanov.delivery.util.PartnerJsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerProgramRuleServiceVisibilityTest {

    @Mock
    private PartnerProgramRuleRepository ruleRepository;

    @Mock
    private AccessControlService accessControl;

    @Mock
    private PartnerJsonMapper jsonMapper;

    @InjectMocks
    private PartnerProgramRuleService ruleService;

    @Test
    void isEnabledForService_falseWhenAllRulesDisabled() {
        UUID serviceId = UUID.randomUUID();
        when(ruleRepository.findByCourierServiceIdOrderByReferrerTypeAscInviteeTypeAsc(serviceId))
                .thenReturn(List.of(
                        rule(PartnerReferrerType.COURIER, PartnerReferralType.COURIER, false),
                        rule(PartnerReferrerType.COURIER, PartnerReferralType.RESTAURANT, false),
                        rule(PartnerReferrerType.RESTAURANT, PartnerReferralType.COURIER, false),
                        rule(PartnerReferrerType.RESTAURANT, PartnerReferralType.RESTAURANT, false)));

        assertFalse(ruleService.isEnabledForService(serviceId));
    }

    @Test
    void isEnabledForService_trueWhenAtLeastOneRuleEnabled() {
        UUID serviceId = UUID.randomUUID();
        when(ruleRepository.findByCourierServiceIdOrderByReferrerTypeAscInviteeTypeAsc(serviceId))
                .thenReturn(List.of(
                        rule(PartnerReferrerType.COURIER, PartnerReferralType.COURIER, false),
                        rule(PartnerReferrerType.RESTAURANT, PartnerReferralType.RESTAURANT, true)));

        assertTrue(ruleService.isEnabledForService(serviceId));
    }

    @Test
    void isEnabledForReferrer_onlyChecksReferrerDirections() {
        UUID serviceId = UUID.randomUUID();
        when(ruleRepository.findByCourierServiceIdOrderByReferrerTypeAscInviteeTypeAsc(serviceId))
                .thenReturn(List.of(
                        rule(PartnerReferrerType.COURIER, PartnerReferralType.COURIER, true),
                        rule(PartnerReferrerType.RESTAURANT, PartnerReferralType.RESTAURANT, false)));

        assertTrue(ruleService.isEnabledForReferrer(serviceId, PartnerReferrerType.COURIER));
        assertFalse(ruleService.isEnabledForReferrer(serviceId, PartnerReferrerType.RESTAURANT));
    }

    private static PartnerProgramRule rule(
            PartnerReferrerType referrerType,
            PartnerReferralType inviteeType,
            boolean enabled) {
        PartnerProgramRule rule = new PartnerProgramRule();
        rule.setReferrerType(referrerType);
        rule.setInviteeType(inviteeType);
        rule.setEnabled(enabled);
        rule.setEffectiveFrom(LocalDate.now().minusDays(1));
        return rule;
    }
}
