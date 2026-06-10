package ru.kzn.buzanov.delivery.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.api.OrderConflictException;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.OrderStatus;
import ru.kzn.buzanov.delivery.repository.CourierProfileRepository;
import ru.kzn.buzanov.delivery.repository.DeliveryOrderRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.repository.PickupPointRepository;
import ru.kzn.buzanov.delivery.service.notification.CourierMessengerNotificationService;
import ru.kzn.buzanov.delivery.service.publication.OrderChannelProjectionService;
import ru.kzn.buzanov.delivery.service.publication.OrderPublicationService;
import ru.kzn.buzanov.delivery.service.realtime.OrderAssignmentEventPublisher;
import ru.kzn.buzanov.delivery.service.realtime.OrderPublicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceAssignTest {

    @Mock
    private DeliveryOrderRepository orderRepository;
    @Mock
    private CourierProfileRepository courierProfileRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationMemberRepository memberRepository;
    @Mock
    private PickupPointRepository pickupPointRepository;
    @Mock
    private AccessControlService accessControl;
    @Mock
    private OrderAccessService orderAccess;
    @Mock
    private OrderStatusTransitionService statusTransition;
    @Mock
    private OrderPublicationService publicationService;
    @Mock
    private OrderChannelProjectionService channelProjectionService;
    @Mock
    private CourierMessengerNotificationService courierMessengerNotificationService;
    @Mock
    private OrderAssignmentEventPublisher assignmentEventPublisher;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private OrderPublicationEventPublisher publicationEventPublisher;
    @Mock
    private CourierBalanceService courierBalanceService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void assignRejectsForeignCourierId() {
        UUID orderId = UUID.randomUUID();
        assertThrows(ResponseStatusException.class, () -> orderService.assign(10L, orderId, 20L));
        verify(orderRepository, never()).assignOrderIfUnassigned(any(), any(), any());
    }

    @Test
    void assignReturnsConflictWhenOrderAlreadyTaken() {
        UUID orderId = UUID.randomUUID();
        Long courierId = 42L;
        DeliveryOrder taken = sampleOrder(orderId);
        taken.setCourierUserId(99L);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder(orderId)), Optional.of(taken));
        when(orderRepository.assignOrderIfUnassigned(eq(orderId), eq(courierId), any(Instant.class))).thenReturn(0);

        OrderConflictException ex = assertThrows(
                OrderConflictException.class, () -> orderService.assign(courierId, orderId, courierId));
        assertEquals("order_already_taken", ex.getErrorCode());
        assertEquals("Заказ уже взят другим курьером", ex.getUserMessage());

        verify(channelProjectionService, never()).syncOrder(any(), any());
        verify(courierMessengerNotificationService, never()).notifyOrderAssigned(any(), any());
        verify(assignmentEventPublisher, never()).publishAssigned(any());
    }

    @Test
    void assignSyncsChannelsAndNotifiesOnSuccess() {
        UUID orderId = UUID.randomUUID();
        Long courierId = 42L;
        DeliveryOrder order = sampleOrder(orderId);
        order.setPublishedAt(Instant.now());
        DeliveryOrder assigned = sampleOrder(orderId);
        assigned.setCourierUserId(courierId);
        assigned.setStatus(OrderStatus.courier_heading_to_pickup);
        assigned.setAcceptedAt(Instant.now());
        assigned.setPublishedAt(order.getPublishedAt());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order), Optional.of(assigned));
        when(orderRepository.assignOrderIfUnassigned(eq(orderId), eq(courierId), any(Instant.class))).thenReturn(1);

        orderService.assign(courierId, orderId, courierId);

        verify(channelProjectionService).syncOrder(eq(assigned), eq("Курьер"));
        verify(courierMessengerNotificationService).notifyOrderAssigned(assigned, courierId);
        verify(assignmentEventPublisher).publishAssigned(assigned);
    }

    private static DeliveryOrder sampleOrder(UUID orderId) {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(orderId);
        order.setCourierServiceId(UUID.randomUUID());
        order.setRestaurantId(UUID.randomUUID());
        order.setPickupAddress("pickup");
        order.setDeliveryAddress("delivery");
        order.setDeliveryTime(Instant.now());
        order.setPrice(BigDecimal.TEN);
        order.setCustomerPhone("+79990000000");
        order.setStatus(OrderStatus.waiting_for_courier);
        order.setCreatedByUserId(1L);
        order.setCreatedAt(Instant.now());
        return order;
    }
}
