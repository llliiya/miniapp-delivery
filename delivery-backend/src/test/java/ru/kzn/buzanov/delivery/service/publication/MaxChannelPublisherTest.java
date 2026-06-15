package ru.kzn.buzanov.delivery.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.kzn.buzanov.delivery.config.DeliveryBotProperties;
import ru.kzn.buzanov.delivery.service.DeliveryDeepLinkService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaxChannelPublisherTest {

    @Mock
    private DeliveryDeepLinkService deepLinkService;

    @Mock
    private OrderMessageFormatter messageFormatter;

    private MaxChannelPublisher publisher;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        publisher = new MaxChannelPublisher(
                new DeliveryBotProperties(),
                deepLinkService,
                messageFormatter,
                objectMapper,
                new MaxOpenAppButtons(objectMapper));
    }

    @Test
    void buildMessageBody_withoutButton_clearsAttachments() {
        var body = publisher.buildMessageBody("updated text", null);

        assertThat(body.get("text").asText()).isEqualTo("updated text");
        assertThat(body.has("attachments")).isTrue();
        ArrayNode attachments = (ArrayNode) body.get("attachments");
        assertThat(attachments).isEmpty();
    }

    @Test
    void buildOpenAppMessageBody_usesWebAppAndPayloadFields() {
        when(deepLinkService.maxOrderOpenAppTarget(java.util.UUID.fromString("427effcf-8248-42fa-a836-9faa7eb5ce09")))
                .thenReturn(new DeliveryDeepLinkService.MaxOpenAppTarget("id544601208994_3_bot", "delivery_order_x"));

        var body = publisher.buildOpenAppMessageBody(
                "order text",
                new DeliveryDeepLinkService.MaxOpenAppTarget("id544601208994_3_bot", "delivery_order_x"),
                "Взять");

        var button = body.get("attachments").get(0).get("payload").get("buttons").get(0).get(0);
        assertThat(button.get("type").asText()).isEqualTo("open_app");
        assertThat(button.get("web_app").asText()).isEqualTo("id544601208994_3_bot");
        assertThat(button.get("payload").asText()).isEqualTo("delivery_order_x");
        assertThat(button.has("webApp")).isFalse();
    }
}
