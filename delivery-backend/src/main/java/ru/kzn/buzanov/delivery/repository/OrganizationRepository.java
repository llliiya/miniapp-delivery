package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("""
            SELECT DISTINCT o.city FROM Organization o
            WHERE o.courierServiceId = :courierServiceId
              AND o.type = ru.kzn.buzanov.delivery.domain.OrganizationType.client_restaurant
              AND o.city IS NOT NULL
              AND TRIM(o.city) <> ''
            """)
    List<String> findDistinctRestaurantCities(@Param("courierServiceId") UUID courierServiceId);
}
