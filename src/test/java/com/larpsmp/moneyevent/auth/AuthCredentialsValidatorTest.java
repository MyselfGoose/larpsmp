package com.larpsmp.moneyevent.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class AuthCredentialsValidatorTest {

    private AuthCredentialsValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AuthCredentialsValidator(new TestAccount("test", "test@larpsmp.local", "test123"));
    }

    @Test
    void authenticatesWithUsername() {
        assertTrue(validator.authenticate("test", "test123"));
        assertTrue(validator.authenticate("TEST", "test123"));
    }

    @Test
    void authenticatesWithEmail() {
        assertTrue(validator.authenticate("test@larpsmp.local", "test123"));
        assertTrue(validator.authenticate("Test@LarpSMP.local", "test123"));
    }

    @Test
    void rejectsWrongPasswordOrIdentifier() {
        assertFalse(validator.authenticate("test", "wrong"));
        assertFalse(validator.authenticate("other", "test123"));
        assertFalse(validator.authenticate("", "test123"));
        assertFalse(validator.authenticate("test", ""));
        assertFalse(validator.authenticate(null, "test123"));
    }

    @Test
    void requiresAllSignupFields() {
        assertTrue(validator.validateSignupFields("", "a@b.c", "pass", "empty").isPresent());
        assertTrue(validator.validateSignupFields("user", " ", "pass", "empty").isPresent());
        assertTrue(validator.validateSignupFields("user", "a@b.c", "", "empty").isPresent());
        assertTrue(validator.validateSignupFields("user", "a@b.c", "pass", "empty").isEmpty());
    }

    @Test
    void detectsEmptyLoginFields() {
        assertTrue(validator.hasEmptyLoginFields(" ", "pass"));
        assertTrue(validator.hasEmptyLoginFields("user", ""));
        assertFalse(validator.hasEmptyLoginFields("user", "pass"));
    }
}
