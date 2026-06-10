package ru.kzn.buzanov.delivery.service.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.dto.OrderAssignedEventDto;

@Service
@RequiredArgsConstructor
public class OrderAssignmentEventPublisher {

    private final OrderEventStreamHub streamHub;

    public void publishAssigned(DeliveryOrder order) {
        if (order.getCourierServiceId() == null || order.getCourierUserId() == null) {
            return;
        }
        OrderAssignedEventDto event = new OrderAssignedEventDto(
                order.getId(),
                order.getCourierUserId(),
                order.getStatus());
        if (order.getCourierServiceId() != null) {
            streamHub.publishOrderAssigned(order.getCourierServiceId(), event);
        }
    }
}
