package ru.kzn.buzanov.delivery.fulfillment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "delivery_zone")
@Getter
@Setter
public class DeliveryZone {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int priority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String geometry;

    @Column(name = "delivery_fee_minor", nullable = false)
    private long deliveryFeeMinor;

    @Column(name = "free_delivery_from_minor")
    private Long freeDeliveryFromMinor;

    @Column(name = "min_order_amount_minor")
    private Long minOrderAmountMinor;

    @Column(name = "eta_min_minutes")
    private Integer etaMinMinutes;

    @Column(name = "eta_max_minutes")
    private Integer etaMaxMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;
}
