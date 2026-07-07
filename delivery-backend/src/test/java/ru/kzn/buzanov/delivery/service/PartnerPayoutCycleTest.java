package ru.kzn.buzanov.delivery.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartnerPayoutCycleTest {

    @Test
    void canConfirmPayoutFromFirstDayOfPayoutMonth() {
        LocalDate scheduled = LocalDate.of(2026, 7, 7);

        assertFalse(PartnerPayoutCycle.canConfirmPayout(LocalDate.of(2026, 6, 30), scheduled));
        assertTrue(PartnerPayoutCycle.canConfirmPayout(LocalDate.of(2026, 7, 1), scheduled));
        assertTrue(PartnerPayoutCycle.canConfirmPayout(LocalDate.of(2026, 7, 5), scheduled));
        assertTrue(PartnerPayoutCycle.canConfirmPayout(LocalDate.of(2026, 7, 7), scheduled));
        assertTrue(PartnerPayoutCycle.canConfirmPayout(LocalDate.of(2026, 7, 15), scheduled));
    }

    @Test
    void canConfirmPayoutWhenScheduledDateMissing() {
        assertTrue(PartnerPayoutCycle.canConfirmPayout(LocalDate.of(2026, 7, 5), null));
    }
}
