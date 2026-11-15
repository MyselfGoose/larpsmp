package com.larpsmp.moneyevent.auth;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Persisted one-time email verification challenge.
 */
public record EmailChallenge(
        UUID id,
        UUID accountId,
        EmailChallengePurpose purpose,
        String codeHash,
        Instant expiresAt,
        int attemptCount,
        int maxAttempts,
        @Nullable Instant consumedAt,
        Instant createdAt,
        Instant lastSentAt
) {
    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean attemptsExhausted() {
        return attemptCount >= maxAttempts;
    }
}
