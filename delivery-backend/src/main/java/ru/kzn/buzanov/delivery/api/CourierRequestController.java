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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.dto.ApproveCourierRequestResponse;
import ru.kzn.buzanov.delivery.dto.CourierRequestDto;
import ru.kzn.buzanov.delivery.dto.MessengerRegistrationStatusDto;
import ru.kzn.buzanov.delivery.dto.request.CreateCourierRequestRequest;
import ru.kzn.buzanov.delivery.service.CourierRequestService;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CourierRequestController {

    private final CourierRequestService courierRequestService;

    @GetMapping("/public/courier-requests/messenger-status")
    public MessengerRegistrationStatusDto messengerStatus(
            @RequestParam String provider,
            @RequestParam String externalId) {
        return courierRequestService.messengerStatus(provider, externalId);
    }

    @PostMapping("/public/courier-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public CourierRequestDto createPublic(@Valid @RequestBody CreateCourierRequestRequest body) {
        return courierRequestService.create(body);
    }

    @GetMapping("/courier-requests")
    public List<CourierRequestDto> listPending(
            HttpServletRequest request,
            @RequestParam UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return courierRequestService.listPending(user.userId(), courierServiceId);
    }

    @PatchMapping("/courier-requests/{requestId}/approve")
    public ApproveCourierRequestResponse approve(
            HttpServletRequest request,
            @PathVariable UUID requestId,
            @RequestParam UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return courierRequestService.approve(user.userId(), courierServiceId, requestId);
    }

    @PatchMapping("/courier-requests/{requestId}/reject")
    public CourierRequestDto reject(
            HttpServletRequest request,
            @PathVariable UUID requestId,
            @RequestParam UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return courierRequestService.reject(user.userId(), courierServiceId, requestId);
    }
}
