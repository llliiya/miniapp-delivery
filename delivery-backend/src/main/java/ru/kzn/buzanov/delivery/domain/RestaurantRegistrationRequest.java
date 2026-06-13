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
@Table(schema = "delivery", name = "restaurant_registration_requests")
public class RestaurantRegistrationRequest {

    @Id
    private UUID id;

    @Column(name = "restaurant_name", nullable = false)
    private String restaurantName;

    @Column(nullable = false, length = 512)
    private String address;

    @Column(name = "contact_person", nullable = false)
    private String contactPerson;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(nullable = false)
    private String email;

    @Column(columnDefinition = "text")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private RestaurantRegistrationSourceType sourceType;

    @Column(name = "partner_code", length = 8)
    private String partnerCode;

    @Column(name = "courier_member_id")
    private UUID courierMemberId;

    @Column(name = "referrer_organization_id")
    private UUID referrerOrganizationId;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RestaurantRegistrationRequestStatus status = RestaurantRegistrationRequestStatus.NEW;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processed_by")
    private Long processedBy;
}
