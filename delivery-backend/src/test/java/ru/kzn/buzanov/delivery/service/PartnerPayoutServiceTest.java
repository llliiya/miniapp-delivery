package ru.kzn.buzanov.delivery.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.kzn.buzanov.delivery.api.PartnerPayoutConflictException;
import ru.kzn.buzanov.delivery.domain.PartnerAccount;
import ru.kzn.buzanov.delivery.domain.PartnerParticipantType;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutMethod;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutRequest;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutStatus;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.dto.PartnerBalanceSummaryDto;
import ru.kzn.buzanov.delivery.dto.PartnerPayoutDetailsDto;
import ru.kzn.buzanov.delivery.dto.PartnerPayoutRequestAdminDto;
import ru.kzn.buzanov.delivery.dto.request.CreatePartnerPayoutRequest;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.repository.PartnerAccountRepository;
import ru.kzn.buzanov.delivery.repository.PartnerPayoutRequestRepository;
import ru.kzn.buzanov.delivery.util.PartnerJsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerPayoutServiceTest {

    @Mock
    private PartnerPayoutRequestRepository payoutRepository;
    @Mock
    private PartnerAccountRepository accountRepository;
    @Mock
    private PartnerAccountService accountService;
    @Mock
    private PartnerBalanceTransferService balanceTransferService;
    @Mock
    private PartnerProgramRuleService ruleService;
    @Mock
    private PartnerJsonMapper jsonMapper;
    @Mock
    private AccessControlService accessControl;
    @Mock
    private OrganizationMemberRepository memberRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private CourierBalancePayoutService courierBalancePayoutService;

    @InjectMocks
    private PartnerPayoutService partnerPayoutService;

    @Test
    void listForServiceRequiresServiceStaffAndReturnsOnlyServicePayouts() {
        UUID serviceId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        PartnerAccount account = courierAccount(memberId, new BigDecimal("100.00"));
        account.setId(accountId);
        account.setCourierServiceId(serviceId);

        PartnerPayoutRequest payout = new PartnerPayoutRequest();
        payout.setId(payoutId);
        payout.setPartnerAccountId(accountId);
        payout.setAmount(new BigDecimal("50.00"));
        payout.setPayoutMethod(PartnerPayoutMethod.BANK_TRANSFER);
        payout.setStatus(PartnerPayoutStatus.PENDING);
        payout.setCreatedAt(Instant.now());
        payout.setUpdatedAt(Instant.now());

        OrganizationMember member = new OrganizationMember();
        member.setId(memberId);
        member.setDisplayName("Иван Курьер");

        doNothing().when(accessControl).requireServiceStaff(1L, serviceId);
        when(payoutRepository.findByCourierServiceIdOrderByCreatedAtDesc(serviceId)).thenReturn(List.of(payout));
        when(accountRepository.findAllById(List.of(accountId))).thenReturn(List.of(account));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(jsonMapper.toMap(null)).thenReturn(java.util.Collections.emptyMap());
        when(courierBalancePayoutService.listAdminPayouts(serviceId)).thenReturn(List.of());

        List<PartnerPayoutRequestAdminDto> result = partnerPayoutService.listForService(1L, serviceId);

        verify(accessControl).requireServiceStaff(1L, serviceId);
        assertEquals(1, result.size());
        assertEquals("Иван Курьер", result.get(0).participantName());
        assertEquals(PartnerParticipantType.COURIER, result.get(0).participantType());
        assertEquals("PARTNER", result.get(0).balanceSource());
        assertEquals(payoutId, result.get(0).id());
    }

    @Test
    void blocksSecondPayoutInSameMonthRegardlessOfPayoutMethod() {
        UUID memberId = UUID.randomUUID();
        PartnerAccount account = courierAccount(memberId, new BigDecimal("200.00"));
        when(accountService.findCourierAccount(memberId)).thenReturn(account);
        when(accountService.toSummary(account)).thenReturn(balanceSummary(account.getAvailableForPayout()));
        org.mockito.Mockito.doThrow(new PartnerPayoutConflictException("payout_once_per_month"))
                .when(balanceTransferService)
                .ensureCycleLimit(eq(account), any());

        PartnerPayoutConflictException ex = assertThrows(
                PartnerPayoutConflictException.class,
                () -> partnerPayoutService.createCourierPayout(
                        1L,
                        memberId,
                        new CreatePartnerPayoutRequest(
                                new BigDecimal("100.00"),
                                PartnerPayoutMethod.TRANSFER_TO_MAIN_BALANCE,
                                null)));

        assertEquals("payout_once_per_month", ex.getErrorCode());
        verify(payoutRepository, never()).save(any());
        verify(accountService, never()).reserveForPayout(any(), any());
    }

    @Test
    void allowsPayoutWhenOnlyRejectedRequestExistsInSameMonth() {
        UUID memberId = UUID.randomUUID();
        PartnerAccount account = courierAccount(memberId, new BigDecimal("200.00"));
        PartnerProgramRule rule = activeRule();

        when(accountService.findCourierAccount(memberId)).thenReturn(account);
        when(accountService.toSummary(account)).thenReturn(balanceSummary(account.getAvailableForPayout()));
        doNothing().when(balanceTransferService).ensureCycleLimit(eq(account), any());
        when(ruleService.isEnabledForReferrer(account.getCourierServiceId(), PartnerReferrerType.COURIER))
                .thenReturn(true);
        when(ruleService.findActiveRule(account.getCourierServiceId(), PartnerReferrerType.COURIER, PartnerReferralType.COURIER))
                .thenReturn(rule);
        when(jsonMapper.toStringList(rule.getPayoutMethods())).thenReturn(List.of("BANK_TRANSFER"));
        when(jsonMapper.toJson(any())).thenReturn("{\"transferType\":\"CARD\",\"cardNumber\":\"4111111111111111\",\"recipientName\":\"Test\"}");
        when(payoutRepository.save(any(PartnerPayoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jsonMapper.toMap(any())).thenReturn(java.util.Map.of(
                "transferType", "CARD",
                "cardNumber", "4111111111111111",
                "recipientName", "Test"));

        partnerPayoutService.createCourierPayout(
                1L,
                memberId,
                new CreatePartnerPayoutRequest(
                        new BigDecimal("100.00"),
                        PartnerPayoutMethod.BANK_TRANSFER,
                        samplePayoutDetails()));

        verify(accountService).reserveForPayout(eq(account), eq(new BigDecimal("100.00")));
        verify(payoutRepository).save(any(PartnerPayoutRequest.class));
    }

    @Test
    void monthlyLimitQueryUsesPendingScheduledAndPaidStatusesOnly() {
        UUID memberId = UUID.randomUUID();
        PartnerAccount account = courierAccount(memberId, new BigDecimal("200.00"));
        when(accountService.findCourierAccount(memberId)).thenReturn(account);
        when(accountService.toSummary(account)).thenReturn(balanceSummary(account.getAvailableForPayout()));
        org.mockito.Mockito.doThrow(new PartnerPayoutConflictException("payout_once_per_month"))
                .when(balanceTransferService)
                .ensureCycleLimit(eq(account), any());

        assertThrows(
                PartnerPayoutConflictException.class,
                () -> partnerPayoutService.createCourierPayout(
                        1L,
                        memberId,
                        new CreatePartnerPayoutRequest(
                                new BigDecimal("50.00"),
                                PartnerPayoutMethod.BANK_TRANSFER,
                                samplePayoutDetails())));

        verify(balanceTransferService).ensureCycleLimit(eq(account), any());
    }

    @Test
    void calendarMonthBoundsStartAtFirstDayUtc() {
        Instant jan15 = LocalDate.of(2026, 1, 15).atTime(12, 0).toInstant(ZoneOffset.UTC);
        PartnerPayoutMonthlyLimit.MonthBounds bounds = PartnerPayoutMonthlyLimit.calendarMonthBounds(jan15);

        assertEquals(
                LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                bounds.startInclusive());
        assertEquals(
                LocalDate.of(2026, 2, 1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                bounds.endExclusive());
    }

    @Test
    void approveReservedPartnerPayoutDoesNotRecheckEligibleForRequest() {
        UUID serviceId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("2800.00");

        PartnerAccount account = courierAccount(UUID.randomUUID(), amount);
        account.setId(accountId);
        account.setCourierServiceId(serviceId);

        PartnerPayoutRequest payout = new PartnerPayoutRequest();
        payout.setId(payoutId);
        payout.setPartnerAccountId(accountId);
        payout.setAmount(amount);
        payout.setPayoutMethod(PartnerPayoutMethod.BANK_TRANSFER);
        payout.setStatus(PartnerPayoutStatus.SCHEDULED);
        payout.setScheduledPayoutDate(LocalDate.of(2026, 7, 7));
        payout.setPayoutDetails("{\"transferType\":\"CARD\",\"cardNumber\":\"4111111111111111\",\"recipientName\":\"Test\"}");
        payout.setCreatedAt(Instant.now());
        payout.setUpdatedAt(Instant.now());

        PartnerBalanceSummaryDto summary = new PartnerBalanceSummaryDto(
                amount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                amount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                "2026-07",
                LocalDate.of(2026, 7, 7),
                false);

        doNothing().when(accessControl).requireServiceStaff(1L, serviceId);
        when(payoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));
        when(accountService.requireById(accountId)).thenReturn(account);
        when(accountService.toSummary(account)).thenReturn(summary);
        when(jsonMapper.toMap(any())).thenReturn(java.util.Map.of(
                "transferType", "CARD",
                "cardNumber", "4111111111111111",
                "recipientName", "Test"));
        when(payoutRepository.save(any(PartnerPayoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(accountService).completePayout(account, amount, false);

        var result = partnerPayoutService.processPayout(1L, serviceId, payoutId, true, null);

        assertEquals(PartnerPayoutStatus.PAID, result.status());
        verify(accountService).completePayout(account, amount, false);
    }

    private static PartnerBalanceSummaryDto balanceSummary(BigDecimal eligible) {
        return new PartnerBalanceSummaryDto(
                eligible,
                BigDecimal.ZERO,
                eligible,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                "2026-08",
                LocalDate.of(2026, 8, 7),
                true);
    }

    private static PartnerAccount courierAccount(UUID memberId, BigDecimal available) {
        PartnerAccount account = new PartnerAccount();
        account.setId(UUID.randomUUID());
        account.setCourierServiceId(UUID.randomUUID());
        account.setParticipantType(PartnerParticipantType.COURIER);
        account.setMemberId(memberId);
        account.setBalance(available);
        account.setAvailableForPayout(available);
        account.setPendingPayout(BigDecimal.ZERO);
        account.setPaidOut(BigDecimal.ZERO);
        account.setTransferredToMainBalance(BigDecimal.ZERO);
        return account;
    }

    private static PartnerProgramRule activeRule() {
        PartnerProgramRule rule = new PartnerProgramRule();
        rule.setReferrerType(PartnerReferrerType.COURIER);
        rule.setInviteeType(PartnerReferralType.COURIER);
        rule.setMinPayoutAmount(BigDecimal.ZERO);
        rule.setPayoutMethods("[\"BANK_TRANSFER\"]");
        return rule;
    }

    private static PartnerPayoutDetailsDto samplePayoutDetails() {
        return new PartnerPayoutDetailsDto("CARD", "4111111111111111", null, "Test User", null);
    }
}
