package ru.kzn.buzanov.delivery.service;

import org.springframework.stereotype.Component;
import ru.kzn.buzanov.delivery.domain.CourierProfile;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.dto.CourierDto;
import ru.kzn.buzanov.delivery.dto.MemberDto;
import ru.kzn.buzanov.delivery.dto.MembershipDto;
import ru.kzn.buzanov.delivery.dto.OrganizationDto;

import java.util.UUID;

@Component
public class DeliveryDtoMapper {

    public OrganizationDto toOrganizationDto(Organization org) {
        return new OrganizationDto(
                org.getId(),
                org.getPublicId(),
                org.getType(),
                org.getName(),
                org.getOwnerUserId(),
                org.getCourierServiceId(),
                org.isActive(),
                org.getCreatedAt(),
                org.getCity()
        );
    }

    public MemberDto toMemberDto(OrganizationMember member) {
        return new MemberDto(
                member.getId(),
                member.getPublicId(),
                member.getOrganizationId(),
                member.getUserId(),
                member.getRole(),
                member.getStatus(),
                member.getDisplayName(),
                member.getCreatedAt()
        );
    }

    public MembershipDto toMembershipDto(OrganizationMember member, Organization org) {
        return new MembershipDto(
                member.getId(),
                member.getPublicId(),
                org.getId(),
                org.getPublicId(),
                org.getName(),
                org.getType(),
                org.getCourierServiceId(),
                member.getRole(),
                member.getStatus(),
                MembershipDto.ACCESS_MEMBER
        );
    }

    public CourierDto toCourierDto(OrganizationMember member, CourierProfile profile, UUID courierServiceId) {
        return new CourierDto(
                member.getId(),
                member.getPublicId(),
                courierServiceId,
                member.getUserId(),
                member.getDisplayName(),
                member.getStatus(),
                profile.getBalance(),
                profile.getCompletedOrdersCount(),
                member.getCreatedAt(),
                null,
                null);
    }
}
