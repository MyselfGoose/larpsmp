package com.larpsmp.moneyevent.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for AuthService validation and rate-limit short-circuiting.
 * Persistence paths require a live Postgres instance and are covered manually.
 */
final class AuthServiceTest {

    private AuthService authService;
    private AuthRateLimiter rateLimiter;
    private UUID profileId;

    @BeforeEach
    void setUp() {
        AuthConfig.SignupConfig signupConfig = new AuthConfig.SignupConfig(true, 8, 64, 3, 16);
        AuthConfig.Messages messages = new AuthConfig.Messages(
                "Login", "body", "id", "pw", "Log in", "Sign up",
                "invalid", "empty login", "uuid other", "account other", "rate login", "internal login",
                "Sign up", "signup body", "user", "email", "pw", "Create", "Back",
                "empty signup", "bad user", "bad email", "bad password",
                "user taken", "email taken", "disabled", "rate signup", "internal signup",
                "uuid bound", "Back to menu", "cancelled", "timeout", "denied", "missing", "db down"
        );
        rateLimiter = new AuthRateLimiter(new AuthConfig.RateLimitConfig(5, 300));
        // Repository is unused for pure validation tests; signup/login that hit DB are not invoked here.
        authService = new AuthService(
                null,
                new PasswordHasher(),
                rateLimiter,
                signupConfig,
                messages,
                Logger.getLogger("AuthServiceTest")
        );
        profileId = UUID.randomUUID();
    }

    @Test
    void validatesSignupFields() {
        assertTrue(authService.validateSignupFields("", "a@b.co", "password1").isPresent());
        assertTrue(authService.validateSignupFields("ab", "a@b.co", "password1").isPresent());
        assertTrue(authService.validateSignupFields("bad name!", "a@b.co", "password1").isPresent());
        assertTrue(authService.validateSignupFields("valid_user", "not-an-email", "password1").isPresent());
        assertTrue(authService.validateSignupFields("valid_user", "a@b.co", "short").isPresent());
        assertTrue(authService.validateSignupFields("valid_user", "a@b.co", "password1").isEmpty());
    }

    @Test
    void detectsEmptyLoginFields() {
        assertTrue(authService.hasEmptyLoginFields(" ", "pass"));
        assertTrue(authService.hasEmptyLoginFields("user", ""));
        assertFalse(authService.hasEmptyLoginFields("user", "pass"));
    }

    @Test
    void signupRateLimitedWithoutTouchingDatabase() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(profileId);
        }
        SignupResult result = authService.signup(
                "valid_user",
                "user@example.com",
                "password1",
                profileId,
                "Player"
        );
        assertInstanceOf(SignupResult.RateLimited.class, result);
    }

    @Test
    void loginRateLimitedWithoutTouchingDatabase() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(profileId);
        }
        LoginResult result = authService.login("valid_user", "password1", profileId, "Player");
        assertInstanceOf(LoginResult.RateLimited.class, result);
    }

    @Test
    void signupValidationErrorWithoutTouchingDatabase() {
        SignupResult result = authService.signup("ab", "bad", "x", profileId, "Player");
        assertInstanceOf(SignupResult.ValidationError.class, result);
        Optional<String> expected = authService.validateSignupFields("ab", "bad", "x");
        assertTrue(expected.isPresent());
    }
}
