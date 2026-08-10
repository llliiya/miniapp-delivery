package ru.kzn.buzanov.delivery.fulfillment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantAccessClient;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantBranchRef;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantMembershipRole;
import ru.kzn.buzanov.delivery.support.AbstractPostgresIT;
import ru.kzn.buzanov.delivery.support.TestJwtFactory;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BranchFulfillmentSettingsApiIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BranchFulfillmentSettingsRepository repository;

    @MockBean
    private RestaurantAccessClient restaurantAccessClient;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void getDefaultsThenPutAndGetConfigured() throws Exception {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        stubBranch(org, company, branchId, RestaurantMembershipRole.OWNER);
        String token = token(1L, org);

        mockMvc.perform(get("/branches/" + branchId + "/fulfillment-settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.deliveryEnabled").value(false))
                .andExpect(jsonPath("$.pickupEnabled").value(true))
                .andExpect(jsonPath("$.freeDeliveryFromMinor").value(nullValue()))
                .andExpect(jsonPath("$.updatedAt").value(nullValue()));

        mockMvc.perform(put("/branches/" + branchId + "/fulfillment-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deliveryEnabled": true,
                                  "pickupEnabled": true,
                                  "minimumDeliveryOrderMinor": 100000,
                                  "deliveryFeeMinor": 30000,
                                  "freeDeliveryFromMinor": 200000,
                                  "deliveryEstimatedMinMinutes": 45,
                                  "deliveryEstimatedMaxMinutes": 90,
                                  "pickupEstimatedMinutes": 30
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.branchId").value(branchId.toString()))
                .andExpect(jsonPath("$.minimumDeliveryOrderMinor").value(100000));

        mockMvc.perform(get("/branches/" + branchId + "/fulfillment-settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.deliveryFeeMinor").value(30000));
    }

    @Test
    void validationAndRolesAndQuotes() throws Exception {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        stubBranch(org, company, branchId, RestaurantMembershipRole.ADMIN);
        String adminToken = token(2L, org);

        mockMvc.perform(put("/branches/" + branchId + "/fulfillment-settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deliveryEnabled": false,
                                  "pickupEnabled": false,
                                  "minimumDeliveryOrderMinor": 0,
                                  "deliveryFeeMinor": 0,
                                  "freeDeliveryFromMinor": null,
                                  "deliveryEstimatedMinMinutes": 45,
                                  "deliveryEstimatedMaxMinutes": 90,
                                  "pickupEstimatedMinutes": 30
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(put("/branches/" + branchId + "/fulfillment-settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deliveryEnabled": true,
                                  "pickupEnabled": true,
                                  "minimumDeliveryOrderMinor": 100000,
                                  "deliveryFeeMinor": 30000,
                                  "freeDeliveryFromMinor": 0,
                                  "deliveryEstimatedMinMinutes": 45,
                                  "deliveryEstimatedMaxMinutes": 90,
                                  "pickupEstimatedMinutes": 30,
                                  "organizationId": "%s",
                                  "companyId": "%s",
                                  "branchId": "%s"
                                }
                                """.formatted(org, company, branchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeDeliveryFromMinor").value(0))
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.companyId").doesNotExist());

        when(restaurantAccessClient.requireMembershipRole(any())).thenReturn(RestaurantMembershipRole.OPERATOR);
        String operatorToken = token(3L, org);

        mockMvc.perform(get("/branches/" + branchId + "/fulfillment-settings")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/branches/" + branchId + "/fulfillment-settings")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deliveryEnabled": true,
                                  "pickupEnabled": true,
                                  "minimumDeliveryOrderMinor": 1,
                                  "deliveryFeeMinor": 1,
                                  "freeDeliveryFromMinor": null,
                                  "deliveryEstimatedMinMinutes": 10,
                                  "deliveryEstimatedMaxMinutes": 20,
                                  "pickupEstimatedMinutes": 5
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        mockMvc.perform(post("/branches/" + branchId + "/fulfillment-quote")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DELIVERY","itemsTotalMinor":150000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.deliveryFeeMinor").value(0))
                .andExpect(jsonPath("$.freeDeliveryApplied").value(true));

        mockMvc.perform(post("/branches/" + branchId + "/fulfillment-quote")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DELIVERY","itemsTotalMinor":50000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("MINIMUM_ORDER_NOT_MET"));

        mockMvc.perform(post("/branches/" + branchId + "/fulfillment-quote")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PICKUP","itemsTotalMinor":50000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.deliveryFeeMinor").value(0));
    }

    @Test
    void missingBranchReturns404() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        when(restaurantAccessClient.requireMembershipRole(any())).thenReturn(RestaurantMembershipRole.OWNER);
        when(restaurantAccessClient.requireBranch(eq(branchId), any()))
                .thenThrow(new FulfillmentApiException("BRANCH_NOT_FOUND",
                        HttpStatus.NOT_FOUND, "Branch not found"));
        String token = token(1L, organizationId);

        mockMvc.perform(get("/branches/" + branchId + "/fulfillment-settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRANCH_NOT_FOUND"));
    }

    @Test
    void atomicUpsertSameBranch() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        stubBranch(organizationId, company, branchId, RestaurantMembershipRole.OWNER);
        String token = token(1L, organizationId);

        String body = """
                {
                  "deliveryEnabled": true,
                  "pickupEnabled": false,
                  "minimumDeliveryOrderMinor": 10,
                  "deliveryFeeMinor": 5,
                  "freeDeliveryFromMinor": null,
                  "deliveryEstimatedMinMinutes": 15,
                  "deliveryEstimatedMaxMinutes": 25,
                  "pickupEstimatedMinutes": 10
                }
                """;
        mockMvc.perform(put("/branches/" + branchId + "/fulfillment-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(put("/branches/" + branchId + "/fulfillment-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"minimumDeliveryOrderMinor\": 10",
                                "\"minimumDeliveryOrderMinor\": 20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minimumDeliveryOrderMinor").value(20));

        assertThat(repository.findAll()).hasSize(1);
    }

    private void stubBranch(UUID org, UUID company, UUID branchId, RestaurantMembershipRole role) {
        when(restaurantAccessClient.requireMembershipRole(any())).thenReturn(role);
        when(restaurantAccessClient.requireBranch(eq(branchId), any()))
                .thenReturn(new RestaurantBranchRef(branchId, company, org));
    }

    private static String token(Long userId, UUID organizationId) {
        return TestJwtFactory.accessToken(userId, organizationId, Set.of("EMPLOYEE"));
    }
}
