package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.OrganizationType;
import ru.kzn.buzanov.delivery.dto.CreateRestaurantResponse;
import ru.kzn.buzanov.delivery.dto.OrganizationDto;
import ru.kzn.buzanov.delivery.dto.ProvisioningCredentialsDto;
import ru.kzn.buzanov.delivery.dto.request.CreateRestaurantRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchOrganizationRequest;
import ru.kzn.buzanov.delivery.integration.AccountProvisioningClient;
import ru.kzn.buzanov.delivery.integration.account.AccountProvisionRequest;
import ru.kzn.buzanov.delivery.util.EmailRequirements;
import ru.kzn.buzanov.delivery.integration.account.AccountProvisionResult;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.util.CityNormalizer;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AccessControlService accessControl;
    private final MemberService memberService;
    private final AccountProvisioningClient accountProvisioningClient;
    private final DeliveryDtoMapper mapper;
    private final RestaurantRegistrationAuditService registrationAuditService;

    @Transactional
    public CreateRestaurantResponse create(Long userId, CreateRestaurantRequest request) {
        if (request.isProvisioningFlow()) {
            return createWithOwnerProvisioning(userId, request, true);
        }
        return new CreateRestaurantResponse(createLegacy(userId, request), null);
    }

    @Transactional
    public CreateRestaurantResponse createForRegistrationApproval(Long userId, CreateRestaurantRequest request) {
        if (!request.isProvisioningFlow()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для одобрения заявки нужны данные владельца");
        }
        return createWithOwnerProvisioning(userId, request, false);
    }

    private CreateRestaurantResponse createWithOwnerProvisioning(
            Long actorUserId, CreateRestaurantRequest request, boolean recordAudit) {
        accessControl.requireServiceStaff(actorUserId, request.courierServiceId());
        String city = requireCity(request.city());
        var owner = request.owner();
        // TECH-DEBT: account provisioning commits before delivery TX. On failure after provision,
        // orphan account users remain (idempotency by phone, deactivation, compensating internal API — later).
        AccountProvisionResult provisioned = accountProvisioningClient.provisionWebEmployee(
                AccountProvisionRequest.forRestaurantOwner(
                        owner.fullName().trim(),
                        owner.phone().trim(),
                        EmailRequirements.requireEmail(owner.email()),
                        request.name().trim()));

        Instant now = Instant.now();
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setType(OrganizationType.client_restaurant);
        org.setName(request.name().trim());
        org.setOwnerUserId(provisioned.userId());
        org.setCourierServiceId(request.courierServiceId());
        org.setCity(city);
        org.setActive(true);
        org.setCreatedAt(now);
        organizationRepository.saveAndFlush(org);
        organizationRepository.findById(org.getId()).ifPresent(refreshed -> org.setPublicId(refreshed.getPublicId()));

        memberService.addMembershipForOrganization(
                org.getId(),
                provisioned.userId(),
                MemberRole.owner,
                owner.fullName().trim());

        OrganizationDto object = mapper.toOrganizationDto(org);
        ProvisioningCredentialsDto ownerCredentials = ProvisioningCredentialsDto.fromProvision(
                provisioned.login(), provisioned.temporaryPassword());
        if (recordAudit) {
            registrationAuditService.recordAdminCreation(
                    org.getId(),
                    request.name().trim(),
                    null,
                    owner.fullName().trim(),
                    owner.phone().trim(),
                    EmailRequirements.requireEmail(owner.email()),
                    actorUserId);
        }
        return new CreateRestaurantResponse(object, ownerCredentials);
    }

    private OrganizationDto createLegacy(Long userId, CreateRestaurantRequest request) {
        accessControl.requireServiceStaff(userId, request.courierServiceId());
        String city = requireCity(request.city());
        Instant now = Instant.now();
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setType(OrganizationType.client_restaurant);
        org.setName(request.name().trim());
        org.setOwnerUserId(userId);
        org.setCourierServiceId(request.courierServiceId());
        org.setCity(city);
        org.setActive(true);
        org.setCreatedAt(now);
        organizationRepository.saveAndFlush(org);
        return mapper.toOrganizationDto(org);
    }

    @Transactional(readOnly = true)
    public List<OrganizationDto> list(Long userId, UUID courierServiceId, String city) {
        String cityFilter = CityNormalizer.normalize(city);
        List<OrganizationMember> memberships = memberRepository.findByUserId(userId);
        Map<UUID, Organization> restaurants = new LinkedHashMap<>();

        for (OrganizationMember membership : memberships) {
            if (membership.getStatus() != ru.kzn.buzanov.delivery.domain.MemberStatus.active) {
                continue;
            }
            Organization org = organizationRepository.findById(membership.getOrganizationId()).orElse(null);
            if (org == null) {
                continue;
            }
            if (org.getType() == OrganizationType.client_restaurant) {
                restaurants.put(org.getId(), org);
            }
            if (org.getType() == OrganizationType.courier_service
                    && (membership.getRole() == ru.kzn.buzanov.delivery.domain.MemberRole.owner
                    || membership.getRole() == ru.kzn.buzanov.delivery.domain.MemberRole.manager)) {
                organizationRepository
                        .findByCourierServiceIdAndType(org.getId(), OrganizationType.client_restaurant)
                        .forEach(r -> restaurants.put(r.getId(), r));
            }
        }
        return restaurants.values().stream()
                .filter(Organization::isActive)
                .filter(r -> courierServiceId == null || courierServiceId.equals(r.getCourierServiceId()))
                .filter(r -> cityFilter == null || CityNormalizer.equals(r.getCity(), cityFilter))
                .map(mapper::toOrganizationDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationDto get(Long userId, UUID restaurantId) {
        Organization restaurant = accessControl.requireOrganization(restaurantId);
        if (restaurant.getType() != OrganizationType.client_restaurant) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ресторан не найден");
        }
        accessControl.requireCanViewOrganization(userId, restaurant);
        return mapper.toOrganizationDto(restaurant);
    }

    @Transactional
    public OrganizationDto patch(Long userId, UUID restaurantId, PatchOrganizationRequest request) {
        Organization restaurant = accessControl.requireOrganization(restaurantId);
        if (restaurant.getType() != OrganizationType.client_restaurant) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ресторан не найден");
        }
        accessControl.requireCanManageOrganization(userId, restaurant);
        if (request.name() != null && !request.name().isBlank()) {
            restaurant.setName(request.name().trim());
        }
        if (request.active() != null) {
            restaurant.setActive(request.active());
        }
        if (request.city() != null) {
            if (restaurant.getCourierServiceId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У объекта не указана курьерская служба");
            }
            accessControl.requireServiceStaff(userId, restaurant.getCourierServiceId());
            restaurant.setCity(CityNormalizer.normalize(request.city()));
        }
        organizationRepository.save(restaurant);
        return mapper.toOrganizationDto(restaurant);
    }

    private static String requireCity(String rawCity) {
        String city = CityNormalizer.normalize(rawCity);
        if (city == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите город");
        }
        return city;
    }
}
