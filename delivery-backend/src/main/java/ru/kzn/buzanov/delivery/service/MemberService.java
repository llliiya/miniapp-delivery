package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.CourierProfile;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.OrganizationType;
import ru.kzn.buzanov.delivery.dto.AddMemberResponse;
import ru.kzn.buzanov.delivery.dto.MemberDto;
import ru.kzn.buzanov.delivery.dto.ProvisioningCredentialsDto;
import ru.kzn.buzanov.delivery.dto.request.AddMemberRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchMemberRequest;
import ru.kzn.buzanov.delivery.integration.AccountProvisioningClient;
import ru.kzn.buzanov.delivery.integration.account.AccountProvisionRequest;
import ru.kzn.buzanov.delivery.util.EmailRequirements;
import ru.kzn.buzanov.delivery.integration.account.AccountProvisionResult;
import ru.kzn.buzanov.delivery.repository.CourierProfileRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberService {

    private static final Set<MemberRole> RESTAURANT_MEMBER_ROLES =
            EnumSet.of(MemberRole.owner, MemberRole.manager);

    private final OrganizationMemberRepository memberRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final PartnerCodeService partnerCodeService;
    private final AccessControlService accessControl;
    private final DeliveryUserProfileService profileService;
    private final AccountProvisioningClient accountProvisioningClient;
    private final DeliveryDtoMapper mapper;

    @Transactional(readOnly = true)
    public List<MemberDto> list(Long userId, UUID organizationId) {
        Organization org = accessControl.requireOrganization(organizationId);
        accessControl.requireCanViewOrganization(userId, org);
        if (org.getType() == OrganizationType.client_restaurant) {
            accessControl.requireCanManageMembers(userId, org);
        } else {
            accessControl.requireOrgManager(userId, organizationId);
        }
        return memberRepository.findByOrganizationId(organizationId).stream()
                .map(mapper::toMemberDto)
                .toList();
    }

    @Transactional
    public AddMemberResponse add(Long actorUserId, UUID organizationId, AddMemberRequest request) {
        if (request.isProvisioningFlow()) {
            return addWithProvisioning(actorUserId, organizationId, request);
        }
        if (request.isLegacyFlow()) {
            MemberDto member = addLegacy(actorUserId, organizationId, request);
            return new AddMemberResponse(member, null);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Укажите ФИО и телефон или ID пользователя (legacy)");
    }

    @Transactional
    public MemberDto addLegacy(Long actorUserId, UUID organizationId, AddMemberRequest request) {
        Organization org = accessControl.requireOrganization(organizationId);
        accessControl.requireCanManageMembers(actorUserId, org);
        accessControl.validateRoleForOrganization(org, request.role());
        return createMembership(
                organizationId,
                request.userId(),
                request.role(),
                resolveDisplayName(request.displayName(), null),
                org,
                null);
    }

    @Transactional
    public MemberDto addMembershipForOrganization(
            UUID organizationId,
            Long userId,
            MemberRole role,
            String displayName) {
        return addMembershipForOrganization(organizationId, userId, role, displayName, null);
    }

    @Transactional
    public MemberDto addMembershipForOrganization(
            UUID organizationId,
            Long userId,
            MemberRole role,
            String displayName,
            Long reservedPublicId) {
        Organization org = accessControl.requireOrganization(organizationId);
        accessControl.validateRoleForOrganization(org, role);
        return createMembership(organizationId, userId, role, displayName, org, reservedPublicId);
    }

    private AddMemberResponse addWithProvisioning(Long actorUserId, UUID organizationId, AddMemberRequest request) {
        Organization org = accessControl.requireOrganization(organizationId);
        accessControl.requireCanManageMembers(actorUserId, org);
        if (org.getType() == OrganizationType.client_restaurant) {
            if (!RESTAURANT_MEMBER_ROLES.contains(request.role())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Для объекта доступны роли owner и manager");
            }
        } else {
            accessControl.validateRoleForOrganization(org, request.role());
        }

        String email = EmailRequirements.requireEmail(request.email());
        AccountProvisionRequest provisionRequest = request.role() == MemberRole.owner
                ? AccountProvisionRequest.forRestaurantOwner(
                        request.fullName().trim(),
                        request.phone().trim(),
                        email,
                        org.getName())
                : AccountProvisionRequest.forRestaurantManager(
                        request.fullName().trim(),
                        request.phone().trim(),
                        email,
                        org.getName());
        AccountProvisionResult provisioned = accountProvisioningClient.provisionWebEmployee(provisionRequest);
        MemberDto member = createMembership(
                organizationId,
                provisioned.userId(),
                request.role(),
                request.fullName().trim(),
                org,
                null);
        ProvisioningCredentialsDto credentials = ProvisioningCredentialsDto.fromProvision(
                provisioned.login(), provisioned.temporaryPassword());
        return new AddMemberResponse(member, credentials);
    }

    private MemberDto createMembership(
            UUID organizationId,
            Long userId,
            MemberRole role,
            String displayName,
            Organization org,
            Long reservedPublicId) {
        if (memberRepository.findByOrganizationIdAndUserId(organizationId, userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Участник уже существует");
        }

        Instant now = Instant.now();
        OrganizationMember member = new OrganizationMember();
        member.setId(UUID.randomUUID());
        member.setOrganizationId(organizationId);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus(MemberStatus.active);
        member.setDisplayName(displayName);
        member.setCreatedAt(now);
        if (reservedPublicId != null) {
            member.setPublicId(reservedPublicId);
        }
        memberRepository.saveAndFlush(member);
        if (reservedPublicId == null) {
            member = memberRepository.findById(member.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Не удалось загрузить участника после создания"));
        }
        profileService.syncFromMembership(member);

        if (org.getType() == OrganizationType.courier_service && role == MemberRole.courier) {
            ensureCourierProfile(member.getId(), now);
        }

        return mapper.toMemberDto(member);
    }

    @Transactional
    public MemberDto patch(Long actorUserId, UUID organizationId, Long targetUserId, PatchMemberRequest request) {
        Organization org = accessControl.requireOrganization(organizationId);
        accessControl.requireCanManageMembers(actorUserId, org);

        OrganizationMember member = memberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Участник не найден"));

        if (request.role() != null) {
            accessControl.validateRoleForOrganization(org, request.role());
            member.setRole(request.role());
            if (org.getType() == OrganizationType.courier_service && request.role() == MemberRole.courier) {
                ensureCourierProfile(member.getId(), Instant.now());
            }
        }
        if (request.status() != null) {
            member.setStatus(request.status());
        }
        if (request.displayName() != null) {
            member.setDisplayName(request.displayName());
        }
        memberRepository.save(member);
        profileService.syncFromMembership(member);
        return mapper.toMemberDto(member);
    }

    @Transactional
    public ProvisioningCredentialsDto resetAccess(Long actorUserId, UUID organizationId, Long targetUserId) {
        Organization org = accessControl.requireOrganization(organizationId);
        accessControl.requireCanManageMembers(actorUserId, org);
        OrganizationMember member = memberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Участник не найден"));
        AccountProvisionResult provisioned = accountProvisioningClient.resetWebCredentials(member.getUserId());
        return ProvisioningCredentialsDto.fromProvision(provisioned.login(), provisioned.temporaryPassword());
    }

    @Transactional
    public void removeFromOrganization(Long actorUserId, UUID organizationId, Long targetUserId) {
        Organization org = accessControl.requireOrganization(organizationId);
        accessControl.requireCanManageMembers(actorUserId, org);
        OrganizationMember member = memberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Участник не найден"));
        if (member.getRole() == MemberRole.owner) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нельзя удалить собственника объекта");
        }
        memberRepository.delete(member);
    }

    public void ensureCourierProfilePublic(UUID memberId) {
        ensureCourierProfile(memberId, Instant.now());
    }

    private void ensureCourierProfile(UUID memberId, Instant now) {
        if (courierProfileRepository.findByMemberId(memberId).isEmpty()) {
            CourierProfile profile = new CourierProfile();
            profile.setId(UUID.randomUUID());
            profile.setMemberId(memberId);
            profile.setBalance(BigDecimal.ZERO);
            profile.setCompletedOrdersCount(0);
            profile.setUpdatedAt(now);
            courierProfileRepository.save(profile);
        }
        partnerCodeService.ensurePartnerCode(memberId);
    }

    private static String resolveDisplayName(String displayName, String fallback) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return fallback;
    }
}
