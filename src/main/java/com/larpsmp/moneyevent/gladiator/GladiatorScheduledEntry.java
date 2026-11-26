package com.larpsmp.moneyevent.gladiator;

import java.util.Objects;
import java.util.UUID;

/**
 * One row in the generated 3v3 schedule.
 */
public final class GladiatorScheduledEntry {

    private final int boutIndex;
    private final UUID teamAId;
    private final UUID teamBId;
    private GladiatorScheduleStatus status;

    public GladiatorScheduledEntry(
            int boutIndex,
            UUID teamAId,
            UUID teamBId,
            GladiatorScheduleStatus status
    ) {
        this.boutIndex = boutIndex;
        this.teamAId = Objects.requireNonNull(teamAId, "teamAId");
        this.teamBId = Objects.requireNonNull(teamBId, "teamBId");
        this.status = Objects.requireNonNull(status, "status");
    }

    public int boutIndex() {
        return boutIndex;
    }

    public UUID teamAId() {
        return teamAId;
    }

    public UUID teamBId() {
        return teamBId;
    }

    public GladiatorScheduleStatus status() {
        return status;
    }

    void setStatus(GladiatorScheduleStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }
}
