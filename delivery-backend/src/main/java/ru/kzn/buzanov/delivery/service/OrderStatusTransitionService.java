package ru.kzn.buzanov.delivery.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.OrderStatus;

import java.util.EnumSet;
import java.util.Set;

@Service
public class OrderStatusTransitionService {

    private static final Set<OrderStatus> FINAL_STATUSES = EnumSet.of(OrderStatus.completed, OrderStatus.cancelled);

    public boolean isFinal(OrderStatus status) {
        return FINAL_STATUSES.contains(status);
    }

    public boolean isAllowedTransition(OrderStatus from, OrderStatus to) {
        if (from == to) {
            return false;
        }
        if (isFinal(from)) {
            return false;
        }
        return switch (from) {
            case waiting_for_courier -> to == OrderStatus.cancelled;
            case courier_heading_to_pickup -> to == OrderStatus.courier_delivering || to == OrderStatus.cancelled;
            case courier_delivering -> to == OrderStatus.completed || to == OrderStatus.cancelled;
            default -> false;
        };
    }

    public void requireAllowedTransition(OrderStatus from, OrderStatus to) {
        if (!isAllowedTransition(from, to)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Недопустимый переход статуса");
        }
    }

    public void requireCourierCanTransition(OrderStatus from, OrderStatus to) {
        requireAllowedTransition(from, to);
        if (to == OrderStatus.cancelled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Курьер не может отменять заказ");
        }
        boolean allowed = (from == OrderStatus.courier_heading_to_pickup && to == OrderStatus.courier_delivering)
                || (from == OrderStatus.courier_delivering && to == OrderStatus.completed);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Курьер не может выполнить этот переход");
        }
    }

    public void requireRestaurantCanTransition(OrderStatus from, OrderStatus to) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ресторан не может менять курьерский статус");
    }
}
