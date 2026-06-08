package ru.kzn.buzanov.delivery.service.publication;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.OrderStatus;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.PickupPoint;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.repository.PickupPointRepository;
import ru.kzn.buzanov.delivery.util.AddressShortener;
import ru.kzn.buzanov.delivery.util.PhoneDisplayFormatter;
import ru.kzn.buzanov.delivery.util.TelegramHtmlEscaper;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class OrderMessageFormatter {

    private static final Duration ASAP_THRESHOLD = Duration.ofMinutes(2);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Moscow"));
    private static final DateTimeFormatter BOARD_TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Moscow"));

    private final OrganizationRepository organizationRepository;
    private final PickupPointRepository pickupPointRepository;

    public String formatOrderCardTelegramHtml(DeliveryOrder order, String courierName) {
        return formatOrderCard(order, courierName, true);
    }

    public String formatOrderCardPlain(DeliveryOrder order, String courierName) {
        return formatOrderCard(order, courierName, false);
    }

    public String formatNewOrderTelegramHtml(DeliveryOrder order) {
        return formatOrderCardTelegramHtml(order, null);
    }

    public String formatRepublishedOrderTelegramHtml(DeliveryOrder order) {
        return formatOrderCardTelegramHtml(order, null);
    }

    public String formatNewOrderPlain(DeliveryOrder order) {
        return formatOrderCardPlain(order, null);
    }

    public String formatRepublishedOrderPlain(DeliveryOrder order) {
        return formatOrderCardPlain(order, null);
    }

    private String formatOrderCard(DeliveryOrder order, String courierName, boolean html) {
        OrderMessageContent content = buildContent(order);
        String title = "🚚 Заказ №" + content.publicNumber();
        StringBuilder sb = new StringBuilder();
        sb.append(html ? TelegramHtmlEscaper.bold(title) : title).append("\n\n");

        if (!content.restaurantName().isEmpty()) {
            sb.append("🏢 ").append(html ? TelegramHtmlEscaper.bold("Объект") : "Объект").append('\n')
                    .append(html ? TelegramHtmlEscaper.escape(content.restaurantName()) : content.restaurantName())
                    .append("\n\n");
        }
        if (!content.pickupAddress().isEmpty()) {
            sb.append("📍 ").append(html ? TelegramHtmlEscaper.bold("Забрать") : "Забрать").append('\n')
                    .append(html ? TelegramHtmlEscaper.escape(content.pickupAddress()) : content.pickupAddress())
                    .append("\n\n");
        }
        if (!content.deliveryAddress().isEmpty()) {
            sb.append("📍 ").append(html ? TelegramHtmlEscaper.bold("Доставить") : "Доставить").append('\n')
                    .append(html ? TelegramHtmlEscaper.escape(content.deliveryAddress()) : content.deliveryAddress())
                    .append("\n\n");
        }
        sb.append("💰 ").append(html ? TelegramHtmlEscaper.bold("Стоимость") : "Стоимость").append('\n')
                .append(html ? TelegramHtmlEscaper.escape(content.price()) : content.price()).append("\n\n");
        sb.append("🟡 ").append(html ? TelegramHtmlEscaper.bold("Статус") : "Статус").append('\n')
                .append(html ? TelegramHtmlEscaper.escape(content.statusLabel()) : content.statusLabel());

        if (order.getCourierUserId() != null) {
            String courier = courierName == null || courierName.isBlank() ? "Курьер" : courierName.trim();
            sb.append("\n\n");
            sb.append("👤 ").append(html ? TelegramHtmlEscaper.bold("Курьер") : "Курьер").append('\n')
                    .append(html ? TelegramHtmlEscaper.escape(courier) : courier);
            if (order.getAcceptedAt() != null) {
                String time = BOARD_TIME_FMT.format(order.getAcceptedAt());
                sb.append("\n\n");
                sb.append("⏰ ")
                        .append(html ? TelegramHtmlEscaper.escape("Взял в " + time) : "Взял в " + time);
            }
        }
        return sb.toString();
    }

    public String formatCourierAssignedDmPlain(DeliveryOrder order) {
        return formatCourierAssignedDm(order, false);
    }

    public String formatCourierAssignedDmTelegramHtml(DeliveryOrder order) {
        return formatCourierAssignedDm(order, true);
    }

    public String formatCourierAcceptedDmPlain(DeliveryOrder order) {
        return formatCourierAssignedDmPlain(order);
    }

    public String formatCourierAcceptedDmTelegramHtml(DeliveryOrder order) {
        return formatCourierAssignedDmTelegramHtml(order);
    }

    private String formatCourierAssignedDm(DeliveryOrder order, boolean html) {
        OrderMessageContent content = buildCourierDmContent(order);
        StringBuilder sb = new StringBuilder();
        String lead = "✅ Заказ закреплён за вами";
        String title = "🚚 Заказ №" + content.publicNumber();
        sb.append(html ? TelegramHtmlEscaper.bold(lead) : lead).append("\n\n");
        sb.append(html ? TelegramHtmlEscaper.bold(title) : title).append("\n\n");

        if (!content.restaurantName().isEmpty()) {
            sb.append("🏢 ").append(html ? TelegramHtmlEscaper.bold("Объект") : "Объект").append('\n')
                    .append(html ? TelegramHtmlEscaper.escape(content.restaurantName()) : content.restaurantName())
                    .append("\n\n");
        }
        if (!content.pickupAddress().isEmpty()) {
            sb.append("📍 ").append(html ? TelegramHtmlEscaper.bold("Забрать") : "Забрать").append('\n')
                    .append(html ? TelegramHtmlEscaper.escape(content.pickupAddress()) : content.pickupAddress())
                    .append("\n\n");
        }
        if (!content.deliveryAddress().isEmpty()) {
            sb.append("📍 ").append(html ? TelegramHtmlEscaper.bold("Доставить") : "Доставить").append('\n')
                    .append(html ? TelegramHtmlEscaper.escape(content.deliveryAddress()) : content.deliveryAddress())
                    .append("\n\n");
        }
        sb.append("💰 ").append(html ? TelegramHtmlEscaper.bold("Стоимость") : "Стоимость").append('\n')
                .append(html ? TelegramHtmlEscaper.escape(content.price()) : content.price());
        if (!content.deliveryTime().isEmpty()) {
            sb.append("\n\n🕐 ").append(html ? TelegramHtmlEscaper.bold("Время") : "Время").append('\n')
                    .append(html ? TelegramHtmlEscaper.escape(content.deliveryTime()) : content.deliveryTime());
        }
        if (!content.comment().isEmpty()) {
            sb.append("\n\n💬 ").append(html ? TelegramHtmlEscaper.bold("Комментарий") : "Комментарий").append('\n')
                    .append(html ? TelegramHtmlEscaper.escape(content.comment()) : content.comment());
        }
        if (!content.customerPhone().isEmpty()) {
            sb.append("\n\n📞 ").append(html ? TelegramHtmlEscaper.bold("Клиент") : "Клиент").append('\n')
                    .append(html ? TelegramHtmlEscaper.code(content.customerPhone()) : content.customerPhone());
        }
        return sb.toString();
    }

    private OrderMessageContent buildContent(DeliveryOrder order) {
        String restaurantName = organizationRepository.findById(order.getRestaurantId())
                .map(Organization::getName)
                .map(String::trim)
                .orElse("");

        return new OrderMessageContent(
                order.getPublicNumber(),
                restaurantName,
                AddressShortener.shorten(order.getPickupAddress()),
                resolvePickupPointPhone(order),
                AddressShortener.shorten(order.getDeliveryAddress()),
                order.getPrice().stripTrailingZeros().toPlainString() + " ₽",
                formatDeliveryTime(order),
                statusLabel(order.getStatus()),
                "",
                "");
    }

    private OrderMessageContent buildCourierDmContent(DeliveryOrder order) {
        String restaurantName = organizationRepository.findById(order.getRestaurantId())
                .map(Organization::getName)
                .map(String::trim)
                .orElse("");
        String customerPhone = order.getCustomerPhone() != null
                ? PhoneDisplayFormatter.format(order.getCustomerPhone().trim())
                : "";
        String comment = order.getComment() != null ? order.getComment().trim() : "";

        return new OrderMessageContent(
                order.getPublicNumber(),
                restaurantName,
                formatFullAddress(order.getPickupAddress(), null),
                "",
                formatFullAddress(order.getDeliveryAddress(), order.getDeliveryAddressFull()),
                order.getPrice().stripTrailingZeros().toPlainString() + " ₽",
                formatDeliveryTime(order),
                "",
                customerPhone,
                comment);
    }

    private static String formatFullAddress(String shortAddress, String fullAddress) {
        if (fullAddress != null && !fullAddress.isBlank()) {
            return fullAddress.trim();
        }
        if (shortAddress != null && !shortAddress.isBlank()) {
            return shortAddress.trim();
        }
        return "";
    }

    private String formatDeliveryTime(DeliveryOrder order) {
        if (isAsapDelivery(order)) {
            return "Как можно скорее";
        }
        if (order.getDeliveryTime() == null) {
            return "—";
        }
        return TIME_FMT.format(order.getDeliveryTime());
    }

    private boolean isAsapDelivery(DeliveryOrder order) {
        if (order.getDeliveryTime() == null || order.getCreatedAt() == null) {
            return false;
        }
        Duration delta = Duration.between(order.getCreatedAt(), order.getDeliveryTime()).abs();
        return delta.compareTo(ASAP_THRESHOLD) <= 0;
    }

    /**
     * Телефон для Telegram берётся только из точки забора (pickup_points.phone).
     * customerPhone заказа никогда не используется в публикации.
     */
    String resolvePickupPointPhone(DeliveryOrder order) {
        if (order.getPickupPointId() == null) {
            return "";
        }
        return pickupPointRepository.findById(order.getPickupPointId())
                .filter(point -> order.getRestaurantId() != null
                        && order.getRestaurantId().equals(point.getRestaurantId()))
                .map(PickupPoint::getPhone)
                .map(String::trim)
                .filter(phone -> !phone.isEmpty())
                .map(PhoneDisplayFormatter::format)
                .orElse("");
    }

    private String statusLabel(OrderStatus status) {
        if (status == null) {
            return "Неизвестный статус";
        }
        return switch (status) {
            case waiting_for_courier -> "Ожидает курьера";
            case courier_heading_to_pickup -> "Курьер едет за заказом";
            case courier_delivering -> "Доставляется";
            case completed -> "Выполнен";
            case cancelled -> "Отменён";
        };
    }

    private record OrderMessageContent(
            Long publicNumber,
            String restaurantName,
            String pickupAddress,
            String pickupPointPhone,
            String deliveryAddress,
            String price,
            String deliveryTime,
            String statusLabel,
            String customerPhone,
            String comment
    ) {
    }
}
