package ru.kzn.buzanov.delivery.fulfillment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentQuoteRequest;
import ru.kzn.buzanov.delivery.fulfillment.dto.InternalCompanyDeliveryRouteRequest;
import ru.kzn.buzanov.delivery.fulfillment.dto.InternalCompanyDeliveryRouteResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.InternalFulfillmentQuoteResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.InternalFulfillmentSettingsResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.PublicDeliveryZoneResponse;

import java.util.List;
import java.util.UUID;

/**
 * Internal S2S endpoints for miniapp-restaurant. Auth via {@code X-Delivery-Service-Key}.
 */
@RestController
@RequiredArgsConstructor
public class InternalFulfillmentQuoteController {

    private final BranchFulfillmentSettingsService service;
    private final DeliveryZoneService deliveryZoneService;

    @GetMapping("/internal/branches/{branchId}/fulfillment-settings")
    public InternalFulfillmentSettingsResponse getSettings(@PathVariable UUID branchId,
                                                           @RequestParam(required = false) UUID companyId,
                                                           @RequestParam(required = false) UUID organizationId) {
        return service.getInternalSettings(branchId, companyId, organizationId);
    }

    @PostMapping("/internal/branches/{branchId}/fulfillment-quote")
    public InternalFulfillmentQuoteResponse quote(@PathVariable UUID branchId,
                                                  @Valid @RequestBody FulfillmentQuoteRequest body) {
        return service.quoteInternal(branchId, body);
    }

    @PostMapping("/internal/companies/{companyId}/delivery-route")
    public InternalCompanyDeliveryRouteResponse route(@PathVariable UUID companyId,
                                                      @Valid @RequestBody InternalCompanyDeliveryRouteRequest body) {
        return service.routeInternal(companyId, body);
    }

    @GetMapping("/internal/branches/{branchId}/delivery-zones")
    public List<PublicDeliveryZoneResponse> listZones(@PathVariable UUID branchId,
                                                      @RequestParam(required = false) UUID companyId,
                                                      @RequestParam(required = false) UUID organizationId) {
        return deliveryZoneService.listPublicInternal(branchId, companyId, organizationId);
    }
}
