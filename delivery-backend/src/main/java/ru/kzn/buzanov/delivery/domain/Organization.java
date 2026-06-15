package ru.kzn.buzanov.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "organizations")
@Getter
@Setter
public class Organization {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    private Long publicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrganizationType type;

    @Column(nullable = false)
    private String name;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "courier_service_id")
    private UUID courierServiceId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "partner_code", length = 8)
    private String partnerCode;

    @Column(length = 128)
    private String city;
}
