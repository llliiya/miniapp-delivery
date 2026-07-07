package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import ru.kzn.buzanov.delivery.domain.PlatformFeeType;

import java.math.BigDecimal;

public record UpsertCourierServiceFinancialSettingsRequest(
        @NotNull Boolean platformFeeEnabled,
        @NotNull PlatformFeeType platformFeeType,
        @NotNull @DecimalMin("0") BigDecimal platformFeeValue
) {
}
