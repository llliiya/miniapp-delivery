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
@Table(schema = "delivery", name = "partner_ledger_transactions")
@Getter
@Setter
public class PartnerLedgerTransaction {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "partner_account_id", nullable = false)
    private UUID partnerAccountId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private PartnerLedgerTransactionType type;

    @Column(name = "balance_transfer_id")
    private UUID balanceTransferId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
