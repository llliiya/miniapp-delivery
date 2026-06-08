package ru.kzn.buzanov.delivery.dto.request;

import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;

public record PatchMemberRequest(
        MemberRole role,
        MemberStatus status,
        String displayName
) {
}
