package ru.kzn.buzanov.delivery.fulfillment.restaurant;

import java.util.UUID;

public record RestaurantBranchRef(
        UUID branchId,
        UUID companyId,
        UUID organizationId
) {
}
