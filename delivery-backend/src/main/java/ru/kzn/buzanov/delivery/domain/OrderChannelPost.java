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
@Table(schema = "delivery", name = "order_channel_posts")
@Getter
@Setter
public class OrderChannelPost {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChannelPlatform platform;

    @Column(name = "external_chat_id", nullable = false, length = 128)
    private String externalChatId;

    @Column(name = "external_message_id", length = 128)
    private String externalMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChannelPostStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
