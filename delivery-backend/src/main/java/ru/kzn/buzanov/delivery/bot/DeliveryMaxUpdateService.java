package ru.kzn.buzanov.delivery.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryMaxUpdateService {

    private final DeliveryMaxBotClient maxBotClient;

    public void handle(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            log.info("Delivery MAX bot: empty webhook payload");
            return;
        }
        String updateType = extractString(payload, "update_type", "event_type", "type", "event");
        log.info("Delivery MAX bot: webhook update_type={}", updateType);
        if ("bot_added".equals(updateType)) {
            handleBotAdded(payload);
            return;
        }
        if (!"message_created".equals(updateType)) {
            return;
        }
        String text = extractMessageText(payload);
        if (!DeliveryTelegramUpdateService.isGetChatIdCommand(text)) {
            log.debug("Delivery MAX bot: message_created ignored, text={}", text);
            return;
        }
        Long chatId = extractChatId(payload);
        String chatType = extractChatType(payload);
        boolean isDialog = isPrivateDialog(chatType, payload);
        if (chatId == null || isDialog || !isPublicationChatType(chatType, chatId, payload)) {
            log.info("Delivery MAX bot: /get_chat_id ignored chatId={} chatType={} dialog={}", chatId, chatType, isDialog);
            return;
        }
        String chatTitle = extractChatTitle(payload);
        log.info("Delivery MAX bot: /get_chat_id in chat {} ({})", chatId, chatTitle);
        replyGetChatId(chatId, chatTitle, chatType);
    }

    private void handleBotAdded(Map<String, Object> payload) {
        Long chatId = parseLong(payload.get("chat_id"));
        if (chatId == null) {
            return;
        }
        boolean channel = Boolean.TRUE.equals(payload.get("is_channel"));
        String chatType = channel ? "channel" : "chat";
        log.info("Delivery MAX bot: bot_added to chat {} channel={}", chatId, channel);
        replyGetChatId(chatId, null, chatType);
    }

    private void replyGetChatId(long chatId, String chatTitle, String chatType) {
        String title = chatTitle != null && !chatTitle.isBlank() ? chatTitle : "Чат";
        String responseText = String.format(
                "📋 <b>Информация о чате</b>%n%n"
                        + "Название: <b>%s</b>%n"
                        + "Тип: <b>%s</b>%n"
                        + "Chat ID: <code>%d</code>%n%n"
                        + "Скопируйте этот ID при добавлении канала в Добровоз.",
                escapeHtml(title),
                chatTypeLabel(chatType),
                chatId
        );
        if (maxBotClient.sendHtmlMessage(chatId, responseText)) {
            log.info("Delivery MAX bot: replied to /get_chat_id in chat {}", chatId);
        } else {
            log.warn("Delivery MAX bot: failed to reply to /get_chat_id in chat {}", chatId);
        }
    }

    static boolean isPublicationChatType(String chatType, Long chatId, Map<String, Object> payload) {
        if (chatType != null && !chatType.isBlank()) {
            return switch (chatType.toLowerCase()) {
                case "channel", "chat", "group" -> true;
                case "dialog", "user" -> false;
                default -> chatId != null;
            };
        }
        return chatId != null && !isPrivateDialog(null, payload);
    }

    @SuppressWarnings("unchecked")
    static boolean isPrivateDialog(String chatType, Map<String, Object> payload) {
        if (chatType != null) {
            String t = chatType.toLowerCase();
            if ("dialog".equals(t) || "user".equals(t)) {
                return true;
            }
            if ("channel".equals(t) || "chat".equals(t) || "group".equals(t)) {
                return false;
            }
        }
        Object message = payload.get("message");
        if (!(message instanceof Map<?, ?> rawMessage)) {
            return false;
        }
        Map<String, Object> m = (Map<String, Object>) rawMessage;
        Object recipient = m.get("recipient");
        if (recipient instanceof Map<?, ?> rawRecipient) {
            if (rawRecipient.get("user_id") != null && rawRecipient.get("chat_id") == null) {
                return true;
            }
            String rt = stringVal(rawRecipient.get("chat_type"));
            if (rt == null) {
                rt = stringVal(rawRecipient.get("type"));
            }
            if (rt != null) {
                return "dialog".equalsIgnoreCase(rt) || "user".equalsIgnoreCase(rt);
            }
        }
        return false;
    }

    private static String chatTypeLabel(String chatType) {
        if (chatType == null) {
            return "Чат";
        }
        return switch (chatType.toLowerCase()) {
            case "channel" -> "Канал";
            case "chat", "group" -> "Группа";
            default -> "Чат";
        };
    }

    @SuppressWarnings("unchecked")
    private static String extractMessageText(Map<String, Object> payload) {
        Object message = payload.get("message");
        if (!(message instanceof Map<?, ?> rawMessage)) {
            return null;
        }
        Map<String, Object> m = (Map<String, Object>) rawMessage;
        Object body = m.get("body");
        if (body instanceof Map<?, ?> rawBody) {
            Object text = rawBody.get("text");
            if (text != null) {
                return text.toString();
            }
        }
        Object text = m.get("text");
        return text != null ? text.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static Long extractChatId(Map<String, Object> payload) {
        Object message = payload.get("message");
        if (message instanceof Map<?, ?> rawMessage) {
            Map<String, Object> m = (Map<String, Object>) rawMessage;
            Object recipient = m.get("recipient");
            if (recipient instanceof Map<?, ?> rawRecipient) {
                Long fromRecipient = parseLong(rawRecipient.get("chat_id"));
                if (fromRecipient != null) {
                    return fromRecipient;
                }
                fromRecipient = parseLong(rawRecipient.get("id"));
                if (fromRecipient != null) {
                    return fromRecipient;
                }
            }
            Long fromMessage = parseLong(m.get("chat_id"));
            if (fromMessage != null) {
                return fromMessage;
            }
        }
        return parseLong(payload.get("chat_id"));
    }

    @SuppressWarnings("unchecked")
    private static String extractChatType(Map<String, Object> payload) {
        Object message = payload.get("message");
        if (!(message instanceof Map<?, ?> rawMessage)) {
            return null;
        }
        Map<String, Object> m = (Map<String, Object>) rawMessage;
        Object recipient = m.get("recipient");
        if (recipient instanceof Map<?, ?> rawRecipient) {
            String type = stringVal(rawRecipient.get("chat_type"));
            if (type != null) {
                return type;
            }
            return stringVal(rawRecipient.get("type"));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String extractChatTitle(Map<String, Object> payload) {
        Object message = payload.get("message");
        if (!(message instanceof Map<?, ?> rawMessage)) {
            return null;
        }
        Map<String, Object> m = (Map<String, Object>) rawMessage;
        Object recipient = m.get("recipient");
        if (recipient instanceof Map<?, ?> rawRecipient) {
            return stringVal(rawRecipient.get("title"));
        }
        return stringVal(m.get("title"));
    }

    private static String extractString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v instanceof String s && !s.isBlank()) {
                return s;
            }
            if (v != null && !(v instanceof Map<?, ?>)) {
                return v.toString();
            }
        }
        return null;
    }

    private static String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
