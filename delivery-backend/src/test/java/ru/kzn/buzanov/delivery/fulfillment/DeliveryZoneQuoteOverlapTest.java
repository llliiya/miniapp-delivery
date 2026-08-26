package ru.kzn.buzanov.delivery.fulfillment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentQuoteRequest;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentQuoteResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryZoneQuoteOverlapTest {

    @Mock
    private BranchFulfillmentSettingsRepository repository;

    @Mock
    private DeliveryZoneService deliveryZoneService;

    @InjectMocks
    private BranchFulfillmentSettingsService service;

    @Test
    void zonesModeUsesMatchedZoneFeeAndIgnoresFlatFee() {
        UUID branchId = UUID.randomUUID();
        BranchFulfillmentSettings settings = flatSettings(branchId);
        settings.setDeliveryPricingMode(DeliveryPricingMode.ZONES.name());
        settings.setDeliveryFeeMinor(99_000L);
        when(repository.findByBranchId(branchId)).thenReturn(Optional.of(settings));

        DeliveryZone zone = zone(branchId, "Центр", 15_000L, 100);
        when(deliveryZoneService.matchActiveZone(eq(branchId), eq(49.1), eq(55.8)))
                .thenReturn(Optional.of(zone));

        FulfillmentQuoteResponse quote = service.quoteByBranchId(
                branchId,
                new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 200_000L, null, null, 55.8, 49.1));

        assertThat(quote.available()).isTrue();
        assertThat(quote.deliveryFeeMinor()).isEqualTo(15_000L);
        assertThat(quote.zoneId()).isEqualTo(zone.getId());
        assertThat(quote.zoneName()).isEqualTo("Центр");
    }

    @Test
    void outsideZonesUnavailable() {
        UUID branchId = UUID.randomUUID();
        BranchFulfillmentSettings settings = flatSettings(branchId);
        settings.setDeliveryPricingMode(DeliveryPricingMode.ZONES.name());
        when(repository.findByBranchId(branchId)).thenReturn(Optional.of(settings));
        when(deliveryZoneService.matchActiveZone(eq(branchId), anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

        FulfillmentQuoteResponse quote = service.quoteByBranchId(
                branchId,
                new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 200_000L, null, null, 55.0, 49.0));

        assertThat(quote.available()).isFalse();
        assertThat(quote.reasonCode()).isEqualTo("OUTSIDE_ZONES");
    }

    @Test
    void zonesWithoutCoordinatesRequireAddress() {
        UUID branchId = UUID.randomUUID();
        BranchFulfillmentSettings settings = flatSettings(branchId);
        settings.setDeliveryPricingMode(DeliveryPricingMode.ZONES.name());
        when(repository.findByBranchId(branchId)).thenReturn(Optional.of(settings));

        FulfillmentQuoteResponse quote = service.quoteByBranchId(
                branchId, new FulfillmentQuoteRequest(FulfillmentType.DELIVERY, 200_000L));

        assertThat(quote.available()).isFalse();
        assertThat(quote.reasonCode()).isEqualTo("ADDRESS_REQUIRED");
    }

    private static BranchFulfillmentSettings flatSettings(UUID branchId) {
        BranchFulfillmentSettings entity = new BranchFulfillmentSettings();
        entity.setId(UUID.randomUUID());
        entity.setOrganizationId(UUID.randomUUID());
        entity.setCompanyId(UUID.randomUUID());
        entity.setBranchId(branchId);
        entity.setDeliveryEnabled(true);
        entity.setPickupEnabled(true);
        entity.setMinimumDeliveryOrderMinor(0L);
        entity.setDeliveryFeeMinor(30_000L);
        entity.setDeliveryEstimatedMinMinutes(45);
        entity.setDeliveryEstimatedMaxMinutes(90);
        entity.setPickupEstimatedMinutes(30);
        entity.setDeliveryPricingMode(DeliveryPricingMode.FLAT.name());
        return entity;
    }

    private static DeliveryZone zone(UUID branchId, String name, long fee, int priority) {
        DeliveryZone zone = new DeliveryZone();
        zone.setId(UUID.randomUUID());
        zone.setBranchId(branchId);
        zone.setOrganizationId(UUID.randomUUID());
        zone.setCompanyId(UUID.randomUUID());
        zone.setName(name);
        zone.setActive(true);
        zone.setPriority(priority);
        zone.setDeliveryFeeMinor(fee);
        zone.setGeometry("""
                {"type":"Polygon","coordinates":[[[49.0,55.7],[49.2,55.7],[49.2,55.9],[49.0,55.9],[49.0,55.7]]]}
                """);
        zone.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        zone.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        zone.setCreatedByUserId(1L);
        return zone;
    }
}
