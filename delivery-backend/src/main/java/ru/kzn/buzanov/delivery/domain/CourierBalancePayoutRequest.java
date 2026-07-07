package ru.kzn.buzanov.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "courier_balance_payout_requests")
@Getter
@Setter
public class CourierBalancePayoutRequest {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "courier_member_id", nullable = false)
    private UUID courierMemberId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method", nullable = false, length = 64)
    private CourierBalancePayoutMethod payoutMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CourierBalancePayoutStatus status;

    @Column(name = "scheduled_payout_date")
    private LocalDate scheduledPayoutDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payout_details", columnDefinition = "jsonb")
    private String payoutDetails;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
