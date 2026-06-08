package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.OrganizationType;

import java.util.UUID;

public record MembershipDto(
        UUID memberId,
        Long memberPublicId,
        UUID organizationId,
        Long organizationPublicId,
        String organizationName,
        OrganizationType organizationType,
        UUID courierServiceId,
        MemberRole role,
        MemberStatus status,
        String accessKind
) {
    public static final String ACCESS_MEMBER = "member";
    public static final String ACCESS_SERVICE_SCOPE = "service_scope";

    public MembershipDto {
        if (accessKind == null || accessKind.isBlank()) {
            accessKind = ACCESS_MEMBER;
        }
    }
}
