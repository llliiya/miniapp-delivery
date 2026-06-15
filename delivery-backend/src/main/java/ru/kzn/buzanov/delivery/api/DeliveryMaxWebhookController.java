package ru.kzn.buzanov.delivery.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.bot.DeliveryMaxUpdateService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/max")
@RequiredArgsConstructor
public class DeliveryMaxWebhookController {

    private final DeliveryMaxUpdateService updateService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> onWebhook(@RequestBody(required = false) Map<String, Object> payload) {
        log.info("Delivery MAX webhook received, keys={}", payload != null ? payload.keySet() : "null");
        try {
            updateService.handle(payload);
        } catch (Exception e) {
            log.warn("Delivery MAX webhook handling failed: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }
}
