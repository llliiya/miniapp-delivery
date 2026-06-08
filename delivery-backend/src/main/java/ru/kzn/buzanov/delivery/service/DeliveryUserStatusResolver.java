package ru.kzn.buzanov.delivery.service;

import ru.kzn.buzanov.delivery.domain.DeliveryAccountStatus;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.OrganizationType;
import ru.kzn.buzanov.delivery.domain.UserDeliveryContext;
import ru.kzn.buzanov.delivery.domain.UserDeliveryStatus;
import ru.kzn.buzanov.delivery.dto.MembershipDto;

import java.util.List;

public final class DeliveryUserStatusResolver {

    private DeliveryUserStatusResolver() {
    }

    public static UserDeliveryStatus resolve(
            UserDeliveryContext profile,
            List<MembershipDto> memberships,
            String interfaceMode) {
        if (profile != null && profile.getAccountStatus() == DeliveryAccountStatus.blocked) {
            return UserDeliveryStatus.BLOCKED;
        }
        if (hasActiveCourierMembership(memberships) || hasActiveUsableMembership(memberships)) {
            return UserDeliveryStatus.ACTIVE;
        }
        if (isBlockedCourier(memberships)) {
            return UserDeliveryStatus.BLOCKED;
        }
        if (isPendingCourier(profile, memberships, interfaceMode)) {
            return UserDeliveryStatus.PENDING;
        }
        return UserDeliveryStatus.NO_ACCESS;
    }

    private static boolean hasActiveCourierMembership(List<MembershipDto> memberships) {
        return memberships.stream().anyMatch(m ->
                m.status() == MemberStatus.active
                        && m.organizationType() == OrganizationType.courier_service
                        && m.role() == MemberRole.courier
                        && !MembershipDto.ACCESS_SERVICE_SCOPE.equals(m.accessKind()));
    }

    private static boolean hasActiveUsableMembership(List<MembershipDto> memberships) {
        return memberships.stream().anyMatch(m ->
                m.status() == MemberStatus.active && InterfaceModeResolver.resolve(m) != null);
    }

    private static boolean isBlockedCourier(List<MembershipDto> memberships) {
        return memberships.stream().anyMatch(m ->
                m.organizationType() == OrganizationType.courier_service
                        && m.role() == MemberRole.courier
                        && !MembershipDto.ACCESS_SERVICE_SCOPE.equals(m.accessKind())
                        && m.status() == MemberStatus.blocked);
    }

    private static boolean isPendingCourier(
            UserDeliveryContext profile,
            List<MembershipDto> memberships,
            String interfaceMode) {
        boolean hasCourierMembership = memberships.stream().anyMatch(m ->
                m.organizationType() == OrganizationType.courier_service
                        && m.role() == MemberRole.courier
                        && !MembershipDto.ACCESS_SERVICE_SCOPE.equals(m.accessKind()));
        if (hasCourierMembership) {
            return true;
        }
        if ("courier".equals(interfaceMode)) {
            return true;
        }
        if (profile != null
                && profile.getDeliveryRole() == MemberRole.courier
                && profile.getAccountStatus() == DeliveryAccountStatus.inactive) {
            return true;
        }
        return memberships.isEmpty();
    }
}
