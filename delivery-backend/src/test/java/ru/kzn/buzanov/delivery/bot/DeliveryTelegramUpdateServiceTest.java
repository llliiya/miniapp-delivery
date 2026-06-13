package ru.kzn.buzanov.delivery.bot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTelegramUpdateServiceTest {

    @Test
    void isGetChatIdCommand_acceptsPlainAndMentionedForms() {
        assertThat(DeliveryTelegramUpdateService.isGetChatIdCommand("/get_chat_id")).isTrue();
        assertThat(DeliveryTelegramUpdateService.isGetChatIdCommand("/get_chat_id@dobrovoz_bot")).isTrue();
        assertThat(DeliveryTelegramUpdateService.isGetChatIdCommand("/get_chat_id extra")).isTrue();
        assertThat(DeliveryTelegramUpdateService.isGetChatIdCommand("тест доставка Казань\n/get_chat_id")).isTrue();
        assertThat(DeliveryTelegramUpdateService.isGetChatIdCommand("/start")).isFalse();
        assertThat(DeliveryTelegramUpdateService.isGetChatIdCommand(null)).isFalse();
    }
}
