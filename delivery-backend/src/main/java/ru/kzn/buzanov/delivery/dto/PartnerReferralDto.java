package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.PartnerReferralType;

import java.time.Instant;
import java.util.UUID;

public record PartnerReferralDto(
        UUID requestId,
        PartnerReferralType referralType,
        String displayName,
        Instant submittedAt,
        String status,
        Instant connectedAt
) {
}
