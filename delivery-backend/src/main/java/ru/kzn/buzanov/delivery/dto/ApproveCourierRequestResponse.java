package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.CourierRequestStatus;

import java.util.UUID;

public record ApproveCourierRequestResponse(
        UUID requestId,
        CourierRequestStatus status,
        UUID memberId,
        Long userId,
        ProvisioningCredentialsDto credentials,
        String message
) {
}
