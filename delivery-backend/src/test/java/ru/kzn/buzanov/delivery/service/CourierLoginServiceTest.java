package ru.kzn.buzanov.delivery.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CourierLoginServiceTest {

    @Test
    void formatLoginUsesCompactPrefix() {
        assertEquals("c_1025", CourierLoginService.formatLogin(1025));
    }

    @Test
    void parseLoginNumberSupportsCompactPrefix() {
        assertEquals(1025L, CourierLoginService.parseLoginNumber("c_1025"));
    }

    @Test
    void parseLoginNumberSupportsLegacyPrefix() {
        assertEquals(25L, CourierLoginService.parseLoginNumber("courier_25"));
        assertEquals(1_000_000_000_025L, CourierLoginService.parseLoginNumber("courier_1000000000025"));
    }

    @Test
    void parseLoginNumberRejectsInvalidValues() {
        assertNull(CourierLoginService.parseLoginNumber(null));
        assertNull(CourierLoginService.parseLoginNumber("c_"));
        assertNull(CourierLoginService.parseLoginNumber("manager_25"));
        assertNull(CourierLoginService.parseLoginNumber("courier_abc"));
    }
}
