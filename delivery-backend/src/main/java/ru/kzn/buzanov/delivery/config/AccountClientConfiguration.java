package ru.kzn.buzanov.delivery.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AccountClientProperties.class)
public class AccountClientConfiguration {

    @Bean
    RestClient accountRestClient(AccountClientProperties properties) {
        String baseUrl = properties.baseUrl() != null ? properties.baseUrl().trim() : "";
        RestClient.Builder builder = RestClient.builder();
        if (!baseUrl.isBlank()) {
            builder = builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
