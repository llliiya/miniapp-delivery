package ru.kzn.buzanov.delivery.fulfillment;

import org.springframework.http.HttpStatus;

public class FulfillmentApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public FulfillmentApiException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
