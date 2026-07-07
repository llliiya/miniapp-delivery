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
@Table(schema = "delivery", name = "partner_referrals")
@Getter
@Setter
public class PartnerReferral {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "courier_service_id", nullable = false)
    private UUID courierServiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "referrer_type", nullable = false, length = 32)
    private PartnerReferrerType referrerType;

    @Column(name = "referrer_member_id")
    private UUID referrerMemberId;

    @Column(name = "referrer_organization_id")
    private UUID referrerOrganizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "invitee_type", nullable = false, length = 32)
    private PartnerReferralType inviteeType;

    @Column(name = "invitee_member_id")
    private UUID inviteeMemberId;

    @Column(name = "invitee_organization_id")
    private UUID inviteeOrganizationId;

    @Column(name = "partner_code", length = 8)
    private String partnerCode;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "program_expires_at")
    private Instant programExpiresAt;

    @Column(name = "source_courier_request_id")
    private UUID sourceCourierRequestId;

    @Column(name = "source_restaurant_request_id")
    private UUID sourceRestaurantRequestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
