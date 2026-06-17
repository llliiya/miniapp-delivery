package ru.kzn.buzanov.delivery.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.api.OrderConflictException;
import ru.kzn.buzanov.delivery.api.CourierConflictException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CourierConflictException.class)
    public ResponseEntity<Map<String, Object>> handleCourierConflict(CourierConflictException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getErrorCode());
        body.put("message", ex.getUserMessage());
        if (ex.getConflictField() != null) {
            body.put("conflictField", ex.getConflictField());
        }
        if (ex.getExistingCourier() != null) {
            body.put("existingCourier", ex.getExistingCourier());
            body.put("existingCourierId", ex.getExistingCourier().memberId());
        }
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(OrderConflictException.class)
    public ResponseEntity<Map<String, String>> handleOrderConflict(OrderConflictException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of(
                "error", ex.getErrorCode(),
                "message", ex.getUserMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleStatus(ResponseStatusException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        detail.setTitle(ex.getStatusCode().toString());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Ошибка валидации");
        detail.setProperty("errors", ex.getBindingResult().getFieldErrors());
        return detail;
    }
}
