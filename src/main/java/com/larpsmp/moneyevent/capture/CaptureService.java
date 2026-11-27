package com.larpsmp.moneyevent.capture;

import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.EconomyRules;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.SplitRewardResult;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Capture session lifecycle, progress ticks, and idempotent economy payout.
 */
public final class CaptureService {

    private final Object lock = new Object();
    private final JdbcCaptureRepository repository;
    private final MoneyService money;
    private final TeamService teams;
    private final Clock clock;
    private final Consumer<String> errorLogger;
    private final List<CaptureEventListener> listeners = new ArrayList<>();

    private CaptureConfig config;
    private CaptureProgressEngine engine;
    private @Nullable CaptureSession activeSession;

    public CaptureService(
            CaptureConfig config,
            JdbcCaptureRepository repository,
            MoneyService money,
            TeamService teams,
            Consumer<String> errorLogger) {
        this(config, repository, money, teams, Clock.systemUTC(), errorLogger);
    }

    public CaptureService(
            CaptureConfig config,
            JdbcCaptureRepository repository,
            MoneyService money,
            TeamService teams,
            Clock clock,
            Consumer<String> errorLogger) {
        this.config = Objects.requireNonNull(config, "config");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.money = Objects.requireNonNull(money, "money");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.engine = new CaptureProgressEngine(this.config);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    public void addListener(CaptureEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public CaptureConfig config() {
        return config;
    }

    public void replaceConfig(CaptureConfig updated) {
        synchronized (lock) {
            this.config = Objects.requireNonNull(updated, "updated");
            this.engine = new CaptureProgressEngine(this.config);
        }
    }

    public Optional<CaptureSession> activeSession() {
        synchronized (lock) {
            return Optional.ofNullable(activeSession);
        }
    }

    public Optional<CapturePointDefinition> activePoint() {
        synchronized (lock) {
            if (activeSession == null) {
                return Optional.empty();
            }
            return config.point(activeSession.pointId());
        }
    }

    /**
     * Restore runtime pointer after plugin enable. Does not apply offline catch-up time.
     */
    public void restoreFromDatabase() {
        synchronized (lock) {
            try {
                JdbcCaptureRepository.RuntimeState runtime = repository.loadRuntime();
                if (runtime.activeCaptureId() == null) {
                    activeSession = null;
                    return;
                }
                Optional<CaptureSession> loaded = repository.findSession(runtime.activeCaptureId());
                if (loaded.isEmpty() || loaded.get().isTerminal()) {
                    activeSession = null;
                    repository.setRuntime(null, false, clock.instant());
                    return;
                }
                CaptureSession session = loaded.get();
                // Pause during downtime: if it was ACTIVE at shutdown, keep ACTIVE but no offline dt.
                // Runtime.paused mirrors PAUSED status.
                if (runtime.paused() && session.status() == CaptureSessionStatus.ACTIVE) {
                    session.pause(clock.instant());
                    persistSession(session);
                }
                activeSession = session;
            } catch (SQLException exception) {
                errorLogger.accept("Failed to restore capture runtime: " + exception.getMessage());
                activeSession = null;
            }
        }
    }

    public StartResult start(@Nullable String pointId) {
        synchronized (lock) {
            if (activeSession != null && !activeSession.isTerminal()) {
                return StartResult.alreadyActive(activeSession);
            }
            Instant now = clock.instant();
            String dayKey = dayKey(now);
            Set<String> completed;
            try {
                completed = repository.completedPointIds(dayKey);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to load capture day completions: " + exception.getMessage());
                return StartResult.failed(exception.getMessage());
            }

            if (pointId != null && !pointId.isBlank()) {
                Optional<CapturePointDefinition> requested = config.point(pointId);
                if (requested.isEmpty()) {
                    return StartResult.unknownPoint(pointId);
                }
                if (completed.contains(requested.get().id())) {
                    return StartResult.alreadyCompleted(requested.get().id(), dayKey);
                }
                if (!requested.get().isRegionDefined()) {
                    return StartResult.regionUndefined(requested.get().id());
                }
                return openSession(requested.get(), dayKey, now);
            }

            Optional<CapturePointDefinition> chosen = chooseRandomEligible(completed);
            if (chosen.isEmpty()) {
                return StartResult.noEligiblePoints(dayKey);
            }
            return openSession(chosen.get(), dayKey, now);
        }
    }

    private StartResult openSession(CapturePointDefinition point, String dayKey, Instant now) {
        if (!point.isRegionDefined()) {
            return StartResult.regionUndefined(point.id());
        }

        CaptureSession session = CaptureSession.open(
                UUID.randomUUID(),
                point.id(),
                dayKey,
                config.fullCaptureMillis(1),
                now
        );
        try {
            persistSession(session);
            repository.setRuntime(session.captureId(), false, now);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to start capture session: " + exception.getMessage());
            return StartResult.failed(exception.getMessage());
        }
        activeSession = session;
        fireOpened(session, point);
        return StartResult.started(session, point);
    }

    public AdminResult pause() {
        synchronized (lock) {
            if (activeSession == null || activeSession.isTerminal()) {
                return AdminResult.noActiveSession();
            }
            if (activeSession.isPaused()) {
                return AdminResult.ok(activeSession, "already-paused");
            }
            Instant now = clock.instant();
            activeSession.pause(now);
            try {
                persistSession(activeSession);
                repository.setRuntime(activeSession.captureId(), true, now);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to pause capture: " + exception.getMessage());
                return AdminResult.failed(exception.getMessage());
            }
            fireBroadcast(config.messages().paused());
            return AdminResult.ok(activeSession, "paused");
        }
    }

    public AdminResult resume() {
        synchronized (lock) {
            if (activeSession == null || activeSession.isTerminal()) {
                return AdminResult.noActiveSession();
            }
            if (activeSession.isRunning()) {
                return AdminResult.ok(activeSession, "already-active");
            }
            Instant now = clock.instant();
            activeSession.resume(now);
            try {
                persistSession(activeSession);
                repository.setRuntime(activeSession.captureId(), false, now);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to resume capture: " + exception.getMessage());
                return AdminResult.failed(exception.getMessage());
            }
            fireBroadcast(config.messages().resumed());
            return AdminResult.ok(activeSession, "resumed");
        }
    }

    public AdminResult stop() {
        synchronized (lock) {
            if (activeSession == null || activeSession.isTerminal()) {
                return AdminResult.noActiveSession();
            }
            Instant now = clock.instant();
            activeSession.stop(now);
            try {
                persistSession(activeSession);
                repository.setRuntime(null, false, now);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to stop capture: " + exception.getMessage());
                return AdminResult.failed(exception.getMessage());
            }
            CaptureSession stopped = activeSession;
            activeSession = null;
            fireBroadcast(config.messages().stopped());
            fireStopped(stopped);
            return AdminResult.ok(stopped, "stopped");
        }
    }

    /**
     * Advance progress for the active session. No-op when paused or absent.
     */
    public TickOutcome tick(CaptureAdvantage advantage, long deltaMillis) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.isRunning()) {
                return TickOutcome.idle();
            }
            CaptureProgressEngine.TickResult result = engine.tick(
                    activeSession.progress(), advantage, deltaMillis);
            Instant now = clock.instant();
            activeSession.markUpdated(now);

            CapturePointDefinition point = config.point(activeSession.pointId()).orElse(null);
            String pointName = point == null ? activeSession.pointId() : point.displayName();
            String teamName = teamDisplayName(result.owningTeamId());

            for (int threshold : result.newlyAnnouncedThresholds()) {
                String message = config.messages().threshold()
                        .replace("{point}", pointName)
                        .replace("{team}", teamName)
                        .replace("{percent}", Integer.toString(threshold));
                fireBroadcast(message);
                fireThreshold(activeSession, threshold, result.owningTeamId());
            }

            try {
                persistSession(activeSession);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to persist capture progress: " + exception.getMessage());
            }

            fireProgress(activeSession, advantage);

            if (result.completed() && result.owningTeamId() != null) {
                return completeLocked(result.owningTeamId(), now, point);
            }
            return TickOutcome.progressed(activeSession, result);
        }
    }

    public void flush() {
        synchronized (lock) {
            if (activeSession == null) {
                return;
            }
            try {
                persistSession(activeSession);
                repository.setRuntime(
                        activeSession.isTerminal() ? null : activeSession.captureId(),
                        activeSession.isPaused(),
                        clock.instant());
            } catch (SQLException exception) {
                errorLogger.accept("Failed to flush capture state: " + exception.getMessage());
            }
        }
    }

    public Set<String> completedPointsToday() {
        try {
            return repository.completedPointIds(dayKey(clock.instant()));
        } catch (SQLException exception) {
            errorLogger.accept("Failed to list capture completions: " + exception.getMessage());
            return Set.of();
        }
    }

    public JdbcCaptureRepository repository() {
        return repository;
    }

    private TickOutcome completeLocked(UUID winningTeamId, Instant now, @Nullable CapturePointDefinition point) {
        CaptureSession session = Objects.requireNonNull(activeSession, "activeSession");
        if (session.status() == CaptureSessionStatus.COMPLETED) {
            return TickOutcome.completed(session, null);
        }

        SplitRewardResult payout = money.processCaptureReward(session.captureId().toString(), winningTeamId);
        if (!payout.successful()) {
            errorLogger.accept("Capture payout failed for " + session.captureId() + ": "
                    + payout.status() + " " + payout.failureReason()
                    + " — leaving session active for retry under the same captureId");
            try {
                persistSession(session);
                repository.setRuntime(session.captureId(), session.isPaused(), now);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to persist capture after payout failure: "
                        + exception.getMessage());
            }
            // Keep progress at 100%; subsequent ticks retry processCaptureReward with the same id.
            return TickOutcome.progressed(session, null);
        }

        String outcome = payout.outcome() == null || payout.outcome().isBlank()
                ? EconomyRules.OUTCOME_APPLIED
                : payout.outcome();

        session.complete(winningTeamId, outcome, now);
        try {
            persistSession(session);
            repository.recordDayCompletion(
                    session.dayKey(), session.pointId(), session.captureId(), winningTeamId, now);
            repository.setRuntime(null, false, now);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to finalize capture session: " + exception.getMessage());
        }

        String pointName = point == null ? session.pointId() : point.displayName();
        String teamName = teamDisplayName(winningTeamId);
        fireBroadcast(config.messages().complete()
                .replace("{point}", pointName)
                .replace("{team}", teamName));
        fireCompleted(session, winningTeamId, payout);

        CaptureSession finished = session;
        activeSession = null;

        // Open next incomplete point immediately when any remain.
        StartResult next = start(null);
        return TickOutcome.completed(finished, next);
    }

    private Optional<CapturePointDefinition> chooseRandomEligible(Set<String> completed) {
        List<CapturePointDefinition> eligible = new ArrayList<>();
        for (CapturePointDefinition point : config.points()) {
            if (!completed.contains(point.id()) && point.isRegionDefined()) {
                eligible.add(point);
            }
        }
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        int index = ThreadLocalRandom.current().nextInt(eligible.size());
        return Optional.of(eligible.get(index));
    }

    private void persistSession(CaptureSession session) throws SQLException {
        repository.upsertSession(session);
    }

    private String teamDisplayName(@Nullable UUID teamId) {
        if (teamId == null) {
            return "Unknown";
        }
        return teams.getTeam(teamId).map(Team::displayName).orElse(teamId.toString());
    }

    private static String dayKey(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC).toString();
    }

    private void fireOpened(CaptureSession session, CapturePointDefinition point) {
        String message = config.messages().open().replace("{point}", point.displayName());
        fireBroadcast(message);
        for (CaptureEventListener listener : listeners) {
            listener.onOpened(session, point);
        }
    }

    private void fireStopped(CaptureSession session) {
        for (CaptureEventListener listener : listeners) {
            listener.onStopped(session);
        }
    }

    private void fireProgress(CaptureSession session, CaptureAdvantage advantage) {
        for (CaptureEventListener listener : listeners) {
            listener.onProgress(session, advantage);
        }
    }

    private void fireThreshold(CaptureSession session, int percent, @Nullable UUID teamId) {
        for (CaptureEventListener listener : listeners) {
            listener.onThreshold(session, percent, teamId);
        }
    }

    private void fireCompleted(CaptureSession session, UUID teamId, SplitRewardResult payout) {
        for (CaptureEventListener listener : listeners) {
            listener.onCompleted(session, teamId, payout);
        }
    }

    private void fireBroadcast(String message) {
        for (CaptureEventListener listener : listeners) {
            listener.onBroadcast(message);
        }
    }

    public sealed interface StartResult {
        record Started(CaptureSession session, CapturePointDefinition point) implements StartResult {
        }

        record AlreadyActive(CaptureSession session) implements StartResult {
        }

        record UnknownPoint(String pointId) implements StartResult {
        }

        record RegionUndefined(String pointId) implements StartResult {
        }

        record NoEligiblePoints(String dayKey) implements StartResult {
        }

        record AlreadyCompleted(String pointId, String dayKey) implements StartResult {
        }

        record Failed(String message) implements StartResult {
        }

        static StartResult started(CaptureSession session, CapturePointDefinition point) {
            return new Started(session, point);
        }

        static StartResult alreadyActive(CaptureSession session) {
            return new AlreadyActive(session);
        }

        static StartResult unknownPoint(String pointId) {
            return new UnknownPoint(pointId);
        }

        static StartResult regionUndefined(String pointId) {
            return new RegionUndefined(pointId);
        }

        static StartResult noEligiblePoints(String dayKey) {
            return new NoEligiblePoints(dayKey);
        }

        static StartResult alreadyCompleted(String pointId, String dayKey) {
            return new AlreadyCompleted(pointId, dayKey);
        }

        static StartResult failed(String message) {
            return new Failed(message);
        }
    }

    public sealed interface AdminResult {
        record Ok(CaptureSession session, String action) implements AdminResult {
        }

        record NoActiveSession() implements AdminResult {
        }

        record Failed(String message) implements AdminResult {
        }

        static AdminResult ok(CaptureSession session, String action) {
            return new Ok(session, action);
        }

        static AdminResult noActiveSession() {
            return new NoActiveSession();
        }

        static AdminResult failed(String message) {
            return new Failed(message);
        }
    }

    public record TickOutcome(
            Kind kind,
            @Nullable CaptureSession session,
            @Nullable CaptureProgressEngine.TickResult tickResult,
            @Nullable StartResult nextStart
    ) {
        public enum Kind {
            IDLE,
            PROGRESSED,
            COMPLETED
        }

        static TickOutcome idle() {
            return new TickOutcome(Kind.IDLE, null, null, null);
        }

        static TickOutcome progressed(CaptureSession session, CaptureProgressEngine.TickResult result) {
            return new TickOutcome(Kind.PROGRESSED, session, result, null);
        }

        static TickOutcome completed(CaptureSession session, @Nullable StartResult next) {
            return new TickOutcome(Kind.COMPLETED, session, null, next);
        }
    }

    public interface CaptureEventListener {
        default void onOpened(CaptureSession session, CapturePointDefinition point) {
        }

        default void onProgress(CaptureSession session, CaptureAdvantage advantage) {
        }

        default void onThreshold(CaptureSession session, int percent, @Nullable UUID teamId) {
        }

        default void onCompleted(CaptureSession session, UUID winningTeamId, SplitRewardResult payout) {
        }

        default void onStopped(CaptureSession session) {
        }

        default void onBroadcast(String message) {
        }
    }
}
