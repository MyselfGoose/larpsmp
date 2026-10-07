package com.larpsmp.moneyevent.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class PasswordHasherTest {

    private PasswordHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new PasswordHasher();
    }

    @Test
    void hashesAndVerifiesPassword() {
        String encoded = hasher.hash("correct-horse-battery");
        assertTrue(encoded.startsWith("$argon2id$v=19$"));
        assertTrue(hasher.verify("correct-horse-battery", encoded));
        assertFalse(hasher.verify("wrong-password", encoded));
    }

    @Test
    void samePasswordProducesDifferentHashes() {
        String first = hasher.hash("same-password");
        String second = hasher.hash("same-password");
        assertNotEquals(first, second);
        assertTrue(hasher.verify("same-password", first));
        assertTrue(hasher.verify("same-password", second));
    }

    @Test
    void rejectsEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(""));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
    }

    @Test
    void verifyRejectsMalformedEncodedHash() {
        assertFalse(hasher.verify("password", "not-a-hash"));
        assertFalse(hasher.verify("password", ""));
        assertFalse(hasher.verify("password", null));
    }
}
