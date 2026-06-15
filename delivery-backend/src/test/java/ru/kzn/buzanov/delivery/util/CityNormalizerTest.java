package ru.kzn.buzanov.delivery.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityNormalizerTest {

    @Test
    void normalizeTrimsAndCollapsesSpaces() {
        assertEquals("Казань", CityNormalizer.normalize("  Казань  "));
        assertEquals("Нижний Новгород", CityNormalizer.normalize("Нижний   Новгород"));
    }

    @Test
    void normalizeReturnsNullForBlank() {
        assertNull(CityNormalizer.normalize(null));
        assertNull(CityNormalizer.normalize("   "));
    }

    @Test
    void equalsIsCaseInsensitive() {
        assertTrue(CityNormalizer.equals("Казань", "казань"));
    }
}
