package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.api.PartnerPayoutConflictException;
import ru.kzn.buzanov.delivery.domain.PartnerAccount;
import ru.kzn.buzanov.delivery.domain.PartnerParticipantType;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutMethod;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutRequest;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutStatus;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutTransferType;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.dto.PartnerBalanceSummaryDto;
import ru.kzn.buzanov.delivery.dto.PartnerPayoutRequestAdminDto;
import ru.kzn.buzanov.delivery.dto.PartnerPayoutRequestDto;
import ru.kzn.buzanov.delivery.dto.request.CreatePartnerPayoutRequest;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.repository.PartnerAccountRepository;
import ru.kzn.buzanov.delivery.repository.PartnerPayoutRequestRepository;
import ru.kzn.buzanov.delivery.util.PartnerJsonMapper;
import ru.kzn.buzanov.delivery.util.PartnerPayoutDetailsSupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartnerPayoutService {

    private final PartnerPayoutRequestRepository payoutRepository;
    private final PartnerBalanceTransferService balanceTransferService;
    private final PartnerAccountRepository accountRepository;
    private final PartnerAccountService accountService;
    private final PartnerProgramRuleService ruleService;
    private final PartnerJsonMapper jsonMapper;
    private final AccessControlService accessControl;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final CourierBalancePayoutService courierBalancePayoutService;

    private static final String BALANCE_SOURCE_PARTNER = "PARTNER";

    @Transactional
    public PartnerPayoutRequestDto createCourierPayout(
            Long userId,
            UUID memberId,
            CreatePartnerPayoutRequest request) {
        if (request.payoutMethod() == PartnerPayoutMethod.TRANSFER_TO_MAIN_BALANCE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Для перевода на общий баланс используйте отдельный запрос");
        }
        PartnerAccount account = accountService.findCourierAccount(memberId);
        if (account.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Партнёрский баланс не найден");
        }
        accessControl.requireActiveMembership(userId, account.getCourierServiceId());
        validatePayout(account, request);
        return createPayout(account, request);
    }

    @Transactional
    public PartnerPayoutRequestDto createRestaurantPayout(
            Long userId,
            UUID restaurantId,
            CreatePartnerPayoutRequest request) {
        accessControl.requireRestaurant(restaurantId);
        accessControl.requireActiveMembership(userId, restaurantId);
        PartnerAccount account = accountService.findRestaurantAccount(restaurantId);
        if (account.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Партнёрский баланс не найден");
        }
        validatePayout(account, request);
        return createPayout(account, request);
    }

    @Transactional
    public PartnerPayoutRequestDto takeInWork(
            Long userId,
            UUID courierServiceId,
            UUID payoutRequestId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        PartnerPayoutRequest payout = requireServicePayout(courierServiceId, payoutRequestId);
        if (payout.getStatus() != PartnerPayoutStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка недоступна для обработки");
        }
        if (!hasCompletePayoutDetails(payout)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Заявка не готова к выплате: отсутствуют банковские реквизиты");
        }
        Instant now = Instant.now();
        payout.setStatus(PartnerPayoutStatus.PROCESSING);
        payout.setUpdatedAt(now);
        return toDto(payoutRepository.save(payout));
    }

    @Transactional
    public PartnerPayoutRequestDto processPayout(
            Long userId,
            UUID courierServiceId,
            UUID payoutRequestId,
            boolean approve,
            String comment) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        PartnerPayoutRequest payout = requireServicePayout(courierServiceId, payoutRequestId);

        if (payout.getStatus() != PartnerPayoutStatus.PENDING
                && payout.getStatus() != PartnerPayoutStatus.PROCESSING
                && payout.getStatus() != PartnerPayoutStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже обработана");
        }

        PartnerAccount account = accountService.requireById(payout.getPartnerAccountId());
        Instant now = Instant.now();

        if (!approve) {
            if (comment == null || comment.isBlank()) {
                throw new PartnerPayoutConflictException(
                        "partner_payout_rejection_comment_required",
                        null,
                        HttpStatus.BAD_REQUEST);
            }
            payout.setStatus(PartnerPayoutStatus.REJECTED);
            payout.setProcessedAt(now);
            payout.setProcessedBy(userId);
            payout.setUpdatedAt(now);
            appendRejectionComment(payout, comment.trim());
            PartnerPayoutRequestDto rejected = toDto(payoutRepository.save(payout));
            accountService.cancelPayoutReservation(account, payout.getAmount());
            return rejected;
        }

        if (!hasCompletePayoutDetails(payout)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Заявка не готова к выплате: отсутствуют банковские реквизиты");
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!PartnerPayoutCycle.canConfirmPayout(today, payout.getScheduledPayoutDate())) {
            throw new PartnerPayoutConflictException("partner_payout_date_not_reached");
        }

        assertPayoutAmountReserved(account, payout);

        payout.setStatus(PartnerPayoutStatus.PAID);
        payout.setProcessedAt(now);
        payout.setProcessedBy(userId);
        payout.setUpdatedAt(now);
        PartnerPayoutRequestDto paid = toDto(payoutRepository.save(payout));
        accountService.completePayout(account, payout.getAmount(), false);
        return paid;
    }

    @Transactional(readOnly = true)
    public List<PartnerPayoutRequestDto> listForAccount(UUID accountId) {
        if (accountId == null) {
            return List.of();
        }
        return payoutRepository.findByPartnerAccountIdOrderByCreatedAtDesc(accountId).stream()
                .filter(p -> p.getPayoutMethod() == PartnerPayoutMethod.BANK_TRANSFER)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerPayoutRequestAdminDto> listForService(Long userId, UUID courierServiceId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        List<PartnerPayoutRequestAdminDto> partnerPayouts = listPartnerPayoutsForService(courierServiceId);
        List<PartnerPayoutRequestAdminDto> mainBalancePayouts =
                courierBalancePayoutService.listAdminPayouts(courierServiceId);
        return java.util.stream.Stream.concat(partnerPayouts.stream(), mainBalancePayouts.stream())
                .sorted(java.util.Comparator.comparing(PartnerPayoutRequestAdminDto::createdAt).reversed())
                .toList();
    }

    private List<PartnerPayoutRequestAdminDto> listPartnerPayoutsForService(UUID courierServiceId) {
        List<PartnerPayoutRequest> payouts =
                payoutRepository.findByCourierServiceIdOrderByCreatedAtDesc(courierServiceId);
        if (payouts.isEmpty()) {
            return List.of();
        }

        Map<UUID, PartnerAccount> accountsById = accountRepository.findAllById(
                        payouts.stream().map(PartnerPayoutRequest::getPartnerAccountId).distinct().toList())
                .stream()
                .filter(account -> account.getCourierServiceId().equals(courierServiceId))
                .collect(Collectors.toMap(PartnerAccount::getId, Function.identity()));

        return payouts.stream()
                .filter(p -> p.getPayoutMethod() == PartnerPayoutMethod.BANK_TRANSFER)
                .map(payout -> {
                    PartnerAccount account = accountsById.get(payout.getPartnerAccountId());
                    if (account == null) {
                        return null;
                    }
                    return toAdminDto(payout, account);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private PartnerPayoutRequest requireServicePayout(UUID courierServiceId, UUID payoutRequestId) {
        PartnerPayoutRequest payout = payoutRepository.findById(payoutRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена"));
        PartnerAccount account = accountService.requireById(payout.getPartnerAccountId());
        if (!account.getCourierServiceId().equals(courierServiceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа");
        }
        return payout;
    }

    private PartnerPayoutRequestDto createPayout(PartnerAccount account, CreatePartnerPayoutRequest request) {
        Instant now = Instant.now();
        int payoutDay = resolvePayoutDay(account);
        YearMonth cycleMonth = PartnerPayoutCycle.upcomingPayoutCycleMonth(now, payoutDay);
        LocalDate scheduledDate = PartnerPayoutCycle.scheduledPayoutDate(cycleMonth, payoutDay);
        String cycleMonthValue = PartnerPayoutCycle.formatCycleMonth(cycleMonth);

        PartnerPayoutRequest payout = new PartnerPayoutRequest();
        payout.setId(UUID.randomUUID());
        payout.setPartnerAccountId(account.getId());
        payout.setAmount(request.amount());
        payout.setPayoutMethod(request.payoutMethod());
        payout.setStatus(PartnerPayoutStatus.SCHEDULED);
        payout.setScheduledPayoutDate(scheduledDate);
        payout.setPayoutCycleMonth(cycleMonthValue);
        payout.setPayoutDetails(jsonMapper.toJson(PartnerPayoutDetailsSupport.toMap(request.payoutDetails())));
        payout.setCreatedAt(now);
        payout.setUpdatedAt(now);

        accountService.reserveForPayout(account, request.amount());
        return toDto(payoutRepository.save(payout));
    }

    private void validatePayout(PartnerAccount account, CreatePartnerPayoutRequest request) {
        if (request.payoutMethod() == PartnerPayoutMethod.BANK_TRANSFER) {
            PartnerPayoutDetailsSupport.requireForBankTransfer(request.payoutDetails());
        }

        int payoutDay = resolvePayoutDay(account);
        Instant now = Instant.now();
        YearMonth cycleMonth = PartnerPayoutCycle.upcomingPayoutCycleMonth(now, payoutDay);
        ensureCyclePayoutLimit(account, PartnerPayoutCycle.formatCycleMonth(cycleMonth));

        PartnerBalanceSummaryDto summary = accountService.toSummary(account);
        if (request.amount().compareTo(summary.eligibleForRequest()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недостаточно средств для выплаты");
        }

        PartnerReferrerType referrerType = account.getParticipantType() == PartnerParticipantType.COURIER
                ? PartnerReferrerType.COURIER
                : PartnerReferrerType.RESTAURANT;

        if (!ruleService.isEnabledForReferrer(account.getCourierServiceId(), referrerType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Партнёрская программа выключена");
        }

        PartnerProgramRule rule = findPayoutRule(account);

        BigDecimal minAmount = rule != null ? rule.getMinPayoutAmount() : BigDecimal.ZERO;
        if (request.amount().compareTo(minAmount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Минимальная сумма выплаты: " + minAmount);
        }

        if (rule != null) {
            List<String> methods = jsonMapper.toStringList(rule.getPayoutMethods());
            if (!methods.isEmpty() && !methods.contains(request.payoutMethod().name())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Способ выплаты недоступен");
            }
        }

        if (account.getParticipantType() == PartnerParticipantType.RESTAURANT
                && request.payoutMethod() == PartnerPayoutMethod.TRANSFER_TO_MAIN_BALANCE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Для объектов доступен только банковский перевод");
        }
        if (request.payoutMethod() != PartnerPayoutMethod.BANK_TRANSFER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимый способ выплаты");
        }
    }

    private void ensureCyclePayoutLimit(PartnerAccount account, String payoutCycleMonth) {
        balanceTransferService.ensureCycleLimit(account, payoutCycleMonth);
    }

    private int resolvePayoutDay(PartnerAccount account) {
        PartnerProgramRule rule = findPayoutRule(account);
        if (rule == null) {
            return PartnerPayoutCycle.DEFAULT_PAYOUT_DAY;
        }
        return PartnerPayoutCycle.readPayoutDay(jsonMapper.toMap(rule.getPayoutRestrictions()));
    }

    private PartnerProgramRule findPayoutRule(PartnerAccount account) {
        PartnerReferrerType referrerType = account.getParticipantType() == PartnerParticipantType.COURIER
                ? PartnerReferrerType.COURIER
                : PartnerReferrerType.RESTAURANT;
        PartnerProgramRule rule = ruleService.findActiveRule(
                account.getCourierServiceId(), referrerType, PartnerReferralType.COURIER);
        if (rule == null) {
            rule = ruleService.findActiveRule(
                    account.getCourierServiceId(), referrerType, PartnerReferralType.RESTAURANT);
        }
        return rule;
    }

    private boolean hasCompletePayoutDetails(PartnerPayoutRequest payout) {
        return PartnerPayoutDetailsSupport.hasRequiredBankDetails(jsonMapper.toMap(payout.getPayoutDetails()));
    }

    private void assertPayoutAmountReserved(PartnerAccount account, PartnerPayoutRequest payout) {
        if (payout.getStatus() != PartnerPayoutStatus.PENDING
                && payout.getStatus() != PartnerPayoutStatus.PROCESSING
                && payout.getStatus() != PartnerPayoutStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сумма заявки не зарезервирована");
        }
        PartnerBalanceSummaryDto summary = accountService.toSummary(account);
        if (summary.balance().compareTo(payout.getAmount()) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Сумма заявки превышает баланс участника");
        }
    }

    private void appendRejectionComment(PartnerPayoutRequest payout, String comment) {
        Map<String, Object> details = new LinkedHashMap<>(jsonMapper.toMap(payout.getPayoutDetails()));
        details.put("rejectionComment", comment);
        payout.setPayoutDetails(jsonMapper.toJson(details));
    }

    private PartnerPayoutRequestDto toDto(PartnerPayoutRequest payout) {
        Map<String, Object> details = jsonMapper.toMap(payout.getPayoutDetails());
        return new PartnerPayoutRequestDto(
                payout.getId(),
                payout.getAmount(),
                payout.getPayoutMethod(),
                payout.getStatus(),
                payout.getScheduledPayoutDate(),
                payout.getCreatedAt(),
                payout.getProcessedAt(),
                PartnerPayoutDetailsSupport.resolveTransferType(details),
                PartnerPayoutDetailsSupport.maskRequisites(details),
                PartnerPayoutDetailsSupport.readRecipientName(details),
                PartnerPayoutDetailsSupport.readRejectionComment(details));
    }

    private PartnerPayoutRequestAdminDto toAdminDto(PartnerPayoutRequest payout, PartnerAccount account) {
        Map<String, Object> details = jsonMapper.toMap(payout.getPayoutDetails());
        PartnerPayoutTransferType transferType = PartnerPayoutDetailsSupport.resolveTransferType(details);
        String cardNumber = transferType == PartnerPayoutTransferType.CARD
                ? PartnerPayoutDetailsSupport.readCardNumber(details)
                : null;
        String phoneNumber = transferType == PartnerPayoutTransferType.SBP_PHONE
                ? PartnerPayoutDetailsSupport.readPhoneNumber(details)
                : null;
        String recipientName = PartnerPayoutDetailsSupport.readRecipientName(details);
        return new PartnerPayoutRequestAdminDto(
                payout.getId(),
                account.getId(),
                account.getParticipantType(),
                resolveParticipantName(account),
                account.getMemberId(),
                account.getOrganizationId(),
                BALANCE_SOURCE_PARTNER,
                payout.getAmount(),
                payout.getPayoutMethod(),
                payout.getStatus(),
                payout.getScheduledPayoutDate(),
                payout.getCreatedAt(),
                payout.getProcessedAt(),
                transferType,
                cardNumber,
                phoneNumber,
                recipientName,
                PartnerPayoutDetailsSupport.readBankName(details),
                PartnerPayoutDetailsSupport.hasCompletePayoutDetails(details));
    }

    private String resolveParticipantName(PartnerAccount account) {
        if (account.getParticipantType() == PartnerParticipantType.COURIER && account.getMemberId() != null) {
            return memberRepository.findById(account.getMemberId())
                    .map(member -> member.getDisplayName() != null ? member.getDisplayName() : "Курьер")
                    .orElse("Курьер");
        }
        if (account.getOrganizationId() != null) {
            return organizationRepository.findById(account.getOrganizationId())
                    .map(Organization::getName)
                    .orElse("Объект");
        }
        return "—";
    }
}
