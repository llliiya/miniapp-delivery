package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.RestaurantChannelBinding;

import java.util.List;
import java.util.UUID;

public interface RestaurantChannelBindingRepository extends JpaRepository<RestaurantChannelBinding, UUID> {

    List<RestaurantChannelBinding> findByRestaurantId(UUID restaurantId);

    void deleteByRestaurantId(UUID restaurantId);

    boolean existsByRestaurantIdAndChannelId(UUID restaurantId, UUID channelId);
}
