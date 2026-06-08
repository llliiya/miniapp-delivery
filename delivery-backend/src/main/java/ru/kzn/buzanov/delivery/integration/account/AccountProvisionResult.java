package ru.kzn.buzanov.delivery.integration.account;

import java.util.UUID;

public record AccountProvisionResult(
        Long userId,
        UUID accountId,
        String login,
        String temporaryPassword,
        boolean passwordSetByManager
) {
}
