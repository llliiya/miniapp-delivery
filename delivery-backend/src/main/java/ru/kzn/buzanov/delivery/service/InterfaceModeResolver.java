package ru.kzn.buzanov.delivery.service;

import ru.kzn.buzanov.delivery.domain.DeliveryAccountStatus;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.OrganizationType;
import ru.kzn.buzanov.delivery.domain.UserDeliveryContext;
import ru.kzn.buzanov.delivery.dto.MembershipDto;

public final class InterfaceModeResolver {

    private InterfaceModeResolver() {
    }

    public static String resolve(MembershipDto membership) {
        if (membership == null || membership.status() != MemberStatus.active) {
            return null;
        }
        if (MembershipDto.ACCESS_SERVICE_SCOPE.equals(membership.accessKind())) {
            return "service";
        }
        if (membership.organizationType() == OrganizationType.courier_service) {
            if (membership.role() == MemberRole.courier) {
                return "courier";
            }
            if (membership.role() == MemberRole.owner || membership.role() == MemberRole.manager) {
                return "service";
            }
        }
        if (membership.organizationType() == OrganizationType.client_restaurant) {
            if (membership.role() == MemberRole.owner || membership.role() == MemberRole.manager) {
                return "restaurant";
            }
        }
        return null;
    }

    /** Пользователь есть в account, профиль delivery ещё не активирован — интерфейс курьера (ожидание). */
    public static String resolvePendingProfile(UserDeliveryContext profile) {
        if (profile == null) {
            return null;
        }
        if (profile.getDeliveryRole() == MemberRole.courier
                && profile.getAccountStatus() == DeliveryAccountStatus.inactive) {
            return "courier";
        }
        return null;
    }
}
