package ru.kzn.buzanov.delivery.util;

public final class CityNormalizer {

    private CityNormalizer() {
    }

    /**
     * Trim and collapse internal whitespace. Returns null for blank input.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static boolean equals(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equalsIgnoreCase(b);
    }
}
