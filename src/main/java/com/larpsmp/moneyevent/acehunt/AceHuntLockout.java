package com.larpsmp.moneyevent.acehunt;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Non-ace respawn lock inside the bedrock box.
 */
public record AceHuntLockout(
        UUID accountId,
        Instant releaseAt,
        UUID hunterSpawnTeamId
) {
    public AceHuntLockout {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(releaseAt, "releaseAt");
        Objects.requireNonNull(hunterSpawnTeamId, "hunterSpawnTeamId");
    }

    public boolean isActive(Instant now) {
        return now.isBefore(releaseAt);
    }
}
