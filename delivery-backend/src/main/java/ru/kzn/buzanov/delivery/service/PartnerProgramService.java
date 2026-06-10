package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequest;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequestStatus;
import ru.kzn.buzanov.delivery.dto.PartnerProgramDto;
import ru.kzn.buzanov.delivery.dto.PartnerReferralDto;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.RestaurantRegistrationRequestRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerProgramService {

    private static final List<RestaurantRegistrationRequestStatus> PENDING_STATUSES = List.of(
            RestaurantRegistrationRequestStatus.NEW,
            RestaurantRegistrationRequestStatus.IN_PROGRESS);

    private final OrganizationMemberRepository memberRepository;
    private final RestaurantRegistrationRequestRepository requestRepository;
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

        String partnerCode = partnerCodeService.ensurePartnerCode(memberId);
        String inviteUrl = buildInviteUrl(partnerCode);

        List<RestaurantRegistrationRequest> referrals =
                requestRepository.findByCourierMemberIdOrderByCreatedAtDesc(memberId);

        long pendingCount = referrals.stream()
                .filter(r -> PENDING_STATUSES.contains(r.getStatus()))
                .count();
        long connectedCount = referrals.stream()
                .filter(r -> r.getStatus() == RestaurantRegistrationRequestStatus.APPROVED)
                .count();

        List<PartnerReferralDto> referralDtos = referrals.stream()
                .map(r -> new PartnerReferralDto(
                        r.getId(),
                        r.getRestaurantName(),
                        r.getCreatedAt(),
                        r.getStatus(),
                        r.getStatus() == RestaurantRegistrationRequestStatus.APPROVED
                                ? r.getProcessedAt()
                                : null))
                .toList();

        return new PartnerProgramDto(
                partnerCode,
                inviteUrl,
                referrals.size(),
                pendingCount,
                connectedCount,
                referralDtos);
    }

    private String buildInviteUrl(String partnerCode) {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + "/join?partner=" + partnerCode;
    }
}
