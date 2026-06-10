package ru.kzn.buzanov.delivery.service.publication;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.PublicationStatus;
import ru.kzn.buzanov.delivery.repository.DeliveryOrderRepository;
import ru.kzn.buzanov.delivery.service.realtime.OrderPublicationEventPublisher;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderPublicationStateService {

    private final DeliveryOrderRepository orderRepository;
    private final OrderPublicationService publicationService;
    private final OrderPublicationEventPublisher publicationEventPublisher;

    @Transactional
    public boolean markProcessing(UUID orderId) {
        DeliveryOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getPublicationStatus() != PublicationStatus.pending) {
            return false;
        }
        order.setPublicationStatus(PublicationStatus.processing);
        orderRepository.save(order);
        return true;
    }

    @Transactional
    public void finalizePublication(UUID orderId, List<String> warnings) {
        DeliveryOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        order.setPublicationStatus(publicationService.resolvePublicationStatusAfterPublish(warnings, order));
        orderRepository.save(order);
        publicationEventPublisher.publishUpdated(order);
    }

    @Transactional
    public void markFailed(UUID orderId) {
        DeliveryOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        order.setPublicationStatus(PublicationStatus.failed);
        orderRepository.save(order);
        publicationEventPublisher.publishUpdated(order);
    }
}
