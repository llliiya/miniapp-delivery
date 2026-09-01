package ru.kzn.buzanov.delivery.config;

import com.pengrad.telegrambot.TelegramBot;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(DeliveryBotProperties.class)
public class DeliveryBotConfiguration {

    private static final int CONNECT_TIMEOUT_SEC = 10;
    private static final int READ_TIMEOUT_SEC = 15;
    private static final int WRITE_TIMEOUT_SEC = 15;

    @Bean
    public TelegramBot deliveryTelegramBot(DeliveryBotProperties properties) {
        String token = properties.getTelegram().getBotToken();
        if (token == null || token.isBlank()) {
            return null;
        }
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build();
        return new TelegramBot.Builder(token.trim()).okHttpClient(httpClient).build();
    }
}
