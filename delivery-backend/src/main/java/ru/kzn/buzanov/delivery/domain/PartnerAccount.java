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
@Table(schema = "delivery", name = "partner_accounts")
@Getter
@Setter
public class PartnerAccount {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "courier_service_id", nullable = false)
    private UUID courierServiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 32)
    private PartnerParticipantType participantType;

    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "available_for_payout", nullable = false, precision = 14, scale = 2)
    private BigDecimal availableForPayout = BigDecimal.ZERO;

    @Column(name = "pending_payout", nullable = false, precision = 14, scale = 2)
    private BigDecimal pendingPayout = BigDecimal.ZERO;

    @Column(name = "paid_out", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidOut = BigDecimal.ZERO;

    @Column(name = "transferred_to_main_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal transferredToMainBalance = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
