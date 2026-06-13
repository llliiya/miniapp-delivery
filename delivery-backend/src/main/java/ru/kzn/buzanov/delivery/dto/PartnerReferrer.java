package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;

import java.util.UUID;

public record PartnerReferrer(
        PartnerReferrerType type,
        String partnerCode,
        UUID memberId,
        UUID organizationId,
        UUID courierServiceId,
        String displayName
) {
}
