package ru.kzn.buzanov.delivery.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountProvisioningClientTest {

    @Test
    void detectsLoginConflict() {
        var ex = new ResponseStatusException(HttpStatus.CONFLICT, "Этот логин уже занят");
        assertTrue(AccountProvisioningClient.isLoginConflict(ex));
    }

    @Test
    void ignoresPhoneConflict() {
        var ex = new ResponseStatusException(HttpStatus.CONFLICT, "Пользователь с таким телефоном уже существует");
        assertFalse(AccountProvisioningClient.isLoginConflict(ex));
    }

    @Test
    void detectsEmailConflictAsNotLoginConflict() {
        var ex = new ResponseStatusException(HttpStatus.CONFLICT, "Этот email уже используется");
        assertFalse(AccountProvisioningClient.isLoginConflict(ex));
    }
}
