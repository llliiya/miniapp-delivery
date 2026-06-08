package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.kzn.buzanov.delivery.domain.OrganizationType;

public record CreateOrganizationRequest(
        @NotNull OrganizationType type,
        @NotBlank String name
) {
}
