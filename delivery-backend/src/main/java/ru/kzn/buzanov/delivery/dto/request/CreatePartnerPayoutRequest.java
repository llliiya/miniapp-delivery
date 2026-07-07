package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutMethod;
import ru.kzn.buzanov.delivery.dto.PartnerPayoutDetailsDto;

import java.math.BigDecimal;

public record CreatePartnerPayoutRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull PartnerPayoutMethod payoutMethod,
        @Valid PartnerPayoutDetailsDto payoutDetails
) {
}
