package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record MemberDto(
        UUID id,
        Long publicId,
        UUID organizationId,
        Long userId,
        MemberRole role,
        MemberStatus status,
        String displayName,
        Instant createdAt
) {
}
