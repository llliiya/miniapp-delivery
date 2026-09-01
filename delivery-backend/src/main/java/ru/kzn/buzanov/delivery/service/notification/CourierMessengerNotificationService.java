package ru.kzn.buzanov.delivery.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.kzn.buzanov.delivery.config.DeliveryBotProperties;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.integration.AccountUserClient;
import ru.kzn.buzanov.delivery.integration.AccountUserContacts;
import ru.kzn.buzanov.delivery.service.DeliveryDeepLinkService;
import ru.kzn.buzanov.delivery.service.publication.MaxOpenAppButtons;
import ru.kzn.buzanov.delivery.service.publication.OrderMessageFormatter;
import ru.kzn.buzanov.delivery.service.publication.TelegramKeyboardFactory;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class CourierMessengerNotificationService {

    private final TelegramBot telegramBot;
    private final AccountUserClient accountUserClient;
    private final OrderMessageFormatter messageFormatter;
    private final TelegramKeyboardFactory keyboardFactory;
    private final DeliveryBotProperties properties;
    private final DeliveryDeepLinkService deepLinkService;
    private final MaxOpenAppButtons maxOpenAppButtons;
    private final ObjectMapper objectMapper;

    public CourierMessengerNotificationService(
            @Autowired(required = false) TelegramBot telegramBot,
            AccountUserClient accountUserClient,
            OrderMessageFormatter messageFormatter,
            TelegramKeyboardFactory keyboardFactory,
            DeliveryBotProperties properties,
            DeliveryDeepLinkService deepLinkService,
            MaxOpenAppButtons maxOpenAppButtons,
            ObjectMapper objectMapper) {
        this.telegramBot = telegramBot;
        this.accountUserClient = accountUserClient;
        this.messageFormatter = messageFormatter;
        this.keyboardFactory = keyboardFactory;
        this.properties = properties;
        this.deepLinkService = deepLinkService;
        this.maxOpenAppButtons = maxOpenAppButtons;
        this.objectMapper = objectMapper;
    }

    /**
     * Одноразовое уведомление курьеру после назначения заказа (courierUserId != null).
     * В канал не дублируется.
     */
    public void notifyOrderAssigned(DeliveryOrder order, Long courierUserId) {
        if (courierUserId == null) {
            return;
        }
        AccountUserContacts contacts = accountUserClient.findUserContacts(courierUserId).orElse(null);
        if (contacts == null) {
            log.info("Courier {} has no linked messenger contacts; skip personal notification", courierUserId);
            return;
        }
        if (telegramBot != null && hasTelegramId(contacts)) {
            notifyTelegram(order, courierUserId, contacts);
        } else if (hasMaxId(contacts)) {
            notifyMax(order, courierUserId, contacts);
        } else {
            log.info("Courier {} has no linked Telegram/MAX id; skip personal notification", courierUserId);
        }
    }

    private static boolean hasTelegramId(AccountUserContacts contacts) {
        return contacts.telegramId() != null && !contacts.telegramId().isBlank();
    }

    private static boolean hasMaxId(AccountUserContacts contacts) {
        return contacts.maxId() != null && !contacts.maxId().isBlank();
    }

    public void notifyOrderAccepted(DeliveryOrder order, Long courierUserId) {
        notifyOrderAssigned(order, courierUserId);
    }

    private void notifyTelegram(DeliveryOrder order, Long courierUserId, AccountUserContacts contacts) {
        if (!hasTelegramId(contacts)) {
            return;
        }
        try {
            long chatId = Long.parseLong(contacts.telegramId().trim());
            String text = messageFormatter.formatCourierAssignedDmTelegramHtml(order);
            InlineKeyboardButton[][] rows = keyboardFactory.buildCourierAssignedDmKeyboard(order);
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(rows);
            SendResponse response = telegramBot.execute(
                    new SendMessage(chatId, text).parseMode(ParseMode.HTML).replyMarkup(keyboard));
            if (!response.isOk()) {
                log.warn(
                        "Failed to send order assigned DM to courier {} via Telegram: {}",
                        courierUserId,
                        response.description());
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid telegram id for courier {}: {}", courierUserId, contacts.telegramId());
        } catch (Exception e) {
            log.warn("Failed to send order assigned DM to courier {} via Telegram: {}", courierUserId, e.getMessage());
        }
    }

    private void notifyMax(DeliveryOrder order, Long courierUserId, AccountUserContacts contacts) {
        String token = properties.getMax().getBotToken();
        if (token == null || token.isBlank() || !hasMaxId(contacts)) {
            return;
        }
        try {
            long chatId = Long.parseLong(contacts.maxId().trim());
            String text = messageFormatter.formatCourierAssignedDmPlain(order);
            List<List<TelegramKeyboardFactory.LinkButton>> buttonRows =
                    keyboardFactory.buildCourierAssignedDmLinkButtons(order);
            ObjectNode body = buildMaxMessageBody(text, buttonRows);
            String base = trimSlash(properties.getMax().getApiBaseUrl());
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(10));
            factory.setReadTimeout(Duration.ofSeconds(15));
            RestClient client = RestClient.builder().baseUrl(base).requestFactory(factory).build();
            client.post()
                    .uri(uriBuilder -> uriBuilder.path("/messages").queryParam("chat_id", chatId).build())
                    .header(HttpHeaders.AUTHORIZATION, token.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .toBodilessEntity();
        } catch (NumberFormatException e) {
            log.warn("Invalid max id for courier {}: {}", courierUserId, contacts.maxId());
        } catch (Exception e) {
            log.warn("Failed to send order assigned DM to courier {} via MAX: {}", courierUserId, e.getMessage());
        }
    }

    private ObjectNode buildMaxMessageBody(String text, List<List<TelegramKeyboardFactory.LinkButton>> buttonRows) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("text", text);
        if (buttonRows == null || buttonRows.isEmpty()) {
            return root;
        }
        ArrayNode rows = objectMapper.createArrayNode();
        for (List<TelegramKeyboardFactory.LinkButton> row : buttonRows) {
            ArrayNode rowNode = objectMapper.createArrayNode();
            for (TelegramKeyboardFactory.LinkButton button : row) {
                ObjectNode btn = objectMapper.createObjectNode();
                if ("open_app".equals(button.maxButtonType())) {
                    btn = maxOpenAppButtons.openAppButton(
                            button.label(), deepLinkService.maxOpenAppTarget(button.startParam()));
                } else {
                    btn.put("type", "link");
                    btn.put("url", button.url());
                    btn.put("text", button.label());
                }
                rowNode.add(btn);
            }
            rows.add(rowNode);
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("buttons", rows);
        ObjectNode attachment = objectMapper.createObjectNode();
        attachment.put("type", "inline_keyboard");
        attachment.set("payload", payload);
        root.set("attachments", objectMapper.createArrayNode().add(attachment));
        return root;
    }

    private static String trimSlash(String url) {
        String s = url == null ? "" : url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
