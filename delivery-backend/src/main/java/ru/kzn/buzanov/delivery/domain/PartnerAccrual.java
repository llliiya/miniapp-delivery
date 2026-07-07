package ru.kzn.buzanov.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "partner_accruals")
@Getter
@Setter
public class PartnerAccrual {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "partner_account_id", nullable = false)
    private UUID partnerAccountId;

    @Column(name = "partner_referral_id", nullable = false)
    private UUID partnerReferralId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "calculation_base_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal calculationBaseAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartnerAccrualStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings_snapshot", nullable = false, columnDefinition = "jsonb")
    private String settingsSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "available_from", nullable = false)
    private Instant availableFrom;

    @Column(name = "accrual_period_month", nullable = false, length = 7)
    private String accrualPeriodMonth;

    @Column(name = "payout_cycle_month", nullable = false, length = 7)
    private String payoutCycleMonth;

    @Column(name = "reversed_at")
    private Instant reversedAt;
}
