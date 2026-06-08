package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.OrganizationType;
import ru.kzn.buzanov.delivery.dto.OrganizationDto;
import ru.kzn.buzanov.delivery.dto.request.CreateOrganizationRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchOrganizationRequest;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AccessControlService accessControl;
    private final DeliveryUserProfileService profileService;
    private final DeliveryDtoMapper mapper;

    @Transactional
    public OrganizationDto create(Long userId, CreateOrganizationRequest request) {
        if (request.type() != OrganizationType.courier_service
                && request.type() != OrganizationType.client_restaurant) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимый type");
        }
        if (request.type() == OrganizationType.client_restaurant) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ресторан создаётся через POST /restaurants");
        }
        Instant now = Instant.now();
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setType(request.type());
        org.setName(request.name().trim());
        org.setOwnerUserId(userId);
        org.setActive(true);
        org.setCreatedAt(now);
        organizationRepository.saveAndFlush(org);

        OrganizationMember owner = new OrganizationMember();
        owner.setId(UUID.randomUUID());
        owner.setOrganizationId(org.getId());
        owner.setUserId(userId);
        owner.setRole(MemberRole.owner);
        owner.setStatus(MemberStatus.active);
        owner.setCreatedAt(now);
        memberRepository.saveAndFlush(owner);
        profileService.activateAs(userId, MemberRole.owner);
        organizationRepository.findById(org.getId()).ifPresent(refreshed -> org.setPublicId(refreshed.getPublicId()));

        return mapper.toOrganizationDto(org);
    }

    @Transactional(readOnly = true)
    public List<OrganizationDto> list(Long userId) {
        List<OrganizationMember> memberships = memberRepository.findByUserId(userId);
        Map<UUID, Organization> orgs = new LinkedHashMap<>();
        for (OrganizationMember membership : memberships) {
            if (membership.getStatus() != MemberStatus.active) {
                continue;
            }
            organizationRepository.findById(membership.getOrganizationId()).ifPresent(org -> {
                orgs.put(org.getId(), org);
                if (org.getType() == OrganizationType.courier_service
                        && (membership.getRole() == MemberRole.owner || membership.getRole() == MemberRole.manager)) {
                    organizationRepository
                            .findByCourierServiceIdAndType(org.getId(), OrganizationType.client_restaurant)
                            .forEach(r -> orgs.put(r.getId(), r));
                }
            });
        }
        return orgs.values().stream().map(mapper::toOrganizationDto).toList();
    }

    @Transactional(readOnly = true)
    public OrganizationDto get(Long userId, UUID organizationId) {
        Organization org = accessControl.requireOrganization(organizationId);
        accessControl.requireCanViewOrganization(userId, org);
        return mapper.toOrganizationDto(org);
    }

    @Transactional
    public OrganizationDto patch(Long userId, UUID organizationId, PatchOrganizationRequest request) {
        Organization org = accessControl.requireOrganization(organizationId);
        accessControl.requireCanManageOrganization(userId, org);
        if (request.name() != null && !request.name().isBlank()) {
            org.setName(request.name().trim());
        }
        if (request.active() != null) {
            org.setActive(request.active());
        }
        organizationRepository.save(org);
        return mapper.toOrganizationDto(org);
    }
}
