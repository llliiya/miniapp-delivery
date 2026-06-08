package ru.kzn.buzanov.delivery.service.notification;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.kzn.buzanov.delivery.config.DeliveryBotProperties;
import ru.kzn.buzanov.delivery.domain.CourierRequest;

@Slf4j
@Service
public class CourierRequestNotificationService {

    private final TelegramBot telegramBot;
    private final DeliveryBotProperties properties;

    public CourierRequestNotificationService(
            @Autowired(required = false) TelegramBot telegramBot,
            DeliveryBotProperties properties) {
        this.telegramBot = telegramBot;
        this.properties = properties;
    }

    public void notifyAdminNewRequest(CourierRequest request) {
        String adminChatId = properties.getTelegram().getAdminChatId();
        if (adminChatId == null || adminChatId.isBlank()) {
            log.info("Admin chat id not configured; skip courier request notification");
            return;
        }
        if (telegramBot == null) {
            log.warn("Telegram bot not configured; skip courier request notification");
            return;
        }
        try {
            String text = formatAdminMessage(request);
            SendResponse response = sendAdminMessage(adminChatId.trim(), text);
            if (!response.isOk()) {
                log.warn("Failed to notify admin about courier request: {}", response.description());
            }
        } catch (Exception e) {
            log.warn("Failed to notify admin about courier request: {}", e.getMessage());
        }
    }

    private SendResponse sendAdminMessage(String adminChatId, String text) {
        if (adminChatId.startsWith("@")) {
            return telegramBot.execute(new SendMessage(adminChatId, text));
        }
        long chatId = Long.parseLong(adminChatId);
        return telegramBot.execute(new SendMessage(chatId, text));
    }

    private static String formatAdminMessage(CourierRequest request) {
        StringBuilder sb = new StringBuilder();
        boolean messenger = request.getMessengerExternalId() != null && !request.getMessengerExternalId().isBlank();
        sb.append(messenger ? "Новая заявка курьера (мессенджер)\n\n" : "Новая заявка на доступ (веб)\n\n");
        sb.append("Имя:\n").append(request.getFullName()).append("\n\n");
        sb.append("Телефон:\n").append(request.getPhone()).append("\n\n");
        sb.append("Город:\n").append(request.getCity());
        if (request.getTransport() != null && !request.getTransport().isBlank()) {
            sb.append("\n\nТранспорт:\n").append(request.getTransport());
        }
        if (messenger) {
            sb.append("\n\n").append(request.getMessengerProvider()).append(" ID:\n")
                    .append(request.getMessengerExternalId());
            String username = request.getMessengerUsername();
            sb.append("\n\nUsername:\n")
                    .append(username != null && !username.isBlank() ? "@" + username.replace("@", "") : "—");
        }
        if (request.getComment() != null && !request.getComment().isBlank()) {
            sb.append("\n\nКомментарий:\n").append(request.getComment());
        }
        return sb.toString();
    }
}
