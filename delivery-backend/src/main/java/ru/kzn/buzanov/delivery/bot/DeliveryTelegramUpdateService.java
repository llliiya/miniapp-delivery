package ru.kzn.buzanov.delivery.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeliveryTelegramUpdateService {

    static final String GET_CHAT_ID_COMMAND = "/get_chat_id";

    private final TelegramBot telegramBot;

    public DeliveryTelegramUpdateService(@Autowired(required = false) TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    public void handleUpdate(Update update) {
        if (telegramBot == null || update == null) {
            return;
        }
        Message primary = primaryMessage(update);
        if (primary == null || primary.chat() == null) {
            return;
        }
        String text = messageText(primary);
        if (!isGetChatIdCommand(text)) {
            return;
        }
        if (!isPublicationChat(primary.chat())) {
            log.debug("Delivery bot: /get_chat_id ignored for chat type {}", primary.chat().type());
            return;
        }
        log.info(
                "Delivery bot: /get_chat_id in chat {} ({})",
                primary.chat().id(),
                primary.chat().title());
        handleGetChatId(primary);
    }

    static Message primaryMessage(Update update) {
        if (update.message() != null) {
            return update.message();
        }
        if (update.editedMessage() != null) {
            return update.editedMessage();
        }
        if (update.channelPost() != null) {
            return update.channelPost();
        }
        return update.editedChannelPost();
    }

    static String messageText(Message message) {
        if (message == null) {
            return null;
        }
        String text = message.text();
        if (text == null || text.isBlank()) {
            text = message.caption();
        }
        return text;
    }

    static boolean isGetChatIdCommand(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String line : text.split("\\R")) {
            if (isGetChatIdCommandLine(line)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGetChatIdCommandLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.equals(GET_CHAT_ID_COMMAND)) {
            return true;
        }
        return trimmed.startsWith(GET_CHAT_ID_COMMAND + "@")
                || trimmed.startsWith(GET_CHAT_ID_COMMAND + " ");
    }

    static boolean isPublicationChat(Chat chat) {
        if (chat == null || chat.type() == null) {
            return false;
        }
        return switch (chat.type()) {
            case group, supergroup, channel -> true;
            default -> false;
        };
    }

    private void handleGetChatId(Message message) {
        Chat chat = message.chat();
        long chatId = chat.id();
        String chatTitle = chat.title() != null && !chat.title().isBlank()
                ? chat.title()
                : "Чат";
        String chatTypeLabel = chatTypeLabel(chat.type());

        String responseText = String.format(
                "📋 <b>Информация о чате</b>%n%n"
                        + "Название: <b>%s</b>%n"
                        + "Тип: <b>%s</b>%n"
                        + "Chat ID: <code>%d</code>%n%n"
                        + "Скопируйте этот ID при добавлении канала в Добровоз.",
                escapeHtml(chatTitle),
                chatTypeLabel,
                chatId
        );

        var response = telegramBot.execute(new SendMessage(chatId, responseText).parseMode(ParseMode.HTML));
        if (!response.isOk()) {
            log.warn("Failed to reply to /get_chat_id in chat {}: {}", chatId, response.description());
        } else {
            log.info("Delivery bot: replied to /get_chat_id in chat {}", chatId);
        }
    }

    private static String chatTypeLabel(Chat.Type type) {
        if (type == null) {
            return "Чат";
        }
        return switch (type) {
            case channel -> "Канал";
            case supergroup -> "Супергруппа";
            case group -> "Группа";
            default -> "Чат";
        };
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
