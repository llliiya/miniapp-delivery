package ru.kzn.buzanov.delivery.util;

public final class TelegramHtmlEscaper {

    private TelegramHtmlEscaper() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static String bold(String value) {
        return "<b>" + escape(value) + "</b>";
    }

    public static String code(String value) {
        return "<code>" + escape(value) + "</code>";
    }
}
