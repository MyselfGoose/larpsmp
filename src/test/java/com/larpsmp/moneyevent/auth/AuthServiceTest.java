package com.larpsmp.moneyevent.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.larpsmp.moneyevent.email.CapturingEmailSender;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for AuthService validation and rate-limit short-circuiting.
 * Persistence paths require a live Postgres instance and are covered by integration tests.
 */
final class AuthServiceTest {

    private AuthService authService;
    private AuthRateLimiter rateLimiter;
    private UUID profileId;

    @BeforeEach
    void setUp() {
        AuthConfig.SignupConfig signupConfig = new AuthConfig.SignupConfig(true, 8, 64, 3, 16);
        AuthConfig.Messages messages = TestAuthFixtures.sampleMessages();
        rateLimiter = new AuthRateLimiter(new AuthConfig.RateLimitConfig(5, 300));
        EmailChallengeService emailChallengeService = new EmailChallengeService(
                null,
                null,
                new CapturingEmailSender(),
                new VerificationCodeHasher("test-pepper", 6),
                TestAuthFixtures.sampleEmailConfig(),
                true,
                Logger.getLogger("AuthServiceTest")
        );
        authService = new AuthService(
                null,
                new PasswordHasher(),
                rateLimiter,
                emailChallengeService,
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
    void signupWithoutEmailConfiguredFailsClosed() {
        EmailChallengeService unavailable = new EmailChallengeService(
                null,
                null,
                null,
                new VerificationCodeHasher("test-pepper", 6),
                TestAuthFixtures.sampleEmailConfig(),
                false,
                Logger.getLogger("AuthServiceTest")
        );
        AuthService service = new AuthService(
                null,
                new PasswordHasher(),
                rateLimiter,
                unavailable,
                new AuthConfig.SignupConfig(true, 8, 64, 3, 16),
                TestAuthFixtures.sampleMessages(),
                Logger.getLogger("AuthServiceTest")
        );
        SignupResult result = service.signup("valid_user", "user@example.com", "password1", profileId, "Player");
        assertInstanceOf(SignupResult.EmailUnavailable.class, result);
    }
}
