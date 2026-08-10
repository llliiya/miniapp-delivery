package ru.kzn.buzanov.delivery.fulfillment.restaurant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.kzn.buzanov.delivery.fulfillment.FulfillmentApiException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;

@Component
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${app.restaurant.base-url:}')")
public class RestRestaurantAccessClient implements RestaurantAccessClient {

    private final RestClient restClient;

    public RestRestaurantAccessClient(
            @Value("${app.restaurant.base-url}") String baseUrl,
            @Value("${app.restaurant.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${app.restaurant.read-timeout-ms:30000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/$", ""))
                .requestFactory(factory)
                .build();
    }

    @Override
    public RestaurantBranchRef requireBranch(UUID branchId, String authorizationHeader) {
        try {
            Map<?, ?> body = restClient.get()
                    .uri("/branches/{id}", branchId)
                    .header("Authorization", authorizationHeader)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            if (body == null || body.get("id") == null) {
                throw new FulfillmentApiException("BRANCH_NOT_FOUND", HttpStatus.NOT_FOUND, "Branch not found");
            }
            UUID id = UUID.fromString(String.valueOf(body.get("id")));
            UUID companyId = UUID.fromString(String.valueOf(body.get("companyId")));
            UUID organizationId = UUID.fromString(String.valueOf(body.get("organizationId")));
            return new RestaurantBranchRef(id, companyId, organizationId);
        } catch (FulfillmentApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new FulfillmentApiException("BRANCH_NOT_FOUND", HttpStatus.NOT_FOUND, "Branch not found");
            }
            if (ex.getStatusCode().value() == 403) {
                throw new FulfillmentApiException("AUTH_ACCESS_DENIED", HttpStatus.FORBIDDEN, "Access denied");
            }
            throw new RestaurantServiceUnavailableException(
                    "Restaurant branch lookup failed with status " + ex.getStatusCode().value(), ex);
        } catch (RestClientException | IllegalArgumentException ex) {
            throw new RestaurantServiceUnavailableException("Restaurant branch lookup failed", ex);
        }
    }

    @Override
    public RestaurantMembershipRole requireMembershipRole(String authorizationHeader) {
        try {
            Map<?, ?> body = restClient.get()
                    .uri("/me")
                    .header("Authorization", authorizationHeader)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                throw new FulfillmentApiException("AUTH_ACCESS_DENIED", HttpStatus.FORBIDDEN, "Access denied");
            }
            Object roleRaw = body.get("restaurantRole");
            RestaurantMembershipRole role = RestaurantMembershipRole.fromApi(
                    roleRaw == null ? null : String.valueOf(roleRaw));
            if (role == null) {
                throw new FulfillmentApiException("AUTH_ACCESS_DENIED", HttpStatus.FORBIDDEN, "Access denied");
            }
            return role;
        } catch (FulfillmentApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 403 || ex.getStatusCode().value() == 401) {
                throw new FulfillmentApiException("AUTH_ACCESS_DENIED", HttpStatus.FORBIDDEN, "Access denied");
            }
            throw new RestaurantServiceUnavailableException(
                    "Restaurant me lookup failed with status " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new RestaurantServiceUnavailableException("Restaurant me lookup failed", ex);
        }
    }
}
