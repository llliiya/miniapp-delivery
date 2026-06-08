package ru.kzn.buzanov.delivery.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddressShortenerTest {

    @Test
    void shortensLongPickupAddress() {
        String full = "24В, улица Родины, Дальний, Советский район, Казань, "
                + "городской округ Казань, Татарстан, Приволжский федеральный округ, 420087, Россия";
        String shortAddress = AddressShortener.shorten(full);
        assertTrue(shortAddress.contains("Казань"));
        assertTrue(shortAddress.contains("24"));
        assertTrue(shortAddress.contains("Родины") || shortAddress.contains("ул."));
    }
}
