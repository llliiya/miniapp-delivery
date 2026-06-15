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
import org.springframework.web.client.RestClientResponseException;
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
    private final MaxOpenAppButtons maxOpenAppButtons;

    public ChannelPublishResult publishOrder(PublicationChannel channel, DeliveryOrder order) {
        String token = properties.getMax().getBotToken();
        if (token == null || token.isBlank()) {
            return ChannelPublishResult.fail("MAX bot not configured");
        }
        try {
            String text = messageFormatter.formatOrderCardPlain(order, null);
            if (order.getStatus() == OrderStatus.waiting_for_courier && order.getCourierUserId() == null) {
                DeliveryDeepLinkService.MaxOpenAppTarget target = deepLinkService.maxOrderOpenAppTarget(order.getId());
                log.info(
                        "MAX order button: type=open_app orderId={} web_app={} payload={}",
                        order.getId(),
                        target.webApp(),
                        target.payload());
                ObjectNode body = buildOpenAppMessageBody(text, target, "🚚 Взять заказ");
                ChannelPublishResult result = send(channel.getExternalId(), token, body, order.getPublicNumber());
                if (!result.success() && MaxOpenAppButtons.isWebAppNullError(result.errorMessage())) {
                    String linkUrl = deepLinkService.maxButtonUrl(order.getId());
                    log.warn(
                            "MAX open_app rejected for order #{}; fallback to link url={}",
                            order.getPublicNumber(),
                            linkUrl);
                    body = buildLinkMessageBody(text, linkUrl, "🚚 Взять заказ");
                    return send(channel.getExternalId(), token, body, order.getPublicNumber());
                }
                return result;
            }
            return send(channel.getExternalId(), token, buildMessageBody(text, null), order.getPublicNumber());
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
        if (messageId == null || messageId.isBlank() || "sent".equalsIgnoreCase(messageId.trim())) {
            return ChannelEditResult.fail("message id is empty or invalid");
        }
        try {
            String text = messageFormatter.formatOrderCardPlain(order, courierName);
            DeliveryDeepLinkService.MaxOpenAppTarget target = null;
            if (order.getStatus() == OrderStatus.waiting_for_courier && order.getCourierUserId() == null) {
                target = deepLinkService.maxOrderOpenAppTarget(order.getId());
                log.info(
                        "MAX order button edit: type=open_app orderId={} web_app={} payload={}",
                        order.getId(),
                        target.webApp(),
                        target.payload());
            }
            ObjectNode body = target != null
                    ? buildOpenAppMessageBody(text, target, "🚚 Взять заказ")
                    : buildMessageBody(text, null);
            String resolvedChatId = chatId != null ? chatId : channel.getExternalId();
            log.info(
                    "MAX editMessage: order #{} channel={} chatId={} messageId={} status={} hasButton={}",
                    order.getPublicNumber(),
                    channel.getName(),
                    resolvedChatId,
                    messageId.trim(),
                    order.getStatus(),
                    target != null);
            String responseBody = putMessage(token, messageId.trim(), body);
            return parseEditResult(responseBody, order.getPublicNumber());
        } catch (RestClientResponseException e) {
            String err = formatHttpError(e);
            log.warn("MAX editMessage HTTP error for order #{}: {}", order.getPublicNumber(), err);
            return ChannelEditResult.fail(err);
        } catch (Exception e) {
            log.warn("MAX editMessage failed for order #{}: {}", order.getPublicNumber(), e.getMessage());
            return ChannelEditResult.fail(e.getMessage());
        }
    }

    private ChannelPublishResult send(String chatId, String token, ObjectNode body, Long publicNumber) {
        long cid = Long.parseLong(chatId.trim());
        String base = trimSlash(properties.getMax().getApiBaseUrl());
        RestClient client = RestClient.builder().baseUrl(base).build();
        try {
            String responseBody = client.post()
                    .uri(uriBuilder -> uriBuilder.path("/messages").queryParam("chat_id", cid).build())
                    .header(HttpHeaders.AUTHORIZATION, token.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            String messageId = extractMessageId(responseBody);
            if (messageId != null) {
                log.info("MAX publish order #{}: messageId={}", publicNumber, messageId);
                return ChannelPublishResult.ok(messageId);
            }
            log.warn("MAX publish order #{}: message id missing in response: {}", publicNumber, abbreviate(responseBody));
            return ChannelPublishResult.fail("MAX response missing message id");
        } catch (RestClientResponseException e) {
            String err = formatHttpError(e);
            log.warn("MAX publish order #{} failed: {}", publicNumber, err);
            return ChannelPublishResult.fail(err);
        }
    }

    private String putMessage(String token, String messageId, ObjectNode body) {
        String base = trimSlash(properties.getMax().getApiBaseUrl());
        RestClient client = RestClient.builder().baseUrl(base).build();
        return client.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/messages")
                        .queryParam("message_id", messageId)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, token.trim())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);
    }

    private ChannelEditResult parseEditResult(String responseBody, Long publicNumber) {
        if (responseBody == null || responseBody.isBlank()) {
            return ChannelEditResult.ok();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode success = root.path("success");
            if (!success.isMissingNode() && !success.isNull()) {
                if (success.asBoolean()) {
                    log.info("MAX editMessage order #{}: success=true", publicNumber);
                    return ChannelEditResult.ok();
                }
                String message = root.path("message").asText("MAX edit failed");
                log.warn("MAX editMessage order #{}: success=false message={}", publicNumber, message);
                return ChannelEditResult.fail(message);
            }
        } catch (Exception e) {
            log.warn("MAX editMessage order #{}: could not parse response: {}", publicNumber, abbreviate(responseBody));
        }
        return ChannelEditResult.ok();
    }

    ObjectNode buildOpenAppMessageBody(String text, DeliveryDeepLinkService.MaxOpenAppTarget target, String buttonLabel) {
        ObjectNode openAppBtn = maxOpenAppButtons.openAppButton(buttonLabel, target);
        ArrayNode row = objectMapper.createArrayNode().add(openAppBtn);
        ArrayNode rows = objectMapper.createArrayNode().add(row);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("buttons", rows);
        ObjectNode attachment = objectMapper.createObjectNode();
        attachment.put("type", "inline_keyboard");
        attachment.set("payload", payload);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("text", text);
        root.set("attachments", objectMapper.createArrayNode().add(attachment));
        return root;
    }

    ObjectNode buildLinkMessageBody(String text, String url, String buttonLabel) {
        ObjectNode linkBtn = objectMapper.createObjectNode();
        linkBtn.put("type", "link");
        linkBtn.put("text", buttonLabel);
        linkBtn.put("url", url);
        ArrayNode row = objectMapper.createArrayNode().add(linkBtn);
        ArrayNode rows = objectMapper.createArrayNode().add(row);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("buttons", rows);
        ObjectNode attachment = objectMapper.createObjectNode();
        attachment.put("type", "inline_keyboard");
        attachment.set("payload", payload);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("text", text);
        root.set("attachments", objectMapper.createArrayNode().add(attachment));
        return root;
    }

    /**
     * MAX API: чтобы убрать inline-кнопки при edit, нужно передать {@code attachments: []}.
     */
    ObjectNode buildMessageBody(String text, DeliveryDeepLinkService.MaxOpenAppTarget target) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("text", text);
        if (target != null) {
            return buildOpenAppMessageBody(text, target, "🚚 Взять заказ");
        }
        root.set("attachments", objectMapper.createArrayNode());
        return root;
    }

    private String extractMessageId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode mid = root.path("message").path("body").path("mid");
            if (!mid.isMissingNode() && !mid.isNull() && !mid.asText().isBlank()) {
                return mid.asText();
            }
            mid = root.path("message").path("mid");
            if (!mid.isMissingNode() && !mid.isNull() && !mid.asText().isBlank()) {
                return mid.asText();
            }
        } catch (Exception ignored) {
            // ignore parse errors
        }
        return null;
    }

    private static String formatHttpError(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body != null && !body.isBlank()) {
            return e.getStatusCode().value() + " " + e.getStatusText() + ": " + abbreviate(body);
        }
        return e.getMessage();
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300) + "...";
    }

    private static String trimSlash(String url) {
        String s = url == null ? "" : url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
