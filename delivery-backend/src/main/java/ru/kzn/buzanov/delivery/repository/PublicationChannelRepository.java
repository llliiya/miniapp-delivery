package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.PublicationChannel;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PublicationChannelRepository extends JpaRepository<PublicationChannel, UUID> {

    List<PublicationChannel> findByCourierServiceIdOrderByCreatedAtDesc(UUID courierServiceId);

    List<PublicationChannel> findByIdIn(Collection<UUID> ids);
}
