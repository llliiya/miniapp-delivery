package ru.kzn.buzanov.delivery.dto;

import jakarta.validation.constraints.NotBlank;

public record PartnerPayoutDetailsDto(
        @NotBlank String transferType,
        String cardNumber,
        String phoneNumber,
        @NotBlank String recipientName,
        String bankName
) {
}
