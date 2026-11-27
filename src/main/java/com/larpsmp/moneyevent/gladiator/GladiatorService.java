package com.larpsmp.moneyevent.gladiator;

import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamMember;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.SplitRewardResult;
import com.larpsmp.moneyevent.wallet.TransactionStatus;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Gladiators lifecycle: schedule, lineups, fights, kit bans, quota, payout, restore.
 */
public final class GladiatorService {

    private final Object lock = new Object();
    private final JdbcGladiatorRepository repository;
    private final MoneyService money;
    private final TeamService teams;
    private final Clock clock;
    private final Consumer<String> errorLogger;
    private GladiatorWorldActions world = GladiatorWorldActions.NOOP;
    private BooleanSupplier otherEventLive = () -> false;

    private GladiatorConfig config;
    private @Nullable GladiatorSession activeSession;

    public GladiatorService(
            GladiatorConfig config,
            JdbcGladiatorRepository repository,
            MoneyService money,
            TeamService teams,
            Consumer<String> errorLogger
    ) {
        this(config, repository, money, teams, Clock.systemUTC(), errorLogger);
    }

    public GladiatorService(
            GladiatorConfig config,
            JdbcGladiatorRepository repository,
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

    public void setWorldActions(GladiatorWorldActions world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    public void setOtherEventLiveGuard(BooleanSupplier otherEventLive) {
        this.otherEventLive = Objects.requireNonNull(otherEventLive, "otherEventLive");
    }

    public GladiatorConfig config() {
        return config;
    }

    public void replaceConfig(GladiatorConfig updated) {
        synchronized (lock) {
            this.config = Objects.requireNonNull(updated, "updated");
        }
    }

    public Optional<GladiatorSession> activeSession() {
        synchronized (lock) {
            return Optional.ofNullable(activeSession);
        }
    }

    public boolean isLive() {
        synchronized (lock) {
            return activeSession != null && activeSession.phase().isLive();
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
                Optional<GladiatorSession> session = repository.findSession(activeId.get());
                if (session.isEmpty()) {
                    repository.setRuntime(null, clock.instant());
                    activeSession = null;
                    return;
                }
                activeSession = session.get();
                if (activeSession.phase().isLive()) {
                    try {
                        refreshKillMoneyParticipants(activeSession);
                    } catch (IOException exception) {
                        errorLogger.accept("Gladiators restored but kill-money refresh failed: "
                                + exception.getMessage());
                    }
                }
            } catch (SQLException exception) {
                errorLogger.accept("Failed to restore Gladiators: " + exception.getMessage());
                activeSession = null;
            }
        }
    }

    public Optional<GladiatorBodySnapshot> frozenBody(UUID accountId) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().isLive()) {
                return Optional.empty();
            }
            return activeSession.bodySnapshot(accountId);
        }
    }

    public AdminResult open() {
        synchronized (lock) {
            if (activeSession != null && activeSession.phase().isLive()) {
                return AdminResult.error("Gladiators is already open.");
            }
            if (otherEventLive.getAsBoolean()) {
                return AdminResult.error("Another event is live. Stop it before opening Gladiators.");
            }
            // Replace any ended session held for payout.
            activeSession = null;
            if (!config.arena().isDefined()) {
                return AdminResult.error("Define the arena region first (/larp gladiators arena define arena …).");
            }
            if (!config.seatsSpawn().isDefined()) {
                return AdminResult.error("Set the seats spawn first (/larp gladiators spawn seats).");
            }

            List<Team> rostered = rosteredTeams();
            if (rostered.size() < 2) {
                return AdminResult.error("Need at least two teams with members.");
            }

            Instant now = clock.instant();
            GladiatorSession session = GladiatorSession.create(UUID.randomUUID(), now);
            List<GladiatorSchedule.TeamOrder> orders = rostered.stream()
                    .map(team -> new GladiatorSchedule.TeamOrder(team.id(), team.slug(), team.sortOrder()))
                    .toList();
            List<UUID> orderedIds = GladiatorSchedule.sortTeamIds(orders);
            List<GladiatorSchedule.ScheduledBout> bouts = GladiatorSchedule.doubleRoundRobin(orderedIds);
            List<GladiatorScheduledEntry> schedule = new ArrayList<>();
            for (int i = 0; i < bouts.size(); i++) {
                schedule.add(new GladiatorScheduledEntry(
                        i, bouts.get(i).teamAId(), bouts.get(i).teamBId(), GladiatorScheduleStatus.PENDING));
            }
            session.replaceSchedule(schedule);

            for (Team team : rostered) {
                for (TeamMember member : teams.membersOf(team.id())) {
                    Optional<GladiatorBodySnapshot> snapshot = world.captureBody(member.accountId());
                    if (snapshot.isPresent()) {
                        session.putBodySnapshot(snapshot.get());
                    }
                    session.setQuota(member.accountId(), 0);
                }
            }

            try {
                repository.upsertSession(session);
                repository.replaceSchedule(session.sessionId(), schedule);
                for (Map.Entry<UUID, Integer> entry : session.quota().entrySet()) {
                    repository.upsertQuota(session.sessionId(), entry.getKey(), entry.getValue());
                }
                for (GladiatorBodySnapshot snapshot : session.bodySnapshots().values()) {
                    repository.upsertBodySnapshot(session.sessionId(), snapshot);
                }
                repository.setRuntime(session.sessionId(), now);
                money.setKillMoneyEvent(GladiatorRules.EVENT_ID, false, "system", "GLADIATORS", "Gladiators open");
                money.clearKillMoneyEventParticipants(GladiatorRules.EVENT_ID);
                for (UUID accountId : session.bodySnapshots().keySet()) {
                    money.addKillMoneyEventParticipant(GladiatorRules.EVENT_ID, accountId);
                }
                for (UUID accountId : session.quota().keySet()) {
                    if (!session.bodySnapshots().containsKey(accountId)) {
                        money.addKillMoneyEventParticipant(GladiatorRules.EVENT_ID, accountId);
                    }
                }
            } catch (SQLException | IOException exception) {
                errorLogger.accept("Failed to open Gladiators: " + exception.getMessage());
                return AdminResult.error("Failed to open Gladiators: " + exception.getMessage());
            }

            activeSession = session;
            world.broadcast(config.messages().sessionOpened());
            return AdminResult.ok("Gladiators opened with " + schedule.size() + " scheduled 3v3 bouts.");
        }
    }

    public AdminResult stop() {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().isLive()) {
                return AdminResult.error("No live Gladiators session.");
            }
            GladiatorSession session = activeSession;
            Instant now = clock.instant();
            if (session.activeFight().isPresent()) {
                return AdminResult.error("End or cancel the active fight before stopping.");
            }
            if (session.phase() == GladiatorPhase.KIT_BAN) {
                return AdminResult.error("Cancel the pending duel before stopping.");
            }

            for (GladiatorBodySnapshot snapshot : session.bodySnapshots().values()) {
                world.restoreBody(snapshot);
            }

            session.end(now);
            try {
                repository.upsertSession(session);
                // Keep runtime pointing at the ended session so payout can still resolve it.
                repository.setRuntime(session.sessionId(), now);
                money.clearKillMoneyEventParticipants(GladiatorRules.EVENT_ID);
            } catch (SQLException | IOException exception) {
                errorLogger.accept("Failed to stop Gladiators cleanly: " + exception.getMessage());
            }
            // Keep ended session in memory for payout; isLive() is false.
            world.broadcast(config.messages().sessionStopped());
            return AdminResult.ok("Gladiators stopped. Bodies restored. Use /larp gladiators payout when ready.");
        }
    }

    public AdminResult setLineup(UUID teamId, List<UUID> accountIds) {
        synchronized (lock) {
            GladiatorSession session = requireOpenBetween();
            if (session == null) {
                return AdminResult.error("Lineups can only be set between bouts.");
            }
            if (teams.getTeam(teamId).isEmpty()) {
                return AdminResult.error("Unknown team.");
            }
            if (accountIds.size() != GladiatorRules.TEAM_FIGHT_SIZE) {
                return AdminResult.error("Lineup requires exactly " + GladiatorRules.TEAM_FIGHT_SIZE + " players.");
            }
            Map<Integer, UUID> slots = new LinkedHashMap<>();
            for (int i = 0; i < accountIds.size(); i++) {
                UUID accountId = accountIds.get(i);
                Optional<Team> memberTeam = teams.teamOf(accountId);
                if (memberTeam.isEmpty() || !memberTeam.get().id().equals(teamId)) {
                    return AdminResult.error("Player is not on that team: " + accountId);
                }
                if (!session.hasQuotaRemaining(accountId)) {
                    return AdminResult.error("Player has no fights remaining: " + accountId);
                }
                if (slots.containsValue(accountId)) {
                    return AdminResult.error("Duplicate player in lineup.");
                }
                slots.put(i, accountId);
            }
            session.setLineup(teamId, slots);
            persistLineups(session);
            return AdminResult.ok("Lineup saved.");
        }
    }

    public AdminResult clearLineup(UUID teamId) {
        synchronized (lock) {
            GladiatorSession session = requireOpenBetween();
            if (session == null) {
                return AdminResult.error("Lineups can only be cleared between bouts.");
            }
            session.clearLineup(teamId);
            persistLineups(session);
            return AdminResult.ok("Lineup cleared.");
        }
    }

    public AdminResult startFight() {
        synchronized (lock) {
            GladiatorSession session = requireOpenBetween();
            if (session == null) {
                return AdminResult.error("Cannot start a fight right now.");
            }
            Optional<GladiatorScheduledEntry> boutOpt = session.currentPendingBout();
            if (boutOpt.isEmpty()) {
                return AdminResult.error("3v3 schedule is finished. Nominate a 1v1 with /larp gladiators duel.");
            }
            GladiatorScheduledEntry bout = boutOpt.get();
            Optional<Map<Integer, UUID>> lineupA = session.lineupOf(bout.teamAId());
            Optional<Map<Integer, UUID>> lineupB = session.lineupOf(bout.teamBId());
            if (lineupA.isEmpty() || lineupA.get().size() != GladiatorRules.TEAM_FIGHT_SIZE) {
                return AdminResult.error("Set a full lineup for team A first.");
            }
            if (lineupB.isEmpty() || lineupB.get().size() != GladiatorRules.TEAM_FIGHT_SIZE) {
                return AdminResult.error("Set a full lineup for team B first.");
            }
            if (!config.arena().isDefined()) {
                return AdminResult.error("Arena region is not defined.");
            }
            Optional<GladiatorKit> kit;
            try {
                kit = repository.findKit(GladiatorRules.TEAM_KIT_ID);
            } catch (SQLException exception) {
                return AdminResult.error("Could not load team kit: " + exception.getMessage());
            }
            if (kit.isEmpty()) {
                return AdminResult.error("Save the team mini-kit first (/larp gladiators kit save team).");
            }

            List<GladiatorSpawnPoint> padsA = teamPads(bout.teamAId());
            List<GladiatorSpawnPoint> padsB = teamPads(bout.teamBId());
            if (padsA.stream().anyMatch(pad -> !pad.isDefined()) || padsB.stream().anyMatch(pad -> !pad.isDefined())) {
                return AdminResult.error("Define all three spawn pads for both teams.");
            }

            List<UUID> fightersA = orderedAccounts(lineupA.get());
            List<UUID> fightersB = orderedAccounts(lineupB.get());
            for (UUID accountId : fightersA) {
                if (!world.isOnline(accountId)) {
                    return AdminResult.error("All fighters must be online (missing team A).");
                }
                if (!session.hasQuotaRemaining(accountId)) {
                    return AdminResult.error("A fighter has no fights remaining.");
                }
            }
            for (UUID accountId : fightersB) {
                if (!world.isOnline(accountId)) {
                    return AdminResult.error("All fighters must be online (missing team B).");
                }
                if (!session.hasQuotaRemaining(accountId)) {
                    return AdminResult.error("A fighter has no fights remaining.");
                }
            }

            Instant now = clock.instant();
            List<GladiatorFighter> fighters = new ArrayList<>();
            for (int i = 0; i < fightersA.size(); i++) {
                fighters.add(new GladiatorFighter(fightersA.get(i), bout.teamAId(), i, true));
            }
            for (int i = 0; i < fightersB.size(); i++) {
                fighters.add(new GladiatorFighter(fightersB.get(i), bout.teamBId(), i, true));
            }
            GladiatorFight fight = GladiatorFight.startTeamFight(
                    UUID.randomUUID(), bout.boutIndex(), bout.teamAId(), bout.teamBId(), now, fighters);

            for (UUID accountId : fightersA) {
                session.incrementQuota(accountId);
            }
            for (UUID accountId : fightersB) {
                session.incrementQuota(accountId);
            }

            session.setActiveFight(fight);
            session.setPhase(GladiatorPhase.TEAM_FIGHT, now);
            session.setScheduleIndex(bout.boutIndex());

            try {
                repository.upsertFight(session.sessionId(), fight);
                repository.upsertSession(session);
                for (UUID accountId : fightersA) {
                    repository.upsertQuota(session.sessionId(), accountId, session.fightsUsed(accountId));
                }
                for (UUID accountId : fightersB) {
                    repository.upsertQuota(session.sessionId(), accountId, session.fightsUsed(accountId));
                }
            } catch (SQLException exception) {
                return AdminResult.error("Failed to start fight: " + exception.getMessage());
            }

            seatNonFighters(session, Set.copyOf(fighters.stream().map(GladiatorFighter::accountId).toList()));
            for (int i = 0; i < fightersA.size(); i++) {
                prepareFighter(fightersA.get(i), kit.get(), padsA.get(i));
            }
            for (int i = 0; i < fightersB.size(); i++) {
                prepareFighter(fightersB.get(i), kit.get(), padsB.get(i));
            }

            String teamA = teamName(bout.teamAId());
            String teamB = teamName(bout.teamBId());
            world.broadcast(config.messages().fightStarted()
                    .replace("{teamA}", teamA)
                    .replace("{teamB}", teamB));
            return AdminResult.ok("Fight started: " + teamA + " vs " + teamB + ".");
        }
    }

    public AdminResult skipFight() {
        synchronized (lock) {
            GladiatorSession session = requireOpenBetween();
            if (session == null) {
                return AdminResult.error("Cannot skip right now.");
            }
            Optional<GladiatorScheduledEntry> boutOpt = session.currentPendingBout();
            if (boutOpt.isEmpty()) {
                return AdminResult.error("No pending schedule bout to skip.");
            }
            GladiatorScheduledEntry bout = boutOpt.get();
            bout.setStatus(GladiatorScheduleStatus.SKIPPED);
            Instant now = clock.instant();
            session.touch(now);
            try {
                repository.upsertScheduleEntry(session.sessionId(), bout);
                repository.upsertSession(session);
            } catch (SQLException exception) {
                return AdminResult.error("Failed to skip bout: " + exception.getMessage());
            }
            session.clearAllLineups();
            persistLineups(session);
            world.broadcast(config.messages().fightSkipped());
            return AdminResult.ok("Bout skipped.");
        }
    }

    public AdminResult cancelFight() {
        synchronized (lock) {
            if (activeSession == null) {
                return AdminResult.error("No active session.");
            }
            GladiatorSession session = activeSession;
            if (session.phase() != GladiatorPhase.TEAM_FIGHT && session.phase() != GladiatorPhase.DUEL) {
                return AdminResult.error("No active fight to cancel.");
            }
            Optional<GladiatorFight> fightOpt = session.activeFight();
            if (fightOpt.isEmpty()) {
                return AdminResult.error("No active fight to cancel.");
            }
            GladiatorFight fight = fightOpt.get();
            Instant now = clock.instant();
            // Cancel voids the bout without points; quota already consumed stays consumed.
            fight.forceWinner(null, now);
            // Mark as voided rather than completed-with-null for clarity.
            try {
                // Persist as COMPLETED with null winner = draw/void for points (0).
                repository.upsertFight(session.sessionId(), fight);
            } catch (SQLException exception) {
                return AdminResult.error("Failed to cancel fight: " + exception.getMessage());
            }
            if (fight.kind() == GladiatorFightKind.TEAM_3V3 && fight.boutIndex() != null) {
                for (GladiatorScheduledEntry entry : session.schedule()) {
                    if (entry.boutIndex() == fight.boutIndex()) {
                        entry.setStatus(GladiatorScheduleStatus.SKIPPED);
                        try {
                            repository.upsertScheduleEntry(session.sessionId(), entry);
                        } catch (SQLException exception) {
                            errorLogger.accept("Schedule skip persist failed: " + exception.getMessage());
                        }
                        break;
                    }
                }
            }
            returnToSeats(session);
            session.setActiveFight(null);
            session.setPhase(GladiatorPhase.OPEN, now);
            session.clearAllLineups();
            persistLineups(session);
            persistSession(session);
            return AdminResult.ok("Fight cancelled. No points awarded.");
        }
    }

    public AdminResult forceWinner(UUID teamId) {
        synchronized (lock) {
            if (activeSession == null || activeSession.activeFight().isEmpty()) {
                return AdminResult.error("No active fight.");
            }
            GladiatorFight fight = activeSession.activeFight().get();
            if (!teamId.equals(fight.teamAId()) && !teamId.equals(fight.teamBId())) {
                return AdminResult.error("Team is not in this fight.");
            }
            Instant now = clock.instant();
            Optional<GladiatorFight.CompleteOutcome> outcome = fight.forceWinner(teamId, now);
            if (outcome.isEmpty()) {
                return AdminResult.error("Fight is not active.");
            }
            completeFight(activeSession, fight, outcome.get().winningTeamId(), now);
            return AdminResult.ok("Winner forced: " + teamName(teamId) + ".");
        }
    }

    public AdminResult nominateDuel(UUID fighterA, UUID fighterB) {
        synchronized (lock) {
            GladiatorSession session = requireOpenBetween();
            if (session == null) {
                return AdminResult.error("Cannot nominate a duel right now.");
            }
            if (!session.scheduleFinished()) {
                return AdminResult.error("Finish or skip the 3v3 schedule before 1v1s.");
            }
            if (fighterA.equals(fighterB)) {
                return AdminResult.error("Fighters must differ.");
            }
            Optional<Team> teamA = teams.teamOf(fighterA);
            Optional<Team> teamB = teams.teamOf(fighterB);
            if (teamA.isEmpty() || teamB.isEmpty()) {
                return AdminResult.error("Both fighters must be rostered.");
            }
            if (teamA.get().id().equals(teamB.get().id())) {
                return AdminResult.error("1v1 fighters must be from different teams.");
            }
            if (!session.hasQuotaRemaining(fighterA) || !session.hasQuotaRemaining(fighterB)) {
                return AdminResult.error("A fighter has no fights remaining.");
            }
            if (!world.isOnline(fighterA) || !world.isOnline(fighterB)) {
                return AdminResult.error("Both fighters must be online.");
            }
            for (int i = 1; i <= GladiatorRules.DUEL_KIT_COUNT; i++) {
                try {
                    if (repository.findKit(GladiatorRules.duelKitId(i)).isEmpty()) {
                        return AdminResult.error("Save all five duel kits first (missing kit " + i + ").");
                    }
                } catch (SQLException exception) {
                    return AdminResult.error("Could not load kits: " + exception.getMessage());
                }
            }

            Instant now = clock.instant();
            GladiatorDuelPending pending = new GladiatorDuelPending(
                    fighterA, fighterB, teamA.get().id(), teamB.get().id(), fighterA);
            session.clearKitBans();
            session.setDuelPending(pending);
            session.setPhase(GladiatorPhase.KIT_BAN, now);
            try {
                repository.clearKitBans(session.sessionId());
                repository.upsertDuelPending(session.sessionId(), pending);
                repository.upsertSession(session);
            } catch (SQLException exception) {
                return AdminResult.error("Failed to nominate duel: " + exception.getMessage());
            }
            String nameA = Objects.requireNonNullElse(world.username(fighterA), fighterA.toString());
            String nameB = Objects.requireNonNullElse(world.username(fighterB), fighterB.toString());
            world.broadcast(config.messages().duelNominated()
                    .replace("{a}", nameA)
                    .replace("{b}", nameB));
            return AdminResult.ok("Duel nominated. " + nameA + " bans first.");
        }
    }

    public AdminResult cancelDuel() {
        synchronized (lock) {
            if (activeSession == null || activeSession.phase() != GladiatorPhase.KIT_BAN) {
                return AdminResult.error("No kit-ban duel to cancel.");
            }
            Instant now = clock.instant();
            activeSession.setDuelPending(null);
            activeSession.clearKitBans();
            activeSession.setPhase(GladiatorPhase.OPEN, now);
            try {
                repository.clearDuelPending(activeSession.sessionId());
                repository.clearKitBans(activeSession.sessionId());
                repository.upsertSession(activeSession);
            } catch (SQLException exception) {
                return AdminResult.error("Failed to cancel duel: " + exception.getMessage());
            }
            return AdminResult.ok("Duel cancelled.");
        }
    }

    public AdminResult banKit(UUID bannerAccountId, String kitId) {
        synchronized (lock) {
            if (activeSession == null || activeSession.phase() != GladiatorPhase.KIT_BAN) {
                return AdminResult.error("No kit ban in progress.");
            }
            Optional<GladiatorDuelPending> pendingOpt = activeSession.duelPending();
            if (pendingOpt.isEmpty()) {
                return AdminResult.error("No pending duel.");
            }
            GladiatorDuelPending pending = pendingOpt.get();
            GladiatorKitBan.BanResult result = GladiatorKitBan.applyBan(
                    bannerAccountId,
                    kitId.trim(),
                    pending.fighterAAccountId(),
                    pending.fighterBAccountId(),
                    activeSession.kitBans());
            if (!result.success()) {
                return AdminResult.error(result.message());
            }

            Instant now = clock.instant();
            if (result instanceof GladiatorKitBan.BanResult.Continue cont) {
                activeSession.replaceKitBans(cont.bannedKitIds());
                GladiatorDuelPending updated = new GladiatorDuelPending(
                        pending.fighterAAccountId(),
                        pending.fighterBAccountId(),
                        pending.fighterATeamId(),
                        pending.fighterBTeamId(),
                        cont.nextBannerAccountId());
                activeSession.setDuelPending(updated);
                try {
                    repository.upsertKitBan(
                            activeSession.sessionId(),
                            cont.bannedKitIds().size() - 1,
                            kitId.trim(),
                            bannerAccountId);
                    repository.upsertDuelPending(activeSession.sessionId(), updated);
                    repository.upsertSession(activeSession);
                } catch (SQLException exception) {
                    return AdminResult.error("Failed to persist ban: " + exception.getMessage());
                }
                announceBan(bannerAccountId, kitId, cont.bannedKitIds());
                return AdminResult.ok("Kit " + kitId + " banned.");
            }

            if (result instanceof GladiatorKitBan.BanResult.Complete complete) {
                activeSession.replaceKitBans(complete.bannedKitIds());
                try {
                    repository.upsertKitBan(
                            activeSession.sessionId(),
                            complete.bannedKitIds().size() - 1,
                            kitId.trim(),
                            bannerAccountId);
                } catch (SQLException exception) {
                    return AdminResult.error("Failed to persist ban: " + exception.getMessage());
                }
                announceBan(bannerAccountId, kitId, complete.bannedKitIds());
                return startDuelFromBan(activeSession, pending, complete.remainingKitId(), now);
            }
            return AdminResult.error("Unexpected ban result.");
        }
    }

    public AdminResult setQuota(UUID accountId, int fightsUsed) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().isLive()) {
                return AdminResult.error("No live Gladiators session.");
            }
            if (fightsUsed < 0 || fightsUsed > GladiatorRules.MAX_FIGHTS) {
                return AdminResult.error("Quota must be 0–" + GladiatorRules.MAX_FIGHTS + ".");
            }
            activeSession.setQuota(accountId, fightsUsed);
            try {
                repository.upsertQuota(activeSession.sessionId(), accountId, fightsUsed);
            } catch (SQLException exception) {
                return AdminResult.error("Failed to set quota: " + exception.getMessage());
            }
            return AdminResult.ok("Quota set to " + fightsUsed + ".");
        }
    }

    public AdminResult saveKitFromPlayer(UUID adminAccountId, String kitId) {
        synchronized (lock) {
            String normalized = normalizeKitId(kitId);
            if (normalized == null) {
                return AdminResult.error("Kit id must be 'team' or 1–5.");
            }
            Optional<GladiatorKit> kit = world.captureKitFromPlayer(adminAccountId, normalized);
            if (kit.isEmpty()) {
                return AdminResult.error("Could not capture kit from your inventory (must be online).");
            }
            try {
                repository.upsertKit(kit.get());
            } catch (SQLException exception) {
                return AdminResult.error("Failed to save kit: " + exception.getMessage());
            }
            return AdminResult.ok("Saved kit '" + normalized + "'.");
        }
    }

    public AdminResult clearKit(String kitId) {
        synchronized (lock) {
            String normalized = normalizeKitId(kitId);
            if (normalized == null) {
                return AdminResult.error("Kit id must be 'team' or 1–5.");
            }
            try {
                repository.deleteKit(normalized);
            } catch (SQLException exception) {
                return AdminResult.error("Failed to clear kit: " + exception.getMessage());
            }
            return AdminResult.ok("Cleared kit '" + normalized + "'.");
        }
    }

    public List<String> listKitIds() {
        try {
            return repository.listKits().stream().map(GladiatorKit::kitId).toList();
        } catch (SQLException exception) {
            errorLogger.accept("list kits failed: " + exception.getMessage());
            return List.of();
        }
    }

    public AdminResult fillBarriers() {
        synchronized (lock) {
            GladiatorCuboid region = config.barriers();
            if (!region.isDefined()) {
                return AdminResult.error("Define the barriers region first.");
            }
            if (region.blockVolume() > GladiatorRules.MAX_BARRIER_BLOCKS) {
                return AdminResult.error("Barrier region is too large (max "
                        + GladiatorRules.MAX_BARRIER_BLOCKS + " blocks).");
            }
            List<GladiatorBarrierBlock> prior = world.fillBarriers(region);
            try {
                repository.replaceBarrierBlocks(prior);
            } catch (SQLException exception) {
                return AdminResult.error("Failed to persist barrier undo rows: " + exception.getMessage());
            }
            return AdminResult.ok("Filled " + prior.size() + " barrier blocks.");
        }
    }

    public AdminResult clearBarriers() {
        synchronized (lock) {
            try {
                List<GladiatorBarrierBlock> blocks = repository.loadBarrierBlocks();
                int restored = world.clearBarriers(blocks);
                repository.clearBarrierBlocks();
                return AdminResult.ok("Restored " + restored + " barrier blocks.");
            } catch (SQLException exception) {
                return AdminResult.error("Failed to clear barriers: " + exception.getMessage());
            }
        }
    }

    public AdminResult payout() {
        synchronized (lock) {
            GladiatorSession session = activeSession;
            if (session == null) {
                try {
                    Optional<UUID> runtimeId = repository.loadActiveSessionId();
                    if (runtimeId.isPresent()) {
                        session = repository.findSession(runtimeId.get()).orElse(null);
                        activeSession = session;
                    }
                } catch (SQLException exception) {
                    return AdminResult.error("Payout failed: " + exception.getMessage());
                }
            }
            if (session == null) {
                return AdminResult.error("No Gladiators session available for payout.");
            }
            if (session.payoutCompleted()) {
                return AdminResult.ok("Payout already completed for this session.");
            }
            if (session.phase().isLive() && session.activeFight().isPresent()) {
                return AdminResult.error("Finish the active fight before payout.");
            }
            if (session.phase().isLive() && session.phase() == GladiatorPhase.KIT_BAN) {
                return AdminResult.error("Cancel or finish the duel ban before payout.");
            }

            Map<UUID, Integer> places = GladiatorPlacements.placesFromPoints(session.pointsByTeam());
            int paidTeams = 0;
            int totalTeams = places.size();
            for (Map.Entry<UUID, Integer> entry : places.entrySet()) {
                long amount = config.prizeForPlace(entry.getValue());
                SplitRewardResult result = money.processGladiatorPrize(
                        session.sessionId().toString(), entry.getKey(), amount);
                if (result.status() != TransactionStatus.SUCCESS) {
                    return AdminResult.error("Payout failed for team " + teamName(entry.getKey())
                            + " after paying " + paidTeams + "/" + totalTeams
                            + " teams: " + result.failureReason()
                            + ". Re-run /larp gladiators payout (already-paid teams are idempotent).");
                }
                paidTeams++;
            }
            session.markPayoutCompleted();
            persistSession(session);
            world.broadcast(config.messages().payoutDone());
            return AdminResult.ok("Paid prizes for " + paidTeams + " teams.");
        }
    }

    public DeathHandlingResult handleDeath(UUID victimAccountId) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().isFightActive()) {
                return DeathHandlingResult.ignored();
            }
            Optional<GladiatorFight> fightOpt = activeSession.activeFight();
            if (fightOpt.isEmpty() || !fightOpt.get().isAliveFighter(victimAccountId)) {
                return DeathHandlingResult.ignored();
            }
            GladiatorFight fight = fightOpt.get();
            Instant now = clock.instant();
            Optional<GladiatorFight.CompleteOutcome> outcome = fight.markDead(victimAccountId, now);
            try {
                repository.upsertFight(activeSession.sessionId(), fight);
            } catch (SQLException exception) {
                errorLogger.accept("Fight death persist failed: " + exception.getMessage());
            }
            if (outcome.isPresent()) {
                completeFight(activeSession, fight, outcome.get().winningTeamId(), now);
            }
            return DeathHandlingResult.seat();
        }
    }

    public boolean isEventCombat(UUID victimAccountId, UUID killerAccountId) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().isLive()) {
                return false;
            }
            return activeSession.isParticipantSnapshot(victimAccountId)
                    || activeSession.isParticipantSnapshot(killerAccountId)
                    || activeSession.quota().containsKey(victimAccountId)
                    || activeSession.quota().containsKey(killerAccountId);
        }
    }

    public boolean shouldKeepInventory(UUID accountId) {
        synchronized (lock) {
            return activeSession != null
                    && activeSession.phase().isFightActive()
                    && activeSession.isAliveFighter(accountId);
        }
    }

    public boolean shouldConfineToArena(UUID accountId) {
        synchronized (lock) {
            return activeSession != null
                    && activeSession.phase().isFightActive()
                    && activeSession.isAliveFighter(accountId);
        }
    }

    public boolean shouldConfineToSeats(UUID accountId) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().isLive()) {
                return false;
            }
            if (!activeSession.isParticipantSnapshot(accountId) && !activeSession.quota().containsKey(accountId)) {
                return false;
            }
            if (activeSession.isAliveFighter(accountId)) {
                return false;
            }
            return activeSession.phase().isFightActive() || activeSession.phase() == GladiatorPhase.KIT_BAN;
        }
    }

    public boolean isBarrierProtected() {
        return config.barriers().isDefined();
    }

    public void onRosterChanged() {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().isLive()) {
                return;
            }
            try {
                refreshKillMoneyParticipants(activeSession);
            } catch (IOException exception) {
                errorLogger.accept("Kill-money refresh failed: " + exception.getMessage());
            }
        }
    }

    public void onPlayerJoin(UUID accountId) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.phase().isLive()) {
                return;
            }
            if (activeSession.isAliveFighter(accountId)) {
                // Re-teleport into arena on next tick via listener using current pads is hard without slot;
                // seat them if somehow offline mid-fight — keep alive flag, world layer re-applies.
                return;
            }
            if (shouldConfineToSeats(accountId) && config.seatsSpawn().isDefined()) {
                world.teleport(accountId, config.seatsSpawn());
                world.setAdventure(accountId);
                world.clearInventory(accountId);
            }
        }
    }

    private AdminResult startDuelFromBan(
            GladiatorSession session,
            GladiatorDuelPending pending,
            String kitId,
            Instant now
    ) {
        Optional<GladiatorKit> kit;
        try {
            kit = repository.findKit(kitId);
        } catch (SQLException exception) {
            return AdminResult.error("Could not load duel kit: " + exception.getMessage());
        }
        if (kit.isEmpty()) {
            return AdminResult.error("Remaining kit is missing from storage.");
        }
        if (!config.duelSpawnA().isDefined() || !config.duelSpawnB().isDefined()) {
            return AdminResult.error("Define duel spawn pads first.");
        }

        session.incrementQuota(pending.fighterAAccountId());
        session.incrementQuota(pending.fighterBAccountId());
        GladiatorFight fight = GladiatorFight.startDuel(
                UUID.randomUUID(),
                pending.fighterATeamId(),
                pending.fighterBTeamId(),
                kitId,
                now,
                new GladiatorFighter(pending.fighterAAccountId(), pending.fighterATeamId(), 0, true),
                new GladiatorFighter(pending.fighterBAccountId(), pending.fighterBTeamId(), 0, true));
        session.setDuelPending(null);
        session.setActiveFight(fight);
        session.setPhase(GladiatorPhase.DUEL, now);
        try {
            repository.clearDuelPending(session.sessionId());
            repository.upsertFight(session.sessionId(), fight);
            repository.upsertQuota(session.sessionId(), pending.fighterAAccountId(),
                    session.fightsUsed(pending.fighterAAccountId()));
            repository.upsertQuota(session.sessionId(), pending.fighterBAccountId(),
                    session.fightsUsed(pending.fighterBAccountId()));
            repository.upsertSession(session);
        } catch (SQLException exception) {
            return AdminResult.error("Failed to start duel: " + exception.getMessage());
        }

        seatNonFighters(session, Set.of(pending.fighterAAccountId(), pending.fighterBAccountId()));
        prepareFighter(pending.fighterAAccountId(), kit.get(), config.duelSpawnA());
        prepareFighter(pending.fighterBAccountId(), kit.get(), config.duelSpawnB());

        String nameA = Objects.requireNonNullElse(world.username(pending.fighterAAccountId()), "?");
        String nameB = Objects.requireNonNullElse(world.username(pending.fighterBAccountId()), "?");
        world.broadcast(config.messages().duelStarted()
                .replace("{kit}", kitId)
                .replace("{a}", nameA)
                .replace("{b}", nameB));
        return AdminResult.ok("Duel started with kit " + kitId + ".");
    }

    private void completeFight(
            GladiatorSession session,
            GladiatorFight fight,
            @Nullable UUID winningTeamId,
            Instant now
    ) {
        try {
            repository.upsertFight(session.sessionId(), fight);
        } catch (SQLException exception) {
            errorLogger.accept("Fight complete persist failed: " + exception.getMessage());
        }
        if (fight.kind() == GladiatorFightKind.TEAM_3V3 && fight.boutIndex() != null) {
            for (GladiatorScheduledEntry entry : session.schedule()) {
                if (entry.boutIndex() == fight.boutIndex()) {
                    entry.setStatus(GladiatorScheduleStatus.COMPLETED);
                    try {
                        repository.upsertScheduleEntry(session.sessionId(), entry);
                    } catch (SQLException exception) {
                        errorLogger.accept("Schedule complete persist failed: " + exception.getMessage());
                    }
                    break;
                }
            }
        }
        returnToSeats(session);
        session.setActiveFight(null);
        session.clearAllLineups();
        persistLineups(session);
        session.setPhase(GladiatorPhase.OPEN, now);
        persistSession(session);

        if (winningTeamId == null) {
            world.broadcast(config.messages().fightDraw());
        } else {
            world.broadcast(config.messages().fightWon().replace("{team}", teamName(winningTeamId)));
        }
    }

    private void prepareFighter(UUID accountId, GladiatorKit kit, GladiatorSpawnPoint spawn) {
        world.setSurvival(accountId);
        world.applyKit(accountId, kit);
        world.healFully(accountId);
        world.teleport(accountId, spawn);
    }

    private void seatNonFighters(GladiatorSession session, Set<UUID> fighterIds) {
        GladiatorSpawnPoint seats = config.seatsSpawn();
        if (!seats.isDefined()) {
            return;
        }
        for (UUID accountId : session.quota().keySet()) {
            if (fighterIds.contains(accountId)) {
                continue;
            }
            world.clearInventory(accountId);
            world.setAdventure(accountId);
            world.teleport(accountId, seats);
        }
        for (UUID accountId : session.bodySnapshots().keySet()) {
            if (fighterIds.contains(accountId) || session.quota().containsKey(accountId)) {
                continue;
            }
            world.clearInventory(accountId);
            world.setAdventure(accountId);
            world.teleport(accountId, seats);
        }
    }

    private void returnToSeats(GladiatorSession session) {
        GladiatorSpawnPoint seats = config.seatsSpawn();
        Optional<GladiatorFight> fight = session.activeFight();
        if (fight.isPresent()) {
            for (GladiatorFighter fighter : fight.get().fighterList()) {
                world.clearInventory(fighter.accountId());
                world.setAdventure(fighter.accountId());
                if (seats.isDefined()) {
                    world.teleport(fighter.accountId(), seats);
                }
            }
        }
    }

    private void announceBan(UUID banner, String kitId, List<String> banned) {
        Set<String> remaining = GladiatorKitBan.remainingKits(banned);
        String player = Objects.requireNonNullElse(world.username(banner), banner.toString());
        world.broadcast(config.messages().kitBanned()
                .replace("{player}", player)
                .replace("{kit}", kitId)
                .replace("{remaining}", String.join(",", remaining)));
    }

    private @Nullable GladiatorSession requireOpenBetween() {
        if (activeSession == null || !activeSession.phase().allowsBetweenBouts()) {
            return null;
        }
        return activeSession;
    }

    private List<Team> rosteredTeams() {
        List<Team> result = new ArrayList<>();
        for (Team team : teams.listTeams()) {
            if (!teams.membersOf(team.id()).isEmpty()) {
                result.add(team);
            }
        }
        return result;
    }

    private List<GladiatorSpawnPoint> teamPads(UUID teamId) {
        Optional<Team> team = teams.getTeam(teamId);
        if (team.isEmpty()) {
            return defaultUndefinedPads();
        }
        return config.teamSpawns(team.get().slug()).orElseGet(this::defaultUndefinedPads);
    }

    private List<GladiatorSpawnPoint> defaultUndefinedPads() {
        List<GladiatorSpawnPoint> pads = new ArrayList<>();
        for (int i = 0; i < GladiatorRules.TEAM_FIGHT_SIZE; i++) {
            pads.add(GladiatorSpawnPoint.undefined());
        }
        return pads;
    }

    private static List<UUID> orderedAccounts(Map<Integer, UUID> lineup) {
        List<UUID> accounts = new ArrayList<>();
        for (int i = 0; i < GladiatorRules.TEAM_FIGHT_SIZE; i++) {
            UUID accountId = lineup.get(i);
            if (accountId != null) {
                accounts.add(accountId);
            }
        }
        return accounts;
    }

    private String teamName(UUID teamId) {
        String fromWorld = world.teamDisplayName(teamId);
        if (fromWorld != null) {
            return fromWorld;
        }
        return teams.getTeam(teamId).map(Team::displayName).orElse(teamId.toString());
    }

    private void persistSession(GladiatorSession session) {
        try {
            repository.upsertSession(session);
        } catch (SQLException exception) {
            errorLogger.accept("Session persist failed: " + exception.getMessage());
        }
    }

    private void persistLineups(GladiatorSession session) {
        try {
            repository.replaceLineups(session.sessionId(), session.lineups());
        } catch (SQLException exception) {
            errorLogger.accept("Lineup persist failed: " + exception.getMessage());
        }
    }

    private void refreshKillMoneyParticipants(GladiatorSession session) throws IOException {
        money.clearKillMoneyEventParticipants(GladiatorRules.EVENT_ID);
        money.setKillMoneyEvent(GladiatorRules.EVENT_ID, false, "system", "GLADIATORS", "Gladiators refresh");
        for (Team team : rosteredTeams()) {
            for (TeamMember member : teams.membersOf(team.id())) {
                money.addKillMoneyEventParticipant(GladiatorRules.EVENT_ID, member.accountId());
                if (!session.quota().containsKey(member.accountId())) {
                    session.setQuota(member.accountId(), 0);
                }
            }
        }
    }

    private static @Nullable String normalizeKitId(String kitId) {
        if (kitId == null || kitId.isBlank()) {
            return null;
        }
        String trimmed = kitId.trim().toLowerCase();
        if (trimmed.equals(GladiatorRules.TEAM_KIT_ID)) {
            return GladiatorRules.TEAM_KIT_ID;
        }
        if (GladiatorRules.isDuelKitId(trimmed)) {
            return trimmed;
        }
        return null;
    }

    public record AdminResult(boolean success, String message) {
        public static AdminResult ok(String message) {
            return new AdminResult(true, message);
        }

        public static AdminResult error(String message) {
            return new AdminResult(false, message);
        }
    }

    public sealed interface DeathHandlingResult {
        record Ignored() implements DeathHandlingResult {
        }

        record Seat() implements DeathHandlingResult {
        }

        static DeathHandlingResult ignored() {
            return new Ignored();
        }

        static DeathHandlingResult seat() {
            return new Seat();
        }
    }
}
