package ru.kzn.buzanov.delivery.service;

import ru.kzn.buzanov.delivery.domain.PartnerPayoutStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;

final class PartnerPayoutMonthlyLimit {

    static final Set<PartnerPayoutStatus> BLOCKING_STATUSES = EnumSet.of(
            PartnerPayoutStatus.PENDING,
            PartnerPayoutStatus.SCHEDULED,
            PartnerPayoutStatus.PROCESSING,
            PartnerPayoutStatus.PAID);

    private PartnerPayoutMonthlyLimit() {
    }

    static MonthBounds calendarMonthBounds(Instant at) {
        LocalDate today = LocalDate.ofInstant(at, ZoneOffset.UTC);
        Instant start = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endExclusive = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new MonthBounds(start, endExclusive);
    }

    record MonthBounds(Instant startInclusive, Instant endExclusive) {
    }
}
