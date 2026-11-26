package com.larpsmp.moneyevent.team;

import java.time.Instant;
import java.util.UUID;

/**
 * Competition participation flags for a LarpSMP account.
 *
 * <p>Absent rows mean neither competitor nor spectator. Competitor and spectator are mutually exclusive.
 */
public record AccountParticipation(
        UUID accountId,
        boolean competitor,
        boolean spectator,
        Instant updatedAt
) {
    public static AccountParticipation neither(UUID accountId) {
        return new AccountParticipation(accountId, false, false, Instant.EPOCH);
    }

    public boolean isCompetitor() {
        return competitor;
    }

    public boolean isSpectator() {
        return spectator;
    }
}
