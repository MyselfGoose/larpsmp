package com.larpsmp.moneyevent.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class AuthRateLimiterTest {

    private AuthRateLimiter limiter;
    private UUID profileId;

    @BeforeEach
    void setUp() {
        limiter = new AuthRateLimiter(new AuthConfig.RateLimitConfig(3, 300));
        profileId = UUID.randomUUID();
    }

    @Test
    void allowsUntilThresholdThenBlocks() {
        assertFalse(limiter.isLimited(profileId));
        limiter.recordFailure(profileId);
        limiter.recordFailure(profileId);
        assertFalse(limiter.isLimited(profileId));
        limiter.recordFailure(profileId);
        assertTrue(limiter.isLimited(profileId));
    }

    @Test
    void clearRemovesLimit() {
        limiter.recordFailure(profileId);
        limiter.recordFailure(profileId);
        limiter.recordFailure(profileId);
        assertTrue(limiter.isLimited(profileId));
        limiter.clear(profileId);
        assertFalse(limiter.isLimited(profileId));
    }

    @Test
    void differentProfilesAreIndependent() {
        UUID other = UUID.randomUUID();
        limiter.recordFailure(profileId);
        limiter.recordFailure(profileId);
        limiter.recordFailure(profileId);
        assertTrue(limiter.isLimited(profileId));
        assertFalse(limiter.isLimited(other));
    }
}
