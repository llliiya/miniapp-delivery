package ru.kzn.buzanov.delivery.service.publication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.repository.DeliveryOrderRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPublicationAsyncService {

    private final DeliveryOrderRepository orderRepository;
    private final OrderPublicationService publicationService;
    private final OrderPublicationStateService publicationStateService;

    @Async
    public void schedulePublication(UUID orderId) {
        if (!publicationStateService.markProcessing(orderId)) {
            return;
        }
        try {
            DeliveryOrder order = orderRepository.findById(orderId).orElseThrow();
            List<String> warnings = publicationService.publishNewOrder(order);
            publicationStateService.finalizePublication(orderId, warnings);
        } catch (Exception ex) {
            log.error("Async publication failed for order {}", orderId, ex);
            publicationStateService.markFailed(orderId);
        }
    }
}
