package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.api.CourierConflictException;
import ru.kzn.buzanov.delivery.domain.MemberRole;
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
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourierService {

    private final OrganizationMemberRepository memberRepository;
    private final AccessControlService accessControl;
    private final MemberService memberService;
    private final DeliveryUserProfileService profileService;
    private final AccountUserClient accountUserClient;
    private final AccountProvisioningClient accountProvisioningClient;
    private final CourierMembershipChecks courierMembershipChecks;

    @Transactional(readOnly = true)
    public List<CourierDto> list(Long userId, UUID courierServiceId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        return memberRepository.findByOrganizationIdAndRole(courierServiceId, MemberRole.courier).stream()
                .map(member -> courierMembershipChecks.toCourierDto(member, courierServiceId))
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
        String fullName = request.fullName().trim();
        String phone = request.phone().trim();
        String email = EmailRequirements.requireEmail(request.email());

        log.info("Courier add: provisioning account for phone={}, email={}, serviceId={}",
                phone, email, request.courierServiceId());
        AccountProvisionResult provisioned = provisionCourierAccount(fullName, phone, email);
        log.info("Courier add: account user found/created userId={}", provisioned.userId());

        courierMembershipChecks.ensureCanAddCourier(request.courierServiceId(), provisioned.userId());

        log.info("Courier add: creating membership for userId={} in serviceId={}",
                provisioned.userId(), request.courierServiceId());
        var member = memberService.addMembershipForOrganization(
                request.courierServiceId(),
                provisioned.userId(),
                MemberRole.courier,
                fullName);
        OrganizationMember organizationMember = memberRepository.findById(member.id()).orElseThrow();
        log.info("Courier add: membership created memberId={}, courier profile ensured",
                organizationMember.getId());

        CourierDto courier = courierMembershipChecks.toCourierDto(organizationMember, request.courierServiceId());
        ProvisioningCredentialsDto credentials = ProvisioningCredentialsDto.fromProvision(
                provisioned.login(), provisioned.temporaryPassword());
        return new CreateCourierResponse(courier, credentials);
    }

    private AccountProvisionResult provisionCourierAccount(String fullName, String phone, String email) {
        try {
            return accountProvisioningClient.provisionWebEmployee(
                    AccountProvisionRequest.forCourier(fullName, phone, email));
        } catch (ResponseStatusException ex) {
            CourierConflictException conflict = mapProvisionConflict(ex);
            if (conflict != null) {
                throw conflict;
            }
            throw ex;
        }
    }

    private static CourierConflictException mapProvisionConflict(ResponseStatusException ex) {
        if (ex.getStatusCode() != HttpStatus.CONFLICT) {
            return null;
        }
        String reason = ex.getReason() != null ? ex.getReason() : "";
        String lower = reason.toLowerCase();
        if (lower.contains("email")) {
            return new CourierConflictException("email_already_used", reason, "email");
        }
        if (lower.contains("телефон") || lower.contains("phone")) {
            return new CourierConflictException("phone_already_used", reason, "phone");
        }
        if (lower.contains("логин") || lower.contains("login")) {
            return new CourierConflictException("login_already_used", reason, "login");
        }
        return new CourierConflictException("account_conflict", reason);
    }

    private CourierDto createLegacy(Long actorUserId, CreateCourierRequest request) {
        accessControl.requireServiceStaff(actorUserId, request.courierServiceId());
        accountUserClient.requireUserExists(request.userId());
        log.info("Courier add (legacy): userId={} for serviceId={}", request.userId(), request.courierServiceId());
        courierMembershipChecks.ensureCanAddCourier(request.courierServiceId(), request.userId());

        String displayName = request.displayName() != null && !request.displayName().isBlank()
                ? request.displayName().trim()
                : null;
        var member = memberService.addMembershipForOrganization(
                request.courierServiceId(),
                request.userId(),
                MemberRole.courier,
                displayName);
        OrganizationMember organizationMember = memberRepository.findById(member.id()).orElseThrow();
        return courierMembershipChecks.toCourierDto(organizationMember, request.courierServiceId());
    }

    @Transactional(readOnly = true)
    public CourierDto get(Long userId, UUID memberId) {
        OrganizationMember member = accessControl.requireCourierMember(memberId);
        if (member.getUserId().equals(userId)) {
            return courierMembershipChecks.toCourierDto(member, member.getOrganizationId());
        }
        accessControl.requireServiceStaff(userId, member.getOrganizationId());
        return courierMembershipChecks.toCourierDto(member, member.getOrganizationId());
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
        return courierMembershipChecks.toCourierDto(member, member.getOrganizationId());
    }

    @Transactional
    public ProvisioningCredentialsDto resetAccess(Long actorUserId, UUID memberId) {
        OrganizationMember member = accessControl.requireCourierMember(memberId);
        accessControl.requireServiceStaff(actorUserId, member.getOrganizationId());
        AccountProvisionResult provisioned = accountProvisioningClient.resetWebCredentials(member.getUserId());
        return ProvisioningCredentialsDto.fromProvision(provisioned.login(), provisioned.temporaryPassword());
    }
}
