package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.OrganizationType;
import ru.kzn.buzanov.delivery.domain.UserDeliveryContext;
import ru.kzn.buzanov.delivery.domain.UserDeliveryStatus;
import ru.kzn.buzanov.delivery.dto.MeResponseDto;
import ru.kzn.buzanov.delivery.dto.MembershipDto;
import ru.kzn.buzanov.delivery.dto.request.SetActiveOrganizationRequest;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.repository.UserDeliveryContextRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeService {

    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final UserDeliveryContextRepository contextRepository;
    private final DeliveryUserProfileService profileService;
    private final DeliveryDtoMapper mapper;

    @Transactional
    public MeResponseDto getMe(Long userId) {
        UserDeliveryContext profile = profileService.ensureProfile(userId);
        List<MembershipDto> memberships = loadMemberships(userId);
        UUID activeOrgId = resolveActiveOrganizationId(userId, memberships);
        String interfaceMode = null;
        if (activeOrgId != null) {
            interfaceMode = memberships.stream()
                    .filter(m -> activeOrgId.equals(m.organizationId()))
                    .findFirst()
                    .map(InterfaceModeResolver::resolve)
                    .orElse(null);
        }
        if (interfaceMode == null) {
            interfaceMode = InterfaceModeResolver.resolvePendingProfile(profile);
        }
        UserDeliveryStatus status = DeliveryUserStatusResolver.resolve(profile, memberships, interfaceMode);
        return new MeResponseDto(
                userId,
                status,
                activeOrgId,
                interfaceMode,
                profile.getDeliveryRole().name(),
                profile.getAccountStatus().name(),
                memberships);
    }

    @Transactional
    public MeResponseDto setActiveOrganization(Long userId, SetActiveOrganizationRequest request) {
        List<MembershipDto> memberships = loadMemberships(userId);
        MembershipDto selected = memberships.stream()
                .filter(m -> request.organizationId().equals(m.organizationId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет membership для организации"));
        if (selected.status() != MemberStatus.active) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Участник заблокирован");
        }
        if (InterfaceModeResolver.resolve(selected) == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступного интерфейса для этой роли");
        }
        UserDeliveryContext ctx = profileService.ensureProfile(userId);
        ctx.setActiveOrganizationId(request.organizationId());
        ctx.setUpdatedAt(Instant.now());
        contextRepository.save(ctx);
        return getMe(userId);
    }

    private UUID resolveActiveOrganizationId(Long userId, List<MembershipDto> memberships) {
        List<MembershipDto> activeWithUi = memberships.stream()
                .filter(m -> m.status() == MemberStatus.active && InterfaceModeResolver.resolve(m) != null)
                .toList();
        if (activeWithUi.isEmpty()) {
            return null;
        }
        UUID stored = contextRepository.findById(userId)
                .map(UserDeliveryContext::getActiveOrganizationId)
                .orElse(null);
        if (stored != null && activeWithUi.stream().anyMatch(m -> stored.equals(m.organizationId()))) {
            return stored;
        }
        return activeWithUi.getFirst().organizationId();
    }

    private List<MembershipDto> loadMemberships(Long userId) {
        List<OrganizationMember> members = memberRepository.findByUserId(userId);
        if (members.isEmpty()) {
            return List.of();
        }
        Map<UUID, Organization> orgById = new LinkedHashMap<>();
        for (OrganizationMember member : members) {
            organizationRepository.findById(member.getOrganizationId()).ifPresent(org -> orgById.put(org.getId(), org));
        }
        List<MembershipDto> result = new ArrayList<>();
        for (OrganizationMember member : members) {
            Organization org = orgById.get(member.getOrganizationId());
            if (org != null) {
                result.add(mapper.toMembershipDto(member, org));
            }
        }
        for (OrganizationMember member : members) {
            Organization org = orgById.get(member.getOrganizationId());
            if (org == null || org.getType() != OrganizationType.courier_service) {
                continue;
            }
            if (member.getStatus() != MemberStatus.active) {
                continue;
            }
            if (member.getRole() != ru.kzn.buzanov.delivery.domain.MemberRole.owner
                    && member.getRole() != ru.kzn.buzanov.delivery.domain.MemberRole.manager) {
                continue;
            }
            List<Organization> restaurants = organizationRepository.findByCourierServiceIdAndType(
                    org.getId(), OrganizationType.client_restaurant);
            for (Organization restaurant : restaurants) {
                boolean already = result.stream().anyMatch(m -> m.organizationId().equals(restaurant.getId()));
                if (!already) {
                    result.add(new MembershipDto(
                            null,
                            null,
                            restaurant.getId(),
                            restaurant.getPublicId(),
                            restaurant.getName(),
                            restaurant.getType(),
                            org.getId(),
                            member.getRole(),
                            member.getStatus(),
                            MembershipDto.ACCESS_SERVICE_SCOPE
                    ));
                }
            }
        }
        return result;
    }
}
