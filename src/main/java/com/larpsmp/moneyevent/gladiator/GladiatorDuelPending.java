package com.larpsmp.moneyevent.gladiator;

import java.util.Objects;
import java.util.UUID;

/**
 * Active kit-ban duel nomination.
 */
public record GladiatorDuelPending(
        UUID fighterAAccountId,
        UUID fighterBAccountId,
        UUID fighterATeamId,
        UUID fighterBTeamId,
        UUID nextBanAccountId
) {
    public GladiatorDuelPending {
        Objects.requireNonNull(fighterAAccountId, "fighterAAccountId");
        Objects.requireNonNull(fighterBAccountId, "fighterBAccountId");
        Objects.requireNonNull(fighterATeamId, "fighterATeamId");
        Objects.requireNonNull(fighterBTeamId, "fighterBTeamId");
        Objects.requireNonNull(nextBanAccountId, "nextBanAccountId");
        if (fighterAAccountId.equals(fighterBAccountId)) {
            throw new IllegalArgumentException("Duel fighters must differ");
        }
        if (fighterATeamId.equals(fighterBTeamId)) {
            throw new IllegalArgumentException("Duel teams must differ");
        }
    }
}
