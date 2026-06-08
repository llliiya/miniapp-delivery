package ru.kzn.buzanov.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "delivery", name = "publication_channels")
@Getter
@Setter
public class PublicationChannel {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "courier_service_id", nullable = false)
    private UUID courierServiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChannelPlatform type;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_type", nullable = false, length = 16)
    private ChatType chatType;

    @Column(nullable = false)
    private String name;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    private String city;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
