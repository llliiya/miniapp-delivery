package ru.kzn.buzanov.delivery.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.config.AccountClientProperties;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccountUserClient {

    private static final String HEADER_INTERNAL_KEY = "X-Internal-Key";

    private final RestClient accountRestClient;
    private final AccountClientProperties properties;

    public Optional<Long> findUserIdByExternalIdentity(String provider, String externalId) {
        if (provider == null || provider.isBlank() || externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        String key = properties.internalKey() != null ? properties.internalKey().trim() : "";
        if (key.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Long> body = accountRestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/internal/monolith/users/external-lookup")
                            .queryParam("provider", provider.trim())
                            .queryParam("externalId", externalId.trim())
                            .build())
                    .header(HEADER_INTERNAL_KEY, key)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null || body.get("userId") == null) {
                return Optional.empty();
            }
            return Optional.of(body.get("userId"));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public Optional<AccountUserContacts> findUserContacts(Long userId) {
        if (userId == null || userId <= 0) {
            return Optional.empty();
        }
        String key = properties.internalKey() != null ? properties.internalKey().trim() : "";
        if (key.isBlank()) {
            return Optional.empty();
        }
        try {
            AccountUserContacts contacts = accountRestClient
                    .get()
                    .uri("/api/internal/monolith/users/{userId}/contacts", userId)
                    .header(HEADER_INTERNAL_KEY, key)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(AccountUserContacts.class);
            return Optional.ofNullable(contacts);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public void linkMessengerIdentity(Long userId, String provider, String externalId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите корректный ID пользователя");
        }
        if (provider == null || provider.isBlank() || externalId == null || externalId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите provider и externalId");
        }
        String key = properties.internalKey() != null ? properties.internalKey().trim() : "";
        if (key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Account internal key not configured");
        }
        try {
            accountRestClient
                    .post()
                    .uri("/api/internal/monolith/users/{userId}/identities/link", userId)
                    .header(HEADER_INTERNAL_KEY, key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("provider", provider.trim().toUpperCase(), "externalId", externalId.trim()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Messenger identity already linked to another user");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось привязать messenger аккаунт");
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось привязать messenger аккаунт");
        }
    }

    public void requireUserExists(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите корректный ID пользователя");
        }
        String key = properties.internalKey() != null ? properties.internalKey().trim() : "";
        if (key.isBlank()) {
            return;
        }
        String baseUrl = properties.baseUrl() != null ? properties.baseUrl().trim() : "";
        if (baseUrl.isBlank()) {
            return;
        }
        try {
            accountRestClient
                    .get()
                    .uri("/api/internal/monolith/users/{userId}/row", userId)
                    .header(HEADER_INTERNAL_KEY, key)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Пользователь с таким ID не найден");
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Не удалось проверить пользователя в account");
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Не удалось проверить пользователя в account");
        }
    }
}
