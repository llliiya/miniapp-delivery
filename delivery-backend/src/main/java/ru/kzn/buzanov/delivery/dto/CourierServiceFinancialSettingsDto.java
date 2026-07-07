package ru.kzn.buzanov.delivery.dto;

import ru.kzn.buzanov.delivery.domain.PlatformFeeType;

import java.math.BigDecimal;
import java.util.UUID;

public record CourierServiceFinancialSettingsDto(
        UUID courierServiceId,
        boolean platformFeeEnabled,
        PlatformFeeType platformFeeType,
        BigDecimal platformFeeValue
) {
}
