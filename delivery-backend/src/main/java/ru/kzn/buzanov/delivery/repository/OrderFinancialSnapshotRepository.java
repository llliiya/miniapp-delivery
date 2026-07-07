package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.OrderFinancialSnapshot;

import java.util.Optional;
import java.util.UUID;

public interface OrderFinancialSnapshotRepository extends JpaRepository<OrderFinancialSnapshot, UUID> {

    boolean existsByOrderId(UUID orderId);

    Optional<OrderFinancialSnapshot> findByOrderId(UUID orderId);
}
