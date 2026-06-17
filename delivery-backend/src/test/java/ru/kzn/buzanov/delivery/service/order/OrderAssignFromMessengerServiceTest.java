package ru.kzn.buzanov.delivery.service.order;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAssignFromMessengerServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void isAssignOrderCallback_acceptsKnownPrefix() {
        assertThat(OrderAssignFromMessengerService.isAssignOrderCallback("assign_order:" + ORDER_ID)).isTrue();
        assertThat(OrderAssignFromMessengerService.isAssignOrderCallback("delivery_order_x")).isFalse();
        assertThat(OrderAssignFromMessengerService.isAssignOrderCallback(null)).isFalse();
    }

    @Test
    void parseOrderIdFromCallback_parsesUuid() {
        Optional<UUID> parsed =
                OrderAssignFromMessengerService.parseOrderIdFromCallback("assign_order:" + ORDER_ID);
        assertThat(parsed).contains(ORDER_ID);
    }

    @Test
    void parseOrderIdFromCallback_rejectsInvalidPayload() {
        assertThat(OrderAssignFromMessengerService.parseOrderIdFromCallback("assign_order:not-a-uuid")).isEmpty();
        assertThat(OrderAssignFromMessengerService.parseOrderIdFromCallback("other")).isEmpty();
    }
}
