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
@Table(schema = "delivery", name = "courier_service_financial_settings")
@Getter
@Setter
public class CourierServiceFinancialSettings {

    @Id
    @Column(name = "courier_service_id", nullable = false)
    private UUID courierServiceId;

    @Column(name = "platform_fee_enabled", nullable = false)
    private boolean platformFeeEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_fee_type", nullable = false, length = 32)
    private PlatformFeeType platformFeeType = PlatformFeeType.PERCENT;

    @Column(name = "platform_fee_value", nullable = false, precision = 14, scale = 4)
    private BigDecimal platformFeeValue = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
