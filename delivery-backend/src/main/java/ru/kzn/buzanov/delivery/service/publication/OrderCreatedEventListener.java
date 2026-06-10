package ru.kzn.buzanov.delivery.service.publication;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.kzn.buzanov.delivery.event.OrderCreatedEvent;

@Component
@RequiredArgsConstructor
public class OrderCreatedEventListener {

    private final OrderPublicationAsyncService publicationAsyncService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        publicationAsyncService.schedulePublication(event.orderId());
    }
}
