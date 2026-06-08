package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.PickupPoint;
import ru.kzn.buzanov.delivery.dto.PickupPointDto;
import ru.kzn.buzanov.delivery.dto.request.CreatePickupPointRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchPickupPointRequest;
import ru.kzn.buzanov.delivery.repository.PickupPointRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PickupPointService {

    private final PickupPointRepository pickupPointRepository;
    private final AccessControlService accessControl;

    @Transactional(readOnly = true)
    public List<PickupPointDto> list(Long userId, UUID restaurantId) {
        Organization restaurant = accessControl.requireRestaurant(restaurantId);
        accessControl.requireCanManagePickupPoints(userId, restaurant);
        return pickupPointRepository.findByRestaurantIdOrderByCreatedAtAsc(restaurantId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public PickupPointDto create(Long userId, UUID restaurantId, CreatePickupPointRequest request) {
        Organization restaurant = accessControl.requireRestaurant(restaurantId);
        accessControl.requireCanManagePickupPoints(userId, restaurant);

        long existing = pickupPointRepository.countByRestaurantId(restaurantId);
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault()) || existing == 0;

        Instant now = Instant.now();
        if (makeDefault) {
            clearDefault(restaurantId, now);
        }

        PickupPoint point = new PickupPoint();
        point.setId(UUID.randomUUID());
        point.setRestaurantId(restaurantId);
        point.setName(request.name().trim());
        point.setAddress(request.address().trim());
        point.setLat(request.lat());
        point.setLon(request.lon());
        point.setPhone(normalizeOptional(request.phone()));
        point.setComment(normalizeOptional(request.comment()));
        point.setDefaultPoint(makeDefault);
        point.setCreatedAt(now);
        point.setUpdatedAt(now);
        pickupPointRepository.save(point);
        return toDto(point);
    }

    @Transactional
    public PickupPointDto patch(Long userId, UUID pointId, PatchPickupPointRequest request) {
        PickupPoint point = pickupPointRepository.findById(pointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Точка забора не найдена"));
        Organization restaurant = accessControl.requireRestaurant(point.getRestaurantId());
        accessControl.requireCanManagePickupPoints(userId, restaurant);

        Instant now = Instant.now();
        if (request.name() != null && !request.name().isBlank()) {
            point.setName(request.name().trim());
        }
        if (request.address() != null && !request.address().isBlank()) {
            point.setAddress(request.address().trim());
        }
        if (request.lat() != null) {
            point.setLat(request.lat());
        }
        if (request.lon() != null) {
            point.setLon(request.lon());
        }
        if (request.phone() != null) {
            point.setPhone(normalizeOptional(request.phone()));
        }
        if (request.comment() != null) {
            point.setComment(normalizeOptional(request.comment()));
        }
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefault(point.getRestaurantId(), now);
            point.setDefaultPoint(true);
        }
        point.setUpdatedAt(now);
        pickupPointRepository.save(point);
        return toDto(point);
    }

    @Transactional
    public void delete(Long userId, UUID pointId) {
        PickupPoint point = pickupPointRepository.findById(pointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Точка забора не найдена"));
        Organization restaurant = accessControl.requireRestaurant(point.getRestaurantId());
        accessControl.requireCanManagePickupPoints(userId, restaurant);

        boolean wasDefault = point.isDefaultPoint();
        UUID restaurantId = point.getRestaurantId();
        pickupPointRepository.delete(point);

        if (wasDefault) {
            List<PickupPoint> remaining = pickupPointRepository.findByRestaurantIdOrderByCreatedAtAsc(restaurantId);
            if (!remaining.isEmpty()) {
                Instant now = Instant.now();
                PickupPoint next = remaining.getFirst();
                next.setDefaultPoint(true);
                next.setUpdatedAt(now);
                pickupPointRepository.save(next);
            }
        }
    }

    private void clearDefault(UUID restaurantId, Instant now) {
        for (PickupPoint p : pickupPointRepository.findByRestaurantIdOrderByCreatedAtAsc(restaurantId)) {
            if (p.isDefaultPoint()) {
                p.setDefaultPoint(false);
                p.setUpdatedAt(now);
                pickupPointRepository.save(p);
            }
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private PickupPointDto toDto(PickupPoint point) {
        return new PickupPointDto(
                point.getId(),
                point.getRestaurantId(),
                point.getName(),
                point.getAddress(),
                point.getLat(),
                point.getLon(),
                point.getPhone(),
                point.getComment(),
                point.isDefaultPoint(),
                point.getCreatedAt(),
                point.getUpdatedAt()
        );
    }
}
