package ru.kzn.buzanov.delivery.web;

public final class CurrentUserHolder {

    public static final String REQUEST_ATTRIBUTE = "ru.kzn.buzanov.delivery.currentUser";

    private CurrentUserHolder() {
    }

    public static CurrentUser require(jakarta.servlet.http.HttpServletRequest request) {
        Object attr = request.getAttribute(REQUEST_ATTRIBUTE);
        if (attr instanceof CurrentUser currentUser) {
            return currentUser;
        }
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Требуется авторизация");
    }

    public static CurrentUser optional(jakarta.servlet.http.HttpServletRequest request) {
        Object attr = request.getAttribute(REQUEST_ATTRIBUTE);
        if (attr instanceof CurrentUser currentUser) {
            return currentUser;
        }
        return null;
    }
}
