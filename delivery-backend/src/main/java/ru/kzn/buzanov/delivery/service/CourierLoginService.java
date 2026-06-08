package ru.kzn.buzanov.delivery.service;

import org.springframework.stereotype.Service;

@Service
public class CourierLoginService {

    public static final String LOGIN_PREFIX = "c_";
    public static final String LEGACY_LOGIN_PREFIX = "courier_";

    private final MemberPublicIdSequence memberPublicIdSequence;

    public CourierLoginService(MemberPublicIdSequence memberPublicIdSequence) {
        this.memberPublicIdSequence = memberPublicIdSequence;
    }

    public long reserveNextPublicId() {
        return memberPublicIdSequence.next();
    }

    public static String formatLogin(long number) {
        return LOGIN_PREFIX + number;
    }

    public static String formatLegacyLogin(long number) {
        return LEGACY_LOGIN_PREFIX + number;
    }

    public static Long parseLoginNumber(String login) {
        if (login == null) {
            return null;
        }
        if (login.startsWith(LOGIN_PREFIX)) {
            return parseNumericSuffix(login.substring(LOGIN_PREFIX.length()));
        }
        if (login.startsWith(LEGACY_LOGIN_PREFIX)) {
            return parseNumericSuffix(login.substring(LEGACY_LOGIN_PREFIX.length()));
        }
        return null;
    }

    private static Long parseNumericSuffix(String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(suffix);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
