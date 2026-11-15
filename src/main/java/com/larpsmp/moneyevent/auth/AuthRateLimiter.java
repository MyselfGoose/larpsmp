package com.larpsmp.moneyevent.auth;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter for failed auth attempts keyed by Minecraft profile UUID.
 */
public final class AuthRateLimiter {

    private final int maxAttempts;
    private final long windowMillis;
    private final Map<UUID, FailureWindow> failures = new ConcurrentHashMap<>();

    public AuthRateLimiter(AuthConfig.RateLimitConfig config) {
        this.maxAttempts = Math.max(1, config.maxAttempts());
        this.windowMillis = Math.max(1L, config.windowSeconds()) * 1_000L;
    }

    public boolean isLimited(UUID profileId) {
        FailureWindow window = failures.get(profileId);
        if (window == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - window.startedAtMillis() >= windowMillis) {
            failures.remove(profileId, window);
            return false;
        }
        return window.attempts().get() >= maxAttempts;
    }

    public void recordFailure(UUID profileId) {
        long now = System.currentTimeMillis();
        failures.compute(profileId, (ignored, existing) -> {
            if (existing == null || now - existing.startedAtMillis() >= windowMillis) {
                return new FailureWindow(now, new AtomicInteger(1));
            }
            existing.attempts().incrementAndGet();
            return existing;
        });
    }

    public void clear(UUID profileId) {
        failures.remove(profileId);
    }

    /**
     * Removes expired windows. Safe to call periodically.
     */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, FailureWindow>> iterator = failures.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, FailureWindow> entry = iterator.next();
            if (now - entry.getValue().startedAtMillis() >= windowMillis) {
                iterator.remove();
            }
        }
    }

    private record FailureWindow(long startedAtMillis, AtomicInteger attempts) {
    }
}
