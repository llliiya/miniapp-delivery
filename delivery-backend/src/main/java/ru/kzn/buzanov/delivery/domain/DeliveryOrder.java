package ru.kzn.buzanov.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "delivery_orders")
@Getter
@Setter
public class DeliveryOrder {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Generated(event = EventType.INSERT)
    @Column(name = "public_number", insertable = false, updatable = false)
    private Long publicNumber;

    @Column(name = "courier_service_id", nullable = false)
    private UUID courierServiceId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "pickup_point_id")
    private UUID pickupPointId;

    @Column(name = "channel_id")
    private UUID channelId;

    @Column(name = "pickup_address", nullable = false, length = 512)
    private String pickupAddress;

    @Column(name = "delivery_address", nullable = false, length = 512)
    private String deliveryAddress;

    @Column(name = "delivery_address_full", length = 512)
    private String deliveryAddressFull;

    @Column(name = "delivery_apartment", length = 128)
    private String deliveryApartment;

    @Column(name = "delivery_entrance", length = 256)
    private String deliveryEntrance;

    @Column(name = "pickup_lat")
    private Double pickupLat;

    @Column(name = "pickup_lon")
    private Double pickupLon;

    @Column(name = "delivery_lat")
    private Double deliveryLat;

    @Column(name = "delivery_lon")
    private Double deliveryLon;

    @Column(name = "delivery_time", nullable = false)
    private Instant deliveryTime;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_source", nullable = false, length = 32)
    private PriceSource priceSource = PriceSource.manual;

    @Column(name = "customer_phone", nullable = false, length = 32)
    private String customerPhone;

    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private OrderStatus status;

    @Column(name = "courier_user_id")
    private Long courierUserId;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
