package ru.kzn.buzanov.delivery.web;

import java.util.List;
import java.util.UUID;

/**
 * Authenticated caller from JWT.
 * {@code organizationId} is present on restaurant-admin access tokens; courier tokens may omit it.
 */
public record CurrentUser(Long userId, List<String> roles, UUID organizationId) {

    public CurrentUser(Long userId, List<String> roles) {
        this(userId, roles, null);
    }
}
