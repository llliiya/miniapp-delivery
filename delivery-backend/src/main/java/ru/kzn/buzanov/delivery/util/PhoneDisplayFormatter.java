package ru.kzn.buzanov.delivery.util;

public final class PhoneDisplayFormatter {

    private PhoneDisplayFormatter() {
    }

    public static String format(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() > 11) {
            digits = digits.substring(0, 11);
        }
        if (digits.length() == 11 && digits.startsWith("7")) {
            return "+7 " + digits.substring(1, 4) + " " + digits.substring(4, 7) + "-"
                    + digits.substring(7, 9) + "-" + digits.substring(9);
        }
        if (digits.length() == 10 && digits.startsWith("9")) {
            return "+7 " + digits.substring(0, 3) + " " + digits.substring(3, 6) + "-"
                    + digits.substring(6, 8) + "-" + digits.substring(8);
        }
        if (!digits.isEmpty()) {
            return "+" + digits;
        }
        return raw.trim();
    }

    public static String toTelUri(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() > 11) {
            digits = digits.substring(0, 11);
        }
        if (digits.length() == 10 && digits.startsWith("9")) {
            digits = "7" + digits;
        }
        if (digits.isEmpty()) {
            return "";
        }
        return "tel:+" + digits;
    }
}
