package ru.kzn.buzanov.delivery.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.dto.ApproveRestaurantRegistrationResponse;
import ru.kzn.buzanov.delivery.dto.RestaurantRegistrationRequestDto;
import ru.kzn.buzanov.delivery.dto.request.CreateRestaurantRegistrationRequest;
import ru.kzn.buzanov.delivery.service.RestaurantRegistrationRequestService;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RestaurantRegistrationRequestController {

    private final RestaurantRegistrationRequestService registrationRequestService;

    @PostMapping("/public/restaurant-registration-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RestaurantRegistrationRequestDto createPublic(
            @Valid @RequestBody CreateRestaurantRegistrationRequest body) {
        return registrationRequestService.createPublic(body);
    }

    @GetMapping("/restaurant-registration-requests")
    public List<RestaurantRegistrationRequestDto> list(
            HttpServletRequest request,
            @RequestParam UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return registrationRequestService.listForService(user.userId(), courierServiceId);
    }

    @GetMapping("/restaurant-registration-requests/{requestId}")
    public RestaurantRegistrationRequestDto get(
            HttpServletRequest request,
            @PathVariable UUID requestId,
            @RequestParam UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return registrationRequestService.get(user.userId(), courierServiceId, requestId);
    }

    @PatchMapping("/restaurant-registration-requests/{requestId}/in-progress")
    public RestaurantRegistrationRequestDto markInProgress(
            HttpServletRequest request,
            @PathVariable UUID requestId,
            @RequestParam UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return registrationRequestService.markInProgress(user.userId(), courierServiceId, requestId);
    }

    @PatchMapping("/restaurant-registration-requests/{requestId}/approve")
    public ApproveRestaurantRegistrationResponse approve(
            HttpServletRequest request,
            @PathVariable UUID requestId,
            @RequestParam UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return registrationRequestService.approve(user.userId(), courierServiceId, requestId);
    }

    @PatchMapping("/restaurant-registration-requests/{requestId}/reject")
    public RestaurantRegistrationRequestDto reject(
            HttpServletRequest request,
            @PathVariable UUID requestId,
            @RequestParam UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return registrationRequestService.reject(user.userId(), courierServiceId, requestId);
    }
}
