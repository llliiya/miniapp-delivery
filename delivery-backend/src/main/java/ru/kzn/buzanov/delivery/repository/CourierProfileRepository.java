package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.CourierProfile;

import java.util.Optional;
import java.util.UUID;

public interface CourierProfileRepository extends JpaRepository<CourierProfile, UUID> {

    Optional<CourierProfile> findByMemberId(UUID memberId);

    Optional<CourierProfile> findByPartnerCode(String partnerCode);

    boolean existsByPartnerCode(String partnerCode);
}
