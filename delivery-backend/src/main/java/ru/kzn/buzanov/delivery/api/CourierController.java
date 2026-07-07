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
import ru.kzn.buzanov.delivery.dto.CreateCourierResponse;
import ru.kzn.buzanov.delivery.dto.CourierBalancePayoutRequestDto;
import ru.kzn.buzanov.delivery.dto.CourierBalanceSummaryDto;
import ru.kzn.buzanov.delivery.dto.CourierDto;
import ru.kzn.buzanov.delivery.dto.ProvisioningCredentialsDto;
import ru.kzn.buzanov.delivery.dto.request.CreateCourierBalancePayoutRequest;
import ru.kzn.buzanov.delivery.dto.request.CreateCourierRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchCourierRequest;
import ru.kzn.buzanov.delivery.service.CourierBalancePayoutService;
import ru.kzn.buzanov.delivery.service.CourierService;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/couriers")
@RequiredArgsConstructor
public class CourierController {

    private final CourierService courierService;
    private final CourierBalancePayoutService courierBalancePayoutService;

    @GetMapping
    public List<CourierDto> list(
            HttpServletRequest request,
            @RequestParam UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return courierService.list(user.userId(), courierServiceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateCourierResponse create(HttpServletRequest request, @Valid @RequestBody CreateCourierRequest body) {
        var user = CurrentUserHolder.require(request);
        return courierService.create(user.userId(), body);
    }

    @GetMapping("/{memberId}")
    public CourierDto get(HttpServletRequest request, @PathVariable UUID memberId) {
        var user = CurrentUserHolder.require(request);
        return courierService.get(user.userId(), memberId);
    }

    @PatchMapping("/{memberId}")
    public CourierDto patch(
            HttpServletRequest request,
            @PathVariable UUID memberId,
            @RequestBody PatchCourierRequest body) {
        var user = CurrentUserHolder.require(request);
        return courierService.patch(user.userId(), memberId, body);
    }

    @PostMapping("/{memberId}/reset-access")
    public ProvisioningCredentialsDto resetAccess(
            HttpServletRequest request,
            @PathVariable UUID memberId) {
        var user = CurrentUserHolder.require(request);
        return courierService.resetAccess(user.userId(), memberId);
    }

    @GetMapping("/{memberId}/balance")
    public CourierBalanceSummaryDto getBalance(HttpServletRequest request, @PathVariable UUID memberId) {
        var user = CurrentUserHolder.require(request);
        return courierBalancePayoutService.getSummary(user.userId(), memberId);
    }

    @PostMapping("/{memberId}/balance/payout-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public CourierBalancePayoutRequestDto createBalancePayout(
            HttpServletRequest request,
            @PathVariable UUID memberId,
            @Valid @RequestBody CreateCourierBalancePayoutRequest body) {
        var user = CurrentUserHolder.require(request);
        return courierBalancePayoutService.createPayout(user.userId(), memberId, body);
    }

    @PostMapping("/{memberId}/balance/payout-requests/{payoutRequestId}/process")
    public CourierBalancePayoutRequestDto processBalancePayout(
            HttpServletRequest request,
            @PathVariable UUID memberId,
            @PathVariable UUID payoutRequestId,
            @RequestParam UUID courierServiceId,
            @RequestParam boolean approve,
            @RequestParam(required = false) String comment) {
        var user = CurrentUserHolder.require(request);
        return courierBalancePayoutService.processPayout(
                user.userId(), courierServiceId, payoutRequestId, approve, comment);
    }
}
