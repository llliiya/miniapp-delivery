package ru.kzn.buzanov.delivery.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.api.OrderConflictException;
import ru.kzn.buzanov.delivery.api.CourierConflictException;
import ru.kzn.buzanov.delivery.api.PartnerPayoutConflictException;

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

    @ExceptionHandler(PartnerPayoutConflictException.class)
    public ResponseEntity<Map<String, Object>> handlePartnerPayoutConflict(PartnerPayoutConflictException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getErrorCode());
        body.put("message", ex.getUserMessage());
        if (ex.getConflictField() != null) {
            body.put("conflictField", ex.getConflictField());
        }
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(CourierBalancePayoutConflictException.class)
    public ResponseEntity<Map<String, Object>> handleCourierBalancePayoutConflict(
            CourierBalancePayoutConflictException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getErrorCode());
        body.put("message", ex.getUserMessage());
        if (ex.getConflictField() != null) {
            body.put("conflictField", ex.getConflictField());
        }
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleStatus(ResponseStatusException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        detail.setTitle(ex.getStatusCode().toString());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        if (fieldError != null) {
            String conflictField = mapPayoutValidationField(fieldError.getField());
            if (conflictField != null) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("error", "courier_payout_details_required");
                body.put("message", payoutValidationMessage(conflictField));
                body.put("conflictField", conflictField);
                return ResponseEntity.badRequest().body(body);
            }
        }
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Ошибка валидации");
        detail.setProperty("errors", ex.getBindingResult().getFieldErrors());
        return ResponseEntity.badRequest().body(Map.of(
                "error", "validation_error",
                "message", "Ошибка валидации"));
    }

    private static String mapPayoutValidationField(String field) {
        if (field == null) {
            return null;
        }
        return switch (field) {
            case "payoutDetails.cardNumber", "cardNumber" -> "cardNumber";
            case "payoutDetails.recipientName", "recipientName" -> "recipientName";
            case "payoutDetails.phoneNumber", "phoneNumber" -> "phoneNumber";
            case "payoutDetails.bankName", "bankName" -> "bankName";
            case "payoutDetails.transferType", "transferType" -> "transferType";
            default -> null;
        };
    }

    private static String payoutValidationMessage(String conflictField) {
        return switch (conflictField) {
            case "cardNumber" -> "Укажите номер банковской карты";
            case "recipientName" -> "Укажите имя получателя";
            case "phoneNumber" -> "Укажите корректный номер телефона";
            case "bankName" -> "Укажите банк получателя";
            case "transferType" -> "Укажите способ получения";
            default -> "Укажите реквизиты для получения выплаты";
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "invalid_payload",
                "message", "Неверный формат данных запроса"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "data_conflict",
                "message", "Конфликт данных при сохранении"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "invalid_request",
                "message", ex.getMessage() != null ? ex.getMessage() : "Некорректные данные запроса"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class)
                .error("Unhandled API error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "internal_error",
                "message", "Внутренняя ошибка сервера"));
    }
}
