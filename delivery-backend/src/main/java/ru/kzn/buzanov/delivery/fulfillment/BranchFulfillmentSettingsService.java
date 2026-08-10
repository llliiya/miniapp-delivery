package ru.kzn.buzanov.delivery.fulfillment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchFulfillmentSettingsService {

    public static final long MAX_MONEY_MINOR = 100_000_000L;
    public static final int DEFAULT_DELIVERY_MIN_MINUTES = 45;
    public static final int DEFAULT_DELIVERY_MAX_MINUTES = 90;
    public static final int DEFAULT_PICKUP_MINUTES = 30;

    private final BranchFulfillmentSettingsRepository repository;
    private final RestaurantAccessClient restaurantAccessClient;

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
        Long freeThreshold = settingsOpt.map(BranchFulfillmentSettings::getFreeDeliveryFromMinor).orElse(null);
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
                quote.reasonCode()
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
                null
        );
    }
}
