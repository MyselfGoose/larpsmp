package com.larpsmp.moneyevent.team;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Persisted competing team definition.
 */
public record Team(
        UUID id,
        String slug,
        String displayName,
        TeamColor color,
        int sortOrder,
        @Nullable UUID captainAccountId,
        Instant createdAt,
        Instant updatedAt
) {
}
