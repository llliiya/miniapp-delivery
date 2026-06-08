package ru.kzn.buzanov.delivery.dto;

public record MessengerRegistrationStatusDto(
        boolean registered,
        boolean applicationPending,
        boolean applicationRejected
) {
}
