package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.PartnerAccount;

import java.util.Optional;
import java.util.UUID;

public interface PartnerAccountRepository extends JpaRepository<PartnerAccount, UUID> {

    Optional<PartnerAccount> findByMemberId(UUID memberId);

    Optional<PartnerAccount> findByOrganizationId(UUID organizationId);
}
