package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.PickupPoint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PickupPointRepository extends JpaRepository<PickupPoint, UUID> {

    List<PickupPoint> findByRestaurantIdOrderByCreatedAtAsc(UUID restaurantId);

    long countByRestaurantId(UUID restaurantId);

    Optional<PickupPoint> findByRestaurantIdAndDefaultPointTrue(UUID restaurantId);
}
