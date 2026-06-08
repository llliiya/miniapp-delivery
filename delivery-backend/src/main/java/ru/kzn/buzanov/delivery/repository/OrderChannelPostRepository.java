package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.OrderChannelPost;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderChannelPostRepository extends JpaRepository<OrderChannelPost, UUID> {

    List<OrderChannelPost> findByOrderId(UUID orderId);

    Optional<OrderChannelPost> findFirstByOrderIdAndChannelIdOrderByUpdatedAtDesc(UUID orderId, UUID channelId);
}
