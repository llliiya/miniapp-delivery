package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.UserDeliveryContext;

public interface UserDeliveryContextRepository extends JpaRepository<UserDeliveryContext, Long> {
}
