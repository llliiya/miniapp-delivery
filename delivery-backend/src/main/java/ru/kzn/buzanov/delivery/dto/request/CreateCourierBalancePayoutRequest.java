package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.kzn.buzanov.delivery.domain.CourierBalancePayoutMethod;
import ru.kzn.buzanov.delivery.dto.PartnerPayoutDetailsDto;

import java.math.BigDecimal;

public record CreateCourierBalancePayoutRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull CourierBalancePayoutMethod payoutMethod,
        @Valid PartnerPayoutDetailsDto payoutDetails
) {
}