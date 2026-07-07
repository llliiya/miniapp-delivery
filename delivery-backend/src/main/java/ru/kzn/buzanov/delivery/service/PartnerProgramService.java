package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.CourierRequest;
import ru.kzn.buzanov.delivery.domain.CourierRequestStatus;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.PartnerAccount;
import ru.kzn.buzanov.delivery.domain.PartnerAccrual;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerReferral;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequest;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequestStatus;
import ru.kzn.buzanov.delivery.dto.PartnerAccrualDto;
import ru.kzn.buzanov.delivery.dto.PartnerConnectedReferralDto;
import ru.kzn.buzanov.delivery.dto.PartnerProgramDto;
import ru.kzn.buzanov.delivery.dto.PartnerReferralDto;
import ru.kzn.buzanov.delivery.repository.CourierRequestRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.repository.PartnerAccrualRepository;
import ru.kzn.buzanov.delivery.repository.PartnerReferralRepository;
import ru.kzn.buzanov.delivery.repository.RestaurantRegistrationRequestRepository;
import ru.kzn.buzanov.delivery.util.PartnerJsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerProgramService {

    private static final List<RestaurantRegistrationRequestStatus> RESTAURANT_PENDING_STATUSES = List.of(
            RestaurantRegistrationRequestStatus.NEW,
            RestaurantRegistrationRequestStatus.IN_PROGRESS);

    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final RestaurantRegistrationRequestRepository restaurantRequestRepository;
    private final CourierRequestRepository courierRequestRepository;
    private final PartnerReferralRepository partnerReferralRepository;
    private final PartnerAccrualRepository partnerAccrualRepository;
    private final PartnerCodeService partnerCodeService;
    private final PartnerAccountService partnerAccountService;
    private final PartnerPayoutService partnerPayoutService;
    private final PartnerBalanceTransferService partnerBalanceTransferService;
    private final PartnerProgramRuleService partnerProgramRuleService;
    private final PartnerJsonMapper jsonMapper;
    private final AccessControlService accessControl;

    @Value("${delivery.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public PartnerProgramDto getForCourierMember(Long userId, UUID memberId) {
        OrganizationMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Курьер не найден"));
        if (member.getRole() != MemberRole.courier) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Участник не является курьером");
        }
        accessControl.requireActiveMembership(userId, member.getOrganizationId());
        if (!member.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа");
        }

        String partnerCode = partnerCodeService.ensurePartnerCodeForCourier(memberId);
        PartnerAccount account = partnerAccountService.findCourierAccount(memberId);
        return buildProgram(
                partnerCode,
                member.getOrganizationId(),
                PartnerReferrerType.COURIER,
                account,
                restaurantRequestRepository.findByCourierMemberIdOrderByCreatedAtDesc(memberId),
                courierRequestRepository.findByReferrerMemberIdOrderByCreatedAtDesc(memberId),
                partnerReferralRepository.findByReferrerMemberIdOrderByConnectedAtDesc(memberId));
    }

    @Transactional(readOnly = true)
    public PartnerProgramDto getForRestaurant(Long userId, UUID restaurantId) {
        Organization restaurant = accessControl.requireRestaurant(restaurantId);
        accessControl.requireActiveMembership(userId, restaurantId);

        String partnerCode = partnerCodeService.ensurePartnerCodeForRestaurant(restaurantId);
        PartnerAccount account = partnerAccountService.findRestaurantAccount(restaurantId);
        return buildProgram(
                partnerCode,
                restaurant.getCourierServiceId(),
                PartnerReferrerType.RESTAURANT,
                account,
                restaurantRequestRepository.findByReferrerOrganizationIdOrderByCreatedAtDesc(restaurantId),
                courierRequestRepository.findByReferrerOrganizationIdOrderByCreatedAtDesc(restaurantId),
                partnerReferralRepository.findByReferrerOrganizationIdOrderByConnectedAtDesc(restaurantId));
    }

    private PartnerProgramDto buildProgram(
            String partnerCode,
            UUID courierServiceId,
            PartnerReferrerType referrerType,
            PartnerAccount account,
            List<RestaurantRegistrationRequest> restaurantReferrals,
            List<CourierRequest> courierReferrals,
            List<PartnerReferral> connectedReferrals) {
        if (!partnerProgramRuleService.isEnabledForReferrer(courierServiceId, referrerType)) {
            return PartnerProgramDto.disabled();
        }

        List<PartnerReferralDto> referrals = new ArrayList<>();
        referrals.addAll(restaurantReferrals.stream().map(this::toRestaurantReferral).toList());
        referrals.addAll(courierReferrals.stream().map(this::toCourierReferral).toList());
        referrals.sort(Comparator.comparing(PartnerReferralDto::submittedAt).reversed());

        long pendingCount = restaurantReferrals.stream()
                .filter(r -> RESTAURANT_PENDING_STATUSES.contains(r.getStatus()))
                .count()
                + courierReferrals.stream()
                        .filter(r -> r.getStatus() == CourierRequestStatus.NEW)
                        .count();
        long connectedCount = connectedReferrals.size();

        List<PartnerConnectedReferralDto> connectedCouriers = connectedReferrals.stream()
                .filter(r -> r.getInviteeType() == PartnerReferralType.COURIER)
                .map(this::toConnectedReferral)
                .toList();
        List<PartnerConnectedReferralDto> connectedRestaurants = connectedReferrals.stream()
                .filter(r -> r.getInviteeType() == PartnerReferralType.RESTAURANT)
                .map(this::toConnectedReferral)
                .toList();

        List<PartnerAccrualDto> accrualHistory = account.getId() != null
                ? partnerAccrualRepository.findByPartnerAccountIdOrderByCreatedAtDesc(account.getId()).stream()
                        .map(this::toAccrualDto)
                        .toList()
                : List.of();

        PayoutConfig payoutConfig = resolvePayoutConfig(courierServiceId, referrerType);

        return new PartnerProgramDto(
                partnerCode,
                isDirectionEnabled(courierServiceId, referrerType, PartnerReferralType.RESTAURANT)
                        ? buildRestaurantInviteUrl(partnerCode)
                        : null,
                isDirectionEnabled(courierServiceId, referrerType, PartnerReferralType.COURIER)
                        ? buildCourierInviteUrl(partnerCode)
                        : null,
                referrals.size(),
                pendingCount,
                connectedCount,
                referrals,
                partnerAccountService.toSummary(account),
                connectedCouriers,
                connectedRestaurants,
                accrualHistory,
                partnerPayoutService.listForAccount(account.getId()),
                partnerBalanceTransferService.listForAccount(account.getId()),
                payoutConfig.methods(),
                payoutConfig.minAmount(),
                true);
    }

    private boolean isDirectionEnabled(
            UUID courierServiceId,
            PartnerReferrerType referrerType,
            PartnerReferralType inviteeType) {
        return partnerProgramRuleService.findActiveRule(courierServiceId, referrerType, inviteeType) != null;
    }

    private PartnerReferralDto toRestaurantReferral(RestaurantRegistrationRequest request) {
        return new PartnerReferralDto(
                request.getId(),
                PartnerReferralType.RESTAURANT,
                request.getRestaurantName(),
                request.getCreatedAt(),
                request.getStatus().name(),
                request.getStatus() == RestaurantRegistrationRequestStatus.APPROVED
                        ? request.getProcessedAt()
                        : null);
    }

    private PartnerReferralDto toCourierReferral(CourierRequest request) {
        return new PartnerReferralDto(
                request.getId(),
                PartnerReferralType.COURIER,
                request.getFullName(),
                request.getCreatedAt(),
                request.getStatus().name(),
                request.getStatus() == CourierRequestStatus.APPROVED
                        ? request.getUpdatedAt()
                        : null);
    }

    private PartnerConnectedReferralDto toConnectedReferral(PartnerReferral referral) {
        String displayName = resolveInviteeDisplayName(referral);
        return new PartnerConnectedReferralDto(
                referral.getId(),
                referral.getInviteeType(),
                referral.getReferrerType(),
                referral.getInviteeMemberId(),
                referral.getInviteeOrganizationId(),
                displayName,
                referral.getConnectedAt(),
                referral.getProgramExpiresAt());
    }

    private PartnerAccrualDto toAccrualDto(PartnerAccrual accrual) {
        PartnerReferral referral = partnerReferralRepository.findById(accrual.getPartnerReferralId()).orElse(null);
        return new PartnerAccrualDto(
                accrual.getId(),
                accrual.getOrderId(),
                accrual.getAmount(),
                accrual.getCalculationBaseAmount(),
                accrual.getStatus(),
                referral != null ? referral.getReferrerType() : null,
                referral != null ? referral.getInviteeType() : null,
                referral != null ? resolveInviteeDisplayName(referral) : null,
                accrual.getCreatedAt(),
                accrual.getAvailableFrom(),
                accrual.getAccrualPeriodMonth(),
                accrual.getPayoutCycleMonth(),
                accrual.getReversedAt());
    }

    private String resolveInviteeDisplayName(PartnerReferral referral) {
        if (referral.getInviteeType() == PartnerReferralType.COURIER && referral.getInviteeMemberId() != null) {
            return memberRepository.findById(referral.getInviteeMemberId())
                    .map(m -> m.getDisplayName() != null ? m.getDisplayName() : "Курьер")
                    .orElse("Курьер");
        }
        if (referral.getInviteeOrganizationId() != null) {
            return organizationRepository.findById(referral.getInviteeOrganizationId())
                    .map(Organization::getName)
                    .orElse("Объект");
        }
        return "—";
    }

    private PayoutConfig resolvePayoutConfig(UUID courierServiceId, PartnerReferrerType referrerType) {
        Set<String> methods = new HashSet<>();
        BigDecimal minAmount = BigDecimal.ZERO;
        for (PartnerReferralType inviteeType : PartnerReferralType.values()) {
            PartnerProgramRule rule = partnerProgramRuleService.findActiveRule(
                    courierServiceId, referrerType, inviteeType);
            if (rule == null) {
                continue;
            }
            for (String method : jsonMapper.toStringList(rule.getPayoutMethods())) {
                if (referrerType == PartnerReferrerType.RESTAURANT
                        && "TRANSFER_TO_MAIN_BALANCE".equals(method)) {
                    continue;
                }
                methods.add(method);
            }
            if (rule.getMinPayoutAmount() != null && rule.getMinPayoutAmount().compareTo(minAmount) > 0) {
                minAmount = rule.getMinPayoutAmount();
            }
        }
        if (methods.isEmpty()) {
            if (referrerType == PartnerReferrerType.COURIER) {
                methods.add("BANK_TRANSFER");
                methods.add("TRANSFER_TO_MAIN_BALANCE");
            } else {
                methods.add("BANK_TRANSFER");
            }
        }
        return new PayoutConfig(List.copyOf(methods), minAmount);
    }

    private String buildRestaurantInviteUrl(String partnerCode) {
        return buildInviteUrl("/join", partnerCode);
    }

    private String buildCourierInviteUrl(String partnerCode) {
        return buildInviteUrl("/join-courier", partnerCode);
    }

    private String buildInviteUrl(String path, String partnerCode) {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + path + "?partner=" + partnerCode;
    }

    private record PayoutConfig(List<String> methods, BigDecimal minAmount) {
    }
}
