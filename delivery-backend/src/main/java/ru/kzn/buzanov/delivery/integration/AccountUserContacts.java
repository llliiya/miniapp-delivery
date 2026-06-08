package ru.kzn.buzanov.delivery.integration;

public record AccountUserContacts(
        Long userId,
        String telegramId,
        String maxId,
        String email
) {
}
