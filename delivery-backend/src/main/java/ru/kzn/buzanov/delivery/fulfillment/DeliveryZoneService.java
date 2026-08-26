package ru.kzn.buzanov.delivery.fulfillment;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.fulfillment.dto.DeliveryZoneResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.PublicDeliveryZoneResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.UpsertDeliveryZoneRequest;
import ru.kzn.buzanov.delivery.fulfillment.geo.GeoJsonGeometry;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantAccessClient;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantBranchRef;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantMembershipRole;
import ru.kzn.buzanov.delivery.web.CurrentUser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryZoneService {

    private final DeliveryZoneRepository repository;
    private final BranchFulfillmentSettingsRepository settingsRepository;
    private final RestaurantAccessClient restaurantAccessClient;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<DeliveryZoneResponse> list(CurrentUser user, UUID branchId, String authorizationHeader) {
        requireActiveRole(authorizationHeader);
        RestaurantBranchRef branch = requireOwnedBranch(user, branchId, authorizationHeader);
        return repository.findByBranchIdOrderByPriorityDescCreatedAtAscIdAsc(branch.branchId()).stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional
    public DeliveryZoneResponse create(CurrentUser user,
                                       UUID branchId,
                                       UpsertDeliveryZoneRequest request,
                                       String authorizationHeader) {
        RestaurantMembershipRole role = requireActiveRole(authorizationHeader);
        requireEditor(role);
        RestaurantBranchRef branch = requireOwnedBranch(user, branchId, authorizationHeader);
        Instant now = Instant.now();
        DeliveryZone entity = new DeliveryZone();
        entity.setId(UUID.randomUUID());
        entity.setOrganizationId(branch.organizationId());
        entity.setCompanyId(branch.companyId());
        entity.setBranchId(branch.branchId());
        entity.setCreatedAt(now);
        entity.setCreatedByUserId(user.userId());
        apply(entity, request, now, user.userId());
        return toAdminResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public DeliveryZoneResponse update(CurrentUser user,
                                       UUID branchId,
                                       UUID zoneId,
                                       UpsertDeliveryZoneRequest request,
                                       String authorizationHeader) {
        RestaurantMembershipRole role = requireActiveRole(authorizationHeader);
        requireEditor(role);
        RestaurantBranchRef branch = requireOwnedBranch(user, branchId, authorizationHeader);
        DeliveryZone entity = repository.findByIdAndBranchId(zoneId, branch.branchId())
                .orElseThrow(() -> new FulfillmentApiException(
                        "DELIVERY_ZONE_NOT_FOUND", HttpStatus.NOT_FOUND, "Delivery zone not found"));
        apply(entity, request, Instant.now(), user.userId());
        return toAdminResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(CurrentUser user, UUID branchId, UUID zoneId, String authorizationHeader) {
        RestaurantMembershipRole role = requireActiveRole(authorizationHeader);
        requireEditor(role);
        RestaurantBranchRef branch = requireOwnedBranch(user, branchId, authorizationHeader);
        DeliveryZone entity = repository.findByIdAndBranchId(zoneId, branch.branchId())
                .orElseThrow(() -> new FulfillmentApiException(
                        "DELIVERY_ZONE_NOT_FOUND", HttpStatus.NOT_FOUND, "Delivery zone not found"));
        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<PublicDeliveryZoneResponse> listPublicInternal(UUID branchId,
                                                               UUID companyId,
                                                               UUID organizationId) {
        Optional<BranchFulfillmentSettings> settingsOpt = settingsRepository.findByBranchId(branchId);
        if (settingsOpt.isPresent()) {
            BranchFulfillmentSettings settings = settingsOpt.get();
            if (companyId != null && !companyId.equals(settings.getCompanyId())) {
                throw new FulfillmentApiException("BRANCH_NOT_FOUND", HttpStatus.NOT_FOUND, "Branch not found");
            }
            if (organizationId != null && !organizationId.equals(settings.getOrganizationId())) {
                throw new FulfillmentApiException("BRANCH_NOT_FOUND", HttpStatus.NOT_FOUND, "Branch not found");
            }
            if (DeliveryPricingMode.fromRaw(settings.getDeliveryPricingMode()) != DeliveryPricingMode.ZONES) {
                return List.of();
            }
        } else if (companyId != null || organizationId != null) {
            // Soft ownership when no settings and no zones: empty without 404.
            return List.of();
        }
        return repository.findByBranchIdAndActiveTrueOrderByPriorityDescCreatedAtAscIdAsc(branchId).stream()
                .map(this::toPublicResponse)
                .toList();
    }

    /**
     * Deterministic match: higher priority wins; ties broken by createdAt asc, then id asc.
     */
    @Transactional(readOnly = true)
    public Optional<DeliveryZone> matchActiveZone(UUID branchId, double lon, double lat) {
        GeoJsonGeometry.requireFiniteCoordinate(lon, lat);
        List<DeliveryZone> candidates =
                repository.findByBranchIdAndActiveTrueOrderByPriorityDescCreatedAtAscIdAsc(branchId);
        return firstCovering(candidates, lon, lat);
    }

    /**
     * Cross-branch match among candidate branches of one company.
     * Higher {@code priority} wins across overlapping zones of different branches;
     * ties broken by createdAt asc, then id asc (stable, never random).
     */
    @Transactional(readOnly = true)
    public Optional<DeliveryZone> matchActiveZoneAmongBranches(UUID companyId,
                                                               List<UUID> branchIds,
                                                               double lon,
                                                               double lat) {
        GeoJsonGeometry.requireFiniteCoordinate(lon, lat);
        if (companyId == null || branchIds == null || branchIds.isEmpty()) {
            return Optional.empty();
        }
        List<DeliveryZone> candidates =
                repository.findByCompanyIdAndBranchIdInAndActiveTrueOrderByPriorityDescCreatedAtAscIdAsc(
                        companyId, branchIds);
        return firstCovering(candidates, lon, lat);
    }

    private static Optional<DeliveryZone> firstCovering(List<DeliveryZone> candidates, double lon, double lat) {
        List<DeliveryZone> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
                .comparingInt(DeliveryZone::getPriority).reversed()
                .thenComparing(DeliveryZone::getCreatedAt)
                .thenComparing(DeliveryZone::getId));
        for (DeliveryZone zone : ordered) {
            GeoJsonGeometry geometry = GeoJsonGeometry.parse(zone.getGeometry());
            if (geometry.covers(lon, lat)) {
                return Optional.of(zone);
            }
        }
        return Optional.empty();
    }

    private void apply(DeliveryZone entity, UpsertDeliveryZoneRequest request, Instant now, Long userId) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > 80) {
            throw validation("name must be between 1 and 80 characters");
        }
        GeoJsonGeometry geometry;
        try {
            geometry = GeoJsonGeometry.parse(request.geometry());
        } catch (IllegalArgumentException ex) {
            throw new FulfillmentApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        if (request.priority() == null
                || request.priority() < -1_000_000
                || request.priority() > 1_000_000) {
            throw validation("priority must be between -1000000 and 1000000");
        }
        requireMoney(request.deliveryFeeMinor(), "deliveryFeeMinor");
        if (request.freeDeliveryFromMinor() != null) {
            requireMoney(request.freeDeliveryFromMinor(), "freeDeliveryFromMinor");
        }
        if (request.minOrderAmountMinor() != null) {
            requireMoney(request.minOrderAmountMinor(), "minOrderAmountMinor");
        }
        Integer etaMin = request.etaMinMinutes();
        Integer etaMax = request.etaMaxMinutes();
        if ((etaMin == null) != (etaMax == null)) {
            throw validation("etaMinMinutes and etaMaxMinutes must be provided together");
        }
        if (etaMin != null) {
            requireMinutes(etaMin, "etaMinMinutes");
            requireMinutes(etaMax, "etaMaxMinutes");
            if (etaMin > etaMax) {
                throw validation("etaMinMinutes must be <= etaMaxMinutes");
            }
        }

        entity.setName(name);
        entity.setActive(request.active() == null || Boolean.TRUE.equals(request.active()));
        entity.setPriority(request.priority());
        entity.setGeometry(geometry.canonicalJson());
        entity.setDeliveryFeeMinor(request.deliveryFeeMinor());
        entity.setFreeDeliveryFromMinor(request.freeDeliveryFromMinor());
        entity.setMinOrderAmountMinor(request.minOrderAmountMinor());
        entity.setEtaMinMinutes(etaMin);
        entity.setEtaMaxMinutes(etaMax);
        entity.setUpdatedAt(now);
        entity.setUpdatedByUserId(userId);
    }

    private DeliveryZoneResponse toAdminResponse(DeliveryZone entity) {
        try {
            return new DeliveryZoneResponse(
                    entity.getId(),
                    entity.getBranchId(),
                    entity.getName(),
                    entity.isActive(),
                    entity.getPriority(),
                    objectMapper.readTree(entity.getGeometry()),
                    entity.getDeliveryFeeMinor(),
                    entity.getFreeDeliveryFromMinor(),
                    entity.getMinOrderAmountMinor(),
                    entity.getEtaMinMinutes(),
                    entity.getEtaMaxMinutes(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt()
            );
        } catch (Exception ex) {
            throw new FulfillmentApiException(
                    "INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "Invalid stored geometry");
        }
    }

    private PublicDeliveryZoneResponse toPublicResponse(DeliveryZone entity) {
        try {
            return new PublicDeliveryZoneResponse(
                    entity.getId(),
                    entity.getName(),
                    objectMapper.readTree(entity.getGeometry()),
                    entity.getDeliveryFeeMinor(),
                    entity.getFreeDeliveryFromMinor(),
                    entity.getMinOrderAmountMinor(),
                    entity.getEtaMinMinutes(),
                    entity.getEtaMaxMinutes()
            );
        } catch (Exception ex) {
            throw new FulfillmentApiException(
                    "INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "Invalid stored geometry");
        }
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

    private static void requireMoney(long value, String field) {
        if (value < 0 || value > BranchFulfillmentSettingsService.MAX_MONEY_MINOR) {
            throw validation(field + " must be between 0 and " + BranchFulfillmentSettingsService.MAX_MONEY_MINOR);
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
}
