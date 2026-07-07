package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.domain.CourierRequest;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerReferral;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequest;
import ru.kzn.buzanov.delivery.repository.PartnerReferralRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerReferralService {

    private final PartnerReferralRepository referralRepository;
    private final PartnerProgramRuleService ruleService;

    @Transactional
    public PartnerReferral createFromApprovedCourierRequest(
            CourierRequest request,
            UUID courierServiceId,
            UUID inviteeMemberId,
            Instant connectedAt) {
        if (request.getReferrerMemberId() == null && request.getReferrerOrganizationId() == null) {
            return null;
        }
        if (referralRepository.findBySourceCourierRequestId(request.getId()).isPresent()) {
            return referralRepository.findBySourceCourierRequestId(request.getId()).orElseThrow();
        }

        PartnerReferrerType referrerType = request.getReferrerMemberId() != null
                ? PartnerReferrerType.COURIER
                : PartnerReferrerType.RESTAURANT;

        PartnerProgramRule rule = ruleService.findActiveRule(
                courierServiceId, referrerType, PartnerReferralType.COURIER);

        PartnerReferral referral = new PartnerReferral();
        referral.setId(UUID.randomUUID());
        referral.setCourierServiceId(courierServiceId);
        referral.setReferrerType(referrerType);
        referral.setReferrerMemberId(request.getReferrerMemberId());
        referral.setReferrerOrganizationId(request.getReferrerOrganizationId());
        referral.setInviteeType(PartnerReferralType.COURIER);
        referral.setInviteeMemberId(inviteeMemberId);
        referral.setPartnerCode(request.getPartnerCode());
        referral.setConnectedAt(connectedAt);
        referral.setProgramExpiresAt(calculateExpiresAt(connectedAt, rule));
        referral.setSourceCourierRequestId(request.getId());
        referral.setCreatedAt(connectedAt);
        return referralRepository.save(referral);
    }

    @Transactional
    public PartnerReferral createFromApprovedRestaurantRequest(
            RestaurantRegistrationRequest request,
            UUID courierServiceId,
            UUID inviteeOrganizationId,
            Instant connectedAt) {
        if (request.getCourierMemberId() == null && request.getReferrerOrganizationId() == null) {
            return null;
        }
        if (referralRepository.findBySourceRestaurantRequestId(request.getId()).isPresent()) {
            return referralRepository.findBySourceRestaurantRequestId(request.getId()).orElseThrow();
        }

        PartnerReferrerType referrerType = request.getCourierMemberId() != null
                ? PartnerReferrerType.COURIER
                : PartnerReferrerType.RESTAURANT;

        PartnerProgramRule rule = ruleService.findActiveRule(
                courierServiceId, referrerType, PartnerReferralType.RESTAURANT);

        PartnerReferral referral = new PartnerReferral();
        referral.setId(UUID.randomUUID());
        referral.setCourierServiceId(courierServiceId);
        referral.setReferrerType(referrerType);
        referral.setReferrerMemberId(request.getCourierMemberId());
        referral.setReferrerOrganizationId(request.getReferrerOrganizationId());
        referral.setInviteeType(PartnerReferralType.RESTAURANT);
        referral.setInviteeOrganizationId(inviteeOrganizationId);
        referral.setPartnerCode(request.getPartnerCode());
        referral.setConnectedAt(connectedAt);
        referral.setProgramExpiresAt(calculateExpiresAt(connectedAt, rule));
        referral.setSourceRestaurantRequestId(request.getId());
        referral.setCreatedAt(connectedAt);
        return referralRepository.save(referral);
    }

    public boolean isActive(PartnerReferral referral, Instant at) {
        if (referral.getProgramExpiresAt() == null) {
            return true;
        }
        return !at.isAfter(referral.getProgramExpiresAt());
    }

    private Instant calculateExpiresAt(Instant connectedAt, PartnerProgramRule rule) {
        if (rule == null || rule.getDurationMonths() == null || rule.getDurationMonths() <= 0) {
            return null;
        }
        return connectedAt.plus(rule.getDurationMonths() * 30L, ChronoUnit.DAYS);
    }
}
