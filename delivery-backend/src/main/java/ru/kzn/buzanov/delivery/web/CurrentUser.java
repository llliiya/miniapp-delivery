package ru.kzn.buzanov.delivery.web;

import java.util.List;

public record CurrentUser(Long userId, List<String> roles) {
}
