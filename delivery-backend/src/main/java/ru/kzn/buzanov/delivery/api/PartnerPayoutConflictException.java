package ru.kzn.buzanov.delivery.api;

import org.springframework.http.HttpStatus;

public class PartnerPayoutConflictException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;
    private final HttpStatus status;
    private final String conflictField;

    public PartnerPayoutConflictException(String errorCode) {
        this(errorCode, defaultMessage(errorCode));
    }

    public PartnerPayoutConflictException(String errorCode, String userMessage) {
        this(errorCode, userMessage, HttpStatus.CONFLICT);
    }

    public PartnerPayoutConflictException(String errorCode, String userMessage, HttpStatus status) {
        this(errorCode, userMessage, status, null);
    }

    public PartnerPayoutConflictException(
            String errorCode,
            String userMessage,
            HttpStatus status,
            String conflictField) {
        super(userMessage != null ? userMessage : errorCode);
        this.errorCode = errorCode;
        this.userMessage = userMessage != null ? userMessage : defaultMessage(errorCode);
        this.status = status != null ? status : HttpStatus.CONFLICT;
        this.conflictField = conflictField;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getConflictField() {
        return conflictField;
    }

    private static String defaultMessage(String errorCode) {
        return switch (errorCode) {
            case "payout_once_per_month" ->
                    "Партнёрскую выплату можно запросить не чаще одного раза в календарный месяц";
            case "partner_payout_details_required" -> "Укажите реквизиты для получения выплаты";
            case "partner_payout_date_not_reached" ->
                    "Подтвердить выплату можно с 1-го числа месяца выплаты";
            case "partner_payout_rejection_comment_required" -> "Укажите причину отклонения";
            default -> "Конфликт при обработке выплаты";
        };
    }
}
