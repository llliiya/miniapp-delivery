package ru.kzn.buzanov.delivery.dto;

import java.util.List;

public record PartnerProgramDto(
        String partnerCode,
        String inviteUrl,
        long totalInvitations,
        long pendingCount,
        long connectedCount,
        List<PartnerReferralDto> referrals
) {
}
