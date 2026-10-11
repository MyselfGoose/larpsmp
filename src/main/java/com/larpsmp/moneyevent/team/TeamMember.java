package com.larpsmp.moneyevent.team;

import java.time.Instant;
import java.util.UUID;

/**
 * Roster membership for a competing account on a team.
 */
public record TeamMember(
        UUID teamId,
        UUID accountId,
        String username,
        int sortOrder,
        Instant joinedAt
) {
}
