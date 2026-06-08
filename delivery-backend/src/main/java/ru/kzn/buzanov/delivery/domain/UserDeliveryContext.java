package ru.kzn.buzanov.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "user_delivery_context")
@Getter
@Setter
public class UserDeliveryContext {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_role", nullable = false)
    private MemberRole deliveryRole = MemberRole.courier;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private DeliveryAccountStatus accountStatus = DeliveryAccountStatus.inactive;

    @Column(name = "active_organization_id")
    private UUID activeOrganizationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
