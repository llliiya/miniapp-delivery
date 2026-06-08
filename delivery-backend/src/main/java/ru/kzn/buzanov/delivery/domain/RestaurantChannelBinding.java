package ru.kzn.buzanov.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "restaurant_channel_bindings")
@Getter
@Setter
public class RestaurantChannelBinding {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
