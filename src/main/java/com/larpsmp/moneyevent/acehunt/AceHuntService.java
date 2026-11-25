package com.larpsmp.moneyevent.acehunt;

import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamMember;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyService;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Ace Hunt lifecycle: nomination, prep, hunt phases, scoring, restore.
 */
public final class AceHuntService {

    private final Object lock = new Object();
    private final JdbcAceHuntRepository repository;
    private final MoneyService money;
    private final TeamService teams;
    private final Clock clock;
    private final Consumer<String> errorLogger;
    private AceHuntWorldActions world = AceHuntWorldActions.NOOP;

    private AceHuntConfig config;
    private @Nullable AceHuntSession activeSession;

    /** Pending nominations before prep (teamId → accountId). */
    private final Map<UUID, UUID> pendingAces = new LinkedHashMap<>();
    /** Pending hunt assignments before prep (hunterTeamId → targetTeamId). */
    private final Map<UUID, UUID> pendingAssignments = new LinkedHashMap<>();

    public AceHuntService(
            AceHuntConfig config,
            JdbcAceHuntRepository repository,
            MoneyService money,
            TeamService teams,
            Consumer<String> errorLogger
    ) {
        this(config, repository, money, teams, Clock.systemUTC(), errorLogger);
    }

    public AceHuntService(
            AceHuntConfig config,
            JdbcAceHuntRepository repository,
            MoneyService money,
            TeamService teams,
            Clock clock,
            Consumer<String> errorLogger
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.money = Objects.requireNonNull(money, "money");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    public void setWorldActions(AceHuntWorldActions world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    public AceHuntConfig config() {
        return config;
    }

    public void replaceConfig(AceHuntConfig updated) {
        synchronized (lock) {
            this.config = Objects.requireNonNull(updated, "updated");
        }
    }

    public Optional<AceHuntSession> activeSession() {
        synchronized (lock) {
            return Optional.ofNullable(activeSession);
        }
    }

    public Map<UUID, UUID> pendingAces() {
        synchronized (lock) {
            return Map.copyOf(pendingAces);
        }
    }

    public Map<UUID, UUID> pendingAssignments() {
        synchronized (lock) {
            return Map.copyOf(pendingAssignments);
        }
    }

    public void restoreFromDatabase() {
        synchronized (lock) {
            try {
                Optional<UUID> activeId = repository.loadActiveSessionId();
                if (activeId.isEmpty()) {
                    activeSession = null;
                    return;
                }
                Optional<AceHuntSession> session = repository.findSession(activeId.get());
                if (session.isEmpty() || session.get().phase() == AceHuntPhase.ENDED
                        || session.get().phase() == AceHuntPhase.IDLE) {
                    repository.setRuntime(null, clock.instant());
                    activeSession = null;
                    return;
                }
                activeSession = session.get();
                reapplyWorldState(activeSession);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to restore Ace Hunt: " + exception.getMessage());
                activeSession = null;
            }
        }
    }

    public AdminResult nominateAce(UUID teamId, UUID accountId) {
        synchronized (lock) {
            Optional<Team> team = teams.getTeam(teamId);
            if (team.isEmpty()) {
                return AdminResult.error("Unknown team.");
            }
            Optional<Team> memberTeam = teams.teamOf(accountId);
            if (memberTeam.isEmpty() || !memberTeam.get().id().equals(teamId)) {
                return AdminResult.error("Player is not on that team.");
            }
            if (activeSession != null && activeSession.phase().isLive()) {
                return AdminResult.error("Cannot change aces during a live Ace Hunt.");
            }
            pendingAces.put(teamId, accountId);
            return AdminResult.ok("Nominated ace for " + team.get().displayName() + ".");
        }
    }

    public AdminResult clearAce(UUID teamId) {
        synchronized (lock) {
            if (activeSession != null && activeSession.phase().isLive()) {
                return AdminResult.error("Cannot clear aces during a live Ace Hunt.");
            }
            pendingAces.remove(teamId);
            return AdminResult.ok("Cleared ace nomination.");
        }
    }

    public AdminResult setHuntAssignment(UUID hunterTeamId, UUID targetTeamId) {
        synchronized (lock) {
            if (hunterTeamId.equals(targetTeamId)) {
                return AdminResult.error("A team cannot hunt itself.");
            }
            if (teams.getTeam(hunterTeamId).isEmpty() || teams.getTeam(targetTeamId).isEmpty()) {
                return AdminResult.error("Unknown team.");
            }
            if (activeSession != null && activeSession.phase().isLive()) {
                return AdminResult.error("Cannot change hunt assignments during a live Ace Hunt.");
            }
            // Ensure unique targets: remove any other hunter targeting this ace.
            pendingAssignments.entrySet().removeIf(entry ->
                    entry.getValue().equals(targetTeamId) && !entry.getKey().equals(hunterTeamId));
            pendingAssignments.put(hunterTeamId, targetTeamId);
            return AdminResult.ok("Hunt assignment set.");
        }
    }

    public AdminResult cycleAssignments() {
        synchronized (lock) {
            if (activeSession != null && activeSession.phase().isLive()) {
                return AdminResult.error("Cannot cycle assignments during a live Ace Hunt.");
            }
            List<Team> ordered;
            if (pendingAces.size() >= 2) {
                ordered = pendingAces.keySet().stream()
                        .map(teams::getTeam)
                        .flatMap(Optional::stream)
                        .sorted(Comparator.comparingInt(Team::sortOrder).thenComparing(Team::slug))
                        .toList();
            } else {
                ordered = teams.listTeams().stream()
                        .sorted(Comparator.comparingInt(Team::sortOrder).thenComparing(Team::slug))
                        .toList();
            }
            if (ordered.size() < 2) {
                return AdminResult.error("Need at least two teams (nominate aces first) to cycle hunts.");
            }
            pendingAssignments.clear();
            for (int i = 0; i < ordered.size(); i++) {
                Team hunter = ordered.get(i);
                Team target = ordered.get((i + 1) % ordered.size());
                pendingAssignments.put(hunter.id(), target.id());
            }
            return AdminResult.ok("Cycled hunt assignments for " + ordered.size() + " teams.");
        }
    }

    public AdminResult startPrep() {
        synchronized (lock) {
            if (activeSession != null && activeSession.phase().isLive()) {
                return AdminResult.error("Ace Hunt is already active.");
            }
            if (pendingAces.size() < 2) {
                return AdminResult.error("Nominate aces for at least two teams before prep.");
            }
            List<Team> participating = new ArrayList<>();
            for (UUID teamId : pendingAces.keySet()) {
                Optional<Team> team = teams.getTeam(teamId);
                if (team.isEmpty()) {
                    return AdminResult.error("Nominated team no longer exists: " + teamId);
                }
                participating.add(team.get());
            }
            for (Team team : participating) {
                Optional<AceHuntTerritory> territory = config.territoryByTeamId(team.id())
                        .or(() -> config.territoryBySlug(team.slug()));
                if (territory.isEmpty() || !territory.get().isReadyForPrep()) {
                    return AdminResult.error("Territory for " + team.displayName()
                            + " is incomplete (region, ace spawn, surface-y, max-legal-y).");
                }
            }
            if (pendingAssignments.size() != participating.size()) {
                return AdminResult.error("Hunt assignments incomplete. Use /larp acehunt cycle or hunt.");
            }
            for (Team team : participating) {
                UUID target = pendingAssignments.get(team.id());
                if (target == null) {
                    return AdminResult.error("Missing hunt target for " + team.displayName() + ".");
                }
                if (target.equals(team.id())) {
                    return AdminResult.error(team.displayName() + " cannot hunt itself.");
                }
                if (!pendingAces.containsKey(target)) {
                    return AdminResult.error("Hunt target has no ace for " + team.displayName() + ".");
                }
            }

            Instant now = clock.instant();
            AceHuntSession session = AceHuntSession.create(UUID.randomUUID(), now);
            for (Map.Entry<UUID, UUID> entry : pendingAces.entrySet()) {
                session.setAce(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<UUID, UUID> entry : pendingAssignments.entrySet()) {
                if (pendingAces.containsKey(entry.getKey())) {
                    session.setHuntAssignment(entry.getKey(), entry.getValue());
                }
            }

            try {
                repository.upsertSession(session);
                repository.replaceAces(session.sessionId(), session.acesByTeam());
                repository.replaceAssignments(session.sessionId(), session.huntAssignments());
                repository.setRuntime(session.sessionId(), now);
            } catch (SQLException exception) {
                return AdminResult.error("Could not persist Ace Hunt session: " + exception.getMessage());
            }

            // Kill money off for aces during prep; hunters join at begin.
            try {
                money.setKillMoneyEvent(AceHuntRules.EVENT_ID, false, "system", "ACE_HUNT", "Ace Hunt prep");
                money.clearKillMoneyEventParticipants(AceHuntRules.EVENT_ID);
                for (UUID aceAccountId : session.acesByTeam().values()) {
                    money.addKillMoneyEventParticipant(AceHuntRules.EVENT_ID, aceAccountId);
                }
            } catch (IOException exception) {
                errorLogger.accept("Kill money prep setup failed: " + exception.getMessage());
            }

            for (Map.Entry<UUID, UUID> entry : session.acesByTeam().entrySet()) {
                UUID teamId = entry.getKey();
                UUID accountId = entry.getValue();
                AceHuntTerritory territory = requireTerritory(teamId);
                AceHuntLocationSnapshot snapshot = world.captureLocation(
                        accountId, AceHuntLocationSnapshot.ParticipantRole.ACE);
                if (snapshot != null) {
                    session.putSnapshot(snapshot);
                    try {
                        repository.upsertSnapshot(session.sessionId(), snapshot);
                    } catch (SQLException exception) {
                        errorLogger.accept("Snapshot save failed: " + exception.getMessage());
                    }
                }
                world.teleportToSpawn(accountId, territory.aceSpawn());
                world.applyTerritoryBorder(accountId, territory);
            }

            activeSession = session;
            world.broadcast(config.messages().prepStarted());
            return AdminResult.ok("Ace Hunt prep started.");
        }
    }

    public AdminResult beginHunt() {
        synchronized (lock) {
            if (activeSession == null || activeSession.phase() != AceHuntPhase.PREP) {
                return AdminResult.error("Ace Hunt must be in PREP to begin.");
            }
            for (UUID teamId : activeSession.acesByTeam().keySet()) {
                Optional<Team> team = teams.getTeam(teamId);
                Optional<AceHuntTerritory> territory = resolveTerritory(teamId);
                if (territory.isEmpty() || !territory.get().isReadyForHunt()) {
                    String name = team.map(Team::displayName).orElse(teamId.toString());
                    return AdminResult.error("Territory for " + name
                            + " needs a hunter spawn before begin.");
                }
            }

            AceHuntSession session = activeSession;
            Instant now = clock.instant();
            session.beginHunt(now);
            persistSession(session);

            try {
                money.clearKillMoneyEventParticipants(AceHuntRules.EVENT_ID);
                for (UUID aceAccountId : session.acesByTeam().values()) {
                    money.addKillMoneyEventParticipant(AceHuntRules.EVENT_ID, aceAccountId);
                }
            } catch (IOException exception) {
                errorLogger.accept("Kill money hunter setup failed: " + exception.getMessage());
            }

            for (Map.Entry<UUID, UUID> assignment : session.huntAssignments().entrySet()) {
                UUID hunterTeamId = assignment.getKey();
                UUID targetTeamId = assignment.getValue();
                AceHuntTerritory targetTerritory = requireTerritory(targetTeamId);
                for (TeamMember member : teams.membersOf(hunterTeamId)) {
                    UUID accountId = member.accountId();
                    if (session.isAce(accountId)) {
                        continue;
                    }
                    AceHuntLocationSnapshot snapshot = world.captureLocation(
                            accountId, AceHuntLocationSnapshot.ParticipantRole.HUNTER);
                    if (snapshot != null) {
                        session.putSnapshot(snapshot);
                        try {
                            repository.upsertSnapshot(session.sessionId(), snapshot);
                        } catch (SQLException exception) {
                            errorLogger.accept("Hunter snapshot failed: " + exception.getMessage());
                        }
                    }
                    try {
                        money.addKillMoneyEventParticipant(AceHuntRules.EVENT_ID, accountId);
                    } catch (IOException exception) {
                        errorLogger.accept("Kill money add hunter failed: " + exception.getMessage());
                    }
                    world.teleportToSpawn(accountId, targetTerritory.hunterSpawn());
                    world.applyTerritoryBorder(accountId, targetTerritory);
                }
            }

            world.broadcast(config.messages().huntStarted());
            return AdminResult.ok("Ace Hunt begun.");
        }
    }

    public AdminResult stop() {
        synchronized (lock) {
            if (activeSession == null) {
                return AdminResult.error("No active Ace Hunt.");
            }
            endSession(activeSession, clock.instant(), true);
            return AdminResult.ok("Ace Hunt stopped.");
        }
    }

    /**
     * Called once per second from the tick task.
     */
    public void tick() {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().isLive()) {
                return;
            }
            Instant now = clock.instant();
            AceHuntSession session = activeSession;

            if (session.phase() == AceHuntPhase.HUNT && session.huntStartedAt() != null) {
                if (!now.isBefore(session.huntStartedAt().plus(config.huntDuration()))) {
                    session.beginSurface(now);
                    persistSession(session);
                    world.broadcast(config.messages().surfaceWarning()
                            .replace("{seconds}", String.valueOf(config.surfaceWarning().toSeconds())));
                }
            } else if (session.phase() == AceHuntPhase.SURFACE && session.surfaceStartedAt() != null) {
                if (!now.isBefore(session.surfaceStartedAt().plus(config.surfaceWarning()))) {
                    session.beginBan(now);
                    persistSession(session);
                    world.broadcast(config.messages().undergroundBan());
                }
            }

            releaseExpiredLockouts(session, now);
            applyUndergroundBanDamage(session, now);

            if (session.phase().isScoring() && session.allAcesDead()) {
                endSession(session, now, true);
            }
        }
    }

    /**
     * Handle a participant death. Attribution may supply killer account/team.
     */
    public DeathHandlingResult handleDeath(
            UUID victimAccountId,
            @Nullable String deathId,
            @Nullable UUID killerAccountId
    ) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().isLive()) {
                return DeathHandlingResult.ignored();
            }
            AceHuntSession session = activeSession;
            Instant now = clock.instant();

            if (session.isSpectator(victimAccountId)) {
                return DeathHandlingResult.ignored();
            }

            Optional<UUID> aceTeam = session.aceTeamOf(victimAccountId);
            if (aceTeam.isPresent()) {
                if (!session.phase().isScoring()) {
                    // Prep: ace keeps inventory and respawns in territory — no score/spectate.
                    AceHuntTerritory territory = requireTerritory(aceTeam.get());
                    return DeathHandlingResult.acePrepRespawn(territory);
                }
                return handleAceDeath(session, aceTeam.get(), victimAccountId, deathId, killerAccountId, now);
            }

            if (!session.phase().isScoring()) {
                return DeathHandlingResult.ignored();
            }
            if (!session.isParticipant(victimAccountId) && session.snapshots().get(victimAccountId) == null) {
                // Hunters are participants once snapshotted at begin.
                return DeathHandlingResult.ignored();
            }
            if (session.isSpectator(victimAccountId)) {
                return DeathHandlingResult.ignored();
            }

            // Non-ace hunter death → lockup.
            Optional<Team> team = teams.teamOf(victimAccountId);
            if (team.isEmpty()) {
                return DeathHandlingResult.keepInventoryOnly();
            }
            UUID targetTeamId = session.huntAssignments().get(team.get().id());
            if (targetTeamId == null) {
                return DeathHandlingResult.keepInventoryOnly();
            }
            // If their target ace is already dead, they should already be spectators.
            if (session.scoredDeaths().stream().anyMatch(d -> d.aceTeamId().equals(targetTeamId))) {
                makeSpectator(session, victimAccountId, "target-already-dead");
                return DeathHandlingResult.spectator();
            }

            AceHuntLockout lockout = new AceHuntLockout(
                    victimAccountId,
                    now.plus(config.respawnLock()),
                    targetTeamId
            );
            session.putLockout(lockout);
            try {
                repository.upsertLockout(session.sessionId(), lockout);
            } catch (SQLException exception) {
                errorLogger.accept("Lockout persist failed: " + exception.getMessage());
            }
            String message = config.messages().lockupEnter()
                    .replace("{seconds}", String.valueOf(config.respawnLock().toSeconds()));
            world.send(victimAccountId, message);
            return DeathHandlingResult.lockup(config.lockupSpawn());
        }
    }

    public boolean isEventCombat(UUID victimAccountId, UUID killerAccountId) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().isLive()) {
                return false;
            }
            return activeSession.isParticipant(victimAccountId)
                    || activeSession.isAce(victimAccountId)
                    || activeSession.isParticipant(killerAccountId)
                    || activeSession.isAce(killerAccountId)
                    || activeSession.snapshots().containsKey(victimAccountId)
                    || activeSession.snapshots().containsKey(killerAccountId);
        }
    }

    public boolean shouldKeepInventory(UUID accountId) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().allowsKeepInventory()) {
                return false;
            }
            return activeSession.isAce(accountId)
                    || activeSession.snapshots().containsKey(accountId)
                    || activeSession.isParticipant(accountId);
        }
    }

    public Optional<AceHuntTerritory> confinementTerritory(UUID accountId) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().isLive()) {
                return Optional.empty();
            }
            if (activeSession.isSpectator(accountId)) {
                return Optional.empty();
            }
            if (activeSession.isLocked(accountId, clock.instant())) {
                return Optional.empty();
            }
            Optional<UUID> aceTeam = activeSession.aceTeamOf(accountId);
            if (aceTeam.isPresent()) {
                return resolveTerritory(aceTeam.get());
            }
            Optional<Team> team = teams.teamOf(accountId);
            if (team.isEmpty()) {
                return Optional.empty();
            }
            UUID target = activeSession.huntAssignments().get(team.get().id());
            if (target == null) {
                return Optional.empty();
            }
            if (!activeSession.snapshots().containsKey(accountId)
                    && activeSession.phase() == AceHuntPhase.PREP) {
                return Optional.empty();
            }
            return resolveTerritory(target);
        }
    }

    public boolean isInLockup(UUID accountId) {
        synchronized (lock) {
            return activeSession != null && activeSession.isLocked(accountId, clock.instant());
        }
    }

    public boolean isSpectator(UUID accountId) {
        synchronized (lock) {
            return activeSession != null && activeSession.isSpectator(accountId);
        }
    }

    public boolean isUndergroundBanned(UUID accountId, String worldName, double y) {
        synchronized (lock) {
            if (activeSession == null || activeSession.phase() != AceHuntPhase.BAN) {
                return false;
            }
            if (activeSession.isSpectator(accountId) || activeSession.isLocked(accountId, clock.instant())) {
                return false;
            }
            Optional<AceHuntTerritory> territory = confinementTerritory(accountId);
            if (territory.isEmpty() || territory.get().surfaceY() == null) {
                return false;
            }
            AceHuntTerritory t = territory.get();
            if (!t.region().isDefined() || t.region().world() == null) {
                return false;
            }
            if (!t.region().world().equalsIgnoreCase(worldName)) {
                return false;
            }
            return y < t.surfaceY();
        }
    }

    public AceHuntScoreboard scoreboard() {
        synchronized (lock) {
            if (activeSession == null) {
                return new AceHuntScoreboard(Map.of(), Map.of(), Map.of(), Map.of());
            }
            return AceHuntScoreboard.from(activeSession);
        }
    }

    public void onPlayerJoin(UUID accountId) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().isLive()) {
                return;
            }
            AceHuntSession session = activeSession;
            if (session.isSpectator(accountId)) {
                world.setSpectator(accountId);
                return;
            }
            if (session.isLocked(accountId, clock.instant())) {
                world.teleportToSpawn(accountId, config.lockupSpawn());
                return;
            }
            confinementTerritory(accountId).ifPresent(territory -> {
                world.applyTerritoryBorder(accountId, territory);
            });
        }
    }

    private DeathHandlingResult handleAceDeath(
            AceHuntSession session,
            UUID aceTeamId,
            UUID aceAccountId,
            @Nullable String deathId,
            @Nullable UUID killerAccountId,
            Instant now
    ) {
        String resolvedDeathId = deathId == null ? "ace-" + UUID.randomUUID() : deathId;
        UUID killerTeamId = null;
        if (killerAccountId != null) {
            killerTeamId = teams.teamOf(killerAccountId).map(Team::id).orElse(null);
        }
        boolean huntScored = AceHuntScoring.isAssignedHunterKill(
                aceTeamId, killerTeamId, session.huntAssignments());
        int deathOrder = session.scoredDeaths().size() + 1;
        AceHuntScoredDeath scored = new AceHuntScoredDeath(
                aceTeamId,
                aceAccountId,
                resolvedDeathId,
                killerAccountId,
                killerTeamId,
                huntScored,
                deathOrder,
                now
        );
        if (!session.tryAddScoredDeath(scored)) {
            return DeathHandlingResult.ignored();
        }
        try {
            if (!repository.insertScoredDeath(session.sessionId(), scored)) {
                // Concurrent duplicate — roll back memory if somehow raced.
                return DeathHandlingResult.ignored();
            }
            repository.markAceDead(session.sessionId(), aceTeamId, deathOrder);
        } catch (SQLException exception) {
            errorLogger.accept("Ace death persist failed: " + exception.getMessage());
        }

        makeSpectator(session, aceAccountId, "ace-death");

        String aceName = teams.teamOf(aceAccountId).map(Team::displayName).orElse("Ace");
        Optional<Team> aceTeam = teams.getTeam(aceTeamId);
        String teamName = aceTeam.map(Team::displayName).orElse("Team");
        world.broadcast(config.messages().aceDied()
                .replace("{ace}", aceName)
                .replace("{team}", teamName));

        if (huntScored && killerTeamId != null) {
            world.broadcast(config.messages().huntScored()
                    .replace("{hunter}", teams.getTeam(killerTeamId).map(Team::displayName).orElse("Hunters"))
                    .replace("{ace}", teamName));
            // Successful hunters (non-ace members of killer team) → spectator.
            for (TeamMember member : teams.membersOf(killerTeamId)) {
                if (session.isAce(member.accountId())) {
                    continue;
                }
                makeSpectator(session, member.accountId(), "successful-hunt");
            }
        } else {
            world.broadcast(config.messages().huntMissed().replace("{ace}", teamName));
            // Still spectate the assigned hunting team's non-ace members? Docs: "When a team kills
            // the ace they are hunting, those hunters go to spectator". Only on successful assigned kill.
            // If someone else killed the ace, assigned hunters keep hunting? Their target is dead —
            // they should become spectators too since there's nothing left to hunt.
            for (Map.Entry<UUID, UUID> assignment : session.huntAssignments().entrySet()) {
                if (!assignment.getValue().equals(aceTeamId)) {
                    continue;
                }
                for (TeamMember member : teams.membersOf(assignment.getKey())) {
                    if (session.isAce(member.accountId())) {
                        continue;
                    }
                    makeSpectator(session, member.accountId(), "target-dead");
                }
            }
        }

        if (session.allAcesDead()) {
            endSession(session, now, true);
        }
        return DeathHandlingResult.spectator();
    }

    private void makeSpectator(AceHuntSession session, UUID accountId, String reason) {
        session.addSpectator(accountId, reason);
        try {
            repository.upsertSpectator(session.sessionId(), accountId, reason);
            repository.deleteLockout(session.sessionId(), accountId);
        } catch (SQLException exception) {
            errorLogger.accept("Spectator persist failed: " + exception.getMessage());
        }
        world.clearBorder(accountId);
        world.setSpectator(accountId);
    }

    private void releaseExpiredLockouts(AceHuntSession session, Instant now) {
        List<UUID> released = new ArrayList<>();
        for (AceHuntLockout lockout : session.lockouts().values()) {
            if (!lockout.isActive(now)) {
                released.add(lockout.accountId());
            }
        }
        for (UUID accountId : released) {
            AceHuntLockout lockout = session.lockouts().get(accountId);
            session.removeLockout(accountId);
            try {
                repository.deleteLockout(session.sessionId(), accountId);
            } catch (SQLException exception) {
                errorLogger.accept("Lockout delete failed: " + exception.getMessage());
            }
            if (session.isSpectator(accountId)) {
                continue;
            }
            AceHuntTerritory territory = requireTerritory(lockout.hunterSpawnTeamId());
            world.teleportToSpawn(accountId, territory.hunterSpawn());
            world.applyTerritoryBorder(accountId, territory);
            world.send(accountId, config.messages().lockupRelease());
        }
    }

    private void applyUndergroundBanDamage(AceHuntSession session, Instant now) {
        if (session.phase() != AceHuntPhase.BAN) {
            return;
        }
        // Damage is applied by world actions for each confined participant reported underground
        // by the tick task / listener; service exposes the check via isUndergroundBanned.
        // The tick task calls damageForUnderground for online players.
    }

    public void damageIfUnderground(UUID accountId, String worldName, double y) {
        synchronized (lock) {
            if (isUndergroundBanned(accountId, worldName, y)) {
                world.damage(accountId, config.undergroundDamagePerSecond());
            }
        }
    }

    private void endSession(AceHuntSession session, Instant now, boolean broadcast) {
        session.end(now);
        persistSession(session);
        try {
            repository.setRuntime(null, now);
        } catch (SQLException exception) {
            errorLogger.accept("Clear Ace Hunt runtime failed: " + exception.getMessage());
        }

        for (UUID accountId : new ArrayList<>(session.spectators().keySet())) {
            world.setSurvival(accountId);
            world.correctOfflineGameMode(accountId, "SURVIVAL");
        }
        for (UUID accountId : session.snapshots().keySet()) {
            world.clearBorder(accountId);
            AceHuntLocationSnapshot snapshot = session.snapshots().get(accountId);
            if (snapshot != null) {
                world.teleportToSnapshot(accountId, snapshot);
            }
            world.setSurvival(accountId);
        }
        for (UUID aceAccountId : session.acesByTeam().values()) {
            world.clearBorder(aceAccountId);
            world.setSurvival(aceAccountId);
            AceHuntLocationSnapshot snapshot = session.snapshots().get(aceAccountId);
            if (snapshot != null) {
                world.teleportToSnapshot(aceAccountId, snapshot);
            }
        }

        try {
            money.clearKillMoneyEventParticipants(AceHuntRules.EVENT_ID);
        } catch (IOException exception) {
            errorLogger.accept("Clear kill money participants failed: " + exception.getMessage());
        }

        if (broadcast) {
            AceHuntScoreboard board = AceHuntScoreboard.from(session);
            StringBuilder summary = new StringBuilder(config.messages().eventEnded());
            for (Map.Entry<UUID, Integer> entry : board.totals().entrySet()) {
                String name = teams.getTeam(entry.getKey()).map(Team::displayName).orElse("?");
                summary.append(' ').append(name).append('=').append(entry.getValue());
            }
            world.broadcast(summary.toString());
        }
        activeSession = null;
    }

    private void reapplyWorldState(AceHuntSession session) {
        for (UUID accountId : session.spectators().keySet()) {
            world.setSpectator(accountId);
        }
        Instant now = clock.instant();
        for (AceHuntLockout lockout : session.lockouts().values()) {
            if (lockout.isActive(now)) {
                world.teleportToSpawn(lockout.accountId(), config.lockupSpawn());
            }
        }
        for (UUID accountId : session.snapshots().keySet()) {
            if (session.isSpectator(accountId) || session.isLocked(accountId, now)) {
                continue;
            }
            confinementTerritoryUnlocked(session, accountId).ifPresent(territory ->
                    world.applyTerritoryBorder(accountId, territory));
        }
        for (Map.Entry<UUID, UUID> ace : session.acesByTeam().entrySet()) {
            if (session.isSpectator(ace.getValue())) {
                continue;
            }
            resolveTerritory(ace.getKey()).ifPresent(territory ->
                    world.applyTerritoryBorder(ace.getValue(), territory));
        }
    }

    private Optional<AceHuntTerritory> confinementTerritoryUnlocked(AceHuntSession session, UUID accountId) {
        Optional<UUID> aceTeam = session.aceTeamOf(accountId);
        if (aceTeam.isPresent()) {
            return resolveTerritory(aceTeam.get());
        }
        Optional<Team> team = teams.teamOf(accountId);
        if (team.isEmpty()) {
            return Optional.empty();
        }
        UUID target = session.huntAssignments().get(team.get().id());
        if (target == null) {
            return Optional.empty();
        }
        return resolveTerritory(target);
    }

    private Optional<AceHuntTerritory> resolveTerritory(UUID teamId) {
        Optional<AceHuntTerritory> byId = config.territoryByTeamId(teamId);
        if (byId.isPresent()) {
            return byId;
        }
        return teams.getTeam(teamId).flatMap(team -> config.territoryBySlug(team.slug()));
    }

    private AceHuntTerritory requireTerritory(UUID teamId) {
        return resolveTerritory(teamId).orElseThrow(() ->
                new IllegalStateException("Missing territory for team " + teamId));
    }

    private void persistSession(AceHuntSession session) {
        try {
            repository.upsertSession(session);
        } catch (SQLException exception) {
            errorLogger.accept("Ace Hunt session persist failed: " + exception.getMessage());
        }
    }

    public sealed interface AdminResult {
        record Ok(String message) implements AdminResult {
        }

        record Error(String message) implements AdminResult {
        }

        static AdminResult ok(String message) {
            return new Ok(message);
        }

        static AdminResult error(String message) {
            return new Error(message);
        }

        default boolean success() {
            return this instanceof Ok;
        }

        default String message() {
            return this instanceof Ok ok ? ok.message() : ((Error) this).message();
        }
    }

    public sealed interface DeathHandlingResult {
        record Ignored() implements DeathHandlingResult {
        }

        record KeepInventoryOnly() implements DeathHandlingResult {
        }

        record AcePrepRespawn(AceHuntTerritory territory) implements DeathHandlingResult {
        }

        record Lockup(AceHuntSpawnPoint spawn) implements DeathHandlingResult {
        }

        record Spectator() implements DeathHandlingResult {
        }

        static DeathHandlingResult ignored() {
            return new Ignored();
        }

        static DeathHandlingResult keepInventoryOnly() {
            return new KeepInventoryOnly();
        }

        static DeathHandlingResult acePrepRespawn(AceHuntTerritory territory) {
            return new AcePrepRespawn(territory);
        }

        static DeathHandlingResult lockup(AceHuntSpawnPoint spawn) {
            return new Lockup(spawn);
        }

        static DeathHandlingResult spectator() {
            return new Spectator();
        }

        default boolean keepInventory() {
            return !(this instanceof Ignored);
        }
    }
}
