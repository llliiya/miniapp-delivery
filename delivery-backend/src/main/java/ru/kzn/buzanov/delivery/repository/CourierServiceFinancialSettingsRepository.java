package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.CourierServiceFinancialSettings;

import java.util.UUID;

public interface CourierServiceFinancialSettingsRepository extends JpaRepository<CourierServiceFinancialSettings, UUID> {
}
