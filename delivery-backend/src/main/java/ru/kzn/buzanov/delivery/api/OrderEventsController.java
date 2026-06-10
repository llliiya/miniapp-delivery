package ru.kzn.buzanov.delivery.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.kzn.buzanov.delivery.service.OrderAccessService;
import ru.kzn.buzanov.delivery.service.realtime.OrderEventStreamHub;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrderEventsController {

    private final OrderEventStreamHub streamHub;
    private final OrderAccessService orderAccess;

    @GetMapping(value = "/orders/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter orderEvents(HttpServletRequest request) {
        var user = CurrentUserHolder.require(request);
        var subscription = orderAccess.resolveOrderEventSubscription(user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к событиям заказов"));
        return streamHub.subscribe(subscription);
    }
}
