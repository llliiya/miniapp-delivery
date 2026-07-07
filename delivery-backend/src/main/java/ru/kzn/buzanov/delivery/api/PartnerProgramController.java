package ru.kzn.buzanov.delivery.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.dto.PartnerReferralAdminDto;
import ru.kzn.buzanov.delivery.dto.PartnerBalanceTransferAdminDto;
import ru.kzn.buzanov.delivery.dto.PartnerBalanceTransferDto;
import ru.kzn.buzanov.delivery.dto.PartnerPayoutRequestAdminDto;
import ru.kzn.buzanov.delivery.dto.PartnerPayoutRequestDto;
import ru.kzn.buzanov.delivery.dto.PartnerProgramDto;
import ru.kzn.buzanov.delivery.dto.PartnerProgramRuleDto;
import ru.kzn.buzanov.delivery.dto.request.CreatePartnerBalanceTransferRequest;
import ru.kzn.buzanov.delivery.dto.request.CreatePartnerPayoutRequest;
import ru.kzn.buzanov.delivery.dto.request.UpsertPartnerProgramRuleRequest;
import ru.kzn.buzanov.delivery.service.PartnerBalanceTransferService;
import ru.kzn.buzanov.delivery.service.PartnerPayoutExecutionService;
import ru.kzn.buzanov.delivery.service.PartnerPayoutService;
import ru.kzn.buzanov.delivery.service.PartnerProgramRuleService;
import ru.kzn.buzanov.delivery.service.PartnerProgramService;
import ru.kzn.buzanov.delivery.service.PartnerReferralAdminService;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PartnerProgramController {

    private final PartnerProgramService partnerProgramService;
    private final PartnerProgramRuleService partnerProgramRuleService;
    private final PartnerPayoutService partnerPayoutService;
    private final PartnerBalanceTransferService partnerBalanceTransferService;
    private final PartnerPayoutExecutionService partnerPayoutExecutionService;
    private final PartnerReferralAdminService partnerReferralAdminService;

    @GetMapping("/couriers/{memberId}/partner-program")
    public PartnerProgramDto getCourierPartnerProgram(
            HttpServletRequest request,
            @PathVariable UUID memberId) {
        var user = CurrentUserHolder.require(request);
        return partnerProgramService.getForCourierMember(user.userId(), memberId);
    }

    @GetMapping("/restaurants/{restaurantId}/partner-program")
    public PartnerProgramDto getRestaurantPartnerProgram(
            HttpServletRequest request,
            @PathVariable UUID restaurantId) {
        var user = CurrentUserHolder.require(request);
        return partnerProgramService.getForRestaurant(user.userId(), restaurantId);
    }

    @PostMapping("/couriers/{memberId}/partner-program/balance-transfers")
    public PartnerBalanceTransferDto createCourierBalanceTransfer(
            HttpServletRequest request,
            @PathVariable UUID memberId,
            @Valid @RequestBody CreatePartnerBalanceTransferRequest body) {
        var user = CurrentUserHolder.require(request);
        return partnerBalanceTransferService.createCourierTransfer(user.userId(), memberId, body);
    }

    @PostMapping("/couriers/{memberId}/partner-program/payout-requests")
    public PartnerPayoutRequestDto createCourierPayout(
            HttpServletRequest request,
            @PathVariable UUID memberId,
            @Valid @RequestBody CreatePartnerPayoutRequest body) {
        var user = CurrentUserHolder.require(request);
        return partnerPayoutService.createCourierPayout(user.userId(), memberId, body);
    }

    @PostMapping("/restaurants/{restaurantId}/partner-program/payout-requests")
    public PartnerPayoutRequestDto createRestaurantPayout(
            HttpServletRequest request,
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CreatePartnerPayoutRequest body) {
        var user = CurrentUserHolder.require(request);
        return partnerPayoutService.createRestaurantPayout(user.userId(), restaurantId, body);
    }

    @GetMapping("/partner-program/rules")
    public List<PartnerProgramRuleDto> listRules(
            HttpServletRequest request,
            @RequestParam UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return partnerProgramRuleService.listForService(user.userId(), courierServiceId);
    }

    @PutMapping("/partner-program/rules")
    public PartnerProgramRuleDto upsertRule(
            HttpServletRequest request,
            @RequestParam UUID courierServiceId,
            @Valid @RequestBody UpsertPartnerProgramRuleRequest body) {
        var user = CurrentUserHolder.require(request);
        return partnerProgramRuleService.upsert(user.userId(), courierServiceId, body);
    }

    @GetMapping("/partner-program/payout-requests")
    public List<PartnerPayoutRequestAdminDto> listPayoutRequests(
            HttpServletRequest request,
            @RequestParam UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return partnerPayoutService.listForService(user.userId(), courierServiceId);
    }

    @GetMapping("/partner-program/balance-transfers")
    public List<PartnerBalanceTransferAdminDto> listBalanceTransfers(
            HttpServletRequest request,
            @RequestParam UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return partnerBalanceTransferService.listForService(user.userId(), courierServiceId);
    }

    @GetMapping("/partner-program/referrals")
    public List<PartnerReferralAdminDto> listReferrals(
            HttpServletRequest request,
            @RequestParam UUID courierServiceId) {
        var user = CurrentUserHolder.require(request);
        return partnerReferralAdminService.listForService(user.userId(), courierServiceId);
    }

    @PostMapping("/partner-program/payout-requests/{payoutRequestId}/process")
    public PartnerPayoutRequestDto processPayout(
            HttpServletRequest request,
            @RequestParam UUID courierServiceId,
            @PathVariable UUID payoutRequestId,
            @RequestParam boolean approve,
            @RequestParam(required = false) String comment) {
        var user = CurrentUserHolder.require(request);
        return partnerPayoutService.processPayout(user.userId(), courierServiceId, payoutRequestId, approve, comment);
    }

    @PostMapping("/partner-program/payout-requests/{payoutRequestId}/take-in-work")
    public PartnerPayoutRequestDto takePayoutInWork(
            HttpServletRequest request,
            @RequestParam UUID courierServiceId,
            @PathVariable UUID payoutRequestId) {
        var user = CurrentUserHolder.require(request);
        return partnerPayoutService.takeInWork(user.userId(), courierServiceId, payoutRequestId);
    }

    /** Dev/staff: trigger scheduled partner payout and balance-transfer execution for a date. */
    @PostMapping("/partner-program/dev/process-due-payouts")
    public Map<String, String> processDuePayoutsDev(
            HttpServletRequest request,
            @RequestParam UUID courierServiceId,
            @RequestParam(required = false) LocalDate payoutDate) {
        var user = CurrentUserHolder.require(request);
        partnerProgramRuleService.listForService(user.userId(), courierServiceId);
        LocalDate date = payoutDate != null ? payoutDate : LocalDate.now(ZoneOffset.UTC);
        partnerPayoutExecutionService.processDuePayouts(date);
        return Map.of("status", "ok", "payoutDate", date.toString());
    }
}
