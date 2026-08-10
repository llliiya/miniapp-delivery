package ru.kzn.buzanov.delivery.fulfillment.restaurant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import ru.kzn.buzanov.delivery.fulfillment.FulfillmentApiException;

import java.util.UUID;

@Configuration
public class RestaurantAccessClientConfig {

    @Bean
    @ConditionalOnMissingBean(RestaurantAccessClient.class)
    public RestaurantAccessClient noOpRestaurantAccessClient() {
        return new RestaurantAccessClient() {
            @Override
            public RestaurantBranchRef requireBranch(UUID branchId, String authorizationHeader) {
                throw new FulfillmentApiException(
                        "DELIVERY_SERVICE_UNAVAILABLE",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Restaurant service is not configured");
            }

            @Override
            public RestaurantMembershipRole requireMembershipRole(String authorizationHeader) {
                throw new FulfillmentApiException(
                        "DELIVERY_SERVICE_UNAVAILABLE",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Restaurant service is not configured");
            }
        };
    }
}
