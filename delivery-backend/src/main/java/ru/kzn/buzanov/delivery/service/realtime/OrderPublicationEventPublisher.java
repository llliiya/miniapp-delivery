package ru.kzn.buzanov.delivery.service.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.dto.OrderPublicationEventDto;
import ru.kzn.buzanov.delivery.service.publication.OrderPublicationService;

@Service
@RequiredArgsConstructor
public class OrderPublicationEventPublisher {

    private final OrderEventStreamHub streamHub;
    private final OrderPublicationService publicationService;

    public void publishUpdated(DeliveryOrder order) {
        if (order.getCourierServiceId() == null && order.getRestaurantId() == null) {
            return;
        }
        OrderPublicationEventDto event = new OrderPublicationEventDto(
                order.getId(),
                order.getPublicationStatus(),
                order.getPublishedAt(),
                publicationService.publicationFailures(order),
                publicationService.canRepublish(order));
        streamHub.publishOrderPublicationUpdated(order.getCourierServiceId(), order.getRestaurantId(), event);
    }
}
