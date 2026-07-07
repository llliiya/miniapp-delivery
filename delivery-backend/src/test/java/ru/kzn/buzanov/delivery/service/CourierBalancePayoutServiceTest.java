package ru.kzn.buzanov.delivery.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.CourierBalancePayoutMethod;
import ru.kzn.buzanov.delivery.domain.CourierBalancePayoutRequest;
import ru.kzn.buzanov.delivery.domain.CourierBalancePayoutStatus;
import ru.kzn.buzanov.delivery.domain.CourierProfile;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.dto.CourierBalancePayoutRequestDto;
import ru.kzn.buzanov.delivery.repository.BalanceTransactionRepository;
import ru.kzn.buzanov.delivery.repository.CourierBalancePayoutRequestRepository;
import ru.kzn.buzanov.delivery.repository.CourierProfileRepository;
import ru.kzn.buzanov.delivery.repository.DeliveryOrderRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.util.PartnerJsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierBalancePayoutServiceTest {

    @Mock
    private CourierBalancePayoutRequestRepository payoutRepository;
    @Mock
    private CourierProfileRepository courierProfileRepository;
    @Mock
    private OrganizationMemberRepository memberRepository;
    @Mock
    private BalanceTransactionRepository balanceTransactionRepository;
    @Mock
    private DeliveryOrderRepository orderRepository;
    @Mock
    private AccessControlService accessControl;
    @Mock
    private PartnerJsonMapper jsonMapper;

    @InjectMocks
    private CourierBalancePayoutService courierBalancePayoutService;

    @Test
    void approveReservedPayoutDoesNotRecheckAvailableForPayout() {
        UUID serviceId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("2800.00");

        OrganizationMember member = new OrganizationMember();
        member.setId(memberId);
        member.setOrganizationId(serviceId);
        member.setRole(MemberRole.courier);

        CourierProfile profile = new CourierProfile();
        profile.setMemberId(memberId);
        profile.setBalance(amount);

        CourierBalancePayoutRequest payout = new CourierBalancePayoutRequest();
        payout.setId(payoutId);
        payout.setCourierMemberId(memberId);
        payout.setAmount(amount);
        payout.setPayoutMethod(CourierBalancePayoutMethod.BANK_TRANSFER);
        payout.setStatus(CourierBalancePayoutStatus.PENDING);
        payout.setPayoutDetails("{\"transferType\":\"CARD\",\"cardNumber\":\"4111111111111111\",\"recipientName\":\"Test\"}");
        payout.setCreatedAt(Instant.now());
        payout.setUpdatedAt(Instant.now());

        doNothing().when(accessControl).requireServiceStaff(1L, serviceId);
        when(payoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(courierProfileRepository.findByMemberId(memberId)).thenReturn(Optional.of(profile));
        when(jsonMapper.toMap(any())).thenReturn(Map.of(
                "transferType", "CARD",
                "cardNumber", "4111111111111111",
                "recipientName", "Test"));
        when(payoutRepository.save(any(CourierBalancePayoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(courierProfileRepository.save(any(CourierProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CourierBalancePayoutRequestDto result = courierBalancePayoutService.processPayout(
                1L, serviceId, payoutId, true, null);

        assertEquals(CourierBalancePayoutStatus.PAID, result.status());
        assertEquals(0, profile.getBalance().compareTo(BigDecimal.ZERO));
        verify(courierProfileRepository).save(profile);
    }

    @Test
    void rejectProcessedPayoutReturnsConflict() {
        UUID serviceId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();

        OrganizationMember member = new OrganizationMember();
        member.setId(memberId);
        member.setOrganizationId(serviceId);

        CourierBalancePayoutRequest payout = new CourierBalancePayoutRequest();
        payout.setId(payoutId);
        payout.setCourierMemberId(memberId);
        payout.setAmount(new BigDecimal("100.00"));
        payout.setStatus(CourierBalancePayoutStatus.PAID);

        doNothing().when(accessControl).requireServiceStaff(1L, serviceId);
        when(payoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> courierBalancePayoutService.processPayout(1L, serviceId, payoutId, true, null));

        assertEquals(409, ex.getStatusCode().value());
    }
}
