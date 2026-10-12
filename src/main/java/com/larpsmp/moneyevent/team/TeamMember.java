package com.larpsmp.moneyevent.team;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Roster membership for a competing account on a team.
 */
public record TeamMember(
        UUID teamId,
        UUID accountId,
        String username,
        int sortOrder,
        Instant joinedAt,
        @Nullable Integer tier
) {
    public Optional<PlayerTier> playerTier() {
        if (tier == null) {
            return Optional.empty();
        }
        return PlayerTier.fromValue(tier);
    }
}
