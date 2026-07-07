package ru.kzn.buzanov.delivery.api;

import org.springframework.http.HttpStatus;

public class CourierBalancePayoutConflictException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;
    private final String conflictField;
    private final HttpStatus status;

    public CourierBalancePayoutConflictException(
            String errorCode,
            String userMessage,
            String conflictField,
            HttpStatus status) {
        super(userMessage != null ? userMessage : errorCode);
        this.errorCode = errorCode;
        this.userMessage = userMessage != null ? userMessage : errorCode;
        this.conflictField = conflictField;
        this.status = status != null ? status : HttpStatus.BAD_REQUEST;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getConflictField() {
        return conflictField;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
