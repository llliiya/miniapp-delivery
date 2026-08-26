package ru.kzn.buzanov.delivery.fulfillment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.fulfillment.dto.DeliveryZoneResponse;
import ru.kzn.buzanov.delivery.fulfillment.dto.UpsertDeliveryZoneRequest;
import ru.kzn.buzanov.delivery.web.CurrentUser;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DeliveryZoneController {

    private final DeliveryZoneService service;

    @GetMapping("/branches/{branchId}/delivery-zones")
    public List<DeliveryZoneResponse> list(HttpServletRequest request, @PathVariable UUID branchId) {
        CurrentUser user = CurrentUserHolder.require(request);
        return service.list(user, branchId, authorization(request));
    }

    @PostMapping("/branches/{branchId}/delivery-zones")
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryZoneResponse create(HttpServletRequest request,
                                       @PathVariable UUID branchId,
                                       @Valid @RequestBody UpsertDeliveryZoneRequest body) {
        CurrentUser user = CurrentUserHolder.require(request);
        return service.create(user, branchId, body, authorization(request));
    }

    @PutMapping("/branches/{branchId}/delivery-zones/{zoneId}")
    public DeliveryZoneResponse update(HttpServletRequest request,
                                       @PathVariable UUID branchId,
                                       @PathVariable UUID zoneId,
                                       @Valid @RequestBody UpsertDeliveryZoneRequest body) {
        CurrentUser user = CurrentUserHolder.require(request);
        return service.update(user, branchId, zoneId, body, authorization(request));
    }

    @DeleteMapping("/branches/{branchId}/delivery-zones/{zoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request,
                       @PathVariable UUID branchId,
                       @PathVariable UUID zoneId) {
        CurrentUser user = CurrentUserHolder.require(request);
        service.delete(user, branchId, zoneId, authorization(request));
    }

    private static String authorization(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header == null ? "" : header;
    }
}
