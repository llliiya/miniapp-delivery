package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.api.PartnerPayoutConflictException;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.PartnerAccount;
import ru.kzn.buzanov.delivery.domain.PartnerBalanceTransfer;
import ru.kzn.buzanov.delivery.domain.PartnerBalanceTransferStatus;
import ru.kzn.buzanov.delivery.domain.PartnerLedgerTransaction;
import ru.kzn.buzanov.delivery.domain.PartnerLedgerTransactionType;
import ru.kzn.buzanov.delivery.domain.PartnerParticipantType;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.dto.PartnerBalanceSummaryDto;
import ru.kzn.buzanov.delivery.dto.PartnerBalanceTransferAdminDto;
import ru.kzn.buzanov.delivery.dto.PartnerBalanceTransferDto;
import ru.kzn.buzanov.delivery.dto.request.CreatePartnerBalanceTransferRequest;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.repository.PartnerAccountRepository;
import ru.kzn.buzanov.delivery.repository.PartnerBalanceTransferRepository;
import ru.kzn.buzanov.delivery.repository.PartnerLedgerTransactionRepository;
import ru.kzn.buzanov.delivery.repository.PartnerPayoutRequestRepository;
import ru.kzn.buzanov.delivery.util.PartnerJsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartnerBalanceTransferService {

    static final Set<PartnerBalanceTransferStatus> BLOCKING_STATUSES = EnumSet.of(
            PartnerBalanceTransferStatus.SCHEDULED,
            PartnerBalanceTransferStatus.COMPLETED);

    private final PartnerBalanceTransferRepository transferRepository;
    private final PartnerLedgerTransactionRepository partnerLedgerRepository;
    private final PartnerAccountRepository accountRepository;
    private final PartnerAccountService accountService;
    private final PartnerProgramRuleService ruleService;
    private final CourierBalanceService courierBalanceService;
    private final PartnerPayoutRequestRepository payoutRepository;
    private final PartnerJsonMapper jsonMapper;
    private final AccessControlService accessControl;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public PartnerBalanceTransferDto createCourierTransfer(
            Long userId,
            UUID memberId,
            CreatePartnerBalanceTransferRequest request) {
        PartnerAccount account = accountService.findCourierAccount(memberId);
        if (account.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Партнёрский баланс не найден");
        }
        accessControl.requireActiveMembership(userId, account.getCourierServiceId());
        validateTransfer(account, request);
        return createTransfer(userId, account, request);
    }

    @Transactional(readOnly = true)
    public List<PartnerBalanceTransferDto> listForAccount(UUID accountId) {
        if (accountId == null) {
            return List.of();
        }
        return transferRepository.findByPartnerAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerBalanceTransferAdminDto> listForService(Long userId, UUID courierServiceId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        List<PartnerBalanceTransfer> transfers =
                transferRepository.findByCourierServiceIdOrderByCreatedAtDesc(courierServiceId);
        if (transfers.isEmpty()) {
            return List.of();
        }

        Map<UUID, PartnerAccount> accountsById = accountRepository.findAllById(
                        transfers.stream().map(PartnerBalanceTransfer::getPartnerAccountId).distinct().toList())
                .stream()
                .filter(account -> account.getCourierServiceId().equals(courierServiceId))
                .collect(Collectors.toMap(PartnerAccount::getId, Function.identity()));

        return transfers.stream()
                .map(transfer -> {
                    PartnerAccount account = accountsById.get(transfer.getPartnerAccountId());
                    if (account == null) {
                        return null;
                    }
                    return toAdminDto(transfer, account);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public void processDueTransfers(LocalDate executionDate) {
        List<PartnerBalanceTransfer> dueTransfers =
                transferRepository.findByStatusAndScheduledExecutionDateLessThanEqual(
                        PartnerBalanceTransferStatus.SCHEDULED, executionDate);
        Instant now = Instant.now();
        for (PartnerBalanceTransfer transfer : dueTransfers) {
            executeTransfer(transfer, now);
        }
    }

    private PartnerBalanceTransferDto createTransfer(
            Long userId,
            PartnerAccount account,
            CreatePartnerBalanceTransferRequest request) {
        Instant now = Instant.now();
        int payoutDay = resolvePayoutDay(account);
        YearMonth cycleMonth = PartnerPayoutCycle.upcomingPayoutCycleMonth(now, payoutDay);
        LocalDate scheduledDate = PartnerPayoutCycle.scheduledPayoutDate(cycleMonth, payoutDay);

        PartnerBalanceTransfer transfer = new PartnerBalanceTransfer();
        transfer.setId(UUID.randomUUID());
        transfer.setUserId(userId);
        transfer.setPartnerAccountId(account.getId());
        transfer.setAmount(request.amount());
        transfer.setScheduledExecutionDate(scheduledDate);
        transfer.setStatus(PartnerBalanceTransferStatus.SCHEDULED);
        transfer.setPayoutCycleMonth(PartnerPayoutCycle.formatCycleMonth(cycleMonth));
        transfer.setCreatedAt(now);
        transfer.setUpdatedAt(now);

        accountService.reserveForPayout(account, request.amount());
        PartnerBalanceTransfer saved = transferRepository.save(transfer);
        executeTransfer(saved, now);
        return toDto(saved);
    }

    private void executeTransfer(PartnerBalanceTransfer transfer, Instant now) {
        PartnerAccount account = accountService.requireById(transfer.getPartnerAccountId());
        if (transfer.getStatus() != PartnerBalanceTransferStatus.SCHEDULED) {
            return;
        }

        PartnerLedgerTransaction partnerLedger = new PartnerLedgerTransaction();
        partnerLedger.setId(UUID.randomUUID());
        partnerLedger.setPartnerAccountId(account.getId());
        partnerLedger.setAmount(transfer.getAmount());
        partnerLedger.setType(PartnerLedgerTransactionType.TRANSFER_OUT);
        partnerLedger.setBalanceTransferId(transfer.getId());
        partnerLedger.setCreatedAt(now);
        partnerLedgerRepository.save(partnerLedger);

        UUID mainLedgerId = null;
        if (account.getMemberId() != null) {
            mainLedgerId = courierBalanceService.creditMainBalanceFromPartnerTransfer(
                    account.getMemberId(), transfer.getAmount(), transfer.getId());
        }

        transfer.setPartnerLedgerTransactionId(partnerLedger.getId());
        transfer.setMainBalanceLedgerTransactionId(mainLedgerId);
        transfer.setStatus(PartnerBalanceTransferStatus.COMPLETED);
        transfer.setExecutedAt(now);
        transfer.setUpdatedAt(now);
        transferRepository.save(transfer);

        accountService.completePayout(account, transfer.getAmount(), true);
    }

    private void validateTransfer(PartnerAccount account, CreatePartnerBalanceTransferRequest request) {
        if (account.getParticipantType() != PartnerParticipantType.COURIER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Перевод на общий баланс доступен только курьерам");
        }

        int payoutDay = resolvePayoutDay(account);
        Instant now = Instant.now();
        YearMonth cycleMonth = PartnerPayoutCycle.upcomingPayoutCycleMonth(now, payoutDay);
        ensureCycleLimit(account, PartnerPayoutCycle.formatCycleMonth(cycleMonth));

        PartnerBalanceSummaryDto summary = accountService.toSummary(account);
        if (request.amount().compareTo(summary.eligibleForRequest()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недостаточно средств для перевода");
        }

        PartnerReferrerType referrerType = PartnerReferrerType.COURIER;
        if (!ruleService.isEnabledForReferrer(account.getCourierServiceId(), referrerType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Партнёрская программа выключена");
        }

        PartnerProgramRule rule = findPayoutRule(account);
        BigDecimal minAmount = rule != null ? rule.getMinPayoutAmount() : BigDecimal.ZERO;
        if (request.amount().compareTo(minAmount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Минимальная сумма перевода: " + minAmount);
        }

        if (rule != null) {
            List<String> methods = jsonMapper.toStringList(rule.getPayoutMethods());
            if (!methods.isEmpty() && !methods.contains("TRANSFER_TO_MAIN_BALANCE")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Перевод на общий баланс недоступен");
            }
        }
    }

    void ensureCycleLimit(PartnerAccount account, String payoutCycleMonth) {
        boolean hasBlockingTransfer = transferRepository.existsByPartnerAccountIdAndPayoutCycleMonthAndStatusIn(
                account.getId(),
                payoutCycleMonth,
                BLOCKING_STATUSES);
        if (hasBlockingTransfer) {
            throw new PartnerPayoutConflictException("payout_once_per_month");
        }
        boolean hasBlockingPayout = payoutRepository.existsByPartnerAccountIdAndPayoutCycleMonthAndStatusIn(
                account.getId(),
                payoutCycleMonth,
                PartnerPayoutMonthlyLimit.BLOCKING_STATUSES);
        if (hasBlockingPayout) {
            throw new PartnerPayoutConflictException("payout_once_per_month");
        }
    }

    private int resolvePayoutDay(PartnerAccount account) {
        PartnerProgramRule rule = findPayoutRule(account);
        if (rule == null) {
            return PartnerPayoutCycle.DEFAULT_PAYOUT_DAY;
        }
        return PartnerPayoutCycle.readPayoutDay(jsonMapper.toMap(rule.getPayoutRestrictions()));
    }

    private PartnerProgramRule findPayoutRule(PartnerAccount account) {
        PartnerReferrerType referrerType = PartnerReferrerType.COURIER;
        PartnerProgramRule rule = ruleService.findActiveRule(
                account.getCourierServiceId(), referrerType, PartnerReferralType.COURIER);
        if (rule == null) {
            rule = ruleService.findActiveRule(
                    account.getCourierServiceId(), referrerType, PartnerReferralType.RESTAURANT);
        }
        return rule;
    }

    private PartnerBalanceTransferDto toDto(PartnerBalanceTransfer transfer) {
        return new PartnerBalanceTransferDto(
                transfer.getId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getScheduledExecutionDate(),
                transfer.getExecutedAt(),
                transfer.getCreatedAt());
    }

    private PartnerBalanceTransferAdminDto toAdminDto(PartnerBalanceTransfer transfer, PartnerAccount account) {
        return new PartnerBalanceTransferAdminDto(
                transfer.getId(),
                account.getId(),
                account.getParticipantType(),
                resolveParticipantName(account),
                account.getMemberId(),
                account.getOrganizationId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getScheduledExecutionDate(),
                transfer.getExecutedAt(),
                transfer.getCreatedAt());
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
