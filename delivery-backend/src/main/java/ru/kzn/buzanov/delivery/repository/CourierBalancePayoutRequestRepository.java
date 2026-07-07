package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kzn.buzanov.delivery.domain.CourierBalancePayoutRequest;
import ru.kzn.buzanov.delivery.domain.CourierBalancePayoutStatus;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CourierBalancePayoutRequestRepository extends JpaRepository<CourierBalancePayoutRequest, UUID> {

    List<CourierBalancePayoutRequest> findByCourierMemberIdOrderByCreatedAtDesc(UUID courierMemberId);

    boolean existsByCourierMemberIdAndStatusIn(UUID courierMemberId, Collection<CourierBalancePayoutStatus> statuses);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM CourierBalancePayoutRequest p
            WHERE p.courierMemberId = :memberId
              AND p.status IN :statuses
            """)
    BigDecimal sumAmountByCourierMemberIdAndStatusIn(
            @Param("memberId") UUID memberId,
            @Param("statuses") Collection<CourierBalancePayoutStatus> statuses);

    @Query("""
            SELECT p FROM CourierBalancePayoutRequest p
            WHERE p.courierMemberId IN (
                SELECT m.id FROM OrganizationMember m
                WHERE m.organizationId = :courierServiceId
                  AND m.role = ru.kzn.buzanov.delivery.domain.MemberRole.courier
            )
            ORDER BY p.createdAt DESC
            """)
    List<CourierBalancePayoutRequest> findByCourierServiceIdOrderByCreatedAtDesc(
            @Param("courierServiceId") UUID courierServiceId);
}
