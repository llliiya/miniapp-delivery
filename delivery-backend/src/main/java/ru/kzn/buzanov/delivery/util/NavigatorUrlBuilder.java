package ru.kzn.buzanov.delivery.util;

import ru.kzn.buzanov.delivery.domain.DeliveryOrder;

import java.util.Optional;

public final class NavigatorUrlBuilder {

    private NavigatorUrlBuilder() {
    }

    public static Optional<String> yandexMapsUrl(DeliveryOrder order) {
        if (order == null) {
            return Optional.empty();
        }
        boolean hasPickup = hasCoords(order.getPickupLat(), order.getPickupLon());
        boolean hasDelivery = hasCoords(order.getDeliveryLat(), order.getDeliveryLon());
        if (hasPickup && hasDelivery) {
            return Optional.of(String.format(
                    "https://yandex.ru/maps/?rtext=%s,%s~%s,%s",
                    order.getPickupLat(),
                    order.getPickupLon(),
                    order.getDeliveryLat(),
                    order.getDeliveryLon()));
        }
        if (hasDelivery) {
            return Optional.of(String.format(
                    "https://yandex.ru/maps/?rtext=~%s,%s", order.getDeliveryLat(), order.getDeliveryLon()));
        }
        if (hasPickup) {
            return Optional.of(String.format(
                    "https://yandex.ru/maps/?rtext=~%s,%s", order.getPickupLat(), order.getPickupLon()));
        }
        return Optional.empty();
    }

    private static boolean hasCoords(Double lat, Double lon) {
        return lat != null && lon != null && Double.isFinite(lat) && Double.isFinite(lon);
    }
}
