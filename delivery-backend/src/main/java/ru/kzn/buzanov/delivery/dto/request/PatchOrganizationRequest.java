package ru.kzn.buzanov.delivery.dto.request;

public record PatchOrganizationRequest(
        String name,
        Boolean active,
        String city
) {
}
