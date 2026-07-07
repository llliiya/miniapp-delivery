package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.domain.CourierRequest;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerReferral;
import ru.kzn.buzanov.delivery.domain.PartnerReferralJournalStatus;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequest;
import ru.kzn.buzanov.delivery.dto.PartnerReferralAdminDto;
import ru.kzn.buzanov.delivery.repository.CourierRequestRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.repository.PartnerAccrualRepository;
import ru.kzn.buzanov.delivery.repository.PartnerProgramRuleRepository;
import ru.kzn.buzanov.delivery.repository.PartnerReferralAccrualStats;
import ru.kzn.buzanov.delivery.repository.PartnerReferralRepository;
import ru.kzn.buzanov.delivery.repository.RestaurantRegistrationRequestRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartnerReferralAdminService {

    private final PartnerReferralRepository referralRepository;
    private final PartnerAccrualRepository accrualRepository;
    private final PartnerProgramRuleRepository ruleRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final CourierRequestRepository courierRequestRepository;
    private final RestaurantRegistrationRequestRepository restaurantRequestRepository;
    private final AccessControlService accessControl;

    @Transactional(readOnly = true)
    public List<PartnerReferralAdminDto> listForService(Long userId, UUID courierServiceId) {
        accessControl.requireServiceStaff(userId, courierServiceId);

        List<PartnerReferral> referrals =
                referralRepository.findByCourierServiceIdOrderByCreatedAtDesc(courierServiceId);
        if (referrals.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now();
        Map<UUID, PartnerReferralAccrualStats> statsByReferralId = loadAccrualStats(referrals);
        Map<UUID, OrganizationMember> membersById = loadMembers(referrals);
        Map<UUID, Organization> organizationsById = loadOrganizations(referrals);
        Map<UUID, CourierRequest> courierRequestsById = loadCourierRequests(referrals);
        Map<UUID, RestaurantRegistrationRequest> restaurantRequestsById = loadRestaurantRequests(referrals);
        Map<String, PartnerProgramRule> rulesByKey = loadRules(courierServiceId);

        return referrals.stream()
                .map(referral -> toAdminDto(
                        referral,
                        now,
                        statsByReferralId.get(referral.getId()),
                        membersById,
                        organizationsById,
                        courierRequestsById,
                        restaurantRequestsById,
                        rulesByKey))
                .toList();
    }

    private Map<UUID, PartnerReferralAccrualStats> loadAccrualStats(List<PartnerReferral> referrals) {
        List<UUID> referralIds = referrals.stream().map(PartnerReferral::getId).toList();
        return accrualRepository.aggregateByReferralIds(referralIds).stream()
                .collect(Collectors.toMap(PartnerReferralAccrualStats::getPartnerReferralId, Function.identity()));
    }

    private Map<UUID, OrganizationMember> loadMembers(List<PartnerReferral> referrals) {
        Set<UUID> memberIds = new HashSet<>();
        for (PartnerReferral referral : referrals) {
            if (referral.getReferrerMemberId() != null) {
                memberIds.add(referral.getReferrerMemberId());
            }
            if (referral.getInviteeMemberId() != null) {
                memberIds.add(referral.getInviteeMemberId());
            }
        }
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(OrganizationMember::getId, Function.identity()));
    }

    private Map<UUID, Organization> loadOrganizations(List<PartnerReferral> referrals) {
        Set<UUID> organizationIds = new HashSet<>();
        for (PartnerReferral referral : referrals) {
            if (referral.getReferrerOrganizationId() != null) {
                organizationIds.add(referral.getReferrerOrganizationId());
            }
            if (referral.getInviteeOrganizationId() != null) {
                organizationIds.add(referral.getInviteeOrganizationId());
            }
        }
        if (organizationIds.isEmpty()) {
            return Map.of();
        }
        return organizationRepository.findByIdIn(organizationIds).stream()
                .collect(Collectors.toMap(Organization::getId, Function.identity()));
    }

    private Map<UUID, CourierRequest> loadCourierRequests(List<PartnerReferral> referrals) {
        Set<UUID> requestIds = referrals.stream()
                .map(PartnerReferral::getSourceCourierRequestId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (requestIds.isEmpty()) {
            return Map.of();
        }
        return courierRequestRepository.findAllById(requestIds).stream()
                .collect(Collectors.toMap(CourierRequest::getId, Function.identity()));
    }

    private Map<UUID, RestaurantRegistrationRequest> loadRestaurantRequests(List<PartnerReferral> referrals) {
        Set<UUID> requestIds = referrals.stream()
                .map(PartnerReferral::getSourceRestaurantRequestId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (requestIds.isEmpty()) {
            return Map.of();
        }
        return restaurantRequestRepository.findAllById(requestIds).stream()
                .collect(Collectors.toMap(RestaurantRegistrationRequest::getId, Function.identity()));
    }

    private Map<String, PartnerProgramRule> loadRules(UUID courierServiceId) {
        Map<String, PartnerProgramRule> rules = new HashMap<>();
        for (PartnerProgramRule rule : ruleRepository.findByCourierServiceIdOrderByReferrerTypeAscInviteeTypeAsc(
                courierServiceId)) {
            rules.put(ruleKey(rule.getReferrerType(), rule.getInviteeType()), rule);
        }
        return rules;
    }

    private PartnerReferralAdminDto toAdminDto(
            PartnerReferral referral,
            Instant now,
            PartnerReferralAccrualStats stats,
            Map<UUID, OrganizationMember> membersById,
            Map<UUID, Organization> organizationsById,
            Map<UUID, CourierRequest> courierRequestsById,
            Map<UUID, RestaurantRegistrationRequest> restaurantRequestsById,
            Map<String, PartnerProgramRule> rulesByKey) {
        String referrerName = resolveReferrerName(referral, membersById, organizationsById);
        String inviteeName = resolveInviteeName(referral, membersById, organizationsById);
        String inviteePhone = resolveInviteePhone(referral, courierRequestsById, restaurantRequestsById);

        long accrualCount = stats != null ? stats.getAccrualCount() : 0L;
        BigDecimal accruedAmount = stats != null ? stats.getAccruedTotal() : BigDecimal.ZERO;
        BigDecimal reversedAmount = stats != null ? stats.getReversedTotal() : BigDecimal.ZERO;
        Instant lastAccrualAt = stats != null ? stats.getLastAccrualAt() : null;

        return new PartnerReferralAdminDto(
                referral.getId(),
                referral.getReferrerType(),
                referrerName,
                null,
                referral.getInviteeType(),
                inviteeName,
                inviteePhone,
                referral.getCreatedAt(),
                referral.getConnectedAt(),
                resolveStatus(referral, now, membersById, organizationsById, rulesByKey),
                relationshipLabel(referral.getReferrerType(), referral.getInviteeType()),
                accrualCount,
                accruedAmount,
                reversedAmount,
                accruedAmount,
                lastAccrualAt);
    }

    private PartnerReferralJournalStatus resolveStatus(
            PartnerReferral referral,
            Instant now,
            Map<UUID, OrganizationMember> membersById,
            Map<UUID, Organization> organizationsById,
            Map<String, PartnerProgramRule> rulesByKey) {
        if (isInviteeInactive(referral, membersById, organizationsById)) {
            return PartnerReferralJournalStatus.INVITEE_INACTIVE;
        }
        PartnerProgramRule rule = rulesByKey.get(ruleKey(referral.getReferrerType(), referral.getInviteeType()));
        if (rule == null || !rule.isEnabled()) {
            return PartnerReferralJournalStatus.RULE_DISABLED;
        }
        if (referral.getProgramExpiresAt() != null && now.isAfter(referral.getProgramExpiresAt())) {
            return PartnerReferralJournalStatus.EXPIRED;
        }
        return PartnerReferralJournalStatus.ACTIVE;
    }

    private boolean isInviteeInactive(
            PartnerReferral referral,
            Map<UUID, OrganizationMember> membersById,
            Map<UUID, Organization> organizationsById) {
        if (referral.getInviteeType() == PartnerReferralType.COURIER && referral.getInviteeMemberId() != null) {
            OrganizationMember member = membersById.get(referral.getInviteeMemberId());
            return member == null || member.getStatus() != MemberStatus.active;
        }
        if (referral.getInviteeOrganizationId() != null) {
            Organization organization = organizationsById.get(referral.getInviteeOrganizationId());
            return organization == null || !organization.isActive();
        }
        return false;
    }

    private String resolveReferrerName(
            PartnerReferral referral,
            Map<UUID, OrganizationMember> membersById,
            Map<UUID, Organization> organizationsById) {
        if (referral.getReferrerType() == PartnerReferrerType.COURIER && referral.getReferrerMemberId() != null) {
            return memberDisplayName(membersById.get(referral.getReferrerMemberId()), "Курьер");
        }
        if (referral.getReferrerOrganizationId() != null) {
            return organizationName(organizationsById.get(referral.getReferrerOrganizationId()));
        }
        return "—";
    }

    private String resolveInviteeName(
            PartnerReferral referral,
            Map<UUID, OrganizationMember> membersById,
            Map<UUID, Organization> organizationsById) {
        if (referral.getInviteeType() == PartnerReferralType.COURIER && referral.getInviteeMemberId() != null) {
            return memberDisplayName(membersById.get(referral.getInviteeMemberId()), "Курьер");
        }
        if (referral.getInviteeOrganizationId() != null) {
            return organizationName(organizationsById.get(referral.getInviteeOrganizationId()));
        }
        return "—";
    }

    private String resolveInviteePhone(
            PartnerReferral referral,
            Map<UUID, CourierRequest> courierRequestsById,
            Map<UUID, RestaurantRegistrationRequest> restaurantRequestsById) {
        if (referral.getSourceCourierRequestId() != null) {
            CourierRequest request = courierRequestsById.get(referral.getSourceCourierRequestId());
            return request != null ? request.getPhone() : null;
        }
        if (referral.getSourceRestaurantRequestId() != null) {
            RestaurantRegistrationRequest request = restaurantRequestsById.get(referral.getSourceRestaurantRequestId());
            return request != null ? request.getPhone() : null;
        }
        return null;
    }

    private static String memberDisplayName(OrganizationMember member, String fallback) {
        if (member == null) {
            return fallback;
        }
        return member.getDisplayName() != null && !member.getDisplayName().isBlank()
                ? member.getDisplayName()
                : fallback;
    }

    private static String organizationName(Organization organization) {
        return organization != null && organization.getName() != null ? organization.getName() : "Объект";
    }

    private static String relationshipLabel(PartnerReferrerType referrerType, PartnerReferralType inviteeType) {
        String referrer = referrerType == PartnerReferrerType.COURIER ? "Курьер" : "Объект";
        String invitee = inviteeType == PartnerReferralType.COURIER ? "Курьер" : "Объект";
        return referrer + " → " + invitee;
    }

    private static String ruleKey(PartnerReferrerType referrerType, PartnerReferralType inviteeType) {
        return referrerType.name() + ":" + inviteeType.name();
    }
}
