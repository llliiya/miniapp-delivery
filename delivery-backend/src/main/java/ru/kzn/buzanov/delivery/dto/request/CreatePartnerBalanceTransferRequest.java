package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePartnerBalanceTransferRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount
) {
}
