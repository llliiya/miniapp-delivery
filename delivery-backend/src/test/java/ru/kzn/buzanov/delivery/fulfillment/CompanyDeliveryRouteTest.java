package ru.kzn.buzanov.delivery.fulfillment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentQuoteRequest;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentType;
import ru.kzn.buzanov.delivery.fulfillment.dto.InternalCompanyDeliveryRouteRequest;
import ru.kzn.buzanov.delivery.fulfillment.dto.InternalCompanyDeliveryRouteResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyDeliveryRouteTest {

    @Mock
    private BranchFulfillmentSettingsRepository repository;

    @Mock
    private DeliveryZoneRepository deliveryZoneRepository;

    @Mock
    private DeliveryZoneService deliveryZoneService;

    @InjectMocks
    private BranchFulfillmentSettingsService service;

    @Test
    void addressInBranchAZoneRoutesToBranchA() {
        UUID companyId = UUID.randomUUID();
        UUID branchA = UUID.randomUUID();
        UUID branchB = UUID.randomUUID();
        when(repository.findByBranchIdIn(any())).thenReturn(List.of(
                zonesSettings(companyId, branchA),
                zonesSettings(companyId, branchB)));
        DeliveryZone zoneA = zone(companyId, branchA, "A", 10);
        when(deliveryZoneService.matchActiveZoneAmongBranches(eq(companyId), any(), eq(49.1), eq(55.8)))
                .thenReturn(Optional.of(zoneA));
        when(deliveryZoneService.matchActiveZone(eq(branchA), eq(49.1), eq(55.8)))
                .thenReturn(Optional.of(zoneA));

        InternalCompanyDeliveryRouteResponse route = service.routeInternal(
                companyId,
                new InternalCompanyDeliveryRouteRequest(
                        null, List.of(branchA, branchB), 200_000L, 55.8, 49.1));

        assertThat(route.branchId()).isEqualTo(branchA);
        assertThat(route.available()).isTrue();
        assertThat(route.zoneName()).isEqualTo("A");
    }

    @Test
    void overlapHigherPriorityWins() {
        UUID companyId = UUID.randomUUID();
        UUID branchA = UUID.randomUUID();
        UUID branchB = UUID.randomUUID();
        when(repository.findByBranchIdIn(any())).thenReturn(List.of(
                zonesSettings(companyId, branchA),
                zonesSettings(companyId, branchB)));
        DeliveryZone zoneB = zone(companyId, branchB, "B-high", 50);
        when(deliveryZoneService.matchActiveZoneAmongBranches(eq(companyId), any(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(zoneB));
        when(deliveryZoneService.matchActiveZone(eq(branchB), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(zoneB));

        InternalCompanyDeliveryRouteResponse route = service.routeInternal(
                companyId,
                new InternalCompanyDeliveryRouteRequest(
                        null, List.of(branchA, branchB), 200_000L, 55.8, 49.1));

        assertThat(route.branchId()).isEqualTo(branchB);
        assertThat(route.zoneName()).isEqualTo("B-high");
    }

    @Test
    void outsideAllZones() {
        UUID companyId = UUID.randomUUID();
        UUID branchA = UUID.randomUUID();
        when(repository.findByBranchIdIn(any())).thenReturn(List.of(zonesSettings(companyId, branchA)));
        when(deliveryZoneService.matchActiveZoneAmongBranches(eq(companyId), any(), anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());
        when(deliveryZoneRepository
                .findByCompanyIdAndBranchIdInAndActiveTrueOrderByPriorityDescCreatedAtAscIdAsc(eq(companyId), any()))
                .thenReturn(List.of(zone(companyId, branchA, "A", 1)));

        InternalCompanyDeliveryRouteResponse route = service.routeInternal(
                companyId,
                new InternalCompanyDeliveryRouteRequest(null, List.of(branchA), 200_000L, 55.0, 49.0));

        assertThat(route.branchId()).isNull();
        assertThat(route.available()).isFalse();
        assertThat(route.issueCode()).isEqualTo("OUTSIDE_ZONES");
    }

    @Test
    void zeroActiveZones() {
        UUID companyId = UUID.randomUUID();
        UUID branchA = UUID.randomUUID();
        when(repository.findByBranchIdIn(any())).thenReturn(List.of(zonesSettings(companyId, branchA)));
        when(deliveryZoneService.matchActiveZoneAmongBranches(eq(companyId), any(), anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());
        when(deliveryZoneRepository
                .findByCompanyIdAndBranchIdInAndActiveTrueOrderByPriorityDescCreatedAtAscIdAsc(eq(companyId), any()))
                .thenReturn(List.of());

        InternalCompanyDeliveryRouteResponse route = service.routeInternal(
                companyId,
                new InternalCompanyDeliveryRouteRequest(null, List.of(branchA), 200_000L, 55.0, 49.0));

        assertThat(route.issueCode()).isEqualTo("NO_DELIVERY_ZONES");
        assertThat(route.available()).isFalse();
    }

    @Test
    void inactiveBranchExcludedFromRouting() {
        UUID companyId = UUID.randomUUID();
        UUID branchA = UUID.randomUUID();
        UUID branchB = UUID.randomUUID();
        BranchFulfillmentSettings disabled = zonesSettings(companyId, branchA);
        disabled.setDeliveryEnabled(false);
        when(repository.findByBranchIdIn(any())).thenReturn(List.of(disabled, zonesSettings(companyId, branchB)));
        DeliveryZone zoneB = zone(companyId, branchB, "B", 1);
        when(deliveryZoneService.matchActiveZoneAmongBranches(eq(companyId), eq(List.of(branchB)), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(zoneB));
        when(deliveryZoneService.matchActiveZone(eq(branchB), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(zoneB));

        InternalCompanyDeliveryRouteResponse route = service.routeInternal(
                companyId,
                new InternalCompanyDeliveryRouteRequest(
                        null, List.of(branchA, branchB), 200_000L, 55.8, 49.1));

        assertThat(route.branchId()).isEqualTo(branchB);
    }

    @Test
    void singleFlatBranchFallback() {
        UUID companyId = UUID.randomUUID();
        UUID branchA = UUID.randomUUID();
        BranchFulfillmentSettings flat = zonesSettings(companyId, branchA);
        flat.setDeliveryPricingMode(DeliveryPricingMode.FLAT.name());
        flat.setDeliveryFeeMinor(25_000L);
        when(repository.findByBranchIdIn(any())).thenReturn(List.of(flat));
        when(repository.findByBranchId(branchA)).thenReturn(Optional.of(flat));

        InternalCompanyDeliveryRouteResponse route = service.routeInternal(
                companyId,
                new InternalCompanyDeliveryRouteRequest(null, List.of(branchA), 200_000L, 55.8, 49.1));

        assertThat(route.branchId()).isEqualTo(branchA);
        assertThat(route.available()).isTrue();
        assertThat(route.deliveryFeeMinor()).isEqualTo(25_000L);
        assertThat(route.pricingMode()).isEqualTo("FLAT");
    }

    private static BranchFulfillmentSettings zonesSettings(UUID companyId, UUID branchId) {
        BranchFulfillmentSettings entity = new BranchFulfillmentSettings();
        entity.setId(UUID.randomUUID());
        entity.setOrganizationId(UUID.randomUUID());
        entity.setCompanyId(companyId);
        entity.setBranchId(branchId);
        entity.setDeliveryEnabled(true);
        entity.setPickupEnabled(true);
        entity.setMinimumDeliveryOrderMinor(0L);
        entity.setDeliveryFeeMinor(30_000L);
        entity.setDeliveryEstimatedMinMinutes(45);
        entity.setDeliveryEstimatedMaxMinutes(90);
        entity.setPickupEstimatedMinutes(30);
        entity.setDeliveryPricingMode(DeliveryPricingMode.ZONES.name());
        return entity;
    }

    private static DeliveryZone zone(UUID companyId, UUID branchId, String name, int priority) {
        DeliveryZone zone = new DeliveryZone();
        zone.setId(UUID.randomUUID());
        zone.setBranchId(branchId);
        zone.setOrganizationId(UUID.randomUUID());
        zone.setCompanyId(companyId);
        zone.setName(name);
        zone.setActive(true);
        zone.setPriority(priority);
        zone.setDeliveryFeeMinor(15_000L);
        zone.setEtaMinMinutes(40);
        zone.setEtaMaxMinutes(60);
        zone.setGeometry("""
                {"type":"Polygon","coordinates":[[[49.0,55.7],[49.2,55.7],[49.2,55.9],[49.0,55.9],[49.0,55.7]]]}
                """);
        zone.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        zone.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        zone.setCreatedByUserId(1L);
        return zone;
    }
}
