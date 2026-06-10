package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.CourierProfile;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.dto.CreateCourierResponse;
import ru.kzn.buzanov.delivery.dto.CourierDto;
import ru.kzn.buzanov.delivery.dto.ProvisioningCredentialsDto;
import ru.kzn.buzanov.delivery.dto.request.CreateCourierRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchCourierRequest;
import ru.kzn.buzanov.delivery.integration.AccountProvisioningClient;
import ru.kzn.buzanov.delivery.integration.AccountUserClient;
import ru.kzn.buzanov.delivery.integration.account.AccountProvisionRequest;
import ru.kzn.buzanov.delivery.util.EmailRequirements;
import ru.kzn.buzanov.delivery.integration.account.AccountProvisionResult;
import ru.kzn.buzanov.delivery.repository.CourierProfileRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourierService {

    private final OrganizationMemberRepository memberRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final AccessControlService accessControl;
    private final MemberService memberService;
    private final DeliveryUserProfileService profileService;
    private final AccountUserClient accountUserClient;
    private final AccountProvisioningClient accountProvisioningClient;
    private final DeliveryDtoMapper mapper;

    @Transactional(readOnly = true)
    public List<CourierDto> list(Long userId, UUID courierServiceId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        return memberRepository.findByOrganizationIdAndRole(courierServiceId, MemberRole.courier).stream()
                .map(member -> toCourierDto(member, courierServiceId))
                .toList();
    }

    @Transactional
    public CreateCourierResponse create(Long actorUserId, CreateCourierRequest request) {
        if (request.isProvisioningFlow()) {
            return createWithProvisioning(actorUserId, request);
        }
        if (request.isLegacyFlow()) {
            return new CreateCourierResponse(createLegacy(actorUserId, request), null);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Укажите ФИО и телефон или ID пользователя (legacy)");
    }

    private CreateCourierResponse createWithProvisioning(Long actorUserId, CreateCourierRequest request) {
        accessControl.requireServiceStaff(actorUserId, request.courierServiceId());
        AccountProvisionResult provisioned = provisionCourierAccount(
                request.fullName().trim(),
                request.phone().trim(),
                EmailRequirements.requireEmail(request.email()));
        assertNotAlreadyMember(request.courierServiceId(), provisioned.userId());

        var member = memberService.addMembershipForOrganization(
                request.courierServiceId(),
                provisioned.userId(),
                MemberRole.courier,
                request.fullName().trim());
        OrganizationMember organizationMember = memberRepository.findById(member.id()).orElseThrow();
        CourierDto courier = toCourierDto(organizationMember, request.courierServiceId());
        ProvisioningCredentialsDto credentials = ProvisioningCredentialsDto.fromProvision(
                provisioned.login(), provisioned.temporaryPassword());
        return new CreateCourierResponse(courier, credentials);
    }

    private AccountProvisionResult provisionCourierAccount(String fullName, String phone, String email) {
        return accountProvisioningClient.provisionWebEmployee(
                AccountProvisionRequest.forCourier(fullName, phone, email));
    }

    private CourierDto createLegacy(Long actorUserId, CreateCourierRequest request) {
        accessControl.requireServiceStaff(actorUserId, request.courierServiceId());
        accountUserClient.requireUserExists(request.userId());
        assertNotAlreadyMember(request.courierServiceId(), request.userId());

        String displayName = request.displayName() != null && !request.displayName().isBlank()
                ? request.displayName().trim()
                : null;
        var member = memberService.addMembershipForOrganization(
                request.courierServiceId(),
                request.userId(),
                MemberRole.courier,
                displayName);
        OrganizationMember organizationMember = memberRepository.findById(member.id()).orElseThrow();
        return toCourierDto(organizationMember, request.courierServiceId());
    }

    private void assertNotAlreadyMember(UUID courierServiceId, Long userId) {
        var existing = memberRepository.findByOrganizationIdAndUserId(courierServiceId, userId);
        if (existing.isPresent()) {
            OrganizationMember member = existing.get();
            if (member.getRole() != MemberRole.courier) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Пользователь уже участник с другой ролью");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Курьер уже добавлен в службу");
        }
    }

    @Transactional(readOnly = true)
    public CourierDto get(Long userId, UUID memberId) {
        OrganizationMember member = accessControl.requireCourierMember(memberId);
        if (member.getUserId().equals(userId)) {
            return toCourierDto(member, member.getOrganizationId());
        }
        accessControl.requireServiceStaff(userId, member.getOrganizationId());
        return toCourierDto(member, member.getOrganizationId());
    }

    @Transactional
    public CourierDto patch(Long actorUserId, UUID memberId, PatchCourierRequest request) {
        OrganizationMember member = accessControl.requireCourierMember(memberId);
        boolean self = member.getUserId().equals(actorUserId);
        if (!self) {
            accessControl.requireServiceStaff(actorUserId, member.getOrganizationId());
        } else if (request.status() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Курьер не может менять свой статус");
        }
        if (request.displayName() != null) {
            member.setDisplayName(request.displayName());
        }
        if (request.status() != null) {
            member.setStatus(request.status());
        }
        memberRepository.save(member);
        profileService.syncFromMembership(member);
        return toCourierDto(member, member.getOrganizationId());
    }

    @Transactional
    public ProvisioningCredentialsDto resetAccess(Long actorUserId, UUID memberId) {
        OrganizationMember member = accessControl.requireCourierMember(memberId);
        accessControl.requireServiceStaff(actorUserId, member.getOrganizationId());
        AccountProvisionResult provisioned = accountProvisioningClient.resetWebCredentials(member.getUserId());
        return ProvisioningCredentialsDto.fromProvision(provisioned.login(), provisioned.temporaryPassword());
    }

    private CourierDto toCourierDto(OrganizationMember member, UUID courierServiceId) {
        CourierProfile profile = courierProfileRepository.findByMemberId(member.getId())
                .orElseGet(() -> {
                    CourierProfile p = new CourierProfile();
                    p.setBalance(BigDecimal.ZERO);
                    p.setCompletedOrdersCount(0);
                    return p;
                });
        return mapper.toCourierDto(member, profile, courierServiceId);
    }
}
