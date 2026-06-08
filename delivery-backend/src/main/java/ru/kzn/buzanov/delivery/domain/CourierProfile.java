package ru.kzn.buzanov.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "courier_profiles")
@Getter
@Setter
public class CourierProfile {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false, unique = true)
    private UUID memberId;

    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "completed_orders_count", nullable = false)
    private int completedOrdersCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
