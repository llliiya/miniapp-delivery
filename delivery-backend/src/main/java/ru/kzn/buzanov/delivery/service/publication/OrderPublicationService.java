package ru.kzn.buzanov.delivery.service.publication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.domain.ChannelPlatform;
import ru.kzn.buzanov.delivery.domain.ChannelPostStatus;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.OrderChannelPost;
import ru.kzn.buzanov.delivery.domain.OrderStatus;
import ru.kzn.buzanov.delivery.domain.PublicationChannel;
import ru.kzn.buzanov.delivery.domain.RestaurantChannelBinding;
import ru.kzn.buzanov.delivery.dto.OrderPublicationFailureDto;
import ru.kzn.buzanov.delivery.repository.OrderChannelPostRepository;
import ru.kzn.buzanov.delivery.repository.PublicationChannelRepository;
import ru.kzn.buzanov.delivery.repository.RestaurantChannelBindingRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPublicationService {

    private final RestaurantChannelBindingRepository bindingRepository;
    private final PublicationChannelRepository channelRepository;
    private final OrderChannelPostRepository postRepository;
    private final TelegramChannelPublisher telegramPublisher;
    private final MaxChannelPublisher maxPublisher;

    @Transactional
    public List<String> publishNewOrder(DeliveryOrder order) {
        List<PublicationChannel> channels = activeChannelsForRestaurant(order.getRestaurantId());
        if (channels.isEmpty()) {
            return List.of("no_active_channels");
        }
        Instant now = Instant.now();
        int sentCount = 0;
        int failedCount = 0;
        for (PublicationChannel channel : channels) {
            ChannelPublishResult result = publishToChannel(channel, order);
            savePost(order, channel, result, now);
            if (result.success()) {
                sentCount++;
            } else {
                failedCount++;
            }
        }
        if (sentCount > 0) {
            order.setPublishedAt(now);
        }
        return buildPublicationWarnings(sentCount, failedCount);
    }

    @Transactional
    public List<String> republishOrder(DeliveryOrder order) {
        List<PublicationChannel> channels = activeChannelsForRestaurant(order.getRestaurantId());
        if (channels.isEmpty()) {
            return List.of("no_active_channels");
        }
        Instant now = Instant.now();
        int sentCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        log.info("Повторная публикация заказа №{}", order.getPublicNumber());
        for (PublicationChannel channel : channels) {
            Optional<OrderChannelPost> existing =
                    postRepository.findFirstByOrderIdAndChannelIdOrderByUpdatedAtDesc(order.getId(), channel.getId());
            if (existing.isPresent() && existing.get().getStatus() == ChannelPostStatus.sent) {
                skippedCount++;
                continue;
            }
            log.info(
                    "Повторная публикация заказа №{}. Канал: {}. ChatId: {}",
                    order.getPublicNumber(),
                    channel.getName(),
                    channel.getExternalId());
            ChannelPublishResult result = publishToChannel(channel, order);
            logRepublishResult(channel, result);
            saveOrUpdatePost(order, channel, result, now, existing.orElse(null));
            if (result.success()) {
                sentCount++;
            } else {
                failedCount++;
            }
        }
        if (sentCount > 0 && order.getPublishedAt() == null) {
            order.setPublishedAt(now);
        }
        if (sentCount == 0 && skippedCount > 0 && failedCount == 0) {
            return List.of();
        }
        return buildPublicationWarnings(sentCount, failedCount);
    }

    /**
     * Обновляет существующие сообщения заказа в каналах через editMessage.
     * Идемпотентно: повторный вызов с теми же данными безопасен.
     */
    @Transactional
    public void syncOrder(DeliveryOrder order, String courierName) {
        if (order.getPublishedAt() == null) {
            return;
        }
        List<OrderChannelPost> posts = postRepository.findByOrderId(order.getId());
        if (posts.isEmpty()) {
            return;
        }
        Map<UUID, OrderChannelPost> latestByChannel = latestPostsByChannel(posts);
        Map<UUID, PublicationChannel> channels = channelRepository.findAllById(latestByChannel.keySet()).stream()
                .collect(Collectors.toMap(PublicationChannel::getId, Function.identity()));
        Instant now = Instant.now();
        for (OrderChannelPost post : latestByChannel.values()) {
            if (post.getStatus() != ChannelPostStatus.sent || post.getExternalMessageId() == null) {
                continue;
            }
            PublicationChannel channel = channels.get(post.getChannelId());
            if (channel == null || !channel.isActive()) {
                continue;
            }
            String chatId = post.getExternalChatId() != null ? post.getExternalChatId() : channel.getExternalId();
            ChannelEditResult result = editOrderMessage(channel, chatId, post.getExternalMessageId(), order, courierName);
            if (result.success()) {
                post.setUpdatedAt(now);
                postRepository.save(post);
                continue;
            }
            log.warn(
                    "Не удалось обновить сообщение заказа №{} в канале «{}»: {}",
                    order.getPublicNumber(),
                    channel.getName(),
                    result.errorMessage());
        }
    }

    public List<OrderPublicationFailureDto> publicationFailures(DeliveryOrder order) {
        List<OrderChannelPost> posts = postRepository.findByOrderId(order.getId());
        if (posts.isEmpty()) {
            return List.of();
        }
        Map<UUID, OrderChannelPost> latestByChannel = latestPostsByChannel(posts);
        Set<UUID> channelIds = latestByChannel.keySet();
        Map<UUID, PublicationChannel> channels = channelRepository.findAllById(channelIds).stream()
                .collect(Collectors.toMap(PublicationChannel::getId, Function.identity()));
        List<OrderPublicationFailureDto> failures = new ArrayList<>();
        for (Map.Entry<UUID, OrderChannelPost> entry : latestByChannel.entrySet()) {
            OrderChannelPost post = entry.getValue();
            if (post.getStatus() != ChannelPostStatus.failed) {
                continue;
            }
            PublicationChannel channel = channels.get(entry.getKey());
            failures.add(new OrderPublicationFailureDto(
                    entry.getKey(),
                    channel != null ? channel.getName() : "Канал",
                    post.getPlatform(),
                    post.getErrorMessage()));
        }
        failures.sort(Comparator.comparing(OrderPublicationFailureDto::channelName, String.CASE_INSENSITIVE_ORDER));
        return failures;
    }

    public boolean canRepublish(DeliveryOrder order) {
        if (order.getStatus() != OrderStatus.waiting_for_courier || order.getCourierUserId() != null) {
            return false;
        }
        if (order.getPublishedAt() == null) {
            return true;
        }
        List<PublicationChannel> channels = activeChannelsForRestaurant(order.getRestaurantId());
        for (PublicationChannel channel : channels) {
            Optional<OrderChannelPost> post =
                    postRepository.findFirstByOrderIdAndChannelIdOrderByUpdatedAtDesc(order.getId(), channel.getId());
            if (post.isEmpty() || post.get().getStatus() == ChannelPostStatus.failed) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildPublicationWarnings(int sentCount, int failedCount) {
        if (sentCount == 0 && failedCount > 0) {
            return List.of("publication_failed");
        }
        if (sentCount > 0 && failedCount > 0) {
            return List.of("publication_partial_failed");
        }
        return List.of();
    }

    private Map<UUID, OrderChannelPost> latestPostsByChannel(List<OrderChannelPost> posts) {
        Map<UUID, OrderChannelPost> latest = new HashMap<>();
        for (OrderChannelPost post : posts) {
            latest.merge(post.getChannelId(), post, (left, right) ->
                    left.getUpdatedAt().isAfter(right.getUpdatedAt()) ? left : right);
        }
        return latest;
    }

    private void logRepublishResult(PublicationChannel channel, ChannelPublishResult result) {
        if (result.success()) {
            log.info(
                    "Результат {}: ok=true messageId={}",
                    platformLabel(channel.getType()),
                    result.externalMessageId());
            return;
        }
        log.warn(
                "Результат {}: ok=false description={}",
                platformLabel(channel.getType()),
                result.errorMessage());
    }

    private static String platformLabel(ChannelPlatform platform) {
        return switch (platform) {
            case telegram -> "Telegram";
            case max -> "MAX";
        };
    }

    private List<PublicationChannel> activeChannelsForRestaurant(UUID restaurantId) {
        List<RestaurantChannelBinding> bindings = bindingRepository.findByRestaurantId(restaurantId);
        if (bindings.isEmpty()) {
            return List.of();
        }
        Set<UUID> channelIds = bindings.stream().map(RestaurantChannelBinding::getChannelId).collect(Collectors.toSet());
        return channelRepository.findAllById(channelIds).stream()
                .filter(PublicationChannel::isActive)
                .toList();
    }

    private ChannelPublishResult publishToChannel(PublicationChannel channel, DeliveryOrder order) {
        return switch (channel.getType()) {
            case telegram -> telegramPublisher.publishOrder(channel, order);
            case max -> maxPublisher.publishOrder(channel, order);
        };
    }

    private ChannelEditResult editOrderMessage(
            PublicationChannel channel,
            String chatId,
            String messageId,
            DeliveryOrder order,
            String courierName) {
        return switch (channel.getType()) {
            case telegram -> telegramPublisher.editOrder(channel, chatId, messageId, order, courierName);
            case max -> maxPublisher.editOrder(channel, chatId, messageId, order, courierName);
        };
    }

    private void savePost(DeliveryOrder order, PublicationChannel channel, ChannelPublishResult result, Instant now) {
        OrderChannelPost post = new OrderChannelPost();
        post.setId(UUID.randomUUID());
        post.setOrderId(order.getId());
        post.setChannelId(channel.getId());
        post.setPlatform(channel.getType());
        post.setExternalChatId(channel.getExternalId());
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        applyPostResult(post, result);
        postRepository.save(post);
    }

    private void saveOrUpdatePost(
            DeliveryOrder order,
            PublicationChannel channel,
            ChannelPublishResult result,
            Instant now,
            OrderChannelPost existing) {
        OrderChannelPost post;
        if (existing != null) {
            post = existing;
            post.setUpdatedAt(now);
        } else {
            post = new OrderChannelPost();
            post.setId(UUID.randomUUID());
            post.setOrderId(order.getId());
            post.setChannelId(channel.getId());
            post.setPlatform(channel.getType());
            post.setExternalChatId(channel.getExternalId());
            post.setCreatedAt(now);
            post.setUpdatedAt(now);
        }
        applyPostResult(post, result);
        postRepository.save(post);
    }

    private void applyPostResult(OrderChannelPost post, ChannelPublishResult result) {
        if (result.success()) {
            post.setStatus(ChannelPostStatus.sent);
            post.setExternalMessageId(result.externalMessageId());
            post.setErrorMessage(null);
        } else {
            post.setStatus(ChannelPostStatus.failed);
            post.setExternalMessageId(null);
            post.setErrorMessage(result.errorMessage());
        }
    }
}
