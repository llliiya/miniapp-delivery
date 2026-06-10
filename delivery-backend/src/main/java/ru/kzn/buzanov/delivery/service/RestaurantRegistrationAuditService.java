package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequest;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequestStatus;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationSourceType;
import ru.kzn.buzanov.delivery.repository.RestaurantRegistrationRequestRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantRegistrationAuditService {

    private final RestaurantRegistrationRequestRepository requestRepository;

    @Transactional
    public void recordAdminCreation(
            UUID restaurantId,
            String restaurantName,
            String address,
            String contactPerson,
            String phone,
            String email,
            Long processedBy) {
        Instant now = Instant.now();
        RestaurantRegistrationRequest entity = new RestaurantRegistrationRequest();
        entity.setId(UUID.randomUUID());
        entity.setRestaurantName(restaurantName);
        entity.setAddress(address != null ? address : "—");
        entity.setContactPerson(contactPerson);
        entity.setPhone(phone);
        entity.setEmail(email);
        entity.setSourceType(RestaurantRegistrationSourceType.ADMIN);
        entity.setRestaurantId(restaurantId);
        entity.setStatus(RestaurantRegistrationRequestStatus.APPROVED);
        entity.setCreatedAt(now);
        entity.setProcessedAt(now);
        entity.setProcessedBy(processedBy);
        requestRepository.save(entity);
    }
}
