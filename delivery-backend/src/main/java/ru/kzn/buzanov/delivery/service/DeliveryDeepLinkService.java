package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.kzn.buzanov.delivery.config.DeliveryBotProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryDeepLinkService {

    private final DeliveryBotProperties properties;

    public static final String MY_ORDERS_START_PARAM = "delivery_my_orders";

    public String myOrderStartParam(UUID orderId) {
        return "delivery_my_order_" + orderId;
    }

    public String orderStartParam(UUID orderId) {
        return "delivery_order_" + orderId;
    }

    public String courierMyOrdersWebUrl() {
        String base = trimTrailingSlash(properties.getFrontendUrl());
        return base + "/courier/my-orders";
    }

    public String courierMyOrderWebUrl(UUID orderId) {
        String base = trimTrailingSlash(properties.getFrontendUrl());
        return base + "/courier/my-orders/" + orderId;
    }

    public String telegramMyOrderMiniAppUrl(UUID orderId) {
        String username = normalizeBotUsername(properties.getTelegram().getBotUsername());
        if (username.isEmpty()) {
            return courierMyOrderWebUrl(orderId);
        }
        String startParam = myOrderStartParam(orderId);
        String shortName = normalizeMiniAppShortName(properties.getTelegram().getMiniAppShortName());
        if (!shortName.isEmpty()) {
            return "https://t.me/" + username + "/" + shortName + "?startapp=" + startParam;
        }
        return "https://t.me/" + username + "?startapp=" + startParam;
    }

    public String telegramMyOrderWebAppButtonUrl(UUID orderId) {
        String base = trimTrailingSlash(properties.getFrontendUrl());
        if (base.isEmpty()) {
            return courierMyOrderWebUrl(orderId);
        }
        return base + "?start_param=" + encodeQuery(myOrderStartParam(orderId));
    }

    public String telegramMyOrdersMiniAppUrl() {
        String username = normalizeBotUsername(properties.getTelegram().getBotUsername());
        if (username.isEmpty()) {
            return courierMyOrdersWebUrl();
        }
        String shortName = normalizeMiniAppShortName(properties.getTelegram().getMiniAppShortName());
        if (!shortName.isEmpty()) {
            return "https://t.me/" + username + "/" + shortName + "?startapp=" + MY_ORDERS_START_PARAM;
        }
        return "https://t.me/" + username + "?startapp=" + MY_ORDERS_START_PARAM;
    }

    public String telegramMyOrdersWebAppButtonUrl() {
        String base = trimTrailingSlash(properties.getFrontendUrl());
        if (base.isEmpty()) {
            return courierMyOrdersWebUrl();
        }
        return base + "?start_param=" + encodeQuery(MY_ORDERS_START_PARAM);
    }

    public String courierOrderWebUrl(UUID orderId) {
        String base = trimTrailingSlash(properties.getFrontendUrl());
        return base + "/courier/orders/" + orderId;
    }

    /**
     * URL для inline-кнопки Web App: открывает mini app напрямую с start_param.
     */
    public String telegramOrderWebAppButtonUrl(UUID orderId) {
        String base = trimTrailingSlash(properties.getFrontendUrl());
        if (base.isEmpty()) {
            return courierOrderWebUrl(orderId);
        }
        return base + "?start_param=" + encodeQuery(orderStartParam(orderId));
    }

    /**
     * Ссылка t.me для открытия mini app через бота (startapp).
     */
    public String telegramMiniAppUrl(UUID orderId) {
        String username = normalizeBotUsername(properties.getTelegram().getBotUsername());
        if (username.isEmpty()) {
            return webUrlWithStartParam(orderId);
        }
        String startParam = orderStartParam(orderId);
        String shortName = normalizeMiniAppShortName(properties.getTelegram().getMiniAppShortName());
        if (!shortName.isEmpty()) {
            return "https://t.me/" + username + "/" + shortName + "?startapp=" + startParam;
        }
        return "https://t.me/" + username + "?startapp=" + startParam;
    }

    public String maxButtonUrl(UUID orderId) {
        return maxStartAppUrl(orderStartParam(orderId), courierOrderWebUrl(orderId));
    }

    public String maxMyOrderButtonUrl(UUID orderId) {
        return maxStartAppUrl(myOrderStartParam(orderId), courierMyOrderWebUrl(orderId));
    }

    private String maxStartAppUrl(String startParam, String fallbackUrl) {
        String username = properties.getMax().getBotUsername();
        if (username != null && !username.isBlank()) {
            String u = username.trim();
            if (u.startsWith("@")) {
                u = u.substring(1);
            }
            return "https://max.ru/" + u + "?startapp=" + encodeQuery(startParam);
        }
        String base = trimTrailingSlash(properties.getFrontendUrl());
        if (base.isEmpty()) {
            return fallbackUrl;
        }
        return base + "?start_param=" + encodeQuery(startParam);
    }

    public String webUrlWithStartParam(UUID orderId) {
        String base = trimTrailingSlash(properties.getFrontendUrl());
        if (base.isEmpty()) {
            return courierOrderWebUrl(orderId);
        }
        return base + "?start_param=" + encodeQuery(orderStartParam(orderId));
    }

    private static String normalizeBotUsername(String username) {
        if (username == null || username.isBlank()) {
            return "";
        }
        String u = username.trim();
        if (u.startsWith("@")) {
            u = u.substring(1);
        }
        return u;
    }

    private static String normalizeMiniAppShortName(String shortName) {
        if (shortName == null || shortName.isBlank()) {
            return "";
        }
        String s = shortName.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        return s;
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String s = url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
