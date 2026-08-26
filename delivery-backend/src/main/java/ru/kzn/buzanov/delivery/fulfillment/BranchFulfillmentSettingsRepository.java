package ru.kzn.buzanov.delivery.fulfillment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchFulfillmentSettingsRepository extends JpaRepository<BranchFulfillmentSettings, UUID> {

    Optional<BranchFulfillmentSettings> findByBranchId(UUID branchId);

    List<BranchFulfillmentSettings> findByBranchIdIn(Collection<UUID> branchIds);
}
