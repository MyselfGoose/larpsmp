package com.larpsmp.moneyevent.acehunt;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * One ace death during a scoring phase.
 */
public record AceHuntScoredDeath(
        UUID aceTeamId,
        UUID aceAccountId,
        String deathId,
        @Nullable UUID killerAccountId,
        @Nullable UUID killerTeamId,
        boolean huntScored,
        int deathOrder,
        Instant diedAt
) {
    public AceHuntScoredDeath {
        Objects.requireNonNull(aceTeamId, "aceTeamId");
        Objects.requireNonNull(aceAccountId, "aceAccountId");
        Objects.requireNonNull(deathId, "deathId");
        Objects.requireNonNull(diedAt, "diedAt");
        if (deathOrder < 1) {
            throw new IllegalArgumentException("deathOrder must be >= 1");
        }
    }
}
