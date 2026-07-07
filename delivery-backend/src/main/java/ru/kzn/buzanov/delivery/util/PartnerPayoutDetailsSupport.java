package ru.kzn.buzanov.delivery.util;

import org.springframework.http.HttpStatus;
import ru.kzn.buzanov.delivery.api.PartnerPayoutConflictException;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutTransferType;
import ru.kzn.buzanov.delivery.dto.PartnerPayoutDetailsDto;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PartnerPayoutDetailsSupport {

    private PartnerPayoutDetailsSupport() {
    }

    public static void requireForBankTransfer(PartnerPayoutDetailsDto details) {
        if (details == null) {
            throw detailsRequired();
        }
        PartnerPayoutTransferType transferType = parseTransferType(details.transferType());
        if (transferType == null) {
            throw fieldError("transferType", "Укажите способ получения");
        }
        switch (transferType) {
            case CARD -> validateCardDetails(details);
            case SBP_PHONE -> validateSbpDetails(details);
        }
    }

    public static Map<String, Object> toMap(PartnerPayoutDetailsDto details) {
        PartnerPayoutTransferType transferType = parseTransferType(details.transferType());
        if (transferType == null) {
            throw fieldError("transferType", "Укажите способ получения");
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("transferType", transferType.name());
        map.put("recipientName", normalizeText(details.recipientName()));

        switch (transferType) {
            case CARD -> {
                map.put("cardNumber", normalizeCardNumber(details.cardNumber()));
                String bankName = normalizeText(details.bankName());
                if (bankName != null) {
                    map.put("bankName", bankName);
                }
            }
            case SBP_PHONE -> {
                map.put("phoneNumber", normalizePhoneNumber(details.phoneNumber()));
                map.put("bankName", normalizeText(details.bankName()));
            }
        }
        return map;
    }

    public static String maskCardNumber(String cardNumber) {
        String digits = normalizeCardNumber(cardNumber);
        if (digits == null || digits.length() < 4) {
            return "••••";
        }
        return "•••• " + digits.substring(digits.length() - 4);
    }

    public static String maskPhoneNumber(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);
        if (normalized == null) {
            return "+7 *** ***-**-**";
        }
        String digits = normalized.replaceAll("\\D", "");
        if (digits.length() < 11 || !digits.startsWith("7")) {
            return "+7 *** ***-**-**";
        }
        String last4 = digits.substring(digits.length() - 4);
        return "+7 *** ***-" + last4.substring(0, 2) + "-" + last4.substring(2);
    }

    public static String maskRequisites(Map<String, Object> details) {
        return switch (resolveTransferType(details)) {
            case SBP_PHONE -> maskPhoneNumber(readPhoneNumber(details));
            case CARD -> maskCardNumber(readCardNumber(details));
            default -> "—";
        };
    }

    public static PartnerPayoutTransferType resolveTransferType(Map<String, Object> details) {
        PartnerPayoutTransferType explicit = parseTransferType(getString(details, "transferType"));
        if (explicit != null) {
            return explicit;
        }
        if (readCardNumber(details) != null) {
            return PartnerPayoutTransferType.CARD;
        }
        if (readPhoneNumber(details) != null) {
            return PartnerPayoutTransferType.SBP_PHONE;
        }
        return null;
    }

    public static String readCardNumber(Map<String, Object> details) {
        return normalizeCardNumber(getString(details, "cardNumber"));
    }

    public static String readPhoneNumber(Map<String, Object> details) {
        return normalizePhoneNumber(getString(details, "phoneNumber"));
    }

    public static String readRecipientName(Map<String, Object> details) {
        return normalizeText(getString(details, "recipientName"));
    }

    public static String readBankName(Map<String, Object> details) {
        return normalizeText(getString(details, "bankName"));
    }

    public static String readRejectionComment(Map<String, Object> details) {
        return normalizeText(getString(details, "rejectionComment"));
    }

    public static boolean hasRequiredBankDetails(Map<String, Object> details) {
        return hasCompletePayoutDetails(details);
    }

    public static boolean hasCompletePayoutDetails(Map<String, Object> details) {
        return switch (resolveTransferType(details)) {
            case CARD -> readCardNumber(details) != null && readRecipientName(details) != null;
            case SBP_PHONE -> readPhoneNumber(details) != null
                    && readRecipientName(details) != null
                    && readBankName(details) != null;
            default -> false;
        };
    }

    private static void validateCardDetails(PartnerPayoutDetailsDto details) {
        String cardNumber = normalizeCardNumber(details.cardNumber());
        String recipientName = normalizeText(details.recipientName());
        if (cardNumber == null) {
            throw fieldError("cardNumber", "Укажите номер банковской карты");
        }
        if (recipientName == null) {
            throw fieldError("recipientName", "Укажите имя получателя");
        }
        if (cardNumber.length() < 13 || cardNumber.length() > 19) {
            throw fieldError("cardNumber", "Укажите корректный номер банковской карты (13–19 цифр)");
        }
    }

    private static void validateSbpDetails(PartnerPayoutDetailsDto details) {
        String phoneNumber = normalizePhoneNumber(details.phoneNumber());
        String recipientName = normalizeText(details.recipientName());
        String bankName = normalizeText(details.bankName());
        if (phoneNumber == null || !isValidRussianPhone(phoneNumber)) {
            throw fieldError("phoneNumber", "Укажите корректный номер телефона");
        }
        if (recipientName == null) {
            throw fieldError("recipientName", "Укажите имя получателя");
        }
        if (bankName == null) {
            throw fieldError("bankName", "Укажите банк получателя");
        }
    }

    private static boolean isValidRussianPhone(String phoneNumber) {
        return phoneNumber != null && phoneNumber.matches("\\+79\\d{9}");
    }

    private static PartnerPayoutTransferType parseTransferType(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        try {
            return PartnerPayoutTransferType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static PartnerPayoutConflictException detailsRequired() {
        return new PartnerPayoutConflictException(
                "partner_payout_details_required",
                "Укажите реквизиты для получения выплаты",
                HttpStatus.BAD_REQUEST);
    }

    private static PartnerPayoutConflictException fieldError(String field, String message) {
        return new PartnerPayoutConflictException(
                "partner_payout_details_required",
                message,
                HttpStatus.BAD_REQUEST,
                field);
    }

    private static String getString(Map<String, Object> details, String key) {
        if (details == null) {
            return null;
        }
        Object value = details.get(key);
        return value != null ? value.toString() : null;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeCardNumber(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private static String normalizePhoneNumber(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.length() == 10 && digits.startsWith("9")) {
            digits = "7" + digits;
        }
        if (digits.length() == 11 && digits.startsWith("8")) {
            digits = "7" + digits.substring(1);
        }
        if (digits.length() == 11 && digits.startsWith("7")) {
            return "+" + digits;
        }
        return null;
    }
}
