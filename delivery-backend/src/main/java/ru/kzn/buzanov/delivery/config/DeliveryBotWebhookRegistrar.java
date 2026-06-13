package ru.kzn.buzanov.delivery.config;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SetWebhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Регистрация Telegram webhook для delivery-бота после старта приложения.
 */
@Component
@Slf4j
public class DeliveryBotWebhookRegistrar implements ApplicationListener<ApplicationReadyEvent> {

    private final ObjectProvider<TelegramBot> telegramBot;
    private final DeliveryBotProperties properties;

    public DeliveryBotWebhookRegistrar(ObjectProvider<TelegramBot> telegramBot, DeliveryBotProperties properties) {
        this.telegramBot = telegramBot;
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        CompletableFuture.runAsync(this::registerTelegramWebhook);
    }

    private void registerTelegramWebhook() {
        if (properties.getTelegram().isPollingEnabled()) {
            log.info("Delivery bot: polling enabled, skip SetWebhook");
            return;
        }
        TelegramBot bot = telegramBot.getIfAvailable();
        if (bot == null) {
            return;
        }
        String publicUrl = resolvePublicApiUrl();
        if (publicUrl == null || publicUrl.isBlank()) {
            log.warn("Delivery bot: public API URL is empty, skip SetWebhook");
            return;
        }
        if (!publicUrl.startsWith("https://")) {
            log.warn("Delivery bot: public API URL must be HTTPS for Telegram webhook: {}", publicUrl);
            return;
        }

        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        String webhookUrl = base + "/api/delivery/bot/webhook";
        log.info("Delivery bot: registering Telegram webhook url={}", webhookUrl);
        try {
            var response = bot.execute(new SetWebhook()
                    .url(webhookUrl)
                    .allowedUpdates(
                            "message",
                            "edited_message",
                            "channel_post",
                            "edited_channel_post"));
            if (!response.isOk()) {
                log.error(
                        "Delivery bot: SetWebhook failed code={} desc={}",
                        response.errorCode(),
                        response.description());
            } else {
                log.info("Delivery bot: SetWebhook OK");
            }
        } catch (Exception e) {
            log.error("Delivery bot: webhook registration failed: {}", e.getMessage());
        }
    }

    private String resolvePublicApiUrl() {
        String configured = properties.getPublicApiUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String frontendUrl = properties.getFrontendUrl();
        return frontendUrl == null ? "" : frontendUrl.trim();
    }
}
