package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutMethod;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutRequest;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PartnerPayoutRequestRepository extends JpaRepository<PartnerPayoutRequest, UUID> {

    List<PartnerPayoutRequest> findByPartnerAccountIdOrderByCreatedAtDesc(UUID partnerAccountId);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM PartnerPayoutRequest p
            WHERE p.partnerAccountId = :accountId
              AND p.status IN :statuses
            """)
    BigDecimal sumAmountByAccountIdAndStatuses(
            @Param("accountId") UUID accountId,
            @Param("statuses") Collection<PartnerPayoutStatus> statuses);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM PartnerPayoutRequest p
            WHERE p.partnerAccountId = :accountId
              AND p.status = ru.kzn.buzanov.delivery.domain.PartnerPayoutStatus.PAID
              AND p.payoutMethod = :method
            """)
    BigDecimal sumPaidAmountByAccountIdAndMethod(
            @Param("accountId") UUID accountId,
            @Param("method") PartnerPayoutMethod method);

    boolean existsByPartnerAccountIdAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID partnerAccountId,
            Collection<PartnerPayoutStatus> statuses,
            Instant createdAtStart,
            Instant createdAtEndExclusive);

    boolean existsByPartnerAccountIdAndPayoutCycleMonthAndStatusIn(
            UUID partnerAccountId,
            String payoutCycleMonth,
            Collection<PartnerPayoutStatus> statuses);

    @Query("""
            SELECT p FROM PartnerPayoutRequest p
            WHERE p.partnerAccountId IN (
                SELECT a.id FROM PartnerAccount a WHERE a.courierServiceId = :courierServiceId
            )
            ORDER BY p.createdAt DESC
            """)
    List<PartnerPayoutRequest> findByCourierServiceIdOrderByCreatedAtDesc(
            @Param("courierServiceId") UUID courierServiceId);

    List<PartnerPayoutRequest> findByStatusAndScheduledPayoutDateLessThanEqual(
            PartnerPayoutStatus status,
            java.time.LocalDate scheduledPayoutDate);
}
