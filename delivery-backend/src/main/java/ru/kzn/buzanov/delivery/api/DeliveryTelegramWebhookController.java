package ru.kzn.buzanov.delivery.api;

import com.pengrad.telegrambot.model.Update;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.bot.DeliveryTelegramUpdateService;

@Slf4j
@RestController
@RequestMapping("/bot")
@RequiredArgsConstructor
public class DeliveryTelegramWebhookController {

    private final DeliveryTelegramUpdateService updateService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> onUpdate(@RequestBody Update update) {
        try {
            updateService.handleUpdate(update);
        } catch (Exception e) {
            log.warn("Telegram webhook handling failed: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }
}
