package com.larpsmp.moneyevent.auth;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Persisted player account.
 */
public record Account(
        UUID id,
        String username,
        String email,
        String passwordHash,
        boolean emailVerified,
        @Nullable Instant emailVerifiedAt,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant lastLoginAt
) {
}
