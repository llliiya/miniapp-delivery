package ru.kzn.buzanov.delivery.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.domain.OrderStatus;
import ru.kzn.buzanov.delivery.dto.CreateOrderResponseDto;
import ru.kzn.buzanov.delivery.dto.OrderDto;
import ru.kzn.buzanov.delivery.dto.PatchOrderResponseDto;
import ru.kzn.buzanov.delivery.dto.RepublishOrderResponseDto;
import ru.kzn.buzanov.delivery.dto.request.AssignOrderRequest;
import ru.kzn.buzanov.delivery.dto.request.ChangeOrderStatusRequest;
import ru.kzn.buzanov.delivery.dto.request.CreateOrderRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchOrderRequest;
import ru.kzn.buzanov.delivery.service.OrderService;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponseDto create(HttpServletRequest request, @Valid @RequestBody CreateOrderRequest body) {
        var user = CurrentUserHolder.require(request);
        return orderService.create(user.userId(), body);
    }

    @GetMapping("/orders")
    public List<OrderDto> list(
            HttpServletRequest request,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) UUID courierServiceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @RequestParam(required = false) String city) {
        var user = CurrentUserHolder.require(request);
        return orderService.list(user.userId(), scope, status, restaurantId, courierServiceId, dateFrom, dateTo, city);
    }

    @GetMapping("/orders/{id}")
    public OrderDto get(HttpServletRequest request, @PathVariable("id") UUID orderId) {
        var user = CurrentUserHolder.require(request);
        return orderService.get(user.userId(), orderId);
    }

    @PatchMapping("/orders/{id}")
    public PatchOrderResponseDto patch(
            HttpServletRequest request,
            @PathVariable("id") UUID orderId,
            @RequestBody PatchOrderRequest body) {
        var user = CurrentUserHolder.require(request);
        return orderService.patch(user.userId(), orderId, body);
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderDto cancel(HttpServletRequest request, @PathVariable("id") UUID orderId) {
        var user = CurrentUserHolder.require(request);
        return orderService.cancel(user.userId(), orderId);
    }

    @PostMapping("/orders/{id}/republish")
    public RepublishOrderResponseDto republish(HttpServletRequest request, @PathVariable("id") UUID orderId) {
        var user = CurrentUserHolder.require(request);
        return orderService.republish(user.userId(), orderId);
    }

    @PostMapping("/orders/{id}/assign")
    public OrderDto assign(
            HttpServletRequest request,
            @PathVariable("id") UUID orderId,
            @Valid @RequestBody AssignOrderRequest body) {
        var user = CurrentUserHolder.require(request);
        return orderService.assign(user.userId(), orderId, body.courierId());
    }

    @PostMapping("/orders/{id}/accept")
    public OrderDto accept(HttpServletRequest request, @PathVariable("id") UUID orderId) {
        var user = CurrentUserHolder.require(request);
        return orderService.accept(user.userId(), orderId);
    }

    @PostMapping("/orders/{id}/status")
    public OrderDto changeStatus(
            HttpServletRequest request,
            @PathVariable("id") UUID orderId,
            @Valid @RequestBody ChangeOrderStatusRequest body) {
        var user = CurrentUserHolder.require(request);
        return orderService.changeStatus(user.userId(), orderId, body);
    }
}
