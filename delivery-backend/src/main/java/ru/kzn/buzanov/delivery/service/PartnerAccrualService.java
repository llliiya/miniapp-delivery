package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.PartnerAccrual;
import ru.kzn.buzanov.delivery.domain.PartnerAccrualStatus;
import ru.kzn.buzanov.delivery.domain.PartnerAccount;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerReferral;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.dto.PartnerRuleSnapshotDto;
import ru.kzn.buzanov.delivery.repository.PartnerAccrualRepository;
import ru.kzn.buzanov.delivery.repository.PartnerReferralRepository;
import ru.kzn.buzanov.delivery.util.PartnerJsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerAccrualService {

    private final PartnerReferralRepository referralRepository;
    private final PartnerAccrualRepository accrualRepository;
    private final PartnerProgramRuleService ruleService;
    private final PartnerAccountService accountService;
    private final OrderAccessService orderAccess;
    private final PartnerJsonMapper jsonMapper;

    List<PlannedPartnerAccrual> planForOrder(DeliveryOrder order, Instant now) {
        List<PlannedPartnerAccrual> planned = new ArrayList<>();
        for (PartnerReferral referral : resolveRelevantReferrals(order)) {
            if (!isReferralActive(referral, now)) {
                continue;
            }
            PartnerProgramRule rule = ruleService.findActiveRule(
                    referral.getCourierServiceId(),
                    referral.getReferrerType(),
                    referral.getInviteeType());
            if (rule == null) {
                continue;
            }
            BigDecimal baseAmount = PartnerAccrualCalculator.resolveCalculationBase(
                    rule.getCalculationBase(),
                    order.getPrice(),
                    jsonMapper.toMap(rule.getAccrualConditions()));
            BigDecimal amount = PartnerAccrualCalculator.calculateAmount(rule, baseAmount);
            if (amount.signum() <= 0) {
                continue;
            }
            planned.add(new PlannedPartnerAccrual(
                    referral,
                    rule,
                    baseAmount,
                    amount,
                    ruleService.toSnapshot(rule)));
        }
        return planned;
    }

    @Transactional
    public void applyPlannedAccruals(DeliveryOrder order, List<PlannedPartnerAccrual> planned, Instant now) {
        for (PlannedPartnerAccrual line : planned) {
            if (accrualRepository.existsByOrderIdAndPartnerReferralIdAndStatus(
                    order.getId(), line.referral().getId(), PartnerAccrualStatus.ACCRUED)) {
                continue;
            }
            PartnerAccount account = resolvePartnerAccount(line.referral());

            PartnerAccrual accrual = new PartnerAccrual();
            accrual.setId(UUID.randomUUID());
            accrual.setPartnerAccountId(account.getId());
            accrual.setPartnerReferralId(line.referral().getId());
            accrual.setOrderId(order.getId());
            accrual.setAmount(line.amount());
            accrual.setCalculationBaseAmount(line.calculationBaseAmount());
            accrual.setStatus(PartnerAccrualStatus.ACCRUED);
            accrual.setSettingsSnapshot(jsonMapper.toJson(line.ruleSnapshot()));
            accrual.setCreatedAt(now);
            YearMonth accrualPeriod = PartnerPayoutCycle.accrualPeriodMonth(now);
            YearMonth payoutCycle = PartnerPayoutCycle.payoutCycleMonthForAccrual(now);
            accrual.setAccrualPeriodMonth(PartnerPayoutCycle.formatCycleMonth(accrualPeriod));
            accrual.setPayoutCycleMonth(PartnerPayoutCycle.formatCycleMonth(payoutCycle));
            accrual.setAvailableFrom(PartnerPayoutCycle.availableFrom(payoutCycle));

            try {
                accrualRepository.save(accrual);
            } catch (DataIntegrityViolationException ex) {
                continue;
            }
            accountService.applyAccrual(account, accrual.getAmount());
        }
    }

    @Transactional
    public void reverseOnOrderCancelled(DeliveryOrder order) {
        List<PartnerAccrual> accruals = accrualRepository.findByOrderIdAndStatus(order.getId(), PartnerAccrualStatus.ACCRUED);
        Instant now = Instant.now();
        for (PartnerAccrual accrual : accruals) {
            reverseAccrual(accrual, now);
        }
    }

    private List<PartnerReferral> resolveRelevantReferrals(DeliveryOrder order) {
        List<PartnerReferral> referrals = new ArrayList<>();

        if (order.getCourierUserId() != null) {
            orderAccess.findActiveCourierMembership(order.getCourierUserId(), order.getCourierServiceId())
                    .map(OrganizationMember::getId)
                    .ifPresent(memberId -> referrals.addAll(referralRepository.findByInviteeMemberId(memberId)));
        }

        referrals.addAll(referralRepository.findByInviteeOrganizationId(order.getRestaurantId()));

        java.util.Set<UUID> seen = new java.util.HashSet<>();
        List<PartnerReferral> result = new ArrayList<>();
        for (PartnerReferral referral : referrals) {
            if (!seen.add(referral.getId())) {
                continue;
            }
            if (!referral.getCourierServiceId().equals(order.getCourierServiceId())) {
                continue;
            }
            if (!isReferralRelevantForOrder(referral, order)) {
                continue;
            }
            result.add(referral);
        }
        return result;
    }

    private void reverseAccrual(PartnerAccrual accrual, Instant now) {
        PartnerAccount account = accountService.requireById(accrual.getPartnerAccountId());
        accrual.setStatus(PartnerAccrualStatus.REVERSED);
        accrual.setReversedAt(now);
        accrualRepository.save(accrual);
        accountService.reverseAccrual(account, accrual.getAmount());
    }

    private PartnerAccount resolvePartnerAccount(PartnerReferral referral) {
        if (referral.getReferrerType() == PartnerReferrerType.COURIER) {
            return accountService.ensureCourierAccount(
                    referral.getCourierServiceId(), referral.getReferrerMemberId());
        }
        return accountService.ensureRestaurantAccount(
                referral.getCourierServiceId(), referral.getReferrerOrganizationId());
    }

    private boolean isReferralActive(PartnerReferral referral, Instant at) {
        if (referral.getProgramExpiresAt() != null && at.isAfter(referral.getProgramExpiresAt())) {
            return false;
        }
        PartnerProgramRule rule = ruleService.findActiveRule(
                referral.getCourierServiceId(),
                referral.getReferrerType(),
                referral.getInviteeType());
        return rule != null;
    }

    private boolean isReferralRelevantForOrder(PartnerReferral referral, DeliveryOrder order) {
        if (referral.getInviteeType() == PartnerReferralType.COURIER) {
            if (order.getCourierUserId() == null) {
                return false;
            }
            return orderAccess.findActiveCourierMembership(order.getCourierUserId(), order.getCourierServiceId())
                    .map(OrganizationMember::getId)
                    .map(id -> id.equals(referral.getInviteeMemberId()))
                    .orElse(false);
        }
        return order.getRestaurantId().equals(referral.getInviteeOrganizationId());
    }
}
