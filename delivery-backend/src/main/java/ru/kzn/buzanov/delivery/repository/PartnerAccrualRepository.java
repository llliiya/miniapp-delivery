package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kzn.buzanov.delivery.domain.PartnerAccrual;
import ru.kzn.buzanov.delivery.domain.PartnerAccrualStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerAccrualRepository extends JpaRepository<PartnerAccrual, UUID> {

    List<PartnerAccrual> findByPartnerAccountIdOrderByCreatedAtDesc(UUID partnerAccountId);

    boolean existsByOrderIdAndPartnerReferralIdAndStatus(
            UUID orderId,
            UUID partnerReferralId,
            PartnerAccrualStatus status);

    Optional<PartnerAccrual> findByOrderIdAndPartnerReferralIdAndStatus(
            UUID orderId,
            UUID partnerReferralId,
            PartnerAccrualStatus status);

    List<PartnerAccrual> findByOrderIdAndStatus(UUID orderId, PartnerAccrualStatus status);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN a.status = ru.kzn.buzanov.delivery.domain.PartnerAccrualStatus.ACCRUED
                THEN a.amount ELSE 0 END), 0)
            FROM PartnerAccrual a
            WHERE a.partnerAccountId = :accountId
            """)
    BigDecimal sumAccruedAmountByAccountId(@Param("accountId") UUID accountId);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN a.status = ru.kzn.buzanov.delivery.domain.PartnerAccrualStatus.REVERSED
                THEN a.amount ELSE 0 END), 0)
            FROM PartnerAccrual a
            WHERE a.partnerAccountId = :accountId
            """)
    BigDecimal sumReversedAmountByAccountId(@Param("accountId") UUID accountId);

    @Query("""
            SELECT COALESCE(SUM(a.amount), 0)
            FROM PartnerAccrual a
            WHERE a.partnerAccountId = :accountId
              AND a.status = ru.kzn.buzanov.delivery.domain.PartnerAccrualStatus.ACCRUED
              AND a.availableFrom > :now
            """)
    BigDecimal sumAccruedNotYetEligibleByAccountId(
            @Param("accountId") UUID accountId,
            @Param("now") Instant now);

    @Query("""
            SELECT a.partnerReferralId AS partnerReferralId,
                   COUNT(a) AS accrualCount,
                   COALESCE(SUM(CASE WHEN a.status = ru.kzn.buzanov.delivery.domain.PartnerAccrualStatus.ACCRUED
                       THEN a.amount ELSE 0 END), 0) AS accruedTotal,
                   COALESCE(SUM(CASE WHEN a.status = ru.kzn.buzanov.delivery.domain.PartnerAccrualStatus.REVERSED
                       THEN a.amount ELSE 0 END), 0) AS reversedTotal,
                   MAX(a.createdAt) AS lastAccrualAt
            FROM PartnerAccrual a
            WHERE a.partnerReferralId IN :referralIds
            GROUP BY a.partnerReferralId
            """)
    List<PartnerReferralAccrualStats> aggregateByReferralIds(@Param("referralIds") Collection<UUID> referralIds);
}
