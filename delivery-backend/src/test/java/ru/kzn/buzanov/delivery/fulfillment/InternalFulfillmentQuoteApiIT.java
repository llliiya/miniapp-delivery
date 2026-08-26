package ru.kzn.buzanov.delivery.fulfillment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantAccessClient;
import ru.kzn.buzanov.delivery.support.AbstractPostgresIT;
import ru.kzn.buzanov.delivery.support.TestJwtFactory;
import ru.kzn.buzanov.delivery.web.DeliveryServiceKeyFilter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalFulfillmentQuoteApiIT extends AbstractPostgresIT {

    private static final String SERVICE_KEY = "test-delivery-service-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchFulfillmentSettingsRepository repository;

    @MockBean
    private RestaurantAccessClient restaurantAccessClient;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void correctServiceKeyReturnsQuote() throws Exception {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        seedConfigured(org, company, branchId);

        mockMvc.perform(post("/internal/branches/" + branchId + "/fulfillment-quote")
                        .header(DeliveryServiceKeyFilter.HEADER, SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DELIVERY","itemsTotalMinor":150000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DELIVERY"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.itemsTotalMinor").value(150000))
                .andExpect(jsonPath("$.deliveryFeeMinor").value(30000))
                .andExpect(jsonPath("$.minimumOrderMinor").value(100000))
                .andExpect(jsonPath("$.minimumOrderSatisfied").value(true))
                .andExpect(jsonPath("$.freeDeliveryThresholdMinor").value(200000))
                .andExpect(jsonPath("$.estimatedMinutesMin").value(45))
                .andExpect(jsonPath("$.estimatedMinutesMax").value(90))
                .andExpect(jsonPath("$.issueCode").value(nullValue()));

        verifyNoInteractions(restaurantAccessClient);
    }

    @Test
    void pickupAndDeliveryQuotes() throws Exception {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        seedConfigured(org, company, branchId);

        mockMvc.perform(post("/internal/branches/" + branchId + "/fulfillment-quote")
                        .header(DeliveryServiceKeyFilter.HEADER, SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PICKUP","itemsTotalMinor":50000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PICKUP"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.deliveryFeeMinor").value(0))
                .andExpect(jsonPath("$.estimatedMinutesMin").value(30))
                .andExpect(jsonPath("$.estimatedMinutesMax").value(30));

        mockMvc.perform(post("/internal/branches/" + branchId + "/fulfillment-quote")
                        .header(DeliveryServiceKeyFilter.HEADER, SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DELIVERY","itemsTotalMinor":250000,"companyId":"%s"}
                                """.formatted(company)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.deliveryFeeMinor").value(0))
                .andExpect(jsonPath("$.minimumOrderSatisfied").value(true));

        mockMvc.perform(post("/internal/branches/" + branchId + "/fulfillment-quote")
                        .header(DeliveryServiceKeyFilter.HEADER, SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DELIVERY","itemsTotalMinor":50000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.issueCode").value("MINIMUM_ORDER_NOT_MET"))
                .andExpect(jsonPath("$.minimumOrderSatisfied").value(false));
    }

    @Test
    void missingKeyReturns401() throws Exception {
        UUID branchId = UUID.randomUUID();
        mockMvc.perform(post("/internal/branches/" + branchId + "/fulfillment-quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PICKUP","itemsTotalMinor":1000}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_MISSING_TOKEN"));
    }

    @Test
    void wrongKeyReturns401() throws Exception {
        UUID branchId = UUID.randomUUID();
        mockMvc.perform(post("/internal/branches/" + branchId + "/fulfillment-quote")
                        .header(DeliveryServiceKeyFilter.HEADER, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PICKUP","itemsTotalMinor":1000}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }

    @Test
    void jwtAloneWithoutServiceKeyReturns401() throws Exception {
        UUID org = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        String token = TestJwtFactory.accessToken(1L, org, Set.of("EMPLOYEE"));

        mockMvc.perform(post("/internal/branches/" + branchId + "/fulfillment-quote")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PICKUP","itemsTotalMinor":1000}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_MISSING_TOKEN"));
    }

    @Test
    void companyMismatchReturns404() throws Exception {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        seedConfigured(org, company, branchId);

        mockMvc.perform(post("/internal/branches/" + branchId + "/fulfillment-quote")
                        .header(DeliveryServiceKeyFilter.HEADER, SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PICKUP","itemsTotalMinor":1000,"companyId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRANCH_NOT_FOUND"));
    }

    @Test
    void unconfiguredBranchReturnsConfiguredFalse() throws Exception {
        UUID branchId = UUID.randomUUID();
        mockMvc.perform(get("/internal/branches/" + branchId + "/fulfillment-settings")
                        .header(DeliveryServiceKeyFilter.HEADER, SERVICE_KEY)
                        .param("companyId", UUID.randomUUID().toString())
                        .param("organizationId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.pickupEnabled").value(true))
                .andExpect(jsonPath("$.deliveryEnabled").value(false));

        verifyNoInteractions(restaurantAccessClient);
    }

    @Test
    void configuredBranchReturnsSavedFlags() throws Exception {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        seedConfigured(org, company, branchId);

        mockMvc.perform(get("/internal/branches/" + branchId + "/fulfillment-settings")
                        .header(DeliveryServiceKeyFilter.HEADER, SERVICE_KEY)
                        .param("companyId", company.toString())
                        .param("organizationId", org.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.pickupEnabled").value(true))
                .andExpect(jsonPath("$.deliveryEnabled").value(true));
    }

    @Test
    void companyMismatchOnSettingsReturnsNotFound() throws Exception {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        seedConfigured(org, company, branchId);

        mockMvc.perform(get("/internal/branches/" + branchId + "/fulfillment-settings")
                        .header(DeliveryServiceKeyFilter.HEADER, SERVICE_KEY)
                        .param("companyId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRANCH_NOT_FOUND"));
    }

    private void seedConfigured(UUID org, UUID company, UUID branchId) {
        Instant now = Instant.now();
        BranchFulfillmentSettings entity = new BranchFulfillmentSettings();
        entity.setId(UUID.randomUUID());
        entity.setOrganizationId(org);
        entity.setCompanyId(company);
        entity.setBranchId(branchId);
        entity.setDeliveryEnabled(true);
        entity.setPickupEnabled(true);
        entity.setMinimumDeliveryOrderMinor(100_000L);
        entity.setDeliveryFeeMinor(30_000L);
        entity.setFreeDeliveryFromMinor(200_000L);
        entity.setDeliveryEstimatedMinMinutes(45);
        entity.setDeliveryEstimatedMaxMinutes(90);
        entity.setPickupEstimatedMinutes(30);
        entity.setDeliveryPricingMode(DeliveryPricingMode.FLAT.name());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedByUserId(1L);
        repository.saveAndFlush(entity);
    }
}
