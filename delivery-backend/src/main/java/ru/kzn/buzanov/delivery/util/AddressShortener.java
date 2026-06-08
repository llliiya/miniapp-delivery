package ru.kzn.buzanov.delivery.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Сокращение полного адреса до короткой формы (как formatShortAddress во фронтенде).
 */
public final class AddressShortener {

    private static final Pattern SIX_DIGIT_POSTCODE = Pattern.compile("^\\d{6}$");

    private AddressShortener() {
    }

    public static String shorten(String full) {
        if (full == null || full.isBlank()) {
            return "";
        }
        return formatFromParts(List.of(full.split(",")));
    }

    private static String formatFromParts(List<String> parts) {
        List<String> clean = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !isNoisePart(trimmed)) {
                clean.add(trimmed);
            }
        }
        if (clean.isEmpty()) {
            return parts.isEmpty() ? "" : parts.get(0).trim();
        }

        String house = "";
        String street = "";
        String city = "";

        for (String p : clean) {
            if (isStreetPart(p)) {
                street = p;
            } else if (isHousePart(p) && house.isEmpty()) {
                house = p;
            }
        }

        for (int i = clean.size() - 1; i >= 0; i--) {
            String p = clean.get(i);
            if (p.equals(house) || p.equals(street) || isStreetPart(p) || isHousePart(p)) {
                continue;
            }
            city = p;
            break;
        }

        if (city.isEmpty()) {
            for (String p : clean) {
                if (p.equals(house) || p.equals(street) || isStreetPart(p) || isHousePart(p)) {
                    continue;
                }
                city = p;
            }
        }

        List<String> out = new ArrayList<>();
        if (!city.isEmpty()) {
            out.add(city);
        }
        if (!street.isEmpty()) {
            out.add(shortenStreet(street));
        }
        if (!house.isEmpty()) {
            out.add(house);
        }
        if (!out.isEmpty()) {
            return String.join(", ", out);
        }
        return String.join(", ", clean.subList(0, Math.min(3, clean.size())));
    }

    private static boolean isNoisePart(String part) {
        String p = part.toLowerCase(Locale.ROOT);
        if (SIX_DIGIT_POSTCODE.matcher(p).matches()) {
            return true;
        }
        if ("россия".equals(p) || "russia".equals(p)) {
            return true;
        }
        if (p.contains("федеральный округ")) {
            return true;
        }
        if (p.contains("городской округ")) {
            return true;
        }
        if (p.startsWith("республика ")) {
            return true;
        }
        if (p.endsWith("область") && p.length() > 12) {
            return true;
        }
        if (p.endsWith("район")) {
            return true;
        }
        return "татарстан".equals(p);
    }

    private static boolean isStreetPart(String part) {
        String t = part.trim();
        return t.matches("(?i)^(ул\\.|улица|пр\\.|проспект|пер\\.|переулок|бульвар|ш\\.|шоссе|наб\\.|набережная|пл\\.|площадь|туп\\.|тупик|ал\\.|аллея)\\s.*")
                || t.matches("(?i).*(магистраль|проспект|бульвар|набережная|шоссе|переулок|аллея|площадь)$");
    }

    private static boolean isHousePart(String part) {
        String t = part.trim();
        return t.matches("^\\d.*") && t.length() <= 24;
    }

    private static String shortenStreet(String street) {
        String s = street.trim();
        if (s.isEmpty()) {
            return "";
        }
        return s.replaceFirst("(?i)^улица\\s+", "ул. ")
                .replaceFirst("(?i)^проспект\\s+", "пр. ")
                .replaceFirst("(?i)^переулок\\s+", "пер. ");
    }
}
