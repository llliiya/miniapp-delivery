package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    List<OrganizationMember> findByUserId(Long userId);

    List<OrganizationMember> findByUserIdAndStatus(Long userId, MemberStatus status);

    List<OrganizationMember> findByOrganizationId(UUID organizationId);

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, Long userId);

    List<OrganizationMember> findByOrganizationIdAndRole(UUID organizationId, MemberRole role);

    List<OrganizationMember> findByOrganizationIdInAndRole(Collection<UUID> organizationIds, MemberRole role);
}
