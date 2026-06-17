package ru.kzn.buzanov.delivery.service.publication;

import com.pengrad.telegrambot.model.WebAppInfo;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.kzn.buzanov.delivery.config.DeliveryBotProperties;
import ru.kzn.buzanov.delivery.domain.ChatType;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.service.DeliveryDeepLinkService;
import ru.kzn.buzanov.delivery.service.order.OrderAssignFromMessengerService;
import ru.kzn.buzanov.delivery.util.NavigatorUrlBuilder;
import ru.kzn.buzanov.delivery.util.PhoneDisplayFormatter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramKeyboardFactory {

    private static final String VIEW_ORDER_BUTTON_LABEL = "👀 Посмотреть заказ";
    private static final String ASSIGN_ORDER_BUTTON_LABEL = "🚚 Взять заказ";
    private static final String OPEN_ORDER_BUTTON_LABEL = "📦 Открыть заказ";
    private static final String CALL_CLIENT_BUTTON_LABEL = "📞 Позвонить клиенту";
    private static final String NAVIGATOR_BUTTON_LABEL = "🗺 Навигатор";
    private static final String BUTTON_TYPE_WEB_APP = "webApp";
    private static final String BUTTON_TYPE_URL = "url";

    private final DeliveryDeepLinkService deepLinkService;
    private final DeliveryBotProperties properties;

    /**
     * Клавиатура для свободного заказа: просмотр (mini app) + взятие (callback).
     */
    public InlineKeyboardButton[][] buildWaitingForCourierKeyboard(UUID orderId, ChatType chatType, long chatId) {
        return new InlineKeyboardButton[][] {
            {
                buildOrderViewButton(orderId, chatType, chatId),
                buildOrderAssignCallbackButton(orderId),
            }
        };
    }

    /**
     * @deprecated используйте {@link #buildOrderViewButton(UUID, ChatType, long)}
     */
    @Deprecated
    public InlineKeyboardButton buildOrderOpenButton(UUID orderId, ChatType chatType, long chatId) {
        return buildOrderViewButton(orderId, chatType, chatId);
    }

    /**
     * В каналах Telegram inline-кнопка web_app не поддерживается (BUTTON_TYPE_INVALID).
     * Для channel — url (t.me/?startapp=), для group — web_app.
     */
    public InlineKeyboardButton buildOrderViewButton(UUID orderId, ChatType chatType, long chatId) {
        String botUsername = normalizeBotUsername(properties.getTelegram().getBotUsername());
        String registeredMiniAppUrl = trimTrailingSlash(properties.getFrontendUrl());

        if (chatType == ChatType.channel) {
            String miniAppUrl = deepLinkService.telegramMiniAppUrl(orderId);
            log.info(
                    "Telegram view order button: miniAppUrl={} botUsername={} chatId={} buttonType={} chatType={}",
                    miniAppUrl,
                    botUsername.isEmpty() ? "(not configured)" : botUsername,
                    chatId,
                    BUTTON_TYPE_URL,
                    chatType);
            return new InlineKeyboardButton(VIEW_ORDER_BUTTON_LABEL).url(miniAppUrl);
        }

        String miniAppUrl = deepLinkService.telegramOrderWebAppButtonUrl(orderId);
        log.info(
                "Telegram view order button: miniAppUrl={} botUsername={} chatId={} buttonType={} chatType={}",
                miniAppUrl,
                botUsername.isEmpty() ? "(not configured)" : botUsername,
                chatId,
                BUTTON_TYPE_WEB_APP,
                chatType);

        if (!registeredMiniAppUrl.isEmpty() && !miniAppUrlMatchesRegistered(registeredMiniAppUrl, miniAppUrl)) {
            log.warn(
                    "Telegram view order button: miniAppUrl base does not match BotFather Web App URL. "
                            + "registeredMiniAppUrl={} miniAppUrl={} "
                            + "Check BotFather → Menu Button / Web App URL for bot @{}",
                    registeredMiniAppUrl,
                    miniAppUrl,
                    botUsername.isEmpty() ? "?" : botUsername);
        }

        return new InlineKeyboardButton(VIEW_ORDER_BUTTON_LABEL).webApp(new WebAppInfo(miniAppUrl));
    }

    public InlineKeyboardButton buildOrderAssignCallbackButton(UUID orderId) {
        String callbackData = OrderAssignFromMessengerService.CALLBACK_PREFIX + orderId;
        log.info("Telegram assign order button: orderId={} callbackData={}", orderId, callbackData);
        return new InlineKeyboardButton(ASSIGN_ORDER_BUTTON_LABEL).callbackData(callbackData);
    }

    public InlineKeyboardButton[][] buildCourierAssignedDmKeyboard(DeliveryOrder order) {
        List<InlineKeyboardButton[]> rows = new ArrayList<>();
        rows.add(new InlineKeyboardButton[] {buildMyOrderActionButton(order.getId(), OPEN_ORDER_BUTTON_LABEL)});

        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        String telUri = PhoneDisplayFormatter.toTelUri(order.getCustomerPhone());
        if (!telUri.isEmpty()) {
            actionRow.add(new InlineKeyboardButton(CALL_CLIENT_BUTTON_LABEL).url(telUri));
        }
        NavigatorUrlBuilder.yandexMapsUrl(order)
                .ifPresent(url -> actionRow.add(new InlineKeyboardButton(NAVIGATOR_BUTTON_LABEL).url(url)));
        if (!actionRow.isEmpty()) {
            rows.add(actionRow.toArray(InlineKeyboardButton[]::new));
        }
        return rows.toArray(InlineKeyboardButton[][]::new);
    }

    public List<List<LinkButton>> buildCourierAssignedDmLinkButtons(DeliveryOrder order) {
        List<List<LinkButton>> rows = new ArrayList<>();
        rows.add(List.of(new LinkButton(
                OPEN_ORDER_BUTTON_LABEL,
                deepLinkService.myOrderStartParam(order.getId()),
                "open_app")));

        List<LinkButton> actionRow = new ArrayList<>();
        String telUri = PhoneDisplayFormatter.toTelUri(order.getCustomerPhone());
        if (!telUri.isEmpty()) {
            actionRow.add(new LinkButton(CALL_CLIENT_BUTTON_LABEL, telUri));
        }
        NavigatorUrlBuilder.yandexMapsUrl(order)
                .ifPresent(url -> actionRow.add(new LinkButton(NAVIGATOR_BUTTON_LABEL, url)));
        if (!actionRow.isEmpty()) {
            rows.add(actionRow);
        }
        return rows;
    }

    public record LinkButton(String label, String url, String maxButtonType) {
        public LinkButton(String label, String url) {
            this(label, url, "link");
        }

        /** Для MAX {@code open_app}: {@code url} — fallback link; {@code startParam} — payload кнопки. */
        public String startParam() {
            return "open_app".equals(maxButtonType) ? url : "";
        }
    }

    private InlineKeyboardButton buildMyOrderActionButton(UUID orderId, String label) {
        String botUsername = normalizeBotUsername(properties.getTelegram().getBotUsername());
        if (!botUsername.isEmpty()) {
            return new InlineKeyboardButton(label).url(deepLinkService.telegramMyOrderMiniAppUrl(orderId));
        }
        return new InlineKeyboardButton(label)
                .webApp(new WebAppInfo(deepLinkService.telegramMyOrderWebAppButtonUrl(orderId)));
    }

    private static boolean miniAppUrlMatchesRegistered(String registeredBase, String miniAppUrl) {
        String actualBase = extractBaseUrl(miniAppUrl);
        return registeredBase.equalsIgnoreCase(actualBase);
    }

    private static String extractBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return trimTrailingSlash(url.split("\\?")[0]);
            }
            int port = uri.getPort();
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                path = "";
            } else {
                path = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            }
            String base = scheme + "://" + host;
            if (port > 0) {
                base += ":" + port;
            }
            return base + path;
        } catch (IllegalArgumentException e) {
            return trimTrailingSlash(url.split("\\?")[0]);
        }
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
