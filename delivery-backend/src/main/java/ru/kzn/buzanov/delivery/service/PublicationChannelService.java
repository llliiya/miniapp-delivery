package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.ChannelPlatform;
import ru.kzn.buzanov.delivery.domain.ChatType;
import ru.kzn.buzanov.delivery.domain.PublicationChannel;
import ru.kzn.buzanov.delivery.dto.PublicationChannelDto;
import ru.kzn.buzanov.delivery.dto.request.CreatePublicationChannelRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchPublicationChannelRequest;
import ru.kzn.buzanov.delivery.repository.PublicationChannelRepository;
import ru.kzn.buzanov.delivery.util.CityNormalizer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicationChannelService {

    private final PublicationChannelRepository channelRepository;
    private final AccessControlService accessControl;

    @Transactional(readOnly = true)
    public List<PublicationChannelDto> list(Long userId, UUID courierServiceId, String city) {
        accessControl.requireCanManagePublicationChannels(userId, courierServiceId);
        String cityFilter = CityNormalizer.normalize(city);
        return channelRepository.findByCourierServiceIdOrderByCreatedAtDesc(courierServiceId).stream()
                .filter(channel -> cityFilter == null || CityNormalizer.equals(channel.getCity(), cityFilter))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public PublicationChannelDto create(Long userId, CreatePublicationChannelRequest request) {
        accessControl.requireCanManagePublicationChannels(userId, request.courierServiceId());
        validatePlatform(request.type());
        validateChatType(request.chatType());

        Instant now = Instant.now();
        PublicationChannel channel = new PublicationChannel();
        channel.setId(UUID.randomUUID());
        channel.setCourierServiceId(request.courierServiceId());
        channel.setType(request.type());
        channel.setChatType(request.chatType());
        channel.setName(request.name().trim());
        channel.setExternalId(request.externalId().trim());
        channel.setCity(CityNormalizer.normalize(request.city()));
        channel.setActive(request.isActive() == null || request.isActive());
        channel.setCreatedAt(now);
        channel.setUpdatedAt(now);
        channelRepository.save(channel);
        return toDto(channel);
    }

    @Transactional
    public PublicationChannelDto patch(Long userId, UUID channelId, PatchPublicationChannelRequest request) {
        PublicationChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Канал не найден"));
        accessControl.requireCanManagePublicationChannels(userId, channel.getCourierServiceId());

        if (request.name() != null && !request.name().isBlank()) {
            channel.setName(request.name().trim());
        }
        if (request.externalId() != null && !request.externalId().isBlank()) {
            channel.setExternalId(request.externalId().trim());
        }
        if (request.city() != null) {
            channel.setCity(CityNormalizer.normalize(request.city()));
        }
        if (request.chatType() != null) {
            validateChatType(request.chatType());
            channel.setChatType(request.chatType());
        }
        if (request.isActive() != null) {
            channel.setActive(request.isActive());
        }
        channel.setUpdatedAt(Instant.now());
        channelRepository.save(channel);
        return toDto(channel);
    }

    @Transactional
    public PublicationChannelDto deactivate(Long userId, UUID channelId) {
        PublicationChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Канал не найден"));
        accessControl.requireCanManagePublicationChannels(userId, channel.getCourierServiceId());
        channel.setActive(false);
        channel.setUpdatedAt(Instant.now());
        channelRepository.save(channel);
        return toDto(channel);
    }

    public PublicationChannel requireChannel(UUID channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Канал не найден"));
    }

    private static void validatePlatform(ChannelPlatform type) {
        if (type != ChannelPlatform.telegram && type != ChannelPlatform.max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type должен быть telegram или max");
        }
    }

    private static void validateChatType(ChatType chatType) {
        if (chatType != ChatType.channel && chatType != ChatType.group) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chatType должен быть channel или group");
        }
    }

    private PublicationChannelDto toDto(PublicationChannel channel) {
        return new PublicationChannelDto(
                channel.getId(),
                channel.getCourierServiceId(),
                channel.getType(),
                channel.getChatType(),
                channel.getName(),
                channel.getExternalId(),
                channel.getCity(),
                channel.isActive(),
                channel.getCreatedAt(),
                channel.getUpdatedAt()
        );
    }
}
