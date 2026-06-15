package ru.kzn.buzanov.delivery.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "delivery")
public class DeliveryBotProperties {

    private String frontendUrl = "http://localhost:5173";
    /** Публичный URL фронта/API для webhook Telegram. По умолчанию — frontend-url. */
    private String publicApiUrl = "";

    private Telegram telegram = new Telegram();
    private Max max = new Max();

    @Getter
    @Setter
    public static class Telegram {
        private String botToken = "";
        private String botUsername = "";
        /** Короткое имя Direct Link Mini App в BotFather (опционально). */
        private String miniAppShortName = "";
        /** Chat ID администратора для уведомлений о заявках курьеров. */
        private String adminChatId = "";
        /**
         * Long polling вместо webhook (удобно для локальной разработки без публичного HTTPS).
         */
        private boolean pollingEnabled = false;
    }

    @Getter
    @Setter
    public static class Max {
        private String botToken = "";
        private String botUsername = "";
        private String apiBaseUrl = "https://platform-api.max.ru";
    }
}
