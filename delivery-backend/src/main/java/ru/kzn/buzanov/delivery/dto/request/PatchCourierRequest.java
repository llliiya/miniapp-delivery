package ru.kzn.buzanov.delivery.dto.request;

import ru.kzn.buzanov.delivery.domain.MemberStatus;

public record PatchCourierRequest(
        MemberStatus status,
        String displayName
) {
}
