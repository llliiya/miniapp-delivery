package ru.kzn.buzanov.delivery.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourierRequestRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 128) String city,
        @Size(max = 128) String transport,
        @Size(max = 2000) String comment,
        @Size(max = 16) String messengerProvider,
        @Size(max = 64) String messengerExternalId,
        @Size(max = 128) String messengerUsername
) {
}
