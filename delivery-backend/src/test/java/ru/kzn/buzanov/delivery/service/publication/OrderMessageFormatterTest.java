package ru.kzn.buzanov.delivery.service.publication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.OrderStatus;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.PickupPoint;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.repository.PickupPointRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderMessageFormatterTest {

    private static final String PICKUP_PHONE = "+79991112233";
    private static final String CUSTOMER_PHONE = "+78885554433";

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PickupPointRepository pickupPointRepository;

    private OrderMessageFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new OrderMessageFormatter(organizationRepository, pickupPointRepository);
    }

    @Test
    void telegramUsesPickupPointPhoneNotCustomerPhone() {
        DeliveryOrder order = sampleOrder();
        stubRepositories(order);

        for (String message : new String[] {
                formatter.formatNewOrderTelegramHtml(order),
                formatter.formatRepublishedOrderTelegramHtml(order),
                formatter.formatNewOrderPlain(order),
                formatter.formatRepublishedOrderPlain(order),
        }) {
            assertDoesNotContainCustomerPhone(message);
            assertFalse(message.contains("Связь с объектом"));
            assertFalse(message.contains("Телефон клиента"));
            assertTrue(message.contains("Ожидает курьера"));
            assertTrue(message.contains("Андрюша"));
        }
    }

    @Test
    void omitsObjectContactBlockWhenPickupPointPhoneMissing() {
        DeliveryOrder order = sampleOrder();
        UUID restaurantId = order.getRestaurantId();

        Organization org = new Organization();
        org.setId(restaurantId);
        org.setName("Андрюша Суши и Роллы");

        PickupPoint pickup = new PickupPoint();
        pickup.setId(order.getPickupPointId());
        pickup.setRestaurantId(restaurantId);
        pickup.setPhone(null);

        when(organizationRepository.findById(restaurantId)).thenReturn(Optional.of(org));
        when(pickupPointRepository.findById(order.getPickupPointId())).thenReturn(Optional.of(pickup));

        String message = formatter.formatNewOrderTelegramHtml(order);

        assertFalse(message.contains("Связь с объектом"));
        assertTrue(message.contains("Ожидает курьера"));
        assertDoesNotContainCustomerPhone(message);
    }

    @Test
    void orderCardShowsCourierAndAcceptedTimeWhenAssigned() {
        DeliveryOrder order = sampleOrder();
        order.setCourierUserId(42L);
        order.setStatus(OrderStatus.courier_heading_to_pickup);
        order.setAcceptedAt(Instant.parse("2026-06-06T11:30:00Z"));
        stubRepositories(order);

        String message = formatter.formatOrderCardTelegramHtml(order, "Иван Курьеров");

        assertTrue(message.contains("Курьер едет за заказом"));
        assertTrue(message.contains("Иван Курьеров"));
        assertTrue(message.contains("Взял в"));
        assertDoesNotContainCustomerPhone(message);
    }

    @Test
    void courierAssignedDmIncludesOrderDetailsAndCustomerPhone() {
        DeliveryOrder order = sampleOrder();
        stubRepositories(order);

        String message = formatter.formatCourierAssignedDmTelegramHtml(order);

        assertTrue(message.contains("Заказ закреплён за вами"));
        assertTrue(message.contains("Заказ №9"));
        assertTrue(message.contains("Стоимость"));
        assertTrue(message.contains("Объект"));
        assertTrue(message.contains("Андрюша"));
        assertTrue(message.contains("Забрать"));
        assertTrue(message.contains("Доставить"));
        assertTrue(message.contains("600"));
        assertTrue(message.contains("Комментарий"));
        assertTrue(message.contains("Позвонить при входе"));
        assertTrue(message.contains("Клиент"));
        assertTrue(message.contains("+7 888 555-44-33") || message.contains("888"));
    }

    @Test
    void courierAssignedDmOmitsCommentWhenBlank() {
        DeliveryOrder order = sampleOrder();
        order.setComment(null);
        stubRepositories(order);

        String message = formatter.formatCourierAssignedDmPlain(order);

        assertFalse(message.contains("Комментарий"));
        assertTrue(message.contains("Клиент"));
    }

    @Test
    void resolvePickupPointPhoneIgnoresCustomerPhone() {
        DeliveryOrder order = sampleOrder();
        stubPickupPoint(order, PICKUP_PHONE);

        assertTrue(formatter.resolvePickupPointPhone(order).contains("999"));
        assertFalse(formatter.resolvePickupPointPhone(order).contains("888"));
    }

    private DeliveryOrder sampleOrder() {
        UUID restaurantId = UUID.randomUUID();
        UUID pickupPointId = UUID.randomUUID();

        DeliveryOrder order = new DeliveryOrder();
        order.setPublicNumber(9L);
        order.setRestaurantId(restaurantId);
        order.setPickupPointId(pickupPointId);
        order.setPickupAddress("Казань, ул. Родины, 24В");
        order.setDeliveryAddress("Казань, ул. Академика Глушко, 5");
        order.setCustomerPhone(CUSTOMER_PHONE);
        order.setComment("Позвонить при входе");
        order.setPrice(BigDecimal.valueOf(600));
        order.setStatus(OrderStatus.waiting_for_courier);
        order.setCreatedAt(Instant.parse("2026-06-06T10:00:00Z"));
        order.setDeliveryTime(Instant.parse("2026-06-06T10:00:01Z"));
        return order;
    }

    private void stubRepositories(DeliveryOrder order) {
        Organization org = new Organization();
        org.setId(order.getRestaurantId());
        org.setName("Андрюша Суши и Роллы");
        when(organizationRepository.findById(order.getRestaurantId())).thenReturn(Optional.of(org));
        stubPickupPoint(order, PICKUP_PHONE);
    }

    private void stubPickupPoint(DeliveryOrder order, String phone) {
        PickupPoint pickup = new PickupPoint();
        pickup.setId(order.getPickupPointId());
        pickup.setRestaurantId(order.getRestaurantId());
        pickup.setPhone(phone);
        when(pickupPointRepository.findById(order.getPickupPointId())).thenReturn(Optional.of(pickup));
    }

    private static void assertContainsPickupPhone(String message) {
        assertTrue(
                message.contains("+7 999 111-22-33") || message.contains("+79991112233"),
                () -> "Expected pickup phone in: " + message);
    }

    private static void assertDoesNotContainCustomerPhone(String message) {
        String digits = message.replaceAll("\\D", "");
        assertFalse(digits.contains("78885554433"), () -> "Customer phone leaked: " + message);
        assertFalse(message.contains(CUSTOMER_PHONE), () -> "Customer phone leaked: " + message);
    }
}
