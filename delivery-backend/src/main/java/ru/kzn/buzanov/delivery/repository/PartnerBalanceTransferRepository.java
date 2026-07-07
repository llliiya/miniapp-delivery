package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kzn.buzanov.delivery.domain.PartnerBalanceTransfer;
import ru.kzn.buzanov.delivery.domain.PartnerBalanceTransferStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PartnerBalanceTransferRepository extends JpaRepository<PartnerBalanceTransfer, UUID> {

    List<PartnerBalanceTransfer> findByPartnerAccountIdOrderByCreatedAtDesc(UUID partnerAccountId);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM PartnerBalanceTransfer t
            WHERE t.partnerAccountId = :accountId
              AND t.status IN :statuses
            """)
    BigDecimal sumAmountByAccountIdAndStatuses(
            @Param("accountId") UUID accountId,
            @Param("statuses") Collection<PartnerBalanceTransferStatus> statuses);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM PartnerBalanceTransfer t
            WHERE t.partnerAccountId = :accountId
              AND t.status = ru.kzn.buzanov.delivery.domain.PartnerBalanceTransferStatus.COMPLETED
            """)
    BigDecimal sumCompletedAmountByAccountId(@Param("accountId") UUID accountId);

    boolean existsByPartnerAccountIdAndPayoutCycleMonthAndStatusIn(
            UUID partnerAccountId,
            String payoutCycleMonth,
            Collection<PartnerBalanceTransferStatus> statuses);

    @Query("""
            SELECT t FROM PartnerBalanceTransfer t
            WHERE t.partnerAccountId IN (
                SELECT a.id FROM PartnerAccount a WHERE a.courierServiceId = :courierServiceId
            )
            ORDER BY t.createdAt DESC
            """)
    List<PartnerBalanceTransfer> findByCourierServiceIdOrderByCreatedAtDesc(
            @Param("courierServiceId") UUID courierServiceId);

    List<PartnerBalanceTransfer> findByStatusAndScheduledExecutionDateLessThanEqual(
            PartnerBalanceTransferStatus status,
            LocalDate scheduledExecutionDate);
}
