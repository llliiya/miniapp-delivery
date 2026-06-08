package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;

import java.time.Instant;
import java.util.UUID;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, UUID>, JpaSpecificationExecutor<DeliveryOrder> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DeliveryOrder o
            SET o.courierUserId = :courierUserId,
                o.status = ru.kzn.buzanov.delivery.domain.OrderStatus.courier_heading_to_pickup,
                o.acceptedAt = :assignedAt
            WHERE o.id = :orderId
              AND o.status = ru.kzn.buzanov.delivery.domain.OrderStatus.waiting_for_courier
              AND o.courierUserId IS NULL
            """)
    int assignOrderIfUnassigned(
            @Param("orderId") UUID orderId,
            @Param("courierUserId") Long courierUserId,
            @Param("assignedAt") Instant assignedAt);
}
