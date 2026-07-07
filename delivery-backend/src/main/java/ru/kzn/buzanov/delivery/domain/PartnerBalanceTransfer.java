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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "partner_balance_transfers")
@Getter
@Setter
public class PartnerBalanceTransfer {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "partner_account_id", nullable = false)
    private UUID partnerAccountId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "scheduled_execution_date", nullable = false)
    private LocalDate scheduledExecutionDate;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartnerBalanceTransferStatus status;

    @Column(name = "payout_cycle_month", nullable = false, length = 7)
    private String payoutCycleMonth;

    @Column(name = "partner_ledger_transaction_id")
    private UUID partnerLedgerTransactionId;

    @Column(name = "main_balance_ledger_transaction_id")
    private UUID mainBalanceLedgerTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
