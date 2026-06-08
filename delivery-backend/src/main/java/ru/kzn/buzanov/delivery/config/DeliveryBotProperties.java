package ru.kzn.buzanov.delivery.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "delivery")
public class DeliveryBotProperties {

    private String frontendUrl = "http://localhost:5174";

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
    }

    @Getter
    @Setter
    public static class Max {
        private String botToken = "";
        private String botUsername = "";
        private String apiBaseUrl = "https://platform-api.max.ru";
    }
}
