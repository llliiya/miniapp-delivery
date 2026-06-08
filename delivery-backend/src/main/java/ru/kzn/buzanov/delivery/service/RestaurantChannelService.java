package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.PublicationChannel;
import ru.kzn.buzanov.delivery.domain.RestaurantChannelBinding;
import ru.kzn.buzanov.delivery.dto.RestaurantBoundChannelDto;
import ru.kzn.buzanov.delivery.dto.RestaurantChannelsResponseDto;
import ru.kzn.buzanov.delivery.dto.request.ReplaceRestaurantChannelsRequest;
import ru.kzn.buzanov.delivery.repository.PublicationChannelRepository;
import ru.kzn.buzanov.delivery.repository.RestaurantChannelBindingRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantChannelService {

    private final RestaurantChannelBindingRepository bindingRepository;
    private final PublicationChannelRepository channelRepository;
    private final AccessControlService accessControl;
    private final PublicationChannelService publicationChannelService;

    @Transactional(readOnly = true)
    public RestaurantChannelsResponseDto list(Long userId, UUID restaurantId) {
        Organization restaurant = accessControl.requireRestaurant(restaurantId);
        accessControl.requireCanViewRestaurantChannels(userId, restaurant);
        return new RestaurantChannelsResponseDto(restaurantId, loadBoundChannels(restaurantId));
    }

    @Transactional
    public RestaurantChannelsResponseDto replace(Long userId, UUID restaurantId, ReplaceRestaurantChannelsRequest request) {
        Organization restaurant = accessControl.requireRestaurant(restaurantId);
        accessControl.requireCanManageRestaurantChannelBindings(userId, restaurant);

        if (restaurant.getCourierServiceId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У ресторана не указана курьерская служба");
        }

        List<UUID> channelIds = request.channelIds() != null ? request.channelIds() : List.of();
        Set<UUID> unique = new HashSet<>(channelIds);
        if (unique.size() != channelIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дубликаты channelIds");
        }

        for (UUID channelId : channelIds) {
            PublicationChannel channel = publicationChannelService.requireChannel(channelId);
            if (!restaurant.getCourierServiceId().equals(channel.getCourierServiceId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Канал не принадлежит курьерской службе ресторана");
            }
            if (!channel.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Нельзя привязать неактивный канал: " + channel.getName());
            }
        }

        bindingRepository.deleteByRestaurantId(restaurantId);
        Instant now = Instant.now();
        for (UUID channelId : channelIds) {
            RestaurantChannelBinding binding = new RestaurantChannelBinding();
            binding.setId(UUID.randomUUID());
            binding.setRestaurantId(restaurantId);
            binding.setChannelId(channelId);
            binding.setCreatedAt(now);
            bindingRepository.save(binding);
        }

        return new RestaurantChannelsResponseDto(restaurantId, loadBoundChannels(restaurantId));
    }

    private List<RestaurantBoundChannelDto> loadBoundChannels(UUID restaurantId) {
        List<RestaurantChannelBinding> bindings = bindingRepository.findByRestaurantId(restaurantId);
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<UUID> channelIds = bindings.stream().map(RestaurantChannelBinding::getChannelId).toList();
        List<PublicationChannel> channels = channelRepository.findByIdIn(channelIds);
        List<RestaurantBoundChannelDto> result = new ArrayList<>();
        for (PublicationChannel channel : channels) {
            result.add(new RestaurantBoundChannelDto(
                    channel.getId(),
                    channel.getType(),
                    channel.getChatType(),
                    channel.getName(),
                    channel.getCity(),
                    channel.isActive()
            ));
        }
        return result;
    }
}
