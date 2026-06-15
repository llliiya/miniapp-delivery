package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kzn.buzanov.delivery.domain.PublicationChannel;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PublicationChannelRepository extends JpaRepository<PublicationChannel, UUID> {

    List<PublicationChannel> findByCourierServiceIdOrderByCreatedAtDesc(UUID courierServiceId);

    List<PublicationChannel> findByIdIn(Collection<UUID> ids);

    @Query("""
            SELECT DISTINCT c.city FROM PublicationChannel c
            WHERE c.courierServiceId = :courierServiceId
              AND c.city IS NOT NULL
              AND TRIM(c.city) <> ''
            """)
    List<String> findDistinctCities(@Param("courierServiceId") UUID courierServiceId);
}
