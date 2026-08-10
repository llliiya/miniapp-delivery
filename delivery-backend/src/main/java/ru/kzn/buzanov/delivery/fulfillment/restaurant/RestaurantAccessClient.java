package ru.kzn.buzanov.delivery.fulfillment.restaurant;

import java.util.UUID;

/**
 * Typed access to miniapp-restaurant for branch ownership and membership role.
 * Does not duplicate Branch persistence in delivery DB.
 */
public interface RestaurantAccessClient {

    RestaurantBranchRef requireBranch(UUID branchId, String authorizationHeader);

    RestaurantMembershipRole requireMembershipRole(String authorizationHeader);
}
