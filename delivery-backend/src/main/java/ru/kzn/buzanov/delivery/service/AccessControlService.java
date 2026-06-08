package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.OrganizationType;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private static final Set<MemberRole> SERVICE_STAFF = EnumSet.of(MemberRole.owner, MemberRole.manager);
    private static final Set<MemberRole> ORG_MANAGERS = EnumSet.of(MemberRole.owner, MemberRole.manager);

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;

    public Organization requireOrganization(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Организация не найдена"));
    }

    public OrganizationMember requireActiveMembership(Long userId, UUID organizationId) {
        OrganizationMember member = memberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к организации"));
        if (member.getStatus() != MemberStatus.active) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Участник заблокирован");
        }
        return member;
    }

    public Optional<OrganizationMember> findMembership(Long userId, UUID organizationId) {
        return memberRepository.findByOrganizationIdAndUserId(organizationId, userId);
    }

    public void requireOrgManager(Long userId, UUID organizationId) {
        OrganizationMember member = requireActiveMembership(userId, organizationId);
        if (!ORG_MANAGERS.contains(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
        }
    }

    public void requireServiceStaff(Long userId, UUID courierServiceId) {
        Organization service = requireOrganization(courierServiceId);
        if (service.getType() != OrganizationType.courier_service) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Указанная организация не является курьерской службой");
        }
        OrganizationMember member = requireActiveMembership(userId, courierServiceId);
        if (!SERVICE_STAFF.contains(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав службы");
        }
    }

    public void requireServiceOwner(Long userId, UUID courierServiceId) {
        OrganizationMember member = requireActiveMembership(userId, courierServiceId);
        if (member.getRole() != MemberRole.owner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Только owner службы");
        }
    }

    public boolean canViewOrganization(Long userId, Organization org) {
        Optional<OrganizationMember> direct = findMembership(userId, org.getId());
        if (direct.isPresent() && direct.get().getStatus() == MemberStatus.active) {
            return true;
        }
        if (org.getType() == OrganizationType.client_restaurant && org.getCourierServiceId() != null) {
            Optional<OrganizationMember> serviceMember =
                    findMembership(userId, org.getCourierServiceId());
            if (serviceMember.isPresent()
                    && serviceMember.get().getStatus() == MemberStatus.active
                    && SERVICE_STAFF.contains(serviceMember.get().getRole())) {
                return true;
            }
        }
        return false;
    }

    public void requireCanViewOrganization(Long userId, Organization org) {
        if (!canViewOrganization(userId, org)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к организации");
        }
    }

    public void requireCanManageOrganization(Long userId, Organization org) {
        Optional<OrganizationMember> direct = findMembership(userId, org.getId());
        if (direct.isPresent()
                && direct.get().getStatus() == MemberStatus.active
                && ORG_MANAGERS.contains(direct.get().getRole())) {
            return;
        }
        if (org.getType() == OrganizationType.client_restaurant
                && org.getCourierServiceId() != null) {
            Optional<OrganizationMember> serviceMember =
                    findMembership(userId, org.getCourierServiceId());
            if (serviceMember.isPresent()
                    && serviceMember.get().getStatus() == MemberStatus.active
                    && SERVICE_STAFF.contains(serviceMember.get().getRole())) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
    }

    public void requireCanManageMembers(Long userId, Organization org) {
        requireCanManageOrganization(userId, org);
    }

    public void validateRoleForOrganization(Organization org, MemberRole role) {
        if (org.getType() == OrganizationType.client_restaurant && role == MemberRole.courier) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Курьера нельзя добавить в ресторан");
        }
        if (org.getType() == OrganizationType.courier_service
                && role != MemberRole.owner
                && role != MemberRole.manager
                && role != MemberRole.courier) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимая роль");
        }
    }

    public Organization requireCourierService(UUID courierServiceId) {
        Organization service = requireOrganization(courierServiceId);
        if (service.getType() != OrganizationType.courier_service) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Организация не является курьерской службой");
        }
        return service;
    }

    public OrganizationMember requireCourierMember(UUID memberId) {
        OrganizationMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Курьер не найден"));
        Organization org = requireOrganization(member.getOrganizationId());
        if (org.getType() != OrganizationType.courier_service || member.getRole() != MemberRole.courier) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Курьер не найден");
        }
        return member;
    }

    public Organization requireRestaurant(UUID restaurantId) {
        Organization org = requireOrganization(restaurantId);
        if (org.getType() != OrganizationType.client_restaurant) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ресторан не найден");
        }
        return org;
    }

    public boolean canManageRestaurantResource(Long userId, Organization restaurant) {
        if (restaurant.getType() != OrganizationType.client_restaurant) {
            return false;
        }
        Optional<OrganizationMember> direct = findMembership(userId, restaurant.getId());
        if (direct.isPresent()
                && direct.get().getStatus() == MemberStatus.active
                && ORG_MANAGERS.contains(direct.get().getRole())) {
            return true;
        }
        if (restaurant.getCourierServiceId() != null) {
            Optional<OrganizationMember> serviceMember =
                    findMembership(userId, restaurant.getCourierServiceId());
            return serviceMember.isPresent()
                    && serviceMember.get().getStatus() == MemberStatus.active
                    && SERVICE_STAFF.contains(serviceMember.get().getRole());
        }
        return false;
    }

    public void requireCanManagePickupPoints(Long userId, Organization restaurant) {
        requireRestaurant(restaurant.getId());
        if (!canManageRestaurantResource(userId, restaurant)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав для точек забора");
        }
    }

    public void requireCanViewRestaurantChannels(Long userId, Organization restaurant) {
        requireRestaurant(restaurant.getId());
        if (!canViewOrganization(userId, restaurant)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа");
        }
    }

    public void requireCanManageRestaurantChannelBindings(Long userId, Organization restaurant) {
        requireRestaurant(restaurant.getId());
        if (restaurant.getCourierServiceId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У ресторана не указана курьерская служба");
        }
        requireServiceStaff(userId, restaurant.getCourierServiceId());
    }

    public void requireCanManagePublicationChannels(Long userId, UUID courierServiceId) {
        requireServiceStaff(userId, courierServiceId);
    }
}
