package ru.kzn.buzanov.delivery.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.kzn.buzanov.delivery.service.DeliveryDeepLinkService;

@Component
@RequiredArgsConstructor
public class MaxOpenAppButtons {

    private final ObjectMapper objectMapper;

    public ObjectNode openAppButton(String text, DeliveryDeepLinkService.MaxOpenAppTarget target) {
        ObjectNode btn = objectMapper.createObjectNode();
        btn.put("type", "open_app");
        btn.put("text", text);
        btn.put("web_app", target.webApp());
        if (target.payload() != null && !target.payload().isBlank()) {
            btn.put("payload", target.payload());
        }
        return btn;
    }

    public static boolean isWebAppNullError(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("webapp") && lower.contains("cannot be null");
    }
}
