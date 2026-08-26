package ru.kzn.buzanov.delivery.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.fulfillment.FulfillmentApiException;
import ru.kzn.buzanov.delivery.fulfillment.restaurant.RestaurantServiceUnavailableException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(FulfillmentApiException.class)
    public ResponseEntity<ProblemDetail> handleFulfillment(FulfillmentApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getStatus().getReasonPhrase());
        problem.setProperty("code", ex.getCode());
        return ResponseEntity.status(ex.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(RestaurantServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleRestaurantUnavailable(RestaurantServiceUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Restaurant service is temporarily unavailable");
        problem.setTitle(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        problem.setProperty("code", "DELIVERY_SERVICE_UNAVAILABLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

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
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage() == null ? "Validation failed" : err.getDefaultMessage())
                .orElse("Validation failed");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors());
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
