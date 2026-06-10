package ru.kzn.buzanov.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "balance_transactions")
@Getter
@Setter
public class BalanceTransaction {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "courier_member_id", nullable = false)
    private UUID courierMemberId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private BalanceTransactionType type;

    @Column(length = 512)
    private String reason;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
