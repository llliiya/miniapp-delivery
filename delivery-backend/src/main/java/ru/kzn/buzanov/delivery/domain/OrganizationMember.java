package ru.kzn.buzanov.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "organization_members")
@DynamicInsert
@Getter
@Setter
public class OrganizationMember {

    @Id
    @Column(nullable = false)
    private UUID id;

    /** DB-generated sequential id for display within an organization. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", updatable = false)
    private Long publicId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MemberStatus status = MemberStatus.active;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
