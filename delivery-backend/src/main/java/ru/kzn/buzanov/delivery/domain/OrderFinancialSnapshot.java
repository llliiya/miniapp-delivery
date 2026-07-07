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
@Table(schema = "delivery", name = "order_financial_snapshots")
@Getter
@Setter
public class OrderFinancialSnapshot {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "delivery_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal deliveryPrice;

    @Column(name = "platform_fee_enabled", nullable = false)
    private boolean platformFeeEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_fee_type", length = 32)
    private PlatformFeeType platformFeeType;

    @Column(name = "platform_fee_value", precision = 14, scale = 4)
    private BigDecimal platformFeeValue;

    @Column(name = "platform_fee_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal platformFeeAmount;

    @Column(name = "partner_reward_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal partnerRewardAmount;

    @Column(name = "courier_net_earning", nullable = false, precision = 14, scale = 2)
    private BigDecimal courierNetEarning;

    @Column(name = "partner_rule_id")
    private UUID partnerRuleId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_snapshot", nullable = false, columnDefinition = "jsonb")
    private String detailsSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
