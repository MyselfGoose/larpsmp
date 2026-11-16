package com.larpsmp.moneyevent.capture;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * One activation of a named capture point (stable {@code captureId} for economy).
 */
public final class CaptureSession {

    private final UUID captureId;
    private final String pointId;
    private final String dayKey;
    private final Instant startedAt;
    private CaptureSessionStatus status;
    private final CaptureProgressState progress;
    private @Nullable UUID winningTeamId;
    private String settlementOutcome;
    private Instant updatedAt;
    private @Nullable Instant completedAt;

    public CaptureSession(
            UUID captureId,
            String pointId,
            String dayKey,
            CaptureSessionStatus status,
            CaptureProgressState progress,
            @Nullable UUID winningTeamId,
            String settlementOutcome,
            Instant startedAt,
            Instant updatedAt,
            @Nullable Instant completedAt) {
        this.captureId = Objects.requireNonNull(captureId, "captureId");
        this.pointId = Objects.requireNonNull(pointId, "pointId").trim().toLowerCase(java.util.Locale.ROOT);
        this.dayKey = Objects.requireNonNull(dayKey, "dayKey");
        this.status = Objects.requireNonNull(status, "status");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.winningTeamId = winningTeamId;
        this.settlementOutcome = settlementOutcome == null ? "" : settlementOutcome;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.completedAt = completedAt;
    }

    public static CaptureSession open(
            UUID captureId,
            String pointId,
            String dayKey,
            long referenceFullMillis,
            Instant now) {
        return new CaptureSession(
                captureId,
                pointId,
                dayKey,
                CaptureSessionStatus.ACTIVE,
                CaptureProgressState.empty(referenceFullMillis),
                null,
                "",
                now,
                now,
                null
        );
    }

    public UUID captureId() {
        return captureId;
    }

    public String pointId() {
        return pointId;
    }

    public String dayKey() {
        return dayKey;
    }

    public CaptureSessionStatus status() {
        return status;
    }

    public CaptureProgressState progress() {
        return progress;
    }

    public @Nullable UUID winningTeamId() {
        return winningTeamId;
    }

    public String settlementOutcome() {
        return settlementOutcome;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public @Nullable Instant completedAt() {
        return completedAt;
    }

    public boolean isRunning() {
        return status == CaptureSessionStatus.ACTIVE;
    }

    public boolean isPaused() {
        return status == CaptureSessionStatus.PAUSED;
    }

    public boolean isTerminal() {
        return status == CaptureSessionStatus.COMPLETED || status == CaptureSessionStatus.STOPPED;
    }

    void markUpdated(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    void pause(Instant now) {
        if (status == CaptureSessionStatus.ACTIVE) {
            status = CaptureSessionStatus.PAUSED;
            markUpdated(now);
        }
    }

    void resume(Instant now) {
        if (status == CaptureSessionStatus.PAUSED) {
            status = CaptureSessionStatus.ACTIVE;
            markUpdated(now);
        }
    }

    void stop(Instant now) {
        if (!isTerminal()) {
            status = CaptureSessionStatus.STOPPED;
            markUpdated(now);
            completedAt = now;
        }
    }

    void complete(UUID winner, String outcome, Instant now) {
        this.status = CaptureSessionStatus.COMPLETED;
        this.winningTeamId = Objects.requireNonNull(winner, "winner");
        this.settlementOutcome = outcome == null ? "" : outcome;
        this.updatedAt = now;
        this.completedAt = now;
    }
}
