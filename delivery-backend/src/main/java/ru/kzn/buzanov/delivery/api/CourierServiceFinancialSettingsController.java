package ru.kzn.buzanov.delivery.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.dto.CourierServiceFinancialSettingsDto;
import ru.kzn.buzanov.delivery.dto.request.UpsertCourierServiceFinancialSettingsRequest;
import ru.kzn.buzanov.delivery.service.CourierServiceFinancialSettingsService;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.util.UUID;

@RestController
@RequestMapping("/courier-services/{courierServiceId}/financial-settings")
@RequiredArgsConstructor
public class CourierServiceFinancialSettingsController {

    private final CourierServiceFinancialSettingsService settingsService;

    @GetMapping
    public CourierServiceFinancialSettingsDto get(
            HttpServletRequest request,
            @PathVariable UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return settingsService.getForService(user.userId(), courierServiceId);
    }

    @PutMapping
    public CourierServiceFinancialSettingsDto upsert(
            HttpServletRequest request,
            @PathVariable UUID courierServiceId,
            @Valid @RequestBody UpsertCourierServiceFinancialSettingsRequest body) {
        var user = CurrentUserHolder.require(request);
        return settingsService.upsert(user.userId(), courierServiceId, body);
    }
}
