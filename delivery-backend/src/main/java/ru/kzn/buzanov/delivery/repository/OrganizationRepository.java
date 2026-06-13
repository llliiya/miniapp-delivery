package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    List<Organization> findByCourierServiceIdAndType(UUID courierServiceId, OrganizationType type);

    List<Organization> findByIdIn(Collection<UUID> ids);

    Optional<Organization> findByPartnerCode(String partnerCode);

    boolean existsByPartnerCode(String partnerCode);
}
