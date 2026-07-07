package ru.kzn.buzanov.delivery.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void invalidEnumPayloadReturns400() {
        ResponseEntity<Map<String, String>> response = handler.handleUnreadable(
                new HttpMessageNotReadableException("bad enum"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid_payload", response.getBody().get("error"));
    }
}

