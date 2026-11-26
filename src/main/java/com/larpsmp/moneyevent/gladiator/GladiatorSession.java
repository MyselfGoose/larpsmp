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
 * Mutable in-memory Gladiators session. Persistence is handled by {@link GladiatorService}.
 */
public final class GladiatorSession {

    private final UUID sessionId;
    private GladiatorPhase phase;
    private int scheduleIndex;
    private boolean payoutCompleted;
    private final Instant createdAt;
    private Instant updatedAt;
    private @Nullable Instant endedAt;

    private final List<GladiatorScheduledEntry> schedule = new ArrayList<>();
    private final List<GladiatorFight> fights = new ArrayList<>();
    private final Map<UUID, Map<Integer, UUID>> lineups = new LinkedHashMap<>();
    private final Map<UUID, Integer> quota = new LinkedHashMap<>();
    private final List<String> kitBans = new ArrayList<>();
    private @Nullable GladiatorDuelPending duelPending;
    private @Nullable GladiatorFight activeFight;
    private final Map<UUID, GladiatorBodySnapshot> bodySnapshots = new LinkedHashMap<>();

    public GladiatorSession(
            UUID sessionId,
            GladiatorPhase phase,
            int scheduleIndex,
            boolean payoutCompleted,
            Instant createdAt,
            Instant updatedAt,
            @Nullable Instant endedAt
    ) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.scheduleIndex = Math.max(0, scheduleIndex);
        this.payoutCompleted = payoutCompleted;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.endedAt = endedAt;
    }

    public static GladiatorSession create(UUID sessionId, Instant now) {
        return new GladiatorSession(sessionId, GladiatorPhase.OPEN, 0, false, now, now, null);
    }

    public UUID sessionId() {
        return sessionId;
    }

    public GladiatorPhase phase() {
        return phase;
    }

    public int scheduleIndex() {
        return scheduleIndex;
    }

    public boolean payoutCompleted() {
        return payoutCompleted;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public @Nullable Instant endedAt() {
        return endedAt;
    }

    public List<GladiatorScheduledEntry> schedule() {
        return List.copyOf(schedule);
    }

    public List<GladiatorFight> fights() {
        return List.copyOf(fights);
    }

    public Map<UUID, Map<Integer, UUID>> lineups() {
        Map<UUID, Map<Integer, UUID>> copy = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<Integer, UUID>> entry : lineups.entrySet()) {
            copy.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    public Map<UUID, Integer> quota() {
        return Map.copyOf(quota);
    }

    public List<String> kitBans() {
        return List.copyOf(kitBans);
    }

    public Optional<GladiatorDuelPending> duelPending() {
        return Optional.ofNullable(duelPending);
    }

    public Optional<GladiatorFight> activeFight() {
        return Optional.ofNullable(activeFight);
    }

    public Map<UUID, GladiatorBodySnapshot> bodySnapshots() {
        return Map.copyOf(bodySnapshots);
    }

    public Optional<GladiatorBodySnapshot> bodySnapshot(UUID accountId) {
        return Optional.ofNullable(bodySnapshots.get(accountId));
    }

    public int fightsUsed(UUID accountId) {
        return quota.getOrDefault(accountId, 0);
    }

    public boolean hasQuotaRemaining(UUID accountId) {
        return fightsUsed(accountId) < GladiatorRules.MAX_FIGHTS;
    }

    public Map<UUID, Integer> pointsByTeam() {
        Map<UUID, Integer> points = new LinkedHashMap<>();
        for (GladiatorScheduledEntry entry : schedule) {
            points.putIfAbsent(entry.teamAId(), 0);
            points.putIfAbsent(entry.teamBId(), 0);
        }
        for (GladiatorFight fight : fights) {
            if (fight.status() == GladiatorFightStatus.COMPLETED && fight.winningTeamId() != null) {
                points.merge(fight.winningTeamId(), 1, Integer::sum);
            }
            if (fight.teamAId() != null) {
                points.putIfAbsent(fight.teamAId(), 0);
            }
            if (fight.teamBId() != null) {
                points.putIfAbsent(fight.teamBId(), 0);
            }
        }
        return points;
    }

    public boolean scheduleFinished() {
        if (schedule.isEmpty()) {
            return true;
        }
        for (GladiatorScheduledEntry entry : schedule) {
            if (entry.status() == GladiatorScheduleStatus.PENDING) {
                return false;
            }
        }
        return true;
    }

    public Optional<GladiatorScheduledEntry> currentPendingBout() {
        for (GladiatorScheduledEntry entry : schedule) {
            if (entry.status() == GladiatorScheduleStatus.PENDING) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public Optional<Map<Integer, UUID>> lineupOf(UUID teamId) {
        Map<Integer, UUID> lineup = lineups.get(teamId);
        if (lineup == null || lineup.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Map.copyOf(lineup));
    }

    public boolean isAliveFighter(UUID accountId) {
        return activeFight != null && activeFight.isAliveFighter(accountId);
    }

    public boolean isFighter(UUID accountId) {
        return activeFight != null && activeFight.isFighter(accountId);
    }

    public boolean isParticipantSnapshot(UUID accountId) {
        return bodySnapshots.containsKey(accountId);
    }

    void touch(Instant now) {
        this.updatedAt = now;
    }

    void setPhase(GladiatorPhase phase, Instant now) {
        this.phase = Objects.requireNonNull(phase, "phase");
        touch(now);
    }

    void setScheduleIndex(int index) {
        this.scheduleIndex = Math.max(0, index);
    }

    void markPayoutCompleted() {
        this.payoutCompleted = true;
    }

    void end(Instant now) {
        this.phase = GladiatorPhase.ENDED;
        this.endedAt = now;
        this.activeFight = null;
        this.duelPending = null;
        touch(now);
    }

    void replaceSchedule(List<GladiatorScheduledEntry> entries) {
        schedule.clear();
        schedule.addAll(entries);
        scheduleIndex = 0;
    }

    void setLineup(UUID teamId, Map<Integer, UUID> slots) {
        lineups.put(teamId, new LinkedHashMap<>(slots));
    }

    void clearLineup(UUID teamId) {
        lineups.remove(teamId);
    }

    void clearAllLineups() {
        lineups.clear();
    }

    void setQuota(UUID accountId, int used) {
        quota.put(accountId, Math.max(0, used));
    }

    void incrementQuota(UUID accountId) {
        quota.merge(accountId, 1, Integer::sum);
    }

    void putBodySnapshot(GladiatorBodySnapshot snapshot) {
        bodySnapshots.put(snapshot.accountId(), snapshot);
    }

    void setActiveFight(@Nullable GladiatorFight fight) {
        this.activeFight = fight;
        if (fight != null && fights.stream().noneMatch(existing -> existing.fightId().equals(fight.fightId()))) {
            fights.add(fight);
        }
    }

    void addFight(GladiatorFight fight) {
        fights.add(fight);
    }

    void setDuelPending(@Nullable GladiatorDuelPending pending) {
        this.duelPending = pending;
    }

    void replaceKitBans(List<String> bans) {
        kitBans.clear();
        kitBans.addAll(bans);
    }

    void addKitBan(String kitId) {
        kitBans.add(kitId);
    }

    void clearKitBans() {
        kitBans.clear();
    }

    void loadSchedule(List<GladiatorScheduledEntry> entries) {
        schedule.clear();
        schedule.addAll(entries);
    }

    void loadFights(List<GladiatorFight> values) {
        fights.clear();
        fights.addAll(values);
        activeFight = values.stream()
                .filter(GladiatorFight::isActive)
                .findFirst()
                .orElse(null);
    }

    void loadLineups(Map<UUID, Map<Integer, UUID>> values) {
        lineups.clear();
        for (Map.Entry<UUID, Map<Integer, UUID>> entry : values.entrySet()) {
            lineups.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
    }

    void loadQuota(Map<UUID, Integer> values) {
        quota.clear();
        quota.putAll(values);
    }

    void loadKitBans(List<String> bans) {
        kitBans.clear();
        kitBans.addAll(bans);
    }

    void loadBodySnapshots(Map<UUID, GladiatorBodySnapshot> values) {
        bodySnapshots.clear();
        bodySnapshots.putAll(values);
    }
}
