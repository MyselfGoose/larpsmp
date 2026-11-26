package com.larpsmp.moneyevent.acehunt;

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
 * Mutable in-memory Ace Hunt session. Persistence is handled by {@link AceHuntService}.
 */
public final class AceHuntSession {

    private final UUID sessionId;
    private AceHuntPhase phase;
    private final Instant createdAt;
    private Instant updatedAt;
    private @Nullable Instant huntStartedAt;
    private @Nullable Instant surfaceStartedAt;
    private @Nullable Instant banStartedAt;
    private @Nullable Instant endedAt;

    /** teamId → ace accountId */
    private final Map<UUID, UUID> acesByTeam = new LinkedHashMap<>();
    /** hunterTeamId → targetAceTeamId */
    private final Map<UUID, UUID> huntAssignments = new LinkedHashMap<>();
    private final List<AceHuntScoredDeath> scoredDeaths = new ArrayList<>();
    private final Map<UUID, AceHuntLockout> lockouts = new LinkedHashMap<>();
    private final Map<UUID, AceHuntLocationSnapshot> snapshots = new LinkedHashMap<>();
    private final Map<UUID, String> spectators = new LinkedHashMap<>();

    public AceHuntSession(
            UUID sessionId,
            AceHuntPhase phase,
            Instant createdAt,
            Instant updatedAt,
            @Nullable Instant huntStartedAt,
            @Nullable Instant surfaceStartedAt,
            @Nullable Instant banStartedAt,
            @Nullable Instant endedAt
    ) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.huntStartedAt = huntStartedAt;
        this.surfaceStartedAt = surfaceStartedAt;
        this.banStartedAt = banStartedAt;
        this.endedAt = endedAt;
    }

    public static AceHuntSession create(UUID sessionId, Instant now) {
        return new AceHuntSession(sessionId, AceHuntPhase.PREP, now, now, null, null, null, null);
    }

    public UUID sessionId() {
        return sessionId;
    }

    public AceHuntPhase phase() {
        return phase;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public @Nullable Instant huntStartedAt() {
        return huntStartedAt;
    }

    public @Nullable Instant surfaceStartedAt() {
        return surfaceStartedAt;
    }

    public @Nullable Instant banStartedAt() {
        return banStartedAt;
    }

    public @Nullable Instant endedAt() {
        return endedAt;
    }

    public Map<UUID, UUID> acesByTeam() {
        return Map.copyOf(acesByTeam);
    }

    public Map<UUID, UUID> huntAssignments() {
        return Map.copyOf(huntAssignments);
    }

    public List<AceHuntScoredDeath> scoredDeaths() {
        return List.copyOf(scoredDeaths);
    }

    public Map<UUID, AceHuntLockout> lockouts() {
        return Map.copyOf(lockouts);
    }

    public Map<UUID, AceHuntLocationSnapshot> snapshots() {
        return Map.copyOf(snapshots);
    }

    public Map<UUID, String> spectators() {
        return Map.copyOf(spectators);
    }

    public boolean isAce(UUID accountId) {
        return acesByTeam.containsValue(accountId);
    }

    public Optional<UUID> aceTeamOf(UUID accountId) {
        for (Map.Entry<UUID, UUID> entry : acesByTeam.entrySet()) {
            if (entry.getValue().equals(accountId)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public Optional<UUID> aceAccountOf(UUID teamId) {
        return Optional.ofNullable(acesByTeam.get(teamId));
    }

    public boolean isSpectator(UUID accountId) {
        return spectators.containsKey(accountId);
    }

    public boolean isParticipant(UUID accountId) {
        return snapshots.containsKey(accountId) || isAce(accountId) || spectators.containsKey(accountId);
    }

    public boolean isLocked(UUID accountId, Instant now) {
        AceHuntLockout lockout = lockouts.get(accountId);
        return lockout != null && lockout.isActive(now);
    }

    public Optional<AceHuntLockout> lockoutOf(UUID accountId) {
        return Optional.ofNullable(lockouts.get(accountId));
    }

    public List<UUID> livingAceTeams() {
        List<UUID> living = new ArrayList<>();
        for (UUID teamId : acesByTeam.keySet()) {
            boolean dead = scoredDeaths.stream().anyMatch(death -> death.aceTeamId().equals(teamId));
            if (!dead) {
                living.add(teamId);
            }
        }
        return living;
    }

    public List<UUID> deathOrderTeams() {
        return scoredDeaths.stream().map(AceHuntScoredDeath::aceTeamId).toList();
    }

    public List<UUID> scoredHuntTeams() {
        return scoredDeaths.stream()
                .filter(AceHuntScoredDeath::huntScored)
                .map(AceHuntScoredDeath::killerTeamId)
                .filter(Objects::nonNull)
                .toList();
    }

    public boolean allAcesDead() {
        return !acesByTeam.isEmpty() && livingAceTeams().isEmpty();
    }

    void markUpdated(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    void setAce(UUID teamId, UUID accountId) {
        acesByTeam.put(teamId, accountId);
    }

    void clearAce(UUID teamId) {
        acesByTeam.remove(teamId);
    }

    void setHuntAssignment(UUID hunterTeamId, UUID targetTeamId) {
        huntAssignments.put(hunterTeamId, targetTeamId);
    }

    void clearHuntAssignments() {
        huntAssignments.clear();
    }

    void putSnapshot(AceHuntLocationSnapshot snapshot) {
        snapshots.put(snapshot.accountId(), snapshot);
    }

    void putLockout(AceHuntLockout lockout) {
        lockouts.put(lockout.accountId(), lockout);
    }

    void removeLockout(UUID accountId) {
        lockouts.remove(accountId);
    }

    void addSpectator(UUID accountId, String reason) {
        spectators.put(accountId, reason);
        lockouts.remove(accountId);
    }

    void removeSpectator(UUID accountId) {
        spectators.remove(accountId);
    }

    boolean tryAddScoredDeath(AceHuntScoredDeath death) {
        for (AceHuntScoredDeath existing : scoredDeaths) {
            if (existing.aceTeamId().equals(death.aceTeamId())
                    || existing.deathId().equals(death.deathId())) {
                return false;
            }
        }
        scoredDeaths.add(death);
        return true;
    }

    void beginHunt(Instant now) {
        this.phase = AceHuntPhase.HUNT;
        this.huntStartedAt = now;
        markUpdated(now);
    }

    void beginSurface(Instant now) {
        this.phase = AceHuntPhase.SURFACE;
        this.surfaceStartedAt = now;
        markUpdated(now);
    }

    void beginBan(Instant now) {
        this.phase = AceHuntPhase.BAN;
        this.banStartedAt = now;
        markUpdated(now);
    }

    void end(Instant now) {
        this.phase = AceHuntPhase.ENDED;
        this.endedAt = now;
        markUpdated(now);
    }

    void loadAces(Map<UUID, UUID> aces) {
        acesByTeam.clear();
        acesByTeam.putAll(aces);
    }

    void loadAssignments(Map<UUID, UUID> assignments) {
        huntAssignments.clear();
        huntAssignments.putAll(assignments);
    }

    void loadScoredDeaths(List<AceHuntScoredDeath> deaths) {
        scoredDeaths.clear();
        scoredDeaths.addAll(deaths);
    }

    void loadLockouts(Map<UUID, AceHuntLockout> values) {
        lockouts.clear();
        lockouts.putAll(values);
    }

    void loadSnapshots(Map<UUID, AceHuntLocationSnapshot> values) {
        snapshots.clear();
        snapshots.putAll(values);
    }

    void loadSpectators(Map<UUID, String> values) {
        spectators.clear();
        spectators.putAll(values);
    }
}
