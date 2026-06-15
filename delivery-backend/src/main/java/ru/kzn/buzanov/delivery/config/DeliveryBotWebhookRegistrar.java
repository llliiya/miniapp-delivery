package ru.kzn.buzanov.delivery.config;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SetWebhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import ru.kzn.buzanov.delivery.bot.DeliveryMaxBotClient;

import java.util.concurrent.CompletableFuture;

/**
 * Регистрация Telegram webhook для delivery-бота после старта приложения.
 */
@Component
@Slf4j
public class DeliveryBotWebhookRegistrar implements ApplicationListener<ApplicationReadyEvent> {

    private final ObjectProvider<TelegramBot> telegramBot;
    private final DeliveryBotProperties properties;
    private final DeliveryMaxBotClient maxBotClient;

    public DeliveryBotWebhookRegistrar(
            ObjectProvider<TelegramBot> telegramBot,
            DeliveryBotProperties properties,
            DeliveryMaxBotClient maxBotClient) {
        this.telegramBot = telegramBot;
        this.properties = properties;
        this.maxBotClient = maxBotClient;
    }

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        CompletableFuture.runAsync(this::registerTelegramWebhook);
        CompletableFuture.runAsync(this::registerMaxWebhook);
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

    private void registerMaxWebhook() {
        String token = properties.getMax().getBotToken();
        if (token == null || token.isBlank()) {
            log.info("Delivery MAX bot: token is empty, skip webhook registration");
            return;
        }
        String publicUrl = resolvePublicApiUrl();
        if (publicUrl == null || publicUrl.isBlank()) {
            log.warn("Delivery MAX bot: public API URL is empty, skip webhook registration");
            return;
        }
        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        String webhookUrl = base + "/api/max/webhook";
        log.info("Delivery MAX bot: registering webhook url={}", webhookUrl);
        if (!maxBotClient.registerWebhook(webhookUrl)) {
            log.warn("Delivery MAX bot: webhook registration failed");
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
