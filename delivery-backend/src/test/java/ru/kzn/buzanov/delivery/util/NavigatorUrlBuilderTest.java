package ru.kzn.buzanov.delivery.util;

import org.junit.jupiter.api.Test;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigatorUrlBuilderTest {

    @Test
    void buildsRouteWhenPickupAndDeliveryCoordinatesPresent() {
        DeliveryOrder order = new DeliveryOrder();
        order.setPickupLat(55.75);
        order.setPickupLon(37.62);
        order.setDeliveryLat(55.76);
        order.setDeliveryLon(37.63);

        Optional<String> url = NavigatorUrlBuilder.yandexMapsUrl(order);

        assertTrue(url.isPresent());
        assertTrue(url.get().contains("55.75,37.62~55.76,37.63"));
    }

    @Test
    void returnsEmptyWhenNoCoordinates() {
        assertFalse(NavigatorUrlBuilder.yandexMapsUrl(new DeliveryOrder()).isPresent());
    }
}
