package com.larpsmp.moneyevent.gladiator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * One recorded fight (3v3 or 1v1).
 */
public final class GladiatorFight {

    private final UUID fightId;
    private final GladiatorFightKind kind;
    private final @Nullable Integer boutIndex;
    private final @Nullable UUID teamAId;
    private final @Nullable UUID teamBId;
    private final String kitId;
    private GladiatorFightStatus status;
    private @Nullable UUID winningTeamId;
    private final Instant startedAt;
    private @Nullable Instant endedAt;
    private final Map<UUID, GladiatorFighter> fighters = new LinkedHashMap<>();

    public GladiatorFight(
            UUID fightId,
            GladiatorFightKind kind,
            @Nullable Integer boutIndex,
            @Nullable UUID teamAId,
            @Nullable UUID teamBId,
            String kitId,
            GladiatorFightStatus status,
            @Nullable UUID winningTeamId,
            Instant startedAt,
            @Nullable Instant endedAt
    ) {
        this.fightId = Objects.requireNonNull(fightId, "fightId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.boutIndex = boutIndex;
        this.teamAId = teamAId;
        this.teamBId = teamBId;
        this.kitId = Objects.requireNonNull(kitId, "kitId");
        this.status = Objects.requireNonNull(status, "status");
        this.winningTeamId = winningTeamId;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.endedAt = endedAt;
    }

    public static GladiatorFight startTeamFight(
            UUID fightId,
            int boutIndex,
            UUID teamAId,
            UUID teamBId,
            Instant now,
            List<GladiatorFighter> fighters
    ) {
        GladiatorFight fight = new GladiatorFight(
                fightId,
                GladiatorFightKind.TEAM_3V3,
                boutIndex,
                teamAId,
                teamBId,
                GladiatorRules.TEAM_KIT_ID,
                GladiatorFightStatus.ACTIVE,
                null,
                now,
                null);
        for (GladiatorFighter fighter : fighters) {
            fight.fighters.put(fighter.accountId(), fighter);
        }
        return fight;
    }

    public static GladiatorFight startDuel(
            UUID fightId,
            UUID teamAId,
            UUID teamBId,
            String kitId,
            Instant now,
            GladiatorFighter fighterA,
            GladiatorFighter fighterB
    ) {
        GladiatorFight fight = new GladiatorFight(
                fightId,
                GladiatorFightKind.DUEL_1V1,
                null,
                teamAId,
                teamBId,
                kitId,
                GladiatorFightStatus.ACTIVE,
                null,
                now,
                null);
        fight.fighters.put(fighterA.accountId(), fighterA);
        fight.fighters.put(fighterB.accountId(), fighterB);
        return fight;
    }

    public UUID fightId() {
        return fightId;
    }

    public GladiatorFightKind kind() {
        return kind;
    }

    public @Nullable Integer boutIndex() {
        return boutIndex;
    }

    public @Nullable UUID teamAId() {
        return teamAId;
    }

    public @Nullable UUID teamBId() {
        return teamBId;
    }

    public String kitId() {
        return kitId;
    }

    public GladiatorFightStatus status() {
        return status;
    }

    public @Nullable UUID winningTeamId() {
        return winningTeamId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public @Nullable Instant endedAt() {
        return endedAt;
    }

    public Map<UUID, GladiatorFighter> fighters() {
        return Map.copyOf(fighters);
    }

    public List<GladiatorFighter> fighterList() {
        return List.copyOf(fighters.values());
    }

    public boolean isActive() {
        return status == GladiatorFightStatus.ACTIVE;
    }

    public boolean isFighter(UUID accountId) {
        return fighters.containsKey(accountId);
    }

    public Optional<GladiatorFighter> fighter(UUID accountId) {
        return Optional.ofNullable(fighters.get(accountId));
    }

    public boolean isAliveFighter(UUID accountId) {
        GladiatorFighter fighter = fighters.get(accountId);
        return fighter != null && fighter.alive();
    }

    void putFighter(GladiatorFighter fighter) {
        fighters.put(fighter.accountId(), fighter);
    }

    void loadFighters(List<GladiatorFighter> values) {
        fighters.clear();
        for (GladiatorFighter fighter : values) {
            fighters.put(fighter.accountId(), fighter);
        }
    }

    /**
     * Marks a fighter dead. Returns optional winner if only one team still has living fighters.
     * Empty optional means the fight continues. Null winning team with completed status means draw.
     */
    Optional<CompleteOutcome> markDead(UUID accountId, Instant now) {
        GladiatorFighter fighter = fighters.get(accountId);
        if (fighter == null || !fighter.alive()) {
            return Optional.empty();
        }
        fighter.markDead();
        return evaluateCompletion(now);
    }

    Optional<CompleteOutcome> forceWinner(@Nullable UUID winningTeam, Instant now) {
        if (!isActive()) {
            return Optional.empty();
        }
        this.status = GladiatorFightStatus.COMPLETED;
        this.winningTeamId = winningTeam;
        this.endedAt = now;
        return Optional.of(new CompleteOutcome(winningTeam));
    }

    private Optional<CompleteOutcome> evaluateCompletion(Instant now) {
        Map<UUID, Integer> livingByTeam = new LinkedHashMap<>();
        for (GladiatorFighter fighter : fighters.values()) {
            if (fighter.alive()) {
                livingByTeam.merge(fighter.teamId(), 1, Integer::sum);
            }
        }
        if (livingByTeam.isEmpty()) {
            this.status = GladiatorFightStatus.COMPLETED;
            this.winningTeamId = null;
            this.endedAt = now;
            return Optional.of(new CompleteOutcome(null));
        }
        if (livingByTeam.size() == 1) {
            UUID winner = livingByTeam.keySet().iterator().next();
            this.status = GladiatorFightStatus.COMPLETED;
            this.winningTeamId = winner;
            this.endedAt = now;
            return Optional.of(new CompleteOutcome(winner));
        }
        return Optional.empty();
    }

    record CompleteOutcome(@Nullable UUID winningTeamId) {
    }
}
