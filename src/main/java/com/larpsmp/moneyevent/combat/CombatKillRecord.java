package com.larpsmp.moneyevent.combat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted attributed kill for audit and future {@code /top kills}.
 */
public record CombatKillRecord(
        String deathId,
        UUID victimAccountId,
        UUID killerAccountId,
        AttributionCause cause,
        String evidence,
        boolean eventKill,
        boolean friendlyFire,
        String settlementOutcome,
        Instant createdAt
) {

    public CombatKillRecord {
        Objects.requireNonNull(deathId, "deathId");
        Objects.requireNonNull(victimAccountId, "victimAccountId");
        Objects.requireNonNull(killerAccountId, "killerAccountId");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(settlementOutcome, "settlementOutcome");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
