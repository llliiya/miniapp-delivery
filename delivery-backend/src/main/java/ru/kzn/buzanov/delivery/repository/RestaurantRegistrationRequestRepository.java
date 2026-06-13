package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequest;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequestStatus;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationSourceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantRegistrationRequestRepository extends JpaRepository<RestaurantRegistrationRequest, UUID> {

    Optional<RestaurantRegistrationRequest> findFirstByPhoneAndStatusInOrderByCreatedAtDesc(
            String phone, List<RestaurantRegistrationRequestStatus> statuses);

    List<RestaurantRegistrationRequest> findByCourierMemberIdOrderByCreatedAtDesc(UUID courierMemberId);

    List<RestaurantRegistrationRequest> findByReferrerOrganizationIdOrderByCreatedAtDesc(UUID referrerOrganizationId);

    long countByCourierMemberId(UUID courierMemberId);

    long countByCourierMemberIdAndStatusIn(UUID courierMemberId, List<RestaurantRegistrationRequestStatus> statuses);

    @Query("""
            SELECT r FROM RestaurantRegistrationRequest r
            LEFT JOIN Organization o ON o.id = r.restaurantId
            LEFT JOIN Organization refOrg ON refOrg.id = r.referrerOrganizationId
            WHERE r.sourceType = :selfType
               OR (r.courierMemberId IN (
                    SELECT m.id FROM OrganizationMember m WHERE m.organizationId = :courierServiceId
               ))
               OR (refOrg.courierServiceId = :courierServiceId)
               OR (r.sourceType = :adminType AND o.courierServiceId = :courierServiceId)
            ORDER BY r.createdAt DESC
            """)
    List<RestaurantRegistrationRequest> findVisibleForCourierService(
            @Param("courierServiceId") UUID courierServiceId,
            @Param("selfType") RestaurantRegistrationSourceType selfType,
            @Param("adminType") RestaurantRegistrationSourceType adminType);
}
