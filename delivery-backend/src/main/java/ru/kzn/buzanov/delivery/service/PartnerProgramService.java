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
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequest;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequestStatus;
import ru.kzn.buzanov.delivery.dto.PartnerProgramDto;
import ru.kzn.buzanov.delivery.dto.PartnerReferralDto;
import ru.kzn.buzanov.delivery.repository.CourierRequestRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.RestaurantRegistrationRequestRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerProgramService {

    private static final List<RestaurantRegistrationRequestStatus> RESTAURANT_PENDING_STATUSES = List.of(
            RestaurantRegistrationRequestStatus.NEW,
            RestaurantRegistrationRequestStatus.IN_PROGRESS);

    private final OrganizationMemberRepository memberRepository;
    private final RestaurantRegistrationRequestRepository restaurantRequestRepository;
    private final CourierRequestRepository courierRequestRepository;
    private final PartnerCodeService partnerCodeService;
    private final AccessControlService accessControl;

    @Value("${delivery.frontend-url:http://localhost:5174}")
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
        return buildProgram(
                partnerCode,
                restaurantRequestRepository.findByCourierMemberIdOrderByCreatedAtDesc(memberId),
                courierRequestRepository.findByReferrerMemberIdOrderByCreatedAtDesc(memberId));
    }

    @Transactional(readOnly = true)
    public PartnerProgramDto getForRestaurant(Long userId, UUID restaurantId) {
        accessControl.requireRestaurant(restaurantId);
        accessControl.requireActiveMembership(userId, restaurantId);

        String partnerCode = partnerCodeService.ensurePartnerCodeForRestaurant(restaurantId);
        return buildProgram(
                partnerCode,
                restaurantRequestRepository.findByReferrerOrganizationIdOrderByCreatedAtDesc(restaurantId),
                courierRequestRepository.findByReferrerOrganizationIdOrderByCreatedAtDesc(restaurantId));
    }

    private PartnerProgramDto buildProgram(
            String partnerCode,
            List<RestaurantRegistrationRequest> restaurantReferrals,
            List<CourierRequest> courierReferrals) {
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
        long connectedCount = restaurantReferrals.stream()
                .filter(r -> r.getStatus() == RestaurantRegistrationRequestStatus.APPROVED)
                .count()
                + courierReferrals.stream()
                        .filter(r -> r.getStatus() == CourierRequestStatus.APPROVED)
                        .count();

        return new PartnerProgramDto(
                partnerCode,
                buildRestaurantInviteUrl(partnerCode),
                buildCourierInviteUrl(partnerCode),
                referrals.size(),
                pendingCount,
                connectedCount,
                referrals);
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
}
