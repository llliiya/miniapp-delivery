package ru.kzn.buzanov.delivery.fulfillment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentQuoteRequest;
import ru.kzn.buzanov.delivery.fulfillment.dto.InternalFulfillmentQuoteResponse;

import java.util.UUID;

/**
 * Internal S2S endpoints for miniapp-restaurant. Auth via {@code X-Delivery-Service-Key}.
 */
@RestController
@RequiredArgsConstructor
public class InternalFulfillmentQuoteController {

    private final BranchFulfillmentSettingsService service;

    @PostMapping("/internal/branches/{branchId}/fulfillment-quote")
    public InternalFulfillmentQuoteResponse quote(@PathVariable UUID branchId,
                                                  @Valid @RequestBody FulfillmentQuoteRequest body) {
        return service.quoteInternal(branchId, body);
    }
}
