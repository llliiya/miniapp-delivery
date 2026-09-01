package ru.kzn.buzanov.delivery.config;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.DeleteWebhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru.kzn.buzanov.delivery.bot.DeliveryTelegramUpdateService;

import java.util.concurrent.CompletableFuture;

import static com.pengrad.telegrambot.UpdatesListener.CONFIRMED_UPDATES_ALL;

/**
 * Long polling только для dev: Telegram не требует публичный HTTPS endpoint.
 * Не блокирует старт приложения и не падает, если api.telegram.org недоступен.
 */
@Component
@Profile("dev")
@Slf4j
public class DeliveryTelegramPollingStarter implements ApplicationListener<ApplicationReadyEvent> {

    private final ObjectProvider<TelegramBot> telegramBot;
    private final DeliveryBotProperties properties;
    private final DeliveryTelegramUpdateService updateService;

    public DeliveryTelegramPollingStarter(
            ObjectProvider<TelegramBot> telegramBot,
            DeliveryBotProperties properties,
            DeliveryTelegramUpdateService updateService) {
        this.telegramBot = telegramBot;
        this.properties = properties;
        this.updateService = updateService;
    }

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        if (!properties.getTelegram().isPollingEnabled()) {
            return;
        }
        CompletableFuture.runAsync(this::startPollingSafely);
    }

    private void startPollingSafely() {
        try {
            startPolling();
        } catch (Exception e) {
            log.warn("Delivery bot: polling startup aborted: {}", e.getMessage());
        }
    }

    private void startPolling() {
        TelegramBot bot = telegramBot.getIfAvailable();
        if (bot == null) {
            log.warn("Delivery bot: polling enabled but Telegram bot is not configured");
            return;
        }

        try {
            log.info("Delivery bot: deleting webhook and starting Telegram long polling");
            var deleteResponse = bot.execute(new DeleteWebhook().dropPendingUpdates(false));
            if (!deleteResponse.isOk()) {
                log.warn(
                        "Delivery bot: DeleteWebhook failed code={} desc={}",
                        deleteResponse.errorCode(),
                        deleteResponse.description());
            }
        } catch (Exception e) {
            log.warn("Delivery bot: DeleteWebhook failed, skip polling start: {}", e.getMessage());
            return;
        }

        try {
            bot.setUpdatesListener(updates -> {
                for (Update update : updates) {
                    try {
                        updateService.handleUpdate(update);
                    } catch (Exception e) {
                        log.warn("Delivery bot: polling update failed: {}", e.getMessage(), e);
                    }
                }
                return CONFIRMED_UPDATES_ALL;
            });
            log.info("Delivery bot: Telegram long polling started");
        } catch (Exception e) {
            log.warn("Delivery bot: failed to start Telegram long polling: {}", e.getMessage());
        }
    }
}
