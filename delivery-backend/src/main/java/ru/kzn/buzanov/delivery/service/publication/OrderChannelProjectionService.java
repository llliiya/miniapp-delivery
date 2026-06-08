package ru.kzn.buzanov.delivery.service.publication;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;

/**
 * Проекция состояния заказа в каналы Telegram/MAX (editMessage, без новых сообщений).
 */
@Service
@RequiredArgsConstructor
public class OrderChannelProjectionService {

    private final OrderPublicationService publicationService;

    @Transactional
    public void syncOrder(DeliveryOrder order, String courierName) {
        publicationService.syncOrder(order, courierName);
    }
}
