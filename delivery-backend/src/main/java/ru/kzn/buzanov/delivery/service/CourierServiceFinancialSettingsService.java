package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.domain.CourierServiceFinancialSettings;
import ru.kzn.buzanov.delivery.domain.PlatformFeeType;
import ru.kzn.buzanov.delivery.dto.CourierServiceFinancialSettingsDto;
import ru.kzn.buzanov.delivery.dto.request.UpsertCourierServiceFinancialSettingsRequest;
import ru.kzn.buzanov.delivery.repository.CourierServiceFinancialSettingsRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourierServiceFinancialSettingsService {

    private final CourierServiceFinancialSettingsRepository repository;
    private final AccessControlService accessControl;

    @Transactional(readOnly = true)
    public CourierServiceFinancialSettingsDto getForService(Long userId, UUID courierServiceId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        return toDto(resolveSettings(courierServiceId));
    }

    @Transactional
    public CourierServiceFinancialSettingsDto upsert(
            Long userId,
            UUID courierServiceId,
            UpsertCourierServiceFinancialSettingsRequest request) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        Instant now = Instant.now();
        CourierServiceFinancialSettings settings = repository.findById(courierServiceId)
                .orElseGet(() -> {
                    CourierServiceFinancialSettings created = new CourierServiceFinancialSettings();
                    created.setCourierServiceId(courierServiceId);
                    created.setCreatedAt(now);
                    return created;
                });
        settings.setPlatformFeeEnabled(request.platformFeeEnabled());
        settings.setPlatformFeeType(request.platformFeeType());
        settings.setPlatformFeeValue(request.platformFeeValue());
        settings.setUpdatedAt(now);
        return toDto(repository.save(settings));
    }

    @Transactional(readOnly = true)
    public CourierServiceFinancialSettings resolveSettings(UUID courierServiceId) {
        return repository.findById(courierServiceId).orElseGet(() -> defaultSettings(courierServiceId));
    }

    private static CourierServiceFinancialSettings defaultSettings(UUID courierServiceId) {
        CourierServiceFinancialSettings settings = new CourierServiceFinancialSettings();
        settings.setCourierServiceId(courierServiceId);
        settings.setPlatformFeeEnabled(false);
        settings.setPlatformFeeType(PlatformFeeType.PERCENT);
        settings.setPlatformFeeValue(BigDecimal.ZERO);
        return settings;
    }

    private static CourierServiceFinancialSettingsDto toDto(CourierServiceFinancialSettings settings) {
        return new CourierServiceFinancialSettingsDto(
                settings.getCourierServiceId(),
                settings.isPlatformFeeEnabled(),
                settings.getPlatformFeeType(),
                settings.getPlatformFeeValue());
    }
}
