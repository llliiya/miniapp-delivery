package ru.kzn.buzanov.delivery.api;

import org.springframework.http.HttpStatus;
import ru.kzn.buzanov.delivery.dto.CourierDto;

public class CourierConflictException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;
    private final String conflictField;
    private final CourierDto existingCourier;

    public CourierConflictException(String errorCode, String userMessage) {
        this(errorCode, userMessage, null, null);
    }

    public CourierConflictException(String errorCode, String userMessage, String conflictField) {
        this(errorCode, userMessage, conflictField, null);
    }

    public CourierConflictException(String errorCode, String userMessage, CourierDto existingCourier) {
        this(errorCode, userMessage, null, existingCourier);
    }

    public CourierConflictException(
            String errorCode,
            String userMessage,
            String conflictField,
            CourierDto existingCourier) {
        super(userMessage != null ? userMessage : errorCode);
        this.errorCode = errorCode;
        this.userMessage = userMessage != null ? userMessage : errorCode;
        this.conflictField = conflictField;
        this.existingCourier = existingCourier;
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

    public CourierDto getExistingCourier() {
        return existingCourier;
    }

    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
