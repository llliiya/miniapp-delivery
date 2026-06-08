package ru.kzn.buzanov.delivery.service.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.kzn.buzanov.delivery.config.DeliveryBotProperties;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.OrderStatus;
import ru.kzn.buzanov.delivery.domain.PublicationChannel;
import ru.kzn.buzanov.delivery.service.DeliveryDeepLinkService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MaxChannelPublisher {

    private final DeliveryBotProperties properties;
    private final DeliveryDeepLinkService deepLinkService;
    private final OrderMessageFormatter messageFormatter;
    private final ObjectMapper objectMapper;

    public ChannelPublishResult publishOrder(PublicationChannel channel, DeliveryOrder order) {
        String token = properties.getMax().getBotToken();
        if (token == null || token.isBlank()) {
            return ChannelPublishResult.fail("MAX bot not configured");
        }
        try {
            String text = messageFormatter.formatOrderCardPlain(order, null);
            String buttonUrl = order.getStatus() == OrderStatus.waiting_for_courier && order.getCourierUserId() == null
                    ? deepLinkService.maxButtonUrl(order.getId())
                    : null;
            String buttonLabel = buttonUrl != null ? "🚚 Взять заказ" : null;
            ObjectNode body = buildMessageBody(text, buttonUrl, buttonLabel);
            return send(channel.getExternalId(), token, body);
        } catch (Exception e) {
            return ChannelPublishResult.fail(e.getMessage());
        }
    }

    public ChannelEditResult editOrder(
            PublicationChannel channel,
            String chatId,
            String messageId,
            DeliveryOrder order,
            String courierName) {
        String token = properties.getMax().getBotToken();
        if (token == null || token.isBlank()) {
            return ChannelEditResult.fail("MAX bot not configured");
        }
        if (messageId == null || messageId.isBlank()) {
            return ChannelEditResult.fail("message id is empty");
        }
        try {
            String text = messageFormatter.formatOrderCardPlain(order, courierName);
            String buttonUrl = order.getStatus() == OrderStatus.waiting_for_courier && order.getCourierUserId() == null
                    ? deepLinkService.maxButtonUrl(order.getId())
                    : null;
            String buttonLabel = buttonUrl != null ? "🚚 Взять заказ" : null;
            ObjectNode body = buildMessageBody(text, buttonUrl, buttonLabel);
            long cid = Long.parseLong((chatId != null ? chatId : channel.getExternalId()).trim());
            String base = trimSlash(properties.getMax().getApiBaseUrl());
            RestClient client = RestClient.builder().baseUrl(base).build();
            client.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/messages")
                            .queryParam("chat_id", cid)
                            .queryParam("message_id", messageId.trim())
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, token.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .toBodilessEntity();
            return ChannelEditResult.ok();
        } catch (Exception e) {
            log.warn("MAX editMessage failed for order #{}: {}", order.getPublicNumber(), e.getMessage());
            return ChannelEditResult.fail(e.getMessage());
        }
    }

    private ChannelPublishResult send(String chatId, String token, ObjectNode body) {
        long cid = Long.parseLong(chatId.trim());
        String base = trimSlash(properties.getMax().getApiBaseUrl());
        RestClient client = RestClient.builder().baseUrl(base).build();
        String responseBody = client.post()
                .uri(uriBuilder -> uriBuilder.path("/messages").queryParam("chat_id", cid).build())
                .header(HttpHeaders.AUTHORIZATION, token.trim())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);
        String messageId = extractMessageId(responseBody);
        if (messageId != null) {
            return ChannelPublishResult.ok(messageId);
        }
        return ChannelPublishResult.ok("sent");
    }

    private ObjectNode buildMessageBody(String text, String buttonUrl, String buttonLabel) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("text", text);
        if (buttonUrl != null && buttonLabel != null) {
            ObjectNode linkBtn = objectMapper.createObjectNode();
            linkBtn.put("type", "link");
            linkBtn.put("url", buttonUrl);
            linkBtn.put("text", buttonLabel);
            ArrayNode row = objectMapper.createArrayNode().add(linkBtn);
            ArrayNode rows = objectMapper.createArrayNode().add(row);
            ObjectNode payload = objectMapper.createObjectNode();
            payload.set("buttons", rows);
            ObjectNode attachment = objectMapper.createObjectNode();
            attachment.put("type", "inline_keyboard");
            attachment.set("payload", payload);
            root.set("attachments", objectMapper.createArrayNode().add(attachment));
        }
        return root;
    }

    private String extractMessageId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("message");
            JsonNode mid = message.path("body").path("mid");
            if (!mid.isMissingNode() && !mid.isNull()) {
                return mid.asText();
            }
        } catch (Exception ignored) {
            // ignore parse errors
        }
        return null;
    }

    private static String trimSlash(String url) {
        String s = url == null ? "" : url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
