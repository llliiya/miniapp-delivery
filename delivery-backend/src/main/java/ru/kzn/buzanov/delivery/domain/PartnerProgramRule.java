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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "partner_program_rules")
@Getter
@Setter
public class PartnerProgramRule {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "courier_service_id", nullable = false)
    private UUID courierServiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "referrer_type", nullable = false, length = 32)
    private PartnerReferrerType referrerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "invitee_type", nullable = false, length = 32)
    private PartnerReferralType inviteeType;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type", nullable = false, length = 32)
    private PartnerCalculationType calculationType;

    @Column(name = "percent_value", precision = 8, scale = 4)
    private BigDecimal percentValue;

    @Column(name = "fixed_amount", precision = 14, scale = 2)
    private BigDecimal fixedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_base", nullable = false, length = 64)
    private PartnerCalculationBase calculationBase;

    @Column(name = "duration_months")
    private Integer durationMonths;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accrual_conditions", columnDefinition = "jsonb")
    private String accrualConditions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payout_restrictions", columnDefinition = "jsonb")
    private String payoutRestrictions;

    @Column(name = "min_payout_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal minPayoutAmount = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payout_methods", columnDefinition = "jsonb")
    private String payoutMethods;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
