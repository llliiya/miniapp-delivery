package ru.kzn.buzanov.delivery.fulfillment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, UUID> {

    List<DeliveryZone> findByBranchIdOrderByPriorityDescCreatedAtAscIdAsc(UUID branchId);

    List<DeliveryZone> findByBranchIdAndActiveTrueOrderByPriorityDescCreatedAtAscIdAsc(UUID branchId);

    List<DeliveryZone> findByCompanyIdAndBranchIdInAndActiveTrueOrderByPriorityDescCreatedAtAscIdAsc(
            UUID companyId,
            Collection<UUID> branchIds);

    List<DeliveryZone> findByCompanyIdAndActiveTrueOrderByPriorityDescCreatedAtAscIdAsc(UUID companyId);

    Optional<DeliveryZone> findByIdAndBranchId(UUID id, UUID branchId);

    long countByBranchId(UUID branchId);

    long countByBranchIdAndActiveTrue(UUID branchId);
}
