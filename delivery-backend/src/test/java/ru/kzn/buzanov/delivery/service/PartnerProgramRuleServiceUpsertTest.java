package ru.kzn.buzanov.delivery.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.kzn.buzanov.delivery.domain.PartnerCalculationBase;
import ru.kzn.buzanov.delivery.domain.PartnerCalculationType;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutMethod;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.dto.request.UpsertPartnerProgramRuleRequest;
import ru.kzn.buzanov.delivery.repository.PartnerProgramRuleRepository;
import ru.kzn.buzanov.delivery.util.PartnerJsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PartnerProgramRuleServiceUpsertTest {

    @Mock
    private PartnerProgramRuleRepository ruleRepository;

    @Mock
    private AccessControlService accessControl;

    @Mock
    private PartnerJsonMapper jsonMapper;

    @InjectMocks
    private PartnerProgramRuleService ruleService;

    @Test
    void createsRestaurantToRestaurantRule() {
        UUID serviceId = UUID.randomUUID();
        UpsertPartnerProgramRuleRequest request = request();

        when(ruleRepository.findAllByCourierServiceIdAndReferrerTypeAndInviteeType(
                serviceId, PartnerReferrerType.RESTAURANT, PartnerReferralType.RESTAURANT))
                .thenReturn(List.of());
        when(jsonMapper.toJson(any())).thenReturn("{}");
        when(ruleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ruleService.upsert(10L, serviceId, request);

        ArgumentCaptor<PartnerProgramRule> captor = ArgumentCaptor.forClass(PartnerProgramRule.class);
        verify(ruleRepository).save(captor.capture());
        PartnerProgramRule saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals(serviceId, saved.getCourierServiceId());
        assertEquals(PartnerReferrerType.RESTAURANT, saved.getReferrerType());
        assertEquals(PartnerReferralType.RESTAURANT, saved.getInviteeType());
        assertEquals(PartnerCalculationBase.DELIVERY_PRICE, saved.getCalculationBase());
        assertEquals(3, saved.getDurationMonths());
        assertEquals(new BigDecimal("500"), saved.getMinPayoutAmount());
    }

    @Test
    void updatesExistingRestaurantToRestaurantRuleWithoutDuplicate() {
        UUID serviceId = UUID.randomUUID();
        PartnerProgramRule existing = new PartnerProgramRule();
        existing.setId(UUID.randomUUID());
        existing.setCourierServiceId(serviceId);
        existing.setReferrerType(PartnerReferrerType.RESTAURANT);
        existing.setInviteeType(PartnerReferralType.RESTAURANT);
        existing.setCreatedAt(Instant.now().minusSeconds(3600));
        existing.setUpdatedAt(Instant.now().minusSeconds(1800));

        when(ruleRepository.findAllByCourierServiceIdAndReferrerTypeAndInviteeType(
                serviceId, PartnerReferrerType.RESTAURANT, PartnerReferralType.RESTAURANT))
                .thenReturn(List.of(existing));
        when(jsonMapper.toJson(any())).thenReturn("{}");
        when(ruleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ruleService.upsert(10L, serviceId, request());

        ArgumentCaptor<PartnerProgramRule> captor = ArgumentCaptor.forClass(PartnerProgramRule.class);
        verify(ruleRepository).save(captor.capture());
        assertEquals(existing.getId(), captor.getValue().getId());
        assertEquals(new BigDecimal("5"), captor.getValue().getPercentValue());
    }

    @Test
    void resolvesCourierEarningBaseForCourierInviteeRegardlessOfRequestBase() {
        UUID serviceId = UUID.randomUUID();
        UpsertPartnerProgramRuleRequest request = new UpsertPartnerProgramRuleRequest(
                PartnerReferrerType.COURIER,
                PartnerReferralType.COURIER,
                true,
                PartnerCalculationType.PERCENT,
                new BigDecimal("10"),
                null,
                PartnerCalculationBase.DELIVERY_PRICE,
                3,
                LocalDate.of(2026, 7, 2),
                Map.of("onlyCompleted", true),
                Map.of("payoutDayOfMonth", 1),
                new BigDecimal("500"),
                List.of(PartnerPayoutMethod.BANK_TRANSFER, PartnerPayoutMethod.TRANSFER_TO_MAIN_BALANCE));

        when(ruleRepository.findAllByCourierServiceIdAndReferrerTypeAndInviteeType(
                serviceId, PartnerReferrerType.COURIER, PartnerReferralType.COURIER))
                .thenReturn(List.of());
        when(jsonMapper.toJson(any())).thenReturn("{}");
        when(ruleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ruleService.upsert(10L, serviceId, request);

        ArgumentCaptor<PartnerProgramRule> captor = ArgumentCaptor.forClass(PartnerProgramRule.class);
        verify(ruleRepository).save(captor.capture());
        assertEquals(PartnerCalculationBase.COURIER_EARNING, captor.getValue().getCalculationBase());
    }

    @Test
    void rejectsDeprecatedPlatformCalculationBases() {
        UUID serviceId = UUID.randomUUID();
        UpsertPartnerProgramRuleRequest request = new UpsertPartnerProgramRuleRequest(
                PartnerReferrerType.COURIER,
                PartnerReferralType.RESTAURANT,
                true,
                PartnerCalculationType.PERCENT,
                new BigDecimal("5"),
                null,
                PartnerCalculationBase.PLATFORM_COMMISSION,
                3,
                LocalDate.of(2026, 7, 2),
                Map.of("onlyCompleted", true),
                Map.of("payoutDayOfMonth", 1),
                new BigDecimal("500"),
                List.of(PartnerPayoutMethod.BANK_TRANSFER, PartnerPayoutMethod.TRANSFER_TO_MAIN_BALANCE));

        assertThrows(
                ResponseStatusException.class,
                () -> ruleService.upsert(10L, serviceId, request));
    }

    private static UpsertPartnerProgramRuleRequest request() {
        return new UpsertPartnerProgramRuleRequest(
                PartnerReferrerType.RESTAURANT,
                PartnerReferralType.RESTAURANT,
                true,
                PartnerCalculationType.PERCENT,
                new BigDecimal("5"),
                null,
                PartnerCalculationBase.DELIVERY_PRICE,
                3,
                LocalDate.of(2026, 7, 2),
                Map.of("onlyCompleted", true),
                Map.of("payoutDayOfMonth", 1),
                new BigDecimal("500"),
                List.of(PartnerPayoutMethod.BANK_TRANSFER));
    }
}

