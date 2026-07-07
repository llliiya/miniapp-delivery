package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.domain.PartnerAccount;
import ru.kzn.buzanov.delivery.domain.PartnerParticipantType;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutMethod;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutStatus;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.dto.PartnerBalanceSummaryDto;
import ru.kzn.buzanov.delivery.repository.PartnerAccountRepository;
import ru.kzn.buzanov.delivery.repository.PartnerAccrualRepository;
import ru.kzn.buzanov.delivery.domain.PartnerBalanceTransferStatus;
import ru.kzn.buzanov.delivery.repository.PartnerBalanceTransferRepository;
import ru.kzn.buzanov.delivery.repository.PartnerPayoutRequestRepository;
import ru.kzn.buzanov.delivery.util.PartnerJsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerAccountService {

    private final PartnerAccountRepository accountRepository;
    private final PartnerAccrualRepository accrualRepository;
    private final PartnerPayoutRequestRepository payoutRepository;
    private final PartnerBalanceTransferRepository transferRepository;
    private final PartnerProgramRuleService ruleService;
    private final PartnerJsonMapper jsonMapper;

    @Transactional
    public PartnerAccount ensureCourierAccount(UUID courierServiceId, UUID memberId) {
        return accountRepository.findByMemberId(memberId).orElseGet(() -> {
            Instant now = Instant.now();
            PartnerAccount account = new PartnerAccount();
            account.setId(UUID.randomUUID());
            account.setCourierServiceId(courierServiceId);
            account.setParticipantType(PartnerParticipantType.COURIER);
            account.setMemberId(memberId);
            account.setCreatedAt(now);
            account.setUpdatedAt(now);
            return accountRepository.save(account);
        });
    }

    @Transactional
    public PartnerAccount ensureRestaurantAccount(UUID courierServiceId, UUID organizationId) {
        return accountRepository.findByOrganizationId(organizationId).orElseGet(() -> {
            Instant now = Instant.now();
            PartnerAccount account = new PartnerAccount();
            account.setId(UUID.randomUUID());
            account.setCourierServiceId(courierServiceId);
            account.setParticipantType(PartnerParticipantType.RESTAURANT);
            account.setOrganizationId(organizationId);
            account.setCreatedAt(now);
            account.setUpdatedAt(now);
            return accountRepository.save(account);
        });
    }

    @Transactional(readOnly = true)
    public PartnerAccount requireById(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Partner account not found: " + accountId));
    }

    @Transactional(readOnly = true)
    public PartnerAccount findCourierAccount(UUID memberId) {
        return accountRepository.findByMemberId(memberId)
                .orElseGet(this::emptySummaryAccount);
    }

    @Transactional(readOnly = true)
    public PartnerAccount findRestaurantAccount(UUID organizationId) {
        return accountRepository.findByOrganizationId(organizationId)
                .orElseGet(this::emptySummaryAccount);
    }

    @Transactional
    public void applyAccrual(PartnerAccount account, BigDecimal rewardAmount) {
        if (rewardAmount == null || rewardAmount.signum() <= 0) {
            throw new IllegalArgumentException("Partner reward amount must be positive");
        }
        syncAccountFromSources(account.getId());
    }

    @Transactional
    public void reverseAccrual(PartnerAccount account, BigDecimal rewardAmount) {
        if (rewardAmount == null || rewardAmount.signum() <= 0) {
            throw new IllegalArgumentException("Partner reward amount must be positive");
        }
        syncAccountFromSources(account.getId());
    }

    @Transactional
    public void reserveForPayout(PartnerAccount account, BigDecimal amount) {
        syncAccountFromSources(account.getId());
    }

    @Transactional
    public void completePayout(PartnerAccount account, BigDecimal amount, boolean transferToMainBalance) {
        syncAccountFromSources(account.getId());
    }

    @Transactional
    public void cancelPayoutReservation(PartnerAccount account, BigDecimal amount) {
        syncAccountFromSources(account.getId());
    }

    @Transactional
    public void syncAccountFromSources(UUID accountId) {
        PartnerAccount account = requireById(accountId);
        PartnerBalanceSummaryDto summary = computeSummary(account, Instant.now());
        account.setBalance(summary.balance());
        account.setAvailableForPayout(summary.eligibleForRequest());
        account.setPendingPayout(summary.awaitingExecution());
        account.setPaidOut(summary.paidOut());
        account.setTransferredToMainBalance(summary.transferredToMainBalance());
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);
    }

    public PartnerBalanceSummaryDto toSummary(PartnerAccount account) {
        if (account.getId() == null) {
            return emptySummary();
        }
        return computeSummary(account, Instant.now());
    }

    private PartnerBalanceSummaryDto computeSummary(PartnerAccount account, Instant now) {
        UUID accountId = account.getId();
        int payoutDay = resolvePayoutDay(account);

        BigDecimal accrued = accrualRepository.sumAccruedAmountByAccountId(accountId);
        BigDecimal reversed = accrualRepository.sumReversedAmountByAccountId(accountId);
        BigDecimal grossBalance = accrued.subtract(reversed);
        BigDecimal awaitingExecution = payoutRepository.sumAmountByAccountIdAndStatuses(
                accountId,
                List.of(
                        PartnerPayoutStatus.PENDING,
                        PartnerPayoutStatus.SCHEDULED,
                        PartnerPayoutStatus.PROCESSING))
                .add(transferRepository.sumAmountByAccountIdAndStatuses(
                        accountId,
                        List.of(PartnerBalanceTransferStatus.SCHEDULED)));
        BigDecimal paidOut = payoutRepository.sumPaidAmountByAccountIdAndMethod(
                accountId, PartnerPayoutMethod.BANK_TRANSFER);
        BigDecimal transferredToMainBalance = transferRepository.sumCompletedAmountByAccountId(accountId);

        BigDecimal balance = grossBalance
                .subtract(paidOut)
                .subtract(transferredToMainBalance);
        if (balance.signum() < 0) {
            balance = BigDecimal.ZERO;
        }

        BigDecimal eligibleForRequest = balance.subtract(awaitingExecution);
        if (eligibleForRequest.signum() < 0) {
            eligibleForRequest = BigDecimal.ZERO;
        }

        BigDecimal accruedNotYetEligible = accrualRepository.sumAccruedNotYetEligibleByAccountId(accountId, now);

        YearMonth upcomingCycle = PartnerPayoutCycle.upcomingPayoutCycleMonth(now, payoutDay);
        LocalDate nextScheduledPayoutDate = PartnerPayoutCycle.upcomingScheduledPayoutDate(now, payoutDay);
        boolean hasPayoutInUpcomingCycle = payoutRepository.existsByPartnerAccountIdAndPayoutCycleMonthAndStatusIn(
                accountId,
                PartnerPayoutCycle.formatCycleMonth(upcomingCycle),
                PartnerPayoutMonthlyLimit.BLOCKING_STATUSES)
                || transferRepository.existsByPartnerAccountIdAndPayoutCycleMonthAndStatusIn(
                        accountId,
                        PartnerPayoutCycle.formatCycleMonth(upcomingCycle),
                        PartnerBalanceTransferService.BLOCKING_STATUSES);
        BigDecimal minPayoutAmount = resolveMinPayoutAmount(account);
        boolean canCreatePayoutRequest = eligibleForRequest.compareTo(minPayoutAmount) >= 0
                && eligibleForRequest.signum() > 0
                && !hasPayoutInUpcomingCycle;

        return new PartnerBalanceSummaryDto(
                balance,
                accruedNotYetEligible,
                eligibleForRequest,
                awaitingExecution,
                accruedNotYetEligible,
                paidOut,
                transferredToMainBalance,
                true,
                PartnerPayoutCycle.formatCycleMonth(upcomingCycle),
                nextScheduledPayoutDate,
                canCreatePayoutRequest);
    }

    private BigDecimal resolveMinPayoutAmount(PartnerAccount account) {
        PartnerReferrerType referrerType = account.getParticipantType() == PartnerParticipantType.COURIER
                ? PartnerReferrerType.COURIER
                : PartnerReferrerType.RESTAURANT;
        PartnerProgramRule rule = ruleService.findActiveRule(
                account.getCourierServiceId(), referrerType, PartnerReferralType.COURIER);
        if (rule == null) {
            rule = ruleService.findActiveRule(
                    account.getCourierServiceId(), referrerType, PartnerReferralType.RESTAURANT);
        }
        if (rule == null || rule.getMinPayoutAmount() == null) {
            return BigDecimal.ZERO;
        }
        return rule.getMinPayoutAmount();
    }

    private int resolvePayoutDay(PartnerAccount account) {
        PartnerReferrerType referrerType = account.getParticipantType() == PartnerParticipantType.COURIER
                ? PartnerReferrerType.COURIER
                : PartnerReferrerType.RESTAURANT;
        PartnerProgramRule rule = ruleService.findActiveRule(
                account.getCourierServiceId(), referrerType, PartnerReferralType.COURIER);
        if (rule == null) {
            rule = ruleService.findActiveRule(
                    account.getCourierServiceId(), referrerType, PartnerReferralType.RESTAURANT);
        }
        if (rule == null) {
            return PartnerPayoutCycle.DEFAULT_PAYOUT_DAY;
        }
        return PartnerPayoutCycle.readPayoutDay(jsonMapper.toMap(rule.getPayoutRestrictions()));
    }

    public PartnerBalanceSummaryDto emptySummary() {
        return new PartnerBalanceSummaryDto(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                null,
                null,
                false);
    }

    private PartnerAccount emptySummaryAccount() {
        PartnerAccount account = new PartnerAccount();
        account.setBalance(BigDecimal.ZERO);
        account.setAvailableForPayout(BigDecimal.ZERO);
        account.setPendingPayout(BigDecimal.ZERO);
        account.setPaidOut(BigDecimal.ZERO);
        account.setTransferredToMainBalance(BigDecimal.ZERO);
        return account;
    }
}
