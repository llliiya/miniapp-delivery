package ru.kzn.buzanov.delivery.api;

import org.springframework.http.HttpStatus;

public class OrderConflictException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;

    public OrderConflictException(String errorCode) {
        this(errorCode, defaultMessage(errorCode));
    }

    public OrderConflictException(String errorCode, String userMessage) {
        super(userMessage != null ? userMessage : errorCode);
        this.errorCode = errorCode;
        this.userMessage = userMessage != null ? userMessage : defaultMessage(errorCode);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }

    private static String defaultMessage(String errorCode) {
        return switch (errorCode) {
            case "order_already_taken" -> "Заказ уже взят другим курьером";
            case "order_not_available" -> "Заказ недоступен";
            default -> "Конфликт при обработке заказа";
        };
    }
}
