package ru.kzn.buzanov.delivery.bot;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryMaxUpdateServiceTest {

    @Test
    void isPublicationChatType_acceptsChannelAndGroup() {
        assertThat(DeliveryMaxUpdateService.isPublicationChatType("channel", 100L, Map.of())).isTrue();
        assertThat(DeliveryMaxUpdateService.isPublicationChatType("group", 100L, Map.of())).isTrue();
        assertThat(DeliveryMaxUpdateService.isPublicationChatType("chat", 100L, Map.of())).isTrue();
        assertThat(DeliveryMaxUpdateService.isPublicationChatType("user", 100L, Map.of())).isFalse();
        assertThat(DeliveryMaxUpdateService.isPublicationChatType(null, null, Map.of())).isFalse();
    }

    @Test
    void isPrivateDialog_detectsDialogRecipient() {
        Map<String, Object> payload = Map.of(
                "message", Map.of(
                        "recipient", Map.of(
                                "chat_type", "dialog",
                                "user_id", 123
                        )
                )
        );
        assertThat(DeliveryMaxUpdateService.isPrivateDialog("dialog", payload)).isTrue();
        assertThat(DeliveryMaxUpdateService.isPublicationChatType("dialog", 1L, payload)).isFalse();
    }
}
