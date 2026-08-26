package ru.kzn.buzanov.delivery.fulfillment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.kzn.buzanov.delivery.fulfillment.dto.BranchFulfillmentSettingsResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentQuoteRequest;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentQuoteResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentType;
import ru.kzn.buzanov.delivery.fulfillment.dto.InternalFulfillmentQuoteResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.UpdateBranchFulfillmentSettingsRequest;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantAccessClient;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantBranchRef;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantMembershipRole;
import ru.kzn.buzanov.delivery.web.CurrentUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchFulfillmentSettingsServiceTest {

    @Mock
    private BranchFulfillmentSettingsRepository repository;

    @Mock
    private RestaurantAccessClient restaurantAccessClient;

    @Mock
    private DeliveryZoneService deliveryZoneService;

    @InjectMocks
    private BranchFulfillmentSettingsService service;

    @Test
    void getReturnsDefaultsWhenUnconfigured() {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        stubAccess(org, company, branchId, RestaurantMembershipRole.OWNER);
        when(repository.findByBranchId(branchId)).thenReturn(Optional.empty());

        BranchFulfillmentSettingsResponse response = service.getSettings(
                user(1L, org), branchId, "Bearer t");

        assertThat(response.configured()).isFalse();
        assertThat(response.deliveryEnabled()).isFalse();
        assertThat(response.pickupEnabled()).isTrue();
        assertThat(response.freeDeliveryFromMinor()).isNull();
        assertThat(response.deliveryEstimatedMinMinutes()).isEqualTo(45);
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    void putCreatesAndUpdatesSettings() {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        stubAccess(org, company, branchId, RestaurantMembershipRole.ADMIN);
        when(repository.findByBranchId(branchId)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateBranchFulfillmentSettingsRequest request = validRequest(true, true);
        BranchFulfillmentSettingsResponse created = service.upsertSettings(
                user(2L, org), branchId, request, "Bearer t");

        assertThat(created.configured()).isTrue();
        assertThat(created.minimumDeliveryOrderMinor()).isEqualTo(100_000L);
        assertThat(created.deliveryFeeMinor()).isEqualTo(30_000L);
        verify(repository).saveAndFlush(any());

        BranchFulfillmentSettings existing = new BranchFulfillmentSettings();
        existing.setId(UUID.randomUUID());
        existing.setOrganizationId(org);
        existing.setCompanyId(company);
        existing.setBranchId(branchId);
        existing.setCreatedByUserId(2L);
        when(repository.findByBranchId(branchId)).thenReturn(Optional.of(existing));

        UpdateBranchFulfillmentSettingsRequest update = new UpdateBranchFulfillmentSettingsRequest(
                true, true, 150_000L, 0L, 0L, 30, 60, 20);
        BranchFulfillmentSettingsResponse updated = service.upsertSettings(
                user(2L, org), branchId, update, "Bearer t");
        assertThat(updated.freeDeliveryFromMinor()).isZero();
        assertThat(updated.deliveryFeeMinor()).isZero();
    }

    @Test
    void validationRejectsBothDisabledAndBadMoneyAndTime() {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        stubAccess(org, company, branchId, RestaurantMembershipRole.OWNER);

        assertThatThrownBy(() -> service.upsertSettings(
                user(1L, org), branchId,
                new UpdateBranchFulfillmentSettingsRequest(false, false, 0L, 0L, null, 45, 90, 30),
                "Bearer t"))
                .isInstanceOf(FulfillmentApiException.class)
                .satisfies(ex -> assertThat(((FulfillmentApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.upsertSettings(
                user(1L, org), branchId,
                new UpdateBranchFulfillmentSettingsRequest(true, true, -1L, 0L, null, 45, 90, 30),
                "Bearer t"))
                .isInstanceOf(FulfillmentApiException.class);

        assertThatThrownBy(() -> service.upsertSettings(
                user(1L, org), branchId,
                new UpdateBranchFulfillmentSettingsRequest(true, true, 0L, 0L, null, 90, 45, 30),
                "Bearer t"))
                .isInstanceOf(FulfillmentApiException.class);
    }

    @Test
    void operatorCannotPutButCanGetAndQuote() {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        stubAccess(org, company, branchId, RestaurantMembershipRole.OPERATOR);
        when(repository.findByBranchId(branchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertSettings(
                user(3L, org), branchId, validRequest(true, true), "Bearer t"))
                .isInstanceOf(FulfillmentApiException.class)
                .satisfies(ex -> {
                    assertThat(((FulfillmentApiException) ex).getCode()).isEqualTo("AUTH_ACCESS_DENIED");
                    assertThat(((FulfillmentApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
        verify(repository, never()).saveAndFlush(any());

        BranchFulfillmentSettingsResponse get = service.getSettings(user(3L, org), branchId, "Bearer t");
        assertThat(get.configured()).isFalse();

        FulfillmentQuoteResponse quote = service.quote(
                user(3L, org), branchId,
                new FulfillmentQuoteRequest(FulfillmentType.PICKUP, 10_000L),
                "Bearer t");
        assertThat(quote.available()).isTrue();
        assertThat(quote.deliveryFeeMinor()).isZero();
    }

    @Test
    void quoteCoversFeeFreeMinimumDisabledAndPickup() {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        stubAccess(org, company, branchId, RestaurantMembershipRole.OWNER);

        BranchFulfillmentSettings entity = configured(org, company, branchId);
        when(repository.findByBranchId(branchId)).thenReturn(Optional.of(entity));

        FulfillmentQuoteResponse fee = service.quote(
                user(1L, org), branchId,
                new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 150_000L), "Bearer t");
        assertThat(fee.available()).isTrue();
        assertThat(fee.deliveryFeeMinor()).isEqualTo(30_000L);
        assertThat(fee.totalMinor()).isEqualTo(180_000L);
        assertThat(fee.freeDeliveryApplied()).isFalse();

        FulfillmentQuoteResponse free = service.quote(
                user(1L, org), branchId,
                new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 250_000L), "Bearer t");
        assertThat(free.freeDeliveryApplied()).isTrue();
        assertThat(free.deliveryFeeMinor()).isZero();
        assertThat(free.totalMinor()).isEqualTo(250_000L);

        FulfillmentQuoteResponse below = service.quote(
                user(1L, org), branchId,
                new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 50_000L), "Bearer t");
        assertThat(below.available()).isFalse();
        assertThat(below.reasonCode()).isEqualTo("MINIMUM_ORDER_NOT_MET");
        assertThat(below.minimumOrderShortfallMinor()).isEqualTo(50_000L);

        entity.setDeliveryEnabled(false);
        FulfillmentQuoteResponse disabled = service.quote(
                user(1L, org), branchId,
                new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 150_000L), "Bearer t");
        assertThat(disabled.reasonCode()).isEqualTo("DELIVERY_DISABLED");

        entity.setDeliveryEnabled(true);
        entity.setPickupEnabled(false);
        FulfillmentQuoteResponse pickupOff = service.quote(
                user(1L, org), branchId,
                new FulfillmentQuoteRequest(FulfillmentType.PICKUP, 10_000L), "Bearer t");
        assertThat(pickupOff.reasonCode()).isEqualTo("PICKUP_DISABLED");
    }

    @Test
    void quoteUnconfiguredAllowsPickupOnly() {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        stubAccess(org, company, branchId, RestaurantMembershipRole.OWNER);
        when(repository.findByBranchId(branchId)).thenReturn(Optional.empty());

        FulfillmentQuoteResponse delivery = service.quote(
                user(1L, org), branchId,
                new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 100_000L), "Bearer t");
        assertThat(delivery.available()).isFalse();
        assertThat(delivery.reasonCode()).isEqualTo("DELIVERY_DISABLED");

        FulfillmentQuoteResponse pickup = service.quote(
                user(1L, org), branchId,
                new FulfillmentQuoteRequest(FulfillmentType.PICKUP, 100_000L), "Bearer t");
        assertThat(pickup.available()).isTrue();
        assertThat(pickup.estimatedMinMinutes()).isEqualTo(30);
        assertThat(pickup.estimatedMaxMinutes()).isEqualTo(30);
    }

    @Test
    void quoteByBranchIdCoversFeeFreeMinimumDisabledAndPickup() {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();

        BranchFulfillmentSettings entity = configured(org, company, branchId);
        when(repository.findByBranchId(branchId)).thenReturn(Optional.of(entity));

        FulfillmentQuoteResponse fee = service.quoteByBranchId(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 150_000L));
        assertThat(fee.available()).isTrue();
        assertThat(fee.deliveryFeeMinor()).isEqualTo(30_000L);
        assertThat(fee.totalMinor()).isEqualTo(180_000L);
        assertThat(fee.freeDeliveryApplied()).isFalse();
        verify(restaurantAccessClient, never()).requireBranch(any(), any());
        verify(restaurantAccessClient, never()).requireMembershipRole(any());

        FulfillmentQuoteResponse free = service.quoteByBranchId(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 250_000L));
        assertThat(free.freeDeliveryApplied()).isTrue();
        assertThat(free.deliveryFeeMinor()).isZero();

        FulfillmentQuoteResponse below = service.quoteByBranchId(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 50_000L));
        assertThat(below.available()).isFalse();
        assertThat(below.reasonCode()).isEqualTo("MINIMUM_ORDER_NOT_MET");
        assertThat(below.minimumOrderShortfallMinor()).isEqualTo(50_000L);

        entity.setDeliveryEnabled(false);
        FulfillmentQuoteResponse disabled = service.quoteByBranchId(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 150_000L));
        assertThat(disabled.reasonCode()).isEqualTo("DELIVERY_DISABLED");

        entity.setDeliveryEnabled(true);
        entity.setPickupEnabled(false);
        FulfillmentQuoteResponse pickupOff = service.quoteByBranchId(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.PICKUP, 10_000L));
        assertThat(pickupOff.reasonCode()).isEqualTo("PICKUP_DISABLED");
    }

    @Test
    void quoteByBranchIdUnconfiguredAllowsPickupOnly() {
        UUID branchId = UUID.randomUUID();
        when(repository.findByBranchId(branchId)).thenReturn(Optional.empty());

        FulfillmentQuoteResponse delivery = service.quoteByBranchId(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 100_000L));
        assertThat(delivery.available()).isFalse();
        assertThat(delivery.reasonCode()).isEqualTo("DELIVERY_DISABLED");

        FulfillmentQuoteResponse pickup = service.quoteByBranchId(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.PICKUP, 100_000L));
        assertThat(pickup.available()).isTrue();
        assertThat(pickup.estimatedMinMinutes()).isEqualTo(30);
        assertThat(pickup.estimatedMaxMinutes()).isEqualTo(30);
    }

    @Test
    void quoteInternalMapsResponseAndSoftOwnership() {
        UUID org = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        BranchFulfillmentSettings entity = configured(org, company, branchId);
        when(repository.findByBranchId(branchId)).thenReturn(Optional.of(entity));

        InternalFulfillmentQuoteResponse fee = service.quoteInternal(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 150_000L));
        assertThat(fee.available()).isTrue();
        assertThat(fee.deliveryFeeMinor()).isEqualTo(30_000L);
        assertThat(fee.minimumOrderSatisfied()).isTrue();
        assertThat(fee.freeDeliveryThresholdMinor()).isEqualTo(200_000L);
        assertThat(fee.estimatedMinutesMin()).isEqualTo(45);
        assertThat(fee.estimatedMinutesMax()).isEqualTo(90);
        assertThat(fee.issueCode()).isNull();
        assertThat(fee.itemsTotalMinor()).isEqualTo(150_000L);

        InternalFulfillmentQuoteResponse free = service.quoteInternal(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 250_000L));
        assertThat(free.deliveryFeeMinor()).isZero();
        assertThat(free.issueCode()).isNull();

        InternalFulfillmentQuoteResponse below = service.quoteInternal(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 50_000L));
        assertThat(below.available()).isFalse();
        assertThat(below.minimumOrderSatisfied()).isFalse();
        assertThat(below.issueCode()).isEqualTo("MINIMUM_ORDER_NOT_MET");

        entity.setDeliveryEnabled(false);
        InternalFulfillmentQuoteResponse disabled = service.quoteInternal(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 150_000L));
        assertThat(disabled.issueCode()).isEqualTo("DELIVERY_DISABLED");

        entity.setDeliveryEnabled(true);
        entity.setPickupEnabled(false);
        InternalFulfillmentQuoteResponse pickupOff = service.quoteInternal(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.PICKUP, 10_000L));
        assertThat(pickupOff.issueCode()).isEqualTo("PICKUP_DISABLED");

        entity.setPickupEnabled(true);
        InternalFulfillmentQuoteResponse withCompany = service.quoteInternal(
                branchId,
                new FulfillmentQuoteRequest(FulfillmentType.PICKUP, 10_000L, company, null));
        assertThat(withCompany.available()).isTrue();

        assertThatThrownBy(() -> service.quoteInternal(
                branchId,
                new FulfillmentQuoteRequest(FulfillmentType.PICKUP, 10_000L, UUID.randomUUID(), null)))
                .isInstanceOf(FulfillmentApiException.class)
                .satisfies(ex -> {
                    assertThat(((FulfillmentApiException) ex).getCode()).isEqualTo("BRANCH_NOT_FOUND");
                    assertThat(((FulfillmentApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void quoteInternalUnconfiguredDefaults() {
        UUID branchId = UUID.randomUUID();
        when(repository.findByBranchId(branchId)).thenReturn(Optional.empty());

        InternalFulfillmentQuoteResponse delivery = service.quoteInternal(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 100_000L));
        assertThat(delivery.available()).isFalse();
        assertThat(delivery.issueCode()).isEqualTo("DELIVERY_DISABLED");
        assertThat(delivery.freeDeliveryThresholdMinor()).isNull();

        InternalFulfillmentQuoteResponse pickup = service.quoteInternal(
                branchId,
                new FulfillmentQuoteRequest(FulfillmentType.PICKUP, 100_000L, UUID.randomUUID(), null));
        assertThat(pickup.available()).isTrue();
        assertThat(pickup.estimatedMinutesMin()).isEqualTo(30);
        assertThat(pickup.freeDeliveryThresholdMinor()).isNull();
    }

    private void stubAccess(UUID org, UUID company, UUID branchId, RestaurantMembershipRole role) {
        when(restaurantAccessClient.requireMembershipRole(any())).thenReturn(role);
        when(restaurantAccessClient.requireBranch(any(), any()))
                .thenReturn(new RestaurantBranchRef(branchId, company, org));
    }

    private static CurrentUser user(Long id, UUID org) {
        return new CurrentUser(id, List.of("EMPLOYEE"), org);
    }

    private static UpdateBranchFulfillmentSettingsRequest validRequest(boolean delivery, boolean pickup) {
        return new UpdateBranchFulfillmentSettingsRequest(
                delivery, pickup, 100_000L, 30_000L, 200_000L, 45, 90, 30);
    }

    private static BranchFulfillmentSettings configured(UUID org, UUID company, UUID branchId) {
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
        entity.setCreatedByUserId(1L);
        return entity;
    }
}
