package ru.kzn.buzanov.delivery.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.kzn.buzanov.delivery.domain.BalanceTransaction;
import ru.kzn.buzanov.delivery.domain.BalanceTransactionType;
import ru.kzn.buzanov.delivery.domain.CourierProfile;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.repository.BalanceTransactionRepository;
import ru.kzn.buzanov.delivery.repository.CourierProfileRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierBalanceServiceTest {

    @Mock
    private OrderAccessService orderAccess;
    @Mock
    private BalanceTransactionRepository balanceTransactionRepository;
    @Mock
    private CourierProfileRepository courierProfileRepository;

    @InjectMocks
    private CourierBalanceService courierBalanceService;

    @Test
    void skipsAccrualWhenCourierNotAssigned() {
        DeliveryOrder order = sampleOrder();
        order.setCourierUserId(null);

        courierBalanceService.creditOrderNetEarning(order, new BigDecimal("150.00"));

        verify(balanceTransactionRepository, never()).save(any());
        verify(courierProfileRepository, never()).save(any());
    }

    @Test
    void skipsDuplicateAccrualForSameOrder() {
        DeliveryOrder order = sampleOrder();
        when(balanceTransactionRepository.existsByOrderIdAndType(order.getId(), BalanceTransactionType.ORDER_COMPLETED))
                .thenReturn(true);

        courierBalanceService.creditOrderNetEarning(order, new BigDecimal("150.00"));

        verify(balanceTransactionRepository, never()).save(any());
        verify(courierProfileRepository, never()).save(any());
    }

    @Test
    void creditsNetEarningToCourierBalance() {
        DeliveryOrder order = sampleOrder();
        order.setCourierUserId(42L);

        UUID memberId = UUID.randomUUID();
        OrganizationMember member = new OrganizationMember();
        member.setId(memberId);
        member.setRole(MemberRole.courier);
        member.setStatus(MemberStatus.active);

        CourierProfile profile = new CourierProfile();
        profile.setId(UUID.randomUUID());
        profile.setMemberId(memberId);
        profile.setBalance(new BigDecimal("200.00"));
        profile.setCompletedOrdersCount(3);
        profile.setUpdatedAt(Instant.now());

        when(balanceTransactionRepository.existsByOrderIdAndType(order.getId(), BalanceTransactionType.ORDER_COMPLETED))
                .thenReturn(false);
        when(orderAccess.findActiveCourierMembership(42L, order.getCourierServiceId()))
                .thenReturn(Optional.of(member));
        when(courierProfileRepository.findByMemberId(memberId)).thenReturn(Optional.of(profile));

        courierBalanceService.creditOrderNetEarning(order, new BigDecimal("150.00"));

        ArgumentCaptor<BalanceTransaction> txCaptor = ArgumentCaptor.forClass(BalanceTransaction.class);
        verify(balanceTransactionRepository).save(txCaptor.capture());
        BalanceTransaction savedTx = txCaptor.getValue();
        assertEquals(BalanceTransactionType.ORDER_COMPLETED, savedTx.getType());
        assertEquals(order.getId(), savedTx.getOrderId());
        assertEquals(memberId, savedTx.getCourierMemberId());
        assertEquals(0, new BigDecimal("150.00").compareTo(savedTx.getAmount()));

        ArgumentCaptor<CourierProfile> profileCaptor = ArgumentCaptor.forClass(CourierProfile.class);
        verify(courierProfileRepository).save(profileCaptor.capture());
        assertEquals(0, new BigDecimal("350.00").compareTo(profileCaptor.getValue().getBalance()));
    }

    private static DeliveryOrder sampleOrder() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(UUID.randomUUID());
        order.setCourierServiceId(UUID.randomUUID());
        order.setRestaurantId(UUID.randomUUID());
        order.setPickupAddress("pickup");
        order.setDeliveryAddress("delivery");
        order.setDeliveryTime(Instant.now());
        order.setPrice(BigDecimal.TEN);
        order.setCustomerPhone("+79990000000");
        order.setCreatedByUserId(1L);
        order.setCreatedAt(Instant.now());
        return order;
    }
}
