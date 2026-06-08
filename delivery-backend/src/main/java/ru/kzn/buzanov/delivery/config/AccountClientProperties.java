package ru.kzn.buzanov.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minapp.account")
public record AccountClientProperties(
        String baseUrl,
        String internalKey
) {
}
