package ru.kzn.buzanov.delivery.fulfillment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.fulfillment.dto.BranchFulfillmentSettingsResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentQuoteRequest;
import ru.kzn.buzanov.delivery.fulfillment.dto.FulfillmentQuoteResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.UpdateBranchFulfillmentSettingsRequest;
import ru.kzn.buzanov.delivery.web.CurrentUser;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BranchFulfillmentSettingsController {

    private final BranchFulfillmentSettingsService service;

    @GetMapping("/branches/{branchId}/fulfillment-settings")
    public BranchFulfillmentSettingsResponse get(HttpServletRequest request,
                                                 @PathVariable UUID branchId) {
        CurrentUser user = CurrentUserHolder.require(request);
        return service.getSettings(user, branchId, authorization(request));
    }

    @PutMapping("/branches/{branchId}/fulfillment-settings")
    public BranchFulfillmentSettingsResponse put(HttpServletRequest request,
                                                 @PathVariable UUID branchId,
                                                 @Valid @RequestBody UpdateBranchFulfillmentSettingsRequest body) {
        CurrentUser user = CurrentUserHolder.require(request);
        return service.upsertSettings(user, branchId, body, authorization(request));
    }

    @PostMapping("/branches/{branchId}/fulfillment-quote")
    public FulfillmentQuoteResponse quote(HttpServletRequest request,
                                          @PathVariable UUID branchId,
                                          @Valid @RequestBody FulfillmentQuoteRequest body) {
        CurrentUser user = CurrentUserHolder.require(request);
        return service.quote(user, branchId, body, authorization(request));
    }

    private static String authorization(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header == null ? "" : header;
    }
}
