package com.larpsmp.moneyevent.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class VerificationCodeHasherTest {

    @Test
    void generatesFixedLengthNumericCodes() {
        VerificationCodeHasher hasher = new VerificationCodeHasher("pepper", 6);
        String code = hasher.generateCode();
        assertEquals(6, code.length());
        assertTrue(code.chars().allMatch(Character::isDigit));
    }

    @Test
    void hashesAreStableAndMatch() {
        VerificationCodeHasher hasher = new VerificationCodeHasher("pepper", 6);
        String hash = hasher.hash("123456");
        assertTrue(hasher.matches("123456", hash));
        assertFalse(hasher.matches("000000", hash));
        assertEquals(hash, hasher.hash("123456"));
    }

    @Test
    void differentPeppersProduceDifferentHashes() {
        String a = new VerificationCodeHasher("pepper-a", 6).hash("123456");
        String b = new VerificationCodeHasher("pepper-b", 6).hash("123456");
        assertFalse(a.equals(b));
    }
}
