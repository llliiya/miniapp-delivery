package ru.kzn.buzanov.delivery.config;

import com.pengrad.telegrambot.TelegramBot;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeliveryBotProperties.class)
public class DeliveryBotConfiguration {

    @Bean
    public TelegramBot deliveryTelegramBot(DeliveryBotProperties properties) {
        String token = properties.getTelegram().getBotToken();
        if (token == null || token.isBlank()) {
            return null;
        }
        return new TelegramBot(token.trim());
    }
}
