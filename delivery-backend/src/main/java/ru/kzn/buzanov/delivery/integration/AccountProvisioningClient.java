package ru.kzn.buzanov.delivery.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.config.AccountClientProperties;
import ru.kzn.buzanov.delivery.integration.account.AccountProvisionRequest;
import ru.kzn.buzanov.delivery.integration.account.AccountProvisionResult;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountProvisioningClient {

    private static final String HEADER_INTERNAL_KEY = "X-Internal-Key";
    private static final String PROVISION_PATH = "/api/internal/monolith/users/provision/web-employee";
    private static final String RESET_CREDENTIALS_PATH_TEMPLATE =
            "/api/internal/monolith/users/%d/reset-web-credentials";

    private final RestClient accountRestClient;
    private final AccountClientProperties properties;

    /**
     * Commits in account service immediately. Callers must handle delivery-side rollback orphans
     * (see RestaurantService.createWithOwnerProvisioning TECH-DEBT).
     */
    public AccountProvisionResult provisionWebEmployee(AccountProvisionRequest body) {
        requireConfigured();
        try {
            AccountProvisionResult result = accountRestClient
                    .post()
                    .uri(PROVISION_PATH)
                    .header(HEADER_INTERNAL_KEY, properties.internalKey().trim())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(AccountProvisionResult.class);
            if (result == null || result.userId() == null) {
                throw provisioningFailed();
            }
            log.info("Account user provisioned for delivery: userId={}, login={}", result.userId(), result.login());
            return result;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapProvisionError(ex);
        } catch (Exception ex) {
            log.warn("Account provisioning failed: {}", ex.getMessage());
            throw provisioningFailed();
        }
    }

    public AccountProvisionResult resetWebCredentials(long userId) {
        requireConfigured();
        try {
            AccountProvisionResult result = accountRestClient
                    .post()
                    .uri(RESET_CREDENTIALS_PATH_TEMPLATE.formatted(userId))
                    .header(HEADER_INTERNAL_KEY, properties.internalKey().trim())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(AccountProvisionResult.class);
            if (result == null || result.userId() == null || result.temporaryPassword() == null) {
                throw provisioningFailed();
            }
            log.info("Account credentials reset for delivery: userId={}, login={}", result.userId(), result.login());
            return result;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapProvisionError(ex);
        } catch (Exception ex) {
            log.warn("Account credentials reset failed: {}", ex.getMessage());
            throw provisioningFailed();
        }
    }

    private void requireConfigured() {
        String key = properties.internalKey() != null ? properties.internalKey().trim() : "";
        String baseUrl = properties.baseUrl() != null ? properties.baseUrl().trim() : "";
        if (key.isBlank() || baseUrl.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Не удалось создать пользователя. Попробуйте позже");
        }
    }

    private static ResponseStatusException provisioningFailed() {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Не удалось создать пользователя. Попробуйте позже");
    }

    private static ResponseStatusException mapProvisionError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == HttpStatus.CONFLICT.value()) {
            String message = extractErrorMessage(ex);
            if (isLoginConflictMessage(message)) {
                return new ResponseStatusException(HttpStatus.CONFLICT, message);
            }
            return new ResponseStatusException(
                    HttpStatus.CONFLICT, "Пользователь с таким телефоном уже существует");
        }
        if (status == HttpStatus.BAD_REQUEST.value()) {
            String message = extractErrorMessage(ex);
            if (message != null && !message.isBlank()) {
                return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
            }
        }
        log.warn("Account provisioning HTTP error: status={}", status);
        return provisioningFailed();
    }

    private static String extractErrorMessage(RestClientResponseException ex) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(ex.getResponseBodyAsString());
            if (node.has("error")) {
                return node.get("error").asText();
            }
        } catch (Exception ignored) {
            // ignore parse errors
        }
        return null;
    }

    public static boolean isLoginConflict(ResponseStatusException ex) {
        return ex.getStatusCode() == HttpStatus.CONFLICT && isLoginConflictMessage(ex.getReason());
    }

    private static boolean isLoginConflictMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("логин") || lower.contains("login");
    }
}
