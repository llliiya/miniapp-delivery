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

@Getter
@Setter
@Entity
@Table(name = "courier_requests")
public class CourierRequest {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phone;

    @Column
    private String email;

    @Column(nullable = false)
    private String city;

    @Column(columnDefinition = "text")
    private String comment;

    @Column
    private String transport;

    @Column(name = "messenger_provider")
    private String messengerProvider;

    @Column(name = "messenger_external_id")
    private String messengerExternalId;

    @Column(name = "messenger_username")
    private String messengerUsername;

    @Column
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourierRequestStatus status = CourierRequestStatus.NEW;

    @Column(name = "linked_user_id")
    private Long linkedUserId;

    @Column(name = "partner_code", length = 8)
    private String partnerCode;

    @Column(name = "referrer_member_id")
    private UUID referrerMemberId;

    @Column(name = "referrer_organization_id")
    private UUID referrerOrganizationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
