package ru.kzn.buzanov.delivery.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.kzn.buzanov.delivery.config.DeliveryBotProperties;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryMaxBotClient {

    private final DeliveryBotProperties properties;
    private final ObjectMapper objectMapper;

    public boolean registerWebhook(String webhookUrl) {
        String token = properties.getMax().getBotToken();
        if (token == null || token.isBlank()) {
            log.warn("Delivery MAX bot: token is empty, skip webhook registration");
            return false;
        }
        if (webhookUrl == null || !webhookUrl.startsWith("https://")) {
            log.warn("Delivery MAX bot: invalid webhook url (must be HTTPS): {}", webhookUrl);
            return false;
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("url", webhookUrl);
            ArrayNode types = objectMapper.createArrayNode();
            types.add("message_created");
            types.add("bot_added");
            body.set("update_types", types);
            RestClient client = restClient(token);
            String response = client.post()
                    .uri("/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            log.info("Delivery MAX bot: webhook registration response: {}", response);
            return true;
        } catch (Exception e) {
            log.error("Delivery MAX bot: webhook registration failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean sendHtmlMessage(long chatId, String htmlText) {
        String token = properties.getMax().getBotToken();
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("text", htmlText);
            body.put("format", "html");
            RestClient client = restClient(token);
            client.post()
                    .uri(uriBuilder -> uriBuilder.path("/messages").queryParam("chat_id", chatId).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Delivery MAX bot: send message to chat {} failed: {}", chatId, e.getMessage());
            return false;
        }
    }

    private RestClient restClient(String token) {
        String base = trimSlash(properties.getMax().getApiBaseUrl());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .baseUrl(base)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, token.trim())
                .build();
    }

    private static String trimSlash(String url) {
        String s = url == null ? "" : url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
