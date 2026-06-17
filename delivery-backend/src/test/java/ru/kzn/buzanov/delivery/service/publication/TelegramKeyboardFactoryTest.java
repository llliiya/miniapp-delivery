package ru.kzn.buzanov.delivery.service.publication;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.kzn.buzanov.delivery.config.DeliveryBotProperties;
import ru.kzn.buzanov.delivery.domain.ChatType;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.service.DeliveryDeepLinkService;
import ru.kzn.buzanov.delivery.service.order.OrderAssignFromMessengerService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramKeyboardFactoryTest {

    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private DeliveryBotProperties properties;
    private TelegramKeyboardFactory factory;

    @BeforeEach
    void setUp() {
        properties = new DeliveryBotProperties();
        properties.setFrontendUrl("https://example.com");
        properties.getTelegram().setBotUsername("dobrovoz_test_bot");
        factory = new TelegramKeyboardFactory(new DeliveryDeepLinkService(properties), properties);
    }

    @Test
    void buildWaitingForCourierKeyboard_group_hasViewWebAppAndAssignCallback() {
        InlineKeyboardButton[][] rows = factory.buildWaitingForCourierKeyboard(ORDER_ID, ChatType.group, -1001234567890L);

        assertThat(rows.length).isEqualTo(1);
        assertThat(rows[0]).hasSize(2);
        assertThat(rows[0][0].text()).isEqualTo("👀 Посмотреть заказ");
        assertThat(rows[0][0].webApp()).isNotNull();
        assertThat(rows[0][0].webApp().url())
                .isEqualTo("https://example.com?start_param=delivery_order_11111111-1111-1111-1111-111111111111");
        assertThat(rows[0][1].text()).isEqualTo("🚚 Взять заказ");
        assertThat(rows[0][1].callbackData())
                .isEqualTo(OrderAssignFromMessengerService.CALLBACK_PREFIX + ORDER_ID);
    }

    @Test
    void buildWaitingForCourierKeyboard_channel_hasViewUrlAndAssignCallback() {
        InlineKeyboardButton[][] rows = factory.buildWaitingForCourierKeyboard(ORDER_ID, ChatType.channel, -1001234567890L);

        assertThat(rows[0][0].text()).isEqualTo("👀 Посмотреть заказ");
        assertThat(rows[0][0].url())
                .isEqualTo("https://t.me/dobrovoz_test_bot?startapp=delivery_order_11111111-1111-1111-1111-111111111111");
        assertThat(rows[0][1].callbackData())
                .isEqualTo(OrderAssignFromMessengerService.CALLBACK_PREFIX + ORDER_ID);
    }

    @Test
    void buildCourierAssignedDmKeyboard_includesOpenCallAndNavigatorButtons() {
        DeliveryOrder order = assignedOrder();

        InlineKeyboardButton[][] rows = factory.buildCourierAssignedDmKeyboard(order);

        assertThat(rows.length).isEqualTo(2);
        assertThat(rows[0][0].text()).isEqualTo("📦 Открыть заказ");
        assertThat(rows[0][0].url()).contains("delivery_my_order_");
        assertThat(rows[1]).extracting(InlineKeyboardButton::text)
                .containsExactly("📞 Позвонить клиенту", "🗺 Навигатор");
        assertThat(rows[1][0].url()).isEqualTo("tel:+78885554433");
        assertThat(rows[1][1].url()).contains("yandex.ru/maps");
    }

    @Test
    void buildCourierAssignedDmLinkButtons_omitsNavigatorWithoutCoordinates() {
        DeliveryOrder order = assignedOrder();
        order.setPickupLat(null);
        order.setPickupLon(null);
        order.setDeliveryLat(null);
        order.setDeliveryLon(null);

        List<List<TelegramKeyboardFactory.LinkButton>> rows = factory.buildCourierAssignedDmLinkButtons(order);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).extracting(TelegramKeyboardFactory.LinkButton::label)
                .containsExactly("📞 Позвонить клиенту");
    }

    private static DeliveryOrder assignedOrder() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(ORDER_ID);
        order.setPickupAddress("pickup");
        order.setDeliveryAddress("delivery");
        order.setDeliveryTime(Instant.now());
        order.setPrice(BigDecimal.TEN);
        order.setCustomerPhone("+78885554433");
        order.setPickupLat(55.75);
        order.setPickupLon(37.62);
        order.setDeliveryLat(55.76);
        order.setDeliveryLon(37.63);
        return order;
    }
}
