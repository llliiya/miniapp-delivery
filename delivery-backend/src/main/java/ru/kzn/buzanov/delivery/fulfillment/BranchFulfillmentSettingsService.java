package ru.kzn.buzanov.delivery.fulfillment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.fulfillment.dto.BranchFulfillmentSettingsResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentQuoteRequest;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentQuoteResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentType;
import ru.kzn.buzanov.delivery.fulfillment.dto.InternalCompanyDeliveryRouteRequest;
import ru.kzn.buzanov.delivery.fulfillment.dto.InternalCompanyDeliveryRouteResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.InternalFulfillmentQuoteResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.InternalFulfillmentSettingsResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.UpdateBranchFulfillmentSettingsRequest;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantAccessClient;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantBranchRef;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantMembershipRole;
import ru.kzn.buzanov.delivery.web.CurrentUser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchFulfillmentSettingsService {

    public static final long MAX_MONEY_MINOR = 100_000_000L;
    public static final int DEFAULT_DELIVERY_MIN_MINUTES = 45;
    public static final int DEFAULT_DELIVERY_MAX_MINUTES = 90;
    public static final int DEFAULT_PICKUP_MINUTES = 30;

    private final BranchFulfillmentSettingsRepository repository;
    private final DeliveryZoneRepository deliveryZoneRepository;
    private final RestaurantAccessClient restaurantAccessClient;
    private final DeliveryZoneService deliveryZoneService;

    @Transactional(readOnly = true)
    public BranchFulfillmentSettingsResponse getSettings(CurrentUser user,
                                                         UUID branchId,
                                                         String authorizationHeader) {
        RestaurantMembershipRole role = requireActiveRole(authorizationHeader);
        RestaurantBranchRef branch = requireOwnedBranch(user, branchId, authorizationHeader);
        return repository.findByBranchId(branch.branchId())
                .map(this::toConfiguredResponse)
                .orElseGet(() -> defaults(branch.branchId()));
    }

    @Transactional
    public BranchFulfillmentSettingsResponse upsertSettings(CurrentUser user,
                                                            UUID branchId,
                                                            UpdateBranchFulfillmentSettingsRequest request,
                                                            String authorizationHeader) {
        RestaurantMembershipRole role = requireActiveRole(authorizationHeader);
        requireEditor(role);
        RestaurantBranchRef branch = requireOwnedBranch(user, branchId, authorizationHeader);
        validateBusinessRules(request);

        Instant now = Instant.now();
        BranchFulfillmentSettings entity = repository.findByBranchId(branch.branchId())
                .orElseGet(BranchFulfillmentSettings::new);

        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(now);
            entity.setCreatedByUserId(user.userId());
            entity.setOrganizationId(branch.organizationId());
            entity.setCompanyId(branch.companyId());
            entity.setBranchId(branch.branchId());
        }

        entity.setDeliveryEnabled(Boolean.TRUE.equals(request.deliveryEnabled()));
        entity.setPickupEnabled(Boolean.TRUE.equals(request.pickupEnabled()));
        entity.setMinimumDeliveryOrderMinor(request.minimumDeliveryOrderMinor());
        entity.setDeliveryFeeMinor(request.deliveryFeeMinor());
        entity.setFreeDeliveryFromMinor(request.freeDeliveryFromMinor());
        entity.setDeliveryEstimatedMinMinutes(request.deliveryEstimatedMinMinutes());
        entity.setDeliveryEstimatedMaxMinutes(request.deliveryEstimatedMaxMinutes());
        entity.setPickupEstimatedMinutes(request.pickupEstimatedMinutes());
        entity.setDeliveryPricingMode(parsePricingMode(request.deliveryPricingMode()).name());
        entity.setUpdatedAt(now);
        entity.setUpdatedByUserId(user.userId());

        return toConfiguredResponse(repository.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public FulfillmentQuoteResponse quote(CurrentUser user,
                                          UUID branchId,
                                          FulfillmentQuoteRequest request,
                                          String authorizationHeader) {
        requireActiveRole(authorizationHeader);
        RestaurantBranchRef branch = requireOwnedBranch(user, branchId, authorizationHeader);
        return quoteByBranchId(branch.branchId(), request);
    }

    /**
     * Quote calculation without JWT / restaurant ownership checks.
     * Loads settings by {@code branchId} only and applies the same fee/availability rules.
     */
    @Transactional(readOnly = true)
    public FulfillmentQuoteResponse quoteByBranchId(UUID branchId, FulfillmentQuoteRequest request) {
        var settingsOpt = repository.findByBranchId(branchId);

        boolean configured = settingsOpt.isPresent();
        boolean deliveryEnabled = configured && settingsOpt.get().isDeliveryEnabled();
        boolean pickupEnabled = !configured || settingsOpt.get().isPickupEnabled();

        long minimumOrder = configured ? settingsOpt.get().getMinimumDeliveryOrderMinor() : 0L;
        long deliveryFee = configured ? settingsOpt.get().getDeliveryFeeMinor() : 0L;
        Long freeFrom = configured ? settingsOpt.get().getFreeDeliveryFromMinor() : null;
        int deliveryMin = configured
                ? settingsOpt.get().getDeliveryEstimatedMinMinutes()
                : DEFAULT_DELIVERY_MIN_MINUTES;
        int deliveryMax = configured
                ? settingsOpt.get().getDeliveryEstimatedMaxMinutes()
                : DEFAULT_DELIVERY_MAX_MINUTES;
        int pickupMinutes = configured
                ? settingsOpt.get().getPickupEstimatedMinutes()
                : DEFAULT_PICKUP_MINUTES;

        long itemsTotal = request.itemsTotalMinor();

        if (request.type() == FulfillmentType.DELIVERY) {
            if (!configured) {
                // Unconfigured settings allow pickup only.
                return unavailable(request.type(), itemsTotal, minimumOrder, "DELIVERY_DISABLED",
                        deliveryMin, deliveryMax);
            }
            if (!deliveryEnabled) {
                return unavailable(request.type(), itemsTotal, minimumOrder, "DELIVERY_DISABLED",
                        deliveryMin, deliveryMax);
            }
            DeliveryPricingMode pricingMode = parsePricingMode(
                    settingsOpt.map(BranchFulfillmentSettings::getDeliveryPricingMode).orElse(null));
            if (pricingMode == DeliveryPricingMode.ZONES) {
                return quoteZones(branchId, request, itemsTotal);
            }
            long shortfall = Math.max(0L, minimumOrder - itemsTotal);
            boolean met = shortfall == 0L;
            if (!met) {
                return new FulfillmentQuoteResponse(
                        FulfillmentType.DELIVERY,
                        false,
                        itemsTotal,
                        minimumOrder,
                        false,
                        shortfall,
                        0L,
                        false,
                        itemsTotal,
                        deliveryMin,
                        deliveryMax,
                        "MINIMUM_ORDER_NOT_MET"
                );
            }
            boolean freeApplied = freeFrom != null && itemsTotal >= freeFrom;
            long fee = freeApplied ? 0L : deliveryFee;
            return new FulfillmentQuoteResponse(
                    FulfillmentType.DELIVERY,
                    true,
                    itemsTotal,
                    minimumOrder,
                    true,
                    0L,
                    fee,
                    freeApplied,
                    itemsTotal + fee,
                    deliveryMin,
                    deliveryMax,
                    null
            );
        }

        // PICKUP
        if (!pickupEnabled) {
            return unavailable(FulfillmentType.PICKUP, itemsTotal, 0L, "PICKUP_DISABLED",
                    pickupMinutes, pickupMinutes);
        }
        return new FulfillmentQuoteResponse(
                FulfillmentType.PICKUP,
                true,
                itemsTotal,
                0L,
                true,
                0L,
                0L,
                false,
                itemsTotal,
                pickupMinutes,
                pickupMinutes,
                null
        );
    }

    /**
     * Internal S2S read of whether fulfillment settings were actually saved.
     * Optional {@code companyId}/{@code organizationId} perform a soft ownership
     * check against stored settings (mismatch → 404 BRANCH_NOT_FOUND).
     */
    @Transactional(readOnly = true)
    public InternalFulfillmentSettingsResponse getInternalSettings(UUID branchId,
                                                                   UUID companyId,
                                                                   UUID organizationId) {
        var settingsOpt = repository.findByBranchId(branchId);
        if (settingsOpt.isEmpty()) {
            return new InternalFulfillmentSettingsResponse(
                    false, true, false, DeliveryPricingMode.FLAT.name(), 0);
        }
        BranchFulfillmentSettings settings = settingsOpt.get();
        if (companyId != null && !companyId.equals(settings.getCompanyId())) {
            throw new FulfillmentApiException("BRANCH_NOT_FOUND", HttpStatus.NOT_FOUND, "Branch not found");
        }
        if (organizationId != null && !organizationId.equals(settings.getOrganizationId())) {
            throw new FulfillmentApiException("BRANCH_NOT_FOUND", HttpStatus.NOT_FOUND, "Branch not found");
        }
        DeliveryPricingMode pricingMode = parsePricingMode(settings.getDeliveryPricingMode());
        int activeZones = pricingMode == DeliveryPricingMode.ZONES
                ? (int) deliveryZoneRepository.countByBranchIdAndActiveTrue(branchId)
                : 0;
        return new InternalFulfillmentSettingsResponse(
                true,
                settings.isPickupEnabled(),
                settings.isDeliveryEnabled(),
                pricingMode.name(),
                activeZones
        );
    }

    /**
     * Internal S2S quote for miniapp-restaurant checkout.
     * Optional {@code companyId}/{@code organizationId} on the request perform a soft ownership
     * check against stored settings (mismatch → 404 BRANCH_NOT_FOUND). Does not call restaurant APIs.
     */
    @Transactional(readOnly = true)
    public InternalFulfillmentQuoteResponse quoteInternal(UUID branchId, FulfillmentQuoteRequest request) {
        var settingsOpt = repository.findByBranchId(branchId);
        if (settingsOpt.isPresent()) {
            BranchFulfillmentSettings settings = settingsOpt.get();
            if (request.companyId() != null && !request.companyId().equals(settings.getCompanyId())) {
                throw new FulfillmentApiException("BRANCH_NOT_FOUND", HttpStatus.NOT_FOUND, "Branch not found");
            }
            if (request.organizationId() != null
                    && !request.organizationId().equals(settings.getOrganizationId())) {
                throw new FulfillmentApiException("BRANCH_NOT_FOUND", HttpStatus.NOT_FOUND, "Branch not found");
            }
        }

        FulfillmentQuoteResponse quote = quoteByBranchId(branchId, request);
        String pricingMode = settingsOpt
                .map(s -> parsePricingMode(s.getDeliveryPricingMode()).name())
                .orElse(DeliveryPricingMode.FLAT.name());
        Long freeThreshold;
        if (quote.zoneId() != null
                && request.latitude() != null
                && request.longitude() != null) {
            freeThreshold = deliveryZoneService.matchActiveZone(
                            branchId, request.longitude(), request.latitude())
                    .filter(zone -> zone.getId().equals(quote.zoneId()))
                    .map(DeliveryZone::getFreeDeliveryFromMinor)
                    .orElse(null);
        } else {
            freeThreshold = settingsOpt.map(BranchFulfillmentSettings::getFreeDeliveryFromMinor).orElse(null);
        }
        return new InternalFulfillmentQuoteResponse(
                quote.type(),
                quote.available(),
                quote.itemsTotalMinor(),
                quote.deliveryFeeMinor(),
                quote.minimumOrderMinor(),
                quote.minimumOrderMet(),
                freeThreshold,
                quote.estimatedMinMinutes(),
                quote.estimatedMaxMinutes(),
                quote.reasonCode(),
                pricingMode,
                quote.zoneId(),
                quote.zoneName()
        );
    }

    /**
     * Company-wide delivery routing: pick the serving branch from overlapping active zones
     * among candidate branches. Zone {@code priority} is the sole business rule for overlaps.
     *
     * <p>Fallback: when no candidate uses ZONES mode, a single FLAT delivery-enabled branch
     * may serve the whole city (backward compatible single-branch flat pricing).
     */
    @Transactional(readOnly = true)
    public InternalCompanyDeliveryRouteResponse routeInternal(UUID companyId,
                                                              InternalCompanyDeliveryRouteRequest request) {
        if (companyId == null) {
            throw new FulfillmentApiException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "companyId is required");
        }
        long itemsTotal = request.itemsTotalMinor();
        double lat = request.latitude();
        double lon = request.longitude();
        try {
            ru.kzn.buzanov.delivery.fulfillment.geo.GeoJsonGeometry.requireFiniteCoordinate(lon, lat);
        } catch (IllegalArgumentException ex) {
            return routeUnavailable(null, itemsTotal, "ADDRESS_REQUIRED", DeliveryPricingMode.ZONES.name());
        }

        Set<UUID> requested = new LinkedHashSet<>(request.branchIds());
        List<BranchFulfillmentSettings> settingsRows = repository.findByBranchIdIn(requested);
        Map<UUID, BranchFulfillmentSettings> byBranch = settingsRows.stream()
                .collect(Collectors.toMap(BranchFulfillmentSettings::getBranchId, Function.identity(), (a, b) -> a));

        List<UUID> zonesEligible = new ArrayList<>();
        List<UUID> flatEligible = new ArrayList<>();
        for (UUID branchId : requested) {
            BranchFulfillmentSettings settings = byBranch.get(branchId);
            if (settings == null || !settings.isDeliveryEnabled()) {
                continue;
            }
            if (request.organizationId() != null
                    && !request.organizationId().equals(settings.getOrganizationId())) {
                continue;
            }
            if (!companyId.equals(settings.getCompanyId())) {
                continue;
            }
            DeliveryPricingMode mode = parsePricingMode(settings.getDeliveryPricingMode());
            if (mode == DeliveryPricingMode.ZONES) {
                zonesEligible.add(branchId);
            } else {
                flatEligible.add(branchId);
            }
        }

        if (!zonesEligible.isEmpty()) {
            DeliveryZone zone = deliveryZoneService
                    .matchActiveZoneAmongBranches(companyId, zonesEligible, lon, lat)
                    .orElse(null);
            if (zone == null) {
                String issue = deliveryZoneRepository
                        .findByCompanyIdAndBranchIdInAndActiveTrueOrderByPriorityDescCreatedAtAscIdAsc(
                                companyId, zonesEligible)
                        .isEmpty()
                        ? "NO_DELIVERY_ZONES"
                        : "OUTSIDE_ZONES";
                return routeUnavailable(null, itemsTotal, issue, DeliveryPricingMode.ZONES.name());
            }
            FulfillmentQuoteResponse quote = quoteZones(zone.getBranchId(),
                    new FulfillmentQuoteRequest(
                            FulfillmentType.DELIVERY, itemsTotal, companyId, request.organizationId(), lat, lon),
                    itemsTotal);
            return toRouteResponse(zone.getBranchId(), quote, zone.getFreeDeliveryFromMinor());
        }

        if (flatEligible.size() == 1) {
            UUID branchId = flatEligible.getFirst();
            FulfillmentQuoteResponse quote = quoteByBranchId(branchId,
                    new FulfillmentQuoteRequest(
                            FulfillmentType.DELIVERY, itemsTotal, companyId, request.organizationId(), lat, lon));
            Long freeThreshold = byBranch.get(branchId) == null
                    ? null
                    : byBranch.get(branchId).getFreeDeliveryFromMinor();
            return toRouteResponse(branchId, quote, freeThreshold);
        }

        if (flatEligible.isEmpty() && zonesEligible.isEmpty()) {
            return routeUnavailable(null, itemsTotal, "DELIVERY_DISABLED", DeliveryPricingMode.FLAT.name());
        }
        // Multiple FLAT branches and no zones: cannot auto-route without zones.
        return routeUnavailable(null, itemsTotal, "NO_DELIVERY_ZONES", DeliveryPricingMode.FLAT.name());
    }

    private static InternalCompanyDeliveryRouteResponse toRouteResponse(UUID branchId,
                                                                        FulfillmentQuoteResponse quote,
                                                                        Long freeThreshold) {
        return new InternalCompanyDeliveryRouteResponse(
                branchId,
                quote.type(),
                quote.available(),
                quote.itemsTotalMinor(),
                quote.deliveryFeeMinor(),
                quote.minimumOrderMinor(),
                quote.minimumOrderMet(),
                freeThreshold,
                quote.estimatedMinMinutes(),
                quote.estimatedMaxMinutes(),
                quote.reasonCode(),
                quote.zoneId() != null ? DeliveryPricingMode.ZONES.name() : DeliveryPricingMode.FLAT.name(),
                quote.zoneId(),
                quote.zoneName()
        );
    }

    private static InternalCompanyDeliveryRouteResponse routeUnavailable(UUID branchId,
                                                                         long itemsTotal,
                                                                         String issueCode,
                                                                         String pricingMode) {
        return new InternalCompanyDeliveryRouteResponse(
                branchId,
                FulfillmentType.DELIVERY,
                false,
                itemsTotal,
                0L,
                0L,
                true,
                null,
                null,
                null,
                issueCode,
                pricingMode,
                null,
                null
        );
    }

    private RestaurantBranchRef requireOwnedBranch(CurrentUser user,
                                                   UUID branchId,
                                                   String authorizationHeader) {
        RestaurantBranchRef branch = restaurantAccessClient.requireBranch(branchId, authorizationHeader);
        if (user.organizationId() != null
                && !user.organizationId().equals(branch.organizationId())) {
            throw new FulfillmentApiException("BRANCH_NOT_FOUND", HttpStatus.NOT_FOUND, "Branch not found");
        }
        return branch;
    }

    private RestaurantMembershipRole requireActiveRole(String authorizationHeader) {
        return restaurantAccessClient.requireMembershipRole(authorizationHeader);
    }

    private static void requireEditor(RestaurantMembershipRole role) {
        if (!role.canEditFulfillmentSettings()) {
            throw new FulfillmentApiException("AUTH_ACCESS_DENIED", HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private void validateBusinessRules(UpdateBranchFulfillmentSettingsRequest request) {
        if (!Boolean.TRUE.equals(request.deliveryEnabled()) && !Boolean.TRUE.equals(request.pickupEnabled())) {
            throw validation("At least one of deliveryEnabled or pickupEnabled must be true");
        }
        requireMoney(request.minimumDeliveryOrderMinor(), "minimumDeliveryOrderMinor");
        requireMoney(request.deliveryFeeMinor(), "deliveryFeeMinor");
        if (request.freeDeliveryFromMinor() != null) {
            requireMoney(request.freeDeliveryFromMinor(), "freeDeliveryFromMinor");
        }
        requireMinutes(request.deliveryEstimatedMinMinutes(), "deliveryEstimatedMinMinutes");
        requireMinutes(request.deliveryEstimatedMaxMinutes(), "deliveryEstimatedMaxMinutes");
        requireMinutes(request.pickupEstimatedMinutes(), "pickupEstimatedMinutes");
        if (request.deliveryEstimatedMinMinutes() > request.deliveryEstimatedMaxMinutes()) {
            throw validation("deliveryEstimatedMinMinutes must be <= deliveryEstimatedMaxMinutes");
        }
        parsePricingMode(request.deliveryPricingMode());
    }

    private FulfillmentQuoteResponse quoteZones(UUID branchId,
                                                FulfillmentQuoteRequest request,
                                                long itemsTotal) {
        if (request.latitude() == null || request.longitude() == null) {
            return unavailable(FulfillmentType.DELIVERY, itemsTotal, 0L, "ADDRESS_REQUIRED", null, null);
        }
        double lat = request.latitude();
        double lon = request.longitude();
        try {
            ru.kzn.buzanov.delivery.fulfillment.geo.GeoJsonGeometry.requireFiniteCoordinate(lon, lat);
        } catch (IllegalArgumentException ex) {
            return unavailable(FulfillmentType.DELIVERY, itemsTotal, 0L, "ADDRESS_REQUIRED", null, null);
        }
        DeliveryZone zone = deliveryZoneService.matchActiveZone(branchId, lon, lat).orElse(null);
        if (zone == null) {
            return unavailable(FulfillmentType.DELIVERY, itemsTotal, 0L, "OUTSIDE_ZONES", null, null);
        }
        long minimumOrder = zone.getMinOrderAmountMinor() == null ? 0L : zone.getMinOrderAmountMinor();
        long shortfall = Math.max(0L, minimumOrder - itemsTotal);
        Integer etaMin = zone.getEtaMinMinutes();
        Integer etaMax = zone.getEtaMaxMinutes();
        if (shortfall > 0) {
            return new FulfillmentQuoteResponse(
                    FulfillmentType.DELIVERY,
                    false,
                    itemsTotal,
                    minimumOrder,
                    false,
                    shortfall,
                    0L,
                    false,
                    itemsTotal,
                    etaMin,
                    etaMax,
                    "MINIMUM_ORDER_NOT_MET",
                    zone.getId(),
                    zone.getName()
            );
        }
        Long freeFrom = zone.getFreeDeliveryFromMinor();
        boolean freeApplied = freeFrom != null && itemsTotal >= freeFrom;
        long fee = freeApplied ? 0L : zone.getDeliveryFeeMinor();
        return new FulfillmentQuoteResponse(
                FulfillmentType.DELIVERY,
                true,
                itemsTotal,
                minimumOrder,
                true,
                0L,
                fee,
                freeApplied,
                itemsTotal + fee,
                etaMin,
                etaMax,
                null,
                zone.getId(),
                zone.getName()
        );
    }

    private static DeliveryPricingMode parsePricingMode(String raw) {
        try {
            return DeliveryPricingMode.fromRaw(raw);
        } catch (IllegalArgumentException ex) {
            throw validation(ex.getMessage());
        }
    }

    private static void requireMoney(long value, String field) {
        if (value < 0 || value > MAX_MONEY_MINOR) {
            throw validation(field + " must be between 0 and " + MAX_MONEY_MINOR);
        }
    }

    private static void requireMinutes(int value, String field) {
        if (value < 1 || value > 1440) {
            throw validation(field + " must be between 1 and 1440");
        }
    }

    private static FulfillmentApiException validation(String detail) {
        return new FulfillmentApiException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, detail);
    }

    private static FulfillmentQuoteResponse unavailable(FulfillmentType type,
                                                        long itemsTotal,
                                                        long minimumOrder,
                                                        String reason,
                                                        Integer minMinutes,
                                                        Integer maxMinutes) {
        return new FulfillmentQuoteResponse(
                type,
                false,
                itemsTotal,
                minimumOrder,
                itemsTotal >= minimumOrder,
                Math.max(0L, minimumOrder - itemsTotal),
                0L,
                false,
                itemsTotal,
                minMinutes,
                maxMinutes,
                reason
        );
    }

    private BranchFulfillmentSettingsResponse toConfiguredResponse(BranchFulfillmentSettings entity) {
        return new BranchFulfillmentSettingsResponse(
                true,
                entity.getBranchId(),
                entity.isDeliveryEnabled(),
                entity.isPickupEnabled(),
                entity.getMinimumDeliveryOrderMinor(),
                entity.getDeliveryFeeMinor(),
                entity.getFreeDeliveryFromMinor(),
                entity.getDeliveryEstimatedMinMinutes(),
                entity.getDeliveryEstimatedMaxMinutes(),
                entity.getPickupEstimatedMinutes(),
                parsePricingMode(entity.getDeliveryPricingMode()).name(),
                entity.getUpdatedAt()
        );
    }

    static BranchFulfillmentSettingsResponse defaults(UUID branchId) {
        return new BranchFulfillmentSettingsResponse(
                false,
                branchId,
                false,
                true,
                0L,
                0L,
                null,
                DEFAULT_DELIVERY_MIN_MINUTES,
                DEFAULT_DELIVERY_MAX_MINUTES,
                DEFAULT_PICKUP_MINUTES,
                DeliveryPricingMode.FLAT.name(),
                null
        );
    }
}
