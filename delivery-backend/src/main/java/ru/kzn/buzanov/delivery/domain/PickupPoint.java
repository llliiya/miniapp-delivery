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
@Table(schema = "delivery", name = "pickup_points")
@Getter
@Setter
public class PickupPoint {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 512)
    private String address;

    private Double lat;

    private Double lon;

    @Column(length = 32)
    private String phone;

    @Column(length = 512)
    private String comment;

    @Column(name = "is_default", nullable = false)
    private boolean defaultPoint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
