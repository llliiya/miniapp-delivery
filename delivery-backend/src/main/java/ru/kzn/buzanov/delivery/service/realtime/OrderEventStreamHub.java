package ru.kzn.buzanov.delivery.service.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.kzn.buzanov.delivery.dto.OrderAssignedEventDto;
import ru.kzn.buzanov.delivery.dto.OrderEventSubscription;
import ru.kzn.buzanov.delivery.dto.OrderPublicationEventDto;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class OrderEventStreamHub {

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByCourierService = new ConcurrentHashMap<>();
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByRestaurant = new ConcurrentHashMap<>();

    public SseEmitter subscribe(OrderEventSubscription subscription) {
        SseEmitter emitter = new SseEmitter(0L);
        if (subscription.courierServiceId() != null) {
            register(emittersByCourierService, subscription.courierServiceId(), emitter);
        }
        if (subscription.restaurantId() != null) {
            register(emittersByRestaurant, subscription.restaurantId(), emitter);
        }
        emitter.onCompletion(() -> unregister(subscription, emitter));
        emitter.onTimeout(() -> unregister(subscription, emitter));
        emitter.onError(ex -> unregister(subscription, emitter));
        return emitter;
    }

    public void publishOrderAssigned(UUID courierServiceId, OrderAssignedEventDto event) {
        publish(emittersByCourierService.get(courierServiceId), "ORDER_ASSIGNED", event);
    }

    public void publishOrderPublicationUpdated(
            UUID courierServiceId,
            UUID restaurantId,
            OrderPublicationEventDto event) {
        publish(emittersByCourierService.get(courierServiceId), "ORDER_PUBLICATION_UPDATED", event);
        publish(emittersByRestaurant.get(restaurantId), "ORDER_PUBLICATION_UPDATED", event);
    }

    private void register(Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByKey, UUID key, SseEmitter emitter) {
        emittersByKey.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    private void unregister(OrderEventSubscription subscription, SseEmitter emitter) {
        if (subscription.courierServiceId() != null) {
            remove(emittersByCourierService, subscription.courierServiceId(), emitter);
        }
        if (subscription.restaurantId() != null) {
            remove(emittersByRestaurant, subscription.restaurantId(), emitter);
        }
    }

    private void publish(CopyOnWriteArrayList<SseEmitter> emitters, String eventName, Object event) {
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(event, MediaType.APPLICATION_JSON));
            } catch (IOException ex) {
                log.debug("SSE client disconnected: {}", ex.getMessage());
            }
        }
    }

    private void remove(Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByKey, UUID key, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByKey.get(key);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByKey.remove(key, emitters);
        }
    }
}
