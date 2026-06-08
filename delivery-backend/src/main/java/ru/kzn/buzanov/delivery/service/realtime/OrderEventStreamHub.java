package ru.kzn.buzanov.delivery.service.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.kzn.buzanov.delivery.dto.OrderAssignedEventDto;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class OrderEventStreamHub {

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByCourierService = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID courierServiceId) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByCourierService
                .computeIfAbsent(courierServiceId, ignored -> new CopyOnWriteArrayList<>())
                .add(emitter);
        emitter.onCompletion(() -> remove(courierServiceId, emitter));
        emitter.onTimeout(() -> remove(courierServiceId, emitter));
        emitter.onError(ex -> remove(courierServiceId, emitter));
        return emitter;
    }

    public void publishOrderAssigned(UUID courierServiceId, OrderAssignedEventDto event) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByCourierService.get(courierServiceId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("ORDER_ASSIGNED")
                        .data(event, MediaType.APPLICATION_JSON));
            } catch (IOException ex) {
                remove(courierServiceId, emitter);
                log.debug("SSE client disconnected: {}", ex.getMessage());
            }
        }
    }

    private void remove(UUID courierServiceId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByCourierService.get(courierServiceId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByCourierService.remove(courierServiceId, emitters);
        }
    }
}
