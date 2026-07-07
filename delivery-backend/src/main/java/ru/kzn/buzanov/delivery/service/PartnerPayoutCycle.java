package ru.kzn.buzanov.delivery.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Partner payout calendar:
 * <ul>
 *   <li>Accruals in month M belong to accrual period M.</li>
 *   <li>Payout requests can be created any day; execution is scheduled on payoutDay.</li>
 *   <li>Created before payoutDay → payoutDay of current month; on/after payoutDay → next month.</li>
 * </ul>
 */
final class PartnerPayoutCycle {

    static final int DEFAULT_PAYOUT_DAY = 7;

    private PartnerPayoutCycle() {
    }

    static YearMonth accrualPeriodMonth(Instant at) {
        return YearMonth.from(LocalDate.ofInstant(at, ZoneOffset.UTC));
    }

    static YearMonth payoutCycleMonthForAccrual(Instant accrualAt) {
        return accrualPeriodMonth(accrualAt).plusMonths(1);
    }

    static Instant availableFrom(YearMonth payoutCycleMonth) {
        return payoutCycleMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    static LocalDate scheduledPayoutDate(YearMonth payoutCycleMonth, int payoutDay) {
        int day = Math.min(payoutDay, payoutCycleMonth.lengthOfMonth());
        return payoutCycleMonth.atDay(day);
    }

    /**
     * Payout cycle month for a request created at {@code now}:
     * before payoutDay → current month; on/after payoutDay → next month.
     */
    static YearMonth upcomingPayoutCycleMonth(Instant now, int payoutDay) {
        LocalDate date = LocalDate.ofInstant(now, ZoneOffset.UTC);
        YearMonth current = YearMonth.from(date);
        if (date.getDayOfMonth() < payoutDay) {
            return current;
        }
        return current.plusMonths(1);
    }

    static LocalDate upcomingScheduledPayoutDate(Instant now, int payoutDay) {
        return scheduledPayoutDate(upcomingPayoutCycleMonth(now, payoutDay), payoutDay);
    }

    /**
     * Manual admin approval is allowed once the payout cycle month has started (day 1),
     * not only on/after the scheduled payout day.
     */
    static boolean canConfirmPayout(LocalDate today, LocalDate scheduledPayoutDate) {
        if (scheduledPayoutDate == null) {
            return true;
        }
        LocalDate cycleMonthStart = YearMonth.from(scheduledPayoutDate).atDay(1);
        return !today.isBefore(cycleMonthStart);
    }

    static String formatCycleMonth(YearMonth month) {
        return month.toString();
    }

    static YearMonth parseCycleMonth(String value) {
        return YearMonth.parse(value);
    }

    static int readPayoutDay(Map<String, Object> payoutRestrictions) {
        if (payoutRestrictions == null) {
            return DEFAULT_PAYOUT_DAY;
        }
        Object payoutDay = payoutRestrictions.get("payoutDayOfMonth");
        if (payoutDay instanceof Number number) {
            int day = number.intValue();
            if (day >= 1 && day <= 28) {
                return day;
            }
        }
        return DEFAULT_PAYOUT_DAY;
    }
}
