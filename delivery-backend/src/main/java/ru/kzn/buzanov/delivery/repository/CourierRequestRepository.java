package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.CourierRequest;
import ru.kzn.buzanov.delivery.domain.CourierRequestStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierRequestRepository extends JpaRepository<CourierRequest, UUID> {

    Optional<CourierRequest> findFirstByPhoneAndStatusOrderByCreatedAtDesc(String phone, CourierRequestStatus status);

    Optional<CourierRequest> findFirstByMessengerProviderAndMessengerExternalIdAndStatusOrderByCreatedAtDesc(
            String messengerProvider,
            String messengerExternalId,
            CourierRequestStatus status);

    List<CourierRequest> findByStatusOrderByCreatedAtDesc(CourierRequestStatus status);
}
