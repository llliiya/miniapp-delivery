package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SetActiveOrganizationRequest(@NotNull UUID organizationId) {
}
