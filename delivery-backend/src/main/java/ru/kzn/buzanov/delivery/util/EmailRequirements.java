package ru.kzn.buzanov.delivery.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class EmailRequirements {

    private EmailRequirements() {
    }

    public static String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите email");
        }
        String normalized = email.trim().toLowerCase();
        if (!normalized.contains("@") || normalized.indexOf('@') <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Введите корректный email");
        }
        return normalized;
    }
}
