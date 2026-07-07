package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;

import java.time.Instant;
import java.util.UUID;

public record PartnerConnectedReferralDto(
        UUID referralId,
        PartnerReferralType inviteeType,
        PartnerReferrerType referrerType,
        UUID inviteeMemberId,
        UUID inviteeOrganizationId,
        String displayName,
        Instant connectedAt,
        Instant programExpiresAt
) {
}
