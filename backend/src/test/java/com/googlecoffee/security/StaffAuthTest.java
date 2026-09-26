package com.googlecoffee.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffAuthTest {

    private final StaffAuth auth = new StaffAuth("2468", "a-test-secret-that-is-long");

    @Test
    void correctPinYieldsVerifiableToken() {
        String token = auth.login("ip", "2468", 1000);
        assertNotNull(token);
        assertTrue(auth.verify(token, 2000));
    }

    @Test
    void tamperedOrExpiredTokensAreRejected() {
        String token = auth.login("ip", "2468", 1000);
        assertFalse(auth.verify(token + "x", 2000));
        assertFalse(auth.verify(token, 1000 + 13L * 60 * 60 * 1000));
        assertFalse(auth.verify("garbage", 2000));
    }

    @Test
    void repeatedWrongPinsLockOut() {
        for (int i = 0; i < 5; i++) assertNull(auth.login("ip2", "0000", 1000));
        assertTrue(auth.isLockedOut("ip2", 2000));
        assertFalse(auth.isLockedOut("ip2", 1000 + 6 * 60 * 1000));
    }
}
