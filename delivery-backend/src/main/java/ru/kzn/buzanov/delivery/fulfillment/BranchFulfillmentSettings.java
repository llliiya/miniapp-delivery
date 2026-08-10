package ru.kzn.buzanov.delivery.fulfillment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "branch_fulfillment_settings")
@Getter
@Setter
public class BranchFulfillmentSettings {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "delivery_enabled", nullable = false)
    private boolean deliveryEnabled;

    @Column(name = "pickup_enabled", nullable = false)
    private boolean pickupEnabled;

    @Column(name = "minimum_delivery_order_minor", nullable = false)
    private long minimumDeliveryOrderMinor;

    @Column(name = "delivery_fee_minor", nullable = false)
    private long deliveryFeeMinor;

    @Column(name = "free_delivery_from_minor")
    private Long freeDeliveryFromMinor;

    @Column(name = "delivery_estimated_min_minutes", nullable = false)
    private int deliveryEstimatedMinMinutes;

    @Column(name = "delivery_estimated_max_minutes", nullable = false)
    private int deliveryEstimatedMaxMinutes;

    @Column(name = "pickup_estimated_minutes", nullable = false)
    private int pickupEstimatedMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;
}
